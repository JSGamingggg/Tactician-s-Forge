package com.example.prefetch

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import coil.Coil
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.data.repository.MetaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class AssetPrefetchExecutionSummary(
    val plannedCount: Int,
    val downloadedCount: Int,
    val skippedCachedCount: Int,
    val failedCount: Int
) {
    val totalReadyCount: Int
        get() = downloadedCount + skippedCachedCount
}

/**
 * Concurrency-bounded asset prefetch executor that skips URLs already present in Coil's
 * persistent DiskCache and warms uncached portraits/icons into disk storage without
 * triggering memory pressure.
 */
object AssetPrefetchExecutor {
    private const val DEFAULT_MAX_CONCURRENCY = 16

    suspend fun prefetchTargets(
        targets: List<PrefetchAssetTarget>,
        isCachedOnDisk: (String) -> Boolean,
        fetchTarget: suspend (PrefetchAssetTarget) -> Boolean,
        maxConcurrency: Int = DEFAULT_MAX_CONCURRENCY,
        onProgress: (suspend (downloaded: Int, skipped: Int, failed: Int, processed: Int) -> Unit)? = null
    ): AssetPrefetchExecutionSummary {
        if (targets.isEmpty()) {
            return AssetPrefetchExecutionSummary(0, 0, 0, 0)
        }

        val downloaded = AtomicInteger(0)
        val skipped = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val processed = AtomicInteger(0)
        val uncachedTargets = ArrayList<PrefetchAssetTarget>(targets.size)

        // Fast disk-cache journal check before spawning concurrent network fetches
        for (target in targets) {
            val cached = runCatching { isCachedOnDisk(target.url) }.getOrDefault(false)
            if (cached) {
                skipped.incrementAndGet()
                val done = processed.incrementAndGet()
                if (onProgress != null && done % 24 == 0) {
                    onProgress(downloaded.get(), skipped.get(), failed.get(), done)
                }
            } else {
                uncachedTargets.add(target)
            }
        }

        if (uncachedTargets.isNotEmpty()) {
            val semaphore = Semaphore(maxConcurrency.coerceAtLeast(1))
            coroutineScope {
                for (target in uncachedTargets) {
                    launch {
                        semaphore.withPermit {
                            val ok = try {
                                fetchTarget(target)
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (_: Throwable) {
                                false
                            }
                            if (ok) {
                                downloaded.incrementAndGet()
                            } else {
                                failed.incrementAndGet()
                            }
                            val done = processed.incrementAndGet()
                            if (onProgress != null && (done % 16 == 0 || done == targets.size)) {
                                onProgress(downloaded.get(), skipped.get(), failed.get(), done)
                            }
                        }
                    }
                }
            }
        } else if (onProgress != null) {
            onProgress(downloaded.get(), skipped.get(), failed.get(), processed.get())
        }

        return AssetPrefetchExecutionSummary(
            plannedCount = targets.size,
            downloadedCount = downloaded.get(),
            skippedCachedCount = skipped.get(),
            failedCount = failed.get()
        )
    }

    @OptIn(ExperimentalCoilApi::class)
    suspend fun prefetchWithCoil(
        context: Context,
        targets: List<PrefetchAssetTarget>,
        imageLoader: ImageLoader = Coil.imageLoader(context.applicationContext),
        warmMemoryCacheCount: Int = 0,
        maxConcurrency: Int = DEFAULT_MAX_CONCURRENCY,
        onProgress: (suspend (downloaded: Int, skipped: Int, failed: Int, processed: Int) -> Unit)? = null
    ): AssetPrefetchExecutionSummary = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val memoryWarmUrls = if (warmMemoryCacheCount > 0) {
            targets.take(warmMemoryCacheCount).mapTo(HashSet(warmMemoryCacheCount)) { it.url }
        } else {
            emptySet()
        }

        prefetchTargets(
            targets = targets,
            isCachedOnDisk = { url ->
                runCatching {
                    imageLoader.diskCache?.openSnapshot(url)?.use { true } == true
                }.getOrDefault(false)
            },
            fetchTarget = { target ->
                val warmMemory = memoryWarmUrls.contains(target.url)
                val request = ImageRequest.Builder(appContext)
                    .data(target.url)
                    .size(target.sizePx)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(if (warmMemory) CachePolicy.ENABLED else CachePolicy.DISABLED)
                    .allowHardware(false)
                    .crossfade(false)
                    .build()
                imageLoader.execute(request) is SuccessResult
            },
            maxConcurrency = maxConcurrency,
            onProgress = onProgress
        )
    }
}

/**
 * WorkManager background worker that prefetches TFT meta snapshots and image assets
 * (champion portraits, trait icons, item icons, and augment icons) into persistent local storage
 * before the user navigates to Meta Snapshots, Tier Lists, Champion Inspector, or Live Overlay.
 *
 * Note: This worker intentionally never dispatches OS notifications; patch/comp alerts remain
 * strictly governed by [com.example.notifications.PatchMonitorWorker] delta detection.
 */
class MetaAssetPrefetchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val scope = PrefetchScope.fromRaw(inputData.getString(KEY_SCOPE))
        val shouldRefreshSnapshot = inputData.getBoolean(KEY_REFRESH_SNAPSHOT, false)
        val maxAssets = inputData.getInt(KEY_MAX_ASSETS, AssetPrefetchPlanner.DEFAULT_MAX_ASSETS)
            .coerceAtLeast(1)
        val warmMemoryCount = inputData.getInt(KEY_WARM_MEMORY_COUNT, 0).coerceAtLeast(0)

        return try {
            val metaRepo = MetaRepository(applicationContext)
            var snapshotRefreshed = false
            var snapshotRefreshFailed = false

            // Always hydrate offline bundle first so TftAssetIconResolver maps are populated
            var bundle = metaRepo.loadOfflineBundleAsync()

            if (shouldRefreshSnapshot) {
                val syncResult = runCatching { metaRepo.refreshAndPersistMeta() }
                if (syncResult.isSuccess) {
                    snapshotRefreshed = syncResult.getOrNull()?.syncedFromNetwork == true
                    bundle = metaRepo.loadOfflineBundleAsync()
                } else {
                    snapshotRefreshFailed = true
                    Log.w(TAG, "Background snapshot refresh failed; falling back to local bundle", syncResult.exceptionOrNull())
                }
            }

            val targets = AssetPrefetchPlanner.buildPrefetchQueue(
                bundle = bundle,
                scope = scope,
                maxAssets = maxAssets
            )
            PrefetchTelemetryStore.markStarted(scope = scope, totalPlanned = targets.size)

            val summary = AssetPrefetchExecutor.prefetchWithCoil(
                context = applicationContext,
                targets = targets,
                warmMemoryCacheCount = warmMemoryCount,
                onProgress = { downloaded, skipped, failed, processed ->
                    PrefetchTelemetryStore.updateProgress(
                        cached = downloaded,
                        skipped = skipped,
                        failed = failed,
                        totalPlanned = targets.size
                    )
                    runCatching {
                        setProgress(
                            workDataOf(
                                OUT_PLANNED_COUNT to targets.size,
                                OUT_DOWNLOADED_COUNT to downloaded,
                                OUT_SKIPPED_CACHED_COUNT to skipped,
                                OUT_FAILED_COUNT to failed,
                                OUT_PROCESSED_COUNT to processed
                            )
                        )
                    }
                }
            )

            PrefetchTelemetryStore.markCompleted(
                context = applicationContext,
                scope = scope,
                cached = summary.downloadedCount,
                skipped = summary.skippedCachedCount,
                failed = summary.failedCount,
                totalPlanned = summary.plannedCount
            )

            val output = workDataOf(
                OUT_PLANNED_COUNT to summary.plannedCount,
                OUT_DOWNLOADED_COUNT to summary.downloadedCount,
                OUT_SKIPPED_CACHED_COUNT to summary.skippedCachedCount,
                OUT_FAILED_COUNT to summary.failedCount,
                OUT_SNAPSHOT_REFRESHED to snapshotRefreshed
            )

            val totalNetworkFailure = snapshotRefreshFailed &&
                summary.downloadedCount == 0 &&
                summary.failedCount > 0 &&
                runAttemptCount < MAX_RETRY_ATTEMPTS

            if (totalNetworkFailure) {
                Result.retry()
            } else {
                Result.success(output)
            }
        } catch (ce: CancellationException) {
            PrefetchTelemetryStore.markIdleOnError()
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "MetaAssetPrefetchWorker failed on attempt $runAttemptCount", t)
            PrefetchTelemetryStore.markIdleOnError()
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "MetaAssetPrefetchWorker"
        private const val MAX_RETRY_ATTEMPTS = 2

        const val KEY_SCOPE = "prefetch_scope"
        const val KEY_REFRESH_SNAPSHOT = "prefetch_refresh_snapshot"
        const val KEY_MAX_ASSETS = "prefetch_max_assets"
        const val KEY_WARM_MEMORY_COUNT = "prefetch_warm_memory_count"

        const val OUT_PLANNED_COUNT = "out_planned_count"
        const val OUT_DOWNLOADED_COUNT = "out_downloaded_count"
        const val OUT_SKIPPED_CACHED_COUNT = "out_skipped_cached_count"
        const val OUT_FAILED_COUNT = "out_failed_count"
        const val OUT_PROCESSED_COUNT = "out_processed_count"
        const val OUT_SNAPSHOT_REFRESHED = "out_snapshot_refreshed"
    }
}

/**
 * Coordinates battery- and storage-aware WorkManager prefetch jobs for meta snapshot JSON
 * and Coil image disk cache warming across app launch, environment switches, post-sync deltas,
 * periodic background intervals, and device reboots.
 */
object MetaPrefetchScheduler {
    const val PERIODIC_PREFETCH_WORK_NAME = "tft_meta_asset_prefetch_periodic"
    const val IMMEDIATE_PREFETCH_WORK_NAME = "tft_meta_asset_prefetch_immediate"
    const val TAG_PREFETCH = "tft_meta_asset_prefetch"

    private const val DEFAULT_PERIODIC_INTERVAL_HOURS = 6L

    fun schedulePeriodicPrefetch(
        context: Context,
        repeatIntervalHours: Long = DEFAULT_PERIODIC_INTERVAL_HOURS
    ) {
        val appContext = context.applicationContext
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val input = workDataOf(
            MetaAssetPrefetchWorker.KEY_SCOPE to PrefetchScope.ALL.name,
            MetaAssetPrefetchWorker.KEY_REFRESH_SNAPSHOT to true,
            MetaAssetPrefetchWorker.KEY_MAX_ASSETS to AssetPrefetchPlanner.DEFAULT_MAX_ASSETS,
            MetaAssetPrefetchWorker.KEY_WARM_MEMORY_COUNT to 0
        )

        val request = PeriodicWorkRequestBuilder<MetaAssetPrefetchWorker>(
            repeatIntervalHours.coerceAtLeast(1L),
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInputData(input)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .addTag(TAG_PREFETCH)
            .build()

        runCatching {
            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                PERIODIC_PREFETCH_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    fun enqueuePrefetch(
        context: Context,
        scope: PrefetchScope = PrefetchScope.ALL,
        refreshSnapshot: Boolean = false,
        maxAssets: Int = if (scope == PrefetchScope.WARM_LAUNCH) {
            AssetPrefetchPlanner.WARM_LAUNCH_MAX_ASSETS
        } else {
            AssetPrefetchPlanner.DEFAULT_MAX_ASSETS
        },
        warmMemoryCount: Int = if (scope == PrefetchScope.WARM_LAUNCH) 36 else 0,
        replaceExisting: Boolean = false,
        initialDelaySeconds: Long = 0L
    ) {
        val appContext = context.applicationContext
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()

        val input = workDataOf(
            MetaAssetPrefetchWorker.KEY_SCOPE to scope.name,
            MetaAssetPrefetchWorker.KEY_REFRESH_SNAPSHOT to refreshSnapshot,
            MetaAssetPrefetchWorker.KEY_MAX_ASSETS to maxAssets,
            MetaAssetPrefetchWorker.KEY_WARM_MEMORY_COUNT to warmMemoryCount
        )

        val builder = OneTimeWorkRequestBuilder<MetaAssetPrefetchWorker>()
            .setConstraints(constraints)
            .setInputData(input)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
            .addTag(TAG_PREFETCH)

        if (initialDelaySeconds > 0L) {
            builder.setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
        }

        runCatching {
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                IMMEDIATE_PREFETCH_WORK_NAME,
                if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                builder.build()
            )
        }
    }
}

package com.example

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.network.RiotApiClientFactory
import com.example.data.repository.MetaRepository
import com.example.notifications.PatchMonitorWorker
import com.example.notifications.TftNotificationHelper
import com.example.prefetch.MetaPrefetchScheduler
import com.example.prefetch.PrefetchScope
import com.example.prefetch.PrefetchTelemetryStore
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TacticiansForgeApp : Application(), Configuration.Provider, ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { RiotApiClientFactory.publicOkHttp }
            .allowRgb565(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.24)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("tft_image_cache"))
                    .maxSizeBytes(96L * 1024L * 1024L)
                    .build()
            }
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }

    private fun isRobolectricRuntime(): Boolean =
        Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

    override fun onCreate() {
        super.onCreate()
        TftNotificationHelper.ensureChannels(this)
        PrefetchTelemetryStore.hydrate(this)
        if (!isRobolectricRuntime()) {
            if (FirebaseApp.getApps(this).isEmpty()) {
                runCatching { FirebaseApp.initializeApp(this) }
            }
            schedulePatchMonitor()
            subscribeToFcmTopics()
            scheduleBackgroundPrefetchOnLaunch()
        }
    }

    private fun scheduleBackgroundPrefetchOnLaunch() {
        if (isRobolectricRuntime()) return
        // Schedule battery- and storage-aware periodic background prefetching via WorkManager
        MetaPrefetchScheduler.schedulePeriodicPrefetch(this)

        appScope.launch {
            // Immediately warm the shared in-memory bundle & icon maps off the main thread
            val repo = MetaRepository(applicationContext)
            runCatching { repo.loadOfflineBundleAsync() }

            // Warm local image cache from existing offline bundle without triggering an immediate update check
            MetaPrefetchScheduler.enqueuePrefetch(
                context = applicationContext,
                scope = PrefetchScope.WARM_LAUNCH,
                refreshSnapshot = false,
                warmMemoryCount = 36,
                replaceExisting = false,
                initialDelaySeconds = 1L
            )
        }
    }

    private fun schedulePatchMonitor() {
        PatchMonitorWorker.schedulePeriodicAndChainedBackgroundChecks(
            context = this,
            triggerImmediateCheck = false
        )
    }

    private fun subscribeToFcmTopics() {
        if (FirebaseApp.getApps(this).isEmpty()) return
        runCatching {
            Firebase.messaging.subscribeToTopic("tft_patches")
            Firebase.messaging.subscribeToTopic("tft_trending_comps")
            Firebase.messaging.subscribeToTopic("tft_app_updates")
        }.onFailure {
            Log.d("TacticiansForgeApp", "FCM topic subscription deferred: ${it.message}")
        }
    }

    companion object {
        const val PATCH_MONITOR_WORK_NAME = PatchMonitorWorker.PERIODIC_WORK_NAME
    }
}

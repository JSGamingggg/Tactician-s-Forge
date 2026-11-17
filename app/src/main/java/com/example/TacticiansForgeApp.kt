package com.example

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.repository.MetaRepository
import com.example.data.repository.OfflineMetaBundle
import com.example.notifications.PatchMonitorWorker
import com.example.notifications.TftNotificationHelper
import com.example.ui.components.TftAssetIconResolver
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class TacticiansForgeApp : Application(), Configuration.Provider, ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.28)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("tft_image_cache"))
                    .maxSizeBytes(64L * 1024L * 1024L)
                    .build()
            }
            .crossfade(120)
            .respectCacheHeaders(false)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        TftNotificationHelper.ensureChannels(this)
        schedulePatchMonitor()
        subscribeToFcmTopics()
        preloadEverythingOnLaunch()
    }

    private fun preloadEverythingOnLaunch() {
        appScope.launch {
            val repo = MetaRepository(applicationContext)
            val initialBundle = runCatching { repo.loadOfflineBundleAsync() }.getOrNull()
            if (initialBundle != null) {
                prefetchBundleIcons(initialBundle)
            }
            runCatching { repo.refreshAndPersistMeta() }
            val refreshedBundle = runCatching { repo.loadOfflineBundleAsync() }.getOrNull()
            if (refreshedBundle != null) {
                prefetchBundleIcons(refreshedBundle)
            }
        }
    }

    private fun prefetchBundleIcons(bundle: OfflineMetaBundle) {
        val imageLoader = Coil.imageLoader(this)
        val urls = LinkedHashSet<String>()
        (bundle.liveComps + bundle.pbeComps).forEach { comp ->
            TftAssetIconResolver.resolveChampionIconUrl(comp.carryName, comp.environment)?.let(urls::add)
            comp.coreUnits.forEach { unit ->
                TftAssetIconResolver.resolveChampionIconUrl(unit, comp.environment)?.let(urls::add)
            }
            comp.hexPlacements.forEach { placement ->
                TftAssetIconResolver.resolveChampionIconUrl(placement.unitName, comp.environment)?.let(urls::add)
                placement.items.forEach { item ->
                    TftAssetIconResolver.resolveItemIconUrl(item)?.let(urls::add)
                }
            }
            comp.traits.forEach { trait ->
                TftAssetIconResolver.resolveTraitIconUrl(trait, comp.environment)?.let(urls::add)
            }
            comp.augmentPriorities.forEach { aug ->
                TftAssetIconResolver.resolveAugmentIconUrl(aug)?.let(urls::add)
            }
        }
        bundle.liveChampions.forEach { champ ->
            TftAssetIconResolver.resolveChampionIconUrl(champ.name)?.let(urls::add)
            champ.traits.forEach { trait ->
                TftAssetIconResolver.resolveTraitIconUrl(trait)?.let(urls::add)
            }
        }
        bundle.liveItems.take(60).forEach { item ->
            TftAssetIconResolver.resolveItemIconUrl(item.name)?.let(urls::add)
        }
        bundle.liveAugments.take(40).forEach { aug ->
            TftAssetIconResolver.resolveAugmentIconUrl(aug.name)?.let(urls::add)
        }
        urls.take(240).forEach { url ->
            val request = ImageRequest.Builder(this)
                .data(url)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            imageLoader.enqueue(request)
        }
    }

    private fun schedulePatchMonitor() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<PatchMonitorWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PATCH_MONITOR_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun subscribeToFcmTopics() {
        if (FirebaseApp.getApps(this).isEmpty()) return
        runCatching {
            Firebase.messaging.subscribeToTopic("tft_patches")
            Firebase.messaging.subscribeToTopic("tft_trending_comps")
        }.onFailure {
            Log.d("TacticiansForgeApp", "FCM topic subscription deferred: ${it.message}")
        }
    }

    companion object {
        const val PATCH_MONITOR_WORK_NAME = "tft_patch_monitor_worker"
    }
}

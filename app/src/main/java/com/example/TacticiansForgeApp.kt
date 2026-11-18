package com.example

import android.app.Application
import android.os.Build
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
import com.example.data.model.TftEnvironment
import com.example.data.network.RiotApiClientFactory
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
import kotlinx.coroutines.delay
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
                    .maxSizeBytes(64L * 1024L * 1024L)
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
        if (!isRobolectricRuntime()) {
            if (FirebaseApp.getApps(this).isEmpty()) {
                runCatching { FirebaseApp.initializeApp(this) }
            }
            schedulePatchMonitor()
            subscribeToFcmTopics()
            preloadEverythingOnLaunch()
        }
    }

    private fun preloadEverythingOnLaunch() {
        if (isRobolectricRuntime()) return
        appScope.launch {
            // Immediately warm the shared in-memory bundle & icon maps off the main thread
            val repo = MetaRepository(applicationContext)
            val initialBundle = runCatching { repo.loadOfflineBundleAsync() }.getOrNull() ?: return@launch
            // Wait briefly so initial screen composition & metadata sync get full bandwidth first
            delay(1_200L)
            prefetchTopIcons(initialBundle)
        }
    }

    private fun prefetchTopIcons(bundle: OfflineMetaBundle) {
        val imageLoader = Coil.imageLoader(this)
        val urls = LinkedHashSet<String>(64)
        (bundle.liveComps.take(10) + bundle.pbeComps.take(8)).forEach { comp ->
            TftAssetIconResolver.resolveChampionIconUrl(comp.carryName, comp.environment)?.let(urls::add)
            comp.coreUnits.take(6).forEach { unit ->
                TftAssetIconResolver.resolveChampionIconUrl(unit, comp.environment)?.let(urls::add)
            }
            comp.hexPlacements.firstOrNull { it.isCarry }?.items?.take(3)?.forEach { item ->
                TftAssetIconResolver.resolveItemIconUrl(item, comp.environment)?.let(urls::add)
            }
        }
        urls.take(48).forEach { url ->
            val request = ImageRequest.Builder(this)
                .data(url)
                .size(96)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            imageLoader.enqueue(request)
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

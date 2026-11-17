package com.example

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.notifications.PatchMonitorWorker
import com.example.notifications.TftNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import java.util.concurrent.TimeUnit

class TacticiansForgeApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        TftNotificationHelper.ensureChannels(this)
        schedulePatchMonitor()
        subscribeToFcmTopics()
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

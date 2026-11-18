package com.example.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.repository.MetaRepository
import com.example.update.AppUpdateCoordinator
import java.util.concurrent.TimeUnit

class PatchMonitorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        TftNotificationHelper.ensureChannels(applicationContext)

        runCatching {
            val metaRepo = MetaRepository(applicationContext)
            val syncResult = metaRepo.refreshAndPersistMeta()
            val bundle = metaRepo.loadOfflineBundle()

            MetaUpdateNotifier.dispatchNotifications(
                context = applicationContext,
                syncResult = syncResult,
                bundle = bundle
            )
        }

        runCatching {
            AppUpdateCoordinator.checkForUpdates(
                context = applicationContext,
                userInitiated = false,
                showInAppModalOnLaunch = false,
                dispatchOsNotification = true
            )
        }

        runCatching {
            scheduleChainedFollowUp(applicationContext)
        }

        return Result.success()
    }

    companion object {
        const val PERIODIC_WORK_NAME = "tft_patch_monitor_worker"
        const val CHAINED_WORK_NAME = "tft_patch_monitor_chained_worker"
        const val IMMEDIATE_WORK_NAME = "tft_patch_monitor_immediate_worker"

        fun schedulePeriodicAndChainedBackgroundChecks(
            context: Context,
            triggerImmediateCheck: Boolean = false
        ) {
            val appContext = context.applicationContext
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<PatchMonitorWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            val workManager = WorkManager.getInstance(appContext)
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )

            scheduleChainedFollowUp(appContext)

            if (triggerImmediateCheck) {
                val immediateRequest = OneTimeWorkRequestBuilder<PatchMonitorWorker>()
                    .setConstraints(constraints)
                    .build()
                workManager.enqueueUniqueWork(
                    IMMEDIATE_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    immediateRequest
                )
            }
        }

        private fun scheduleChainedFollowUp(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val followUpRequest = OneTimeWorkRequestBuilder<PatchMonitorWorker>()
                .setInitialDelay(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                CHAINED_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                followUpRequest
            )
        }
    }
}


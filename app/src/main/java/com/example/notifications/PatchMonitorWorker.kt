package com.example.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.repository.MetaRepository
import com.example.update.AppUpdateCoordinator
import java.util.concurrent.TimeUnit

class PatchMonitorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        TftNotificationHelper.ensureChannels(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, TftNotificationHelper.CHANNEL_OVERLAY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText("Checking TFT patches, compositions & app updates...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return ForegroundInfo(BACKGROUND_SYNC_NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result {
        TftNotificationHelper.ensureChannels(applicationContext)

        var metaSyncedFromNetwork = false
        val metaResult = runCatching {
            val metaRepo = MetaRepository(applicationContext)
            val syncResult = metaRepo.refreshAndPersistMeta()
            metaSyncedFromNetwork = syncResult.syncedFromNetwork
            val bundle = metaRepo.loadOfflineBundle()

            MetaUpdateNotifier.dispatchNotifications(
                context = applicationContext,
                syncResult = syncResult,
                bundle = bundle
            )
        }

        val appUpdateResult = runCatching {
            AppUpdateCoordinator.checkForUpdates(
                context = applicationContext,
                userInitiated = false,
                showInAppModalOnLaunch = false,
                dispatchOsNotification = true
            )
        }

        runCatching {
            scheduleChainedFollowUp(applicationContext, replaceExisting = true)
            BackgroundSyncAlarmReceiver.scheduleNext(applicationContext, replaceExisting = true)
        }

        val shouldRetry = (metaResult.isFailure || !metaSyncedFromNetwork || appUpdateResult.isFailure) &&
            runAttemptCount < MAX_RETRY_ATTEMPTS
        return if (shouldRetry) Result.retry() else Result.success()
    }

    companion object {
        const val PERIODIC_WORK_NAME = "tft_patch_monitor_worker"
        const val CHAINED_WORK_NAME = "tft_patch_monitor_chained_worker"
        const val IMMEDIATE_WORK_NAME = "tft_patch_monitor_immediate_worker"
        private const val BACKGROUND_SYNC_NOTIFICATION_ID = 40_101
        private const val MAX_RETRY_ATTEMPTS = 2

        private fun connectedConstraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedulePeriodicAndChainedBackgroundChecks(
            context: Context,
            triggerImmediateCheck: Boolean = false,
            replaceChained: Boolean = false
        ) {
            val appContext = context.applicationContext
            val constraints = connectedConstraints()

            val periodicRequest = PeriodicWorkRequestBuilder<PatchMonitorWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            val workManager = WorkManager.getInstance(appContext)
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )

            scheduleChainedFollowUp(appContext, replaceExisting = replaceChained)
            BackgroundSyncAlarmReceiver.scheduleNext(appContext, replaceExisting = false)

            if (triggerImmediateCheck) {
                enqueueImmediateCheck(appContext, expedited = true, replaceExisting = false)
            }
        }

        fun enqueueImmediateCheck(
            context: Context,
            expedited: Boolean = true,
            replaceExisting: Boolean = false
        ) {
            val appContext = context.applicationContext
            val builder = OneTimeWorkRequestBuilder<PatchMonitorWorker>()
                .setConstraints(connectedConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 3, TimeUnit.MINUTES)
            if (expedited) {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }

            WorkManager.getInstance(appContext).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                builder.build()
            )
        }

        private fun scheduleChainedFollowUp(
            context: Context,
            replaceExisting: Boolean
        ) {
            val followUpRequest = OneTimeWorkRequestBuilder<PatchMonitorWorker>()
                .setInitialDelay(15, TimeUnit.MINUTES)
                .setConstraints(connectedConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                CHAINED_WORK_NAME,
                if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                followUpRequest
            )
        }
    }
}

package com.example.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.repository.MetaRepository
import com.example.update.AppUpdateCoordinator

class PatchMonitorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val metaRepo = MetaRepository(applicationContext)
        val syncResult = metaRepo.refreshAndPersistMeta()
        val bundle = metaRepo.loadOfflineBundle()

        MetaUpdateNotifier.dispatchNotifications(
            context = applicationContext,
            syncResult = syncResult,
            bundle = bundle
        )

        AppUpdateCoordinator.checkForUpdates(
            context = applicationContext,
            userInitiated = false,
            showInAppModalOnLaunch = false,
            dispatchOsNotification = true
        )
        return Result.success()
    }
}

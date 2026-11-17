package com.example.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.repository.MetaRepository

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
        return Result.success()
    }
}

package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.prefetch.MetaPrefetchScheduler

/**
 * Restores background monitoring for new app updates, compositions, LIVE/PBE patches,
 * and WorkManager meta/image prefetching across device reboots and APK upgrades.
 */
class BackgroundSyncBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val appContext = context.applicationContext
            TftNotificationHelper.ensureChannels(appContext)
            BackgroundSyncAlarmReceiver.scheduleNext(appContext, replaceExisting = true)
            PatchMonitorWorker.schedulePeriodicAndChainedBackgroundChecks(
                context = appContext,
                triggerImmediateCheck = true,
                replaceChained = false
            )
            PatchMonitorWorker.enqueueImmediateCheck(
                context = appContext,
                expedited = true,
                replaceExisting = false
            )
            MetaPrefetchScheduler.schedulePeriodicPrefetch(appContext)
        }
    }
}

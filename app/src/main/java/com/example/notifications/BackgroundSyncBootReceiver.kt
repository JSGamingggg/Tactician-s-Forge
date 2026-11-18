package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restores background monitoring for new app updates, compositions, and LIVE/PBE patches
 * across device reboots and in-place APK upgrades even before the user opens the app.
 */
class BackgroundSyncBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val appContext = context.applicationContext
            TftNotificationHelper.ensureChannels(appContext)
            PatchMonitorWorker.schedulePeriodicAndChainedBackgroundChecks(
                context = appContext,
                triggerImmediateCheck = true
            )
        }
    }
}

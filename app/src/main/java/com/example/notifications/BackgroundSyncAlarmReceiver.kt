package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Doze-friendly inexact wakeup backup alarm that periodically wakes [PatchMonitorWorker]
 * even when deferred WorkManager jobs are throttled by OEM battery managers or while the app
 * UI is unloaded or swiped from Recents.
 */
class BackgroundSyncAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != null && action != ACTION_BACKGROUND_SYNC_ALARM) return

        val appContext = context.applicationContext
        TftNotificationHelper.ensureChannels(appContext)
        scheduleNext(appContext, replaceExisting = true)
        PatchMonitorWorker.schedulePeriodicAndChainedBackgroundChecks(
            context = appContext,
            triggerImmediateCheck = true,
            replaceChained = false
        )
    }

    companion object {
        const val ACTION_BACKGROUND_SYNC_ALARM = "com.example.notifications.ACTION_BACKGROUND_SYNC_ALARM"
        private const val REQUEST_CODE = 40_901
        const val INTERVAL_MILLIS = 15 * 60_000L
        private const val PREFS_NAME = "tft_alarm_guard_prefs"
        private const val KEY_LAST_ARMED_WALL_MS = "last_armed_wall_ms"
        private const val STALE_ALARM_MS = 20 * 60_000L

        private fun createBroadcastIntent(context: Context): Intent =
            Intent(context, BackgroundSyncAlarmReceiver::class.java).apply {
                action = ACTION_BACKGROUND_SYNC_ALARM
            }

        fun isAlarmScheduled(context: Context): Boolean {
            val appContext = context.applicationContext
            return PendingIntent.getBroadcast(
                appContext,
                REQUEST_CODE,
                createBroadcastIntent(appContext),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) != null
        }

        fun scheduleNext(context: Context, replaceExisting: Boolean = false) {
            val appContext = context.applicationContext
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = createBroadcastIntent(appContext)

            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val nowWall = System.currentTimeMillis()
            val lastArmed = prefs.getLong(KEY_LAST_ARMED_WALL_MS, 0L)
            val isStale = lastArmed <= 0L || (nowWall - lastArmed) > STALE_ALARM_MS

            if (!replaceExisting && !isStale && isAlarmScheduled(appContext)) {
                return
            }

            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MILLIS
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
                prefs.edit().putLong(KEY_LAST_ARMED_WALL_MS, nowWall).apply()
            }
        }
    }
}

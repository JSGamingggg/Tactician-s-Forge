package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.TftEnvironment

object TftNotificationHelper {
    const val CHANNEL_PATCH_UPDATES = "tft_patch_updates"
    const val CHANNEL_TRENDING_COMPS = "tft_trending_comps"
    const val CHANNEL_OVERLAY = "tft_live_overlay"
    const val CHANNEL_APP_UPDATES = "tft_app_updates"

    const val EXTRA_NAV_TAB = "extra_nav_tab"
    const val EXTRA_ENVIRONMENT = "extra_environment"
    const val EXTRA_OPEN_APP_UPDATE = "extra_open_app_update"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val patchChannel = NotificationChannel(
            CHANNEL_PATCH_UPDATES,
            context.getString(R.string.channel_patch_updates_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_patch_updates_desc)
            enableLights(true)
            enableVibration(true)
        }

        val trendingChannel = NotificationChannel(
            CHANNEL_TRENDING_COMPS,
            context.getString(R.string.channel_trending_comps_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_trending_comps_desc)
            enableLights(true)
            enableVibration(true)
        }

        val overlayChannel = NotificationChannel(
            CHANNEL_OVERLAY,
            context.getString(R.string.channel_overlay_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_overlay_desc)
            setShowBadge(false)
        }

        val appUpdatesChannel = NotificationChannel(
            CHANNEL_APP_UPDATES,
            context.getString(R.string.channel_app_updates_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_app_updates_desc)
            enableLights(true)
            enableVibration(true)
        }

        manager.createNotificationChannels(
            listOf(patchChannel, trendingChannel, overlayChannel, appUpdatesChannel)
        )
    }

    fun hasNotificationPermission(context: Context): Boolean {
        val notificationsEnabled = runCatching {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrDefault(true)
        if (!notificationsEnabled) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun postPatchOrBalanceNotification(
        context: Context,
        title: String,
        shortBody: String,
        expandedBody: String,
        environment: TftEnvironment,
        notificationId: Int = (System.currentTimeMillis() % 100_000).toInt()
    ): Boolean {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_TAB, "alerts")
            putExtra(EXTRA_ENVIRONMENT, environment.name)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PATCH_UPDATES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(shortBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun postTrendingCompNotification(
        context: Context,
        compTitle: String,
        environment: TftEnvironment,
        summary: String,
        notificationId: Int = 20_401
    ): Boolean {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_TAB, "meta")
            putExtra(EXTRA_ENVIRONMENT, environment.name)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_TRENDING_COMPS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${environment.label} New Comp: $compTitle")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun postNewCompsBatchNotification(
        context: Context,
        title: String,
        shortBody: String,
        expandedBody: String,
        environment: TftEnvironment,
        notificationId: Int = 20_402
    ): Boolean {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_TAB, "meta")
            putExtra(EXTRA_ENVIRONMENT, environment.name)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_TRENDING_COMPS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(shortBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun postAppUpdateNotification(
        context: Context,
        release: com.example.update.GitHubReleaseInfo,
        notificationId: Int = 30_901
    ): Boolean {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_APP_UPDATE, true)
            putExtra(EXTRA_NAV_TAB, "alerts")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sizeLabel = com.example.update.AppUpdateCoordinator.formatMegabytes(release.apkSizeBytes)
        val shortSummary = buildString {
            append("${release.displayVersionLabel} is ready on GitHub")
            if (sizeLabel.isNotBlank()) append(" ($sizeLabel)")
            append(". Tap to update in-app without reinstalling.")
        }
        val expandedSummary = buildString {
            append(shortSummary)
            val cleanNotes = release.body.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(4)
                .joinToString("\n")
            if (cleanNotes.isNotBlank()) {
                append("\n\n")
                append(cleanNotes)
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_APP_UPDATES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("App Update Ready: ${release.displayVersionLabel}")
            .setContentText(shortSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedSummary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Update In-App", pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}

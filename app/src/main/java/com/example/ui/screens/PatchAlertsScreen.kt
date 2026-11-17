package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.R
import com.example.data.model.LiveVersionInfo
import com.example.data.model.PatchUpdateRecord
import com.example.data.model.TftEnvironment
import com.example.data.model.ThemeMode
import com.example.data.model.UserPreferencesDoc
import com.example.notifications.PatchMonitorWorker
import com.example.notifications.TftNotificationHelper
import com.example.overlay.OverlayController
import com.example.ui.auth.CloudSyncStatusCard
import com.example.ui.components.DeltaBadge
import com.example.ui.components.EnvironmentToggleBar
import com.example.ui.components.HextechPanelCard
import com.example.ui.components.MadeByAttributionBadge
import com.example.ui.components.TftAugmentIcon
import com.example.ui.components.TftChampionAvatar
import com.example.ui.components.TftItemIcon
import com.example.ui.components.environmentSlideTransitionSpec
import com.google.firebase.auth.FirebaseUser

@Composable
fun PatchAlertsScreen(
    environment: TftEnvironment,
    onSelectEnvironment: (TftEnvironment) -> Unit,
    patches: List<PatchUpdateRecord>,
    preferences: UserPreferencesDoc,
    liveVersionInfo: LiveVersionInfo?,
    isRefreshingVersions: Boolean,
    themeMode: ThemeMode,
    isOverlayRunning: Boolean,
    currentUser: FirebaseUser?,
    onRefreshVersions: () -> Unit,
    onUpdateAlertSettings: (Boolean?, Boolean?, Boolean?, Boolean?) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onToggleOverlay: () -> Unit,
    onShowMessage: (String) -> Unit,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasNotifPerm by remember { mutableStateOf(TftNotificationHelper.hasNotificationPermission(context)) }
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasNotifPerm = granted
            onShowMessage(if (granted) "Patch notifications enabled." else "Notifications permission denied.")
        }
    )

    val canDrawOverlay = OverlayController.canDrawOverlays(context)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Patches, Alerts & Control",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = liveVersionInfo?.let {
                            "LIVE ${it.livePatchVersion} • PBE ${it.pbePatchVersion}"
                        } ?: "Checking https://tftips.app/en/patches...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = stringResource(R.string.auto_sync_cadence_summary),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = {
                        onRefreshVersions()
                        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<PatchMonitorWorker>().build())
                    },
                    enabled = !isRefreshingVersions,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("refresh_patches_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync patches and comps", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sync")
                }
            }
        }

        // LIVE vs PBE Patch Feed Switcher
        item {
            EnvironmentToggleBar(
                selected = environment,
                onSelect = onSelectEnvironment,
                liveLabel = liveVersionInfo?.livePatchVersion ?: "18.6b Live",
                pbeLabel = liveVersionInfo?.pbePatchVersion ?: "18.7 PBE",
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Push Notification Control Card
        item {
            HextechPanelCard(accentBorder = true) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Patch & Balance Push Notifications",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Button(
                            onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("grant_notifications_button")
                        ) {
                            Text("Enable OS Push Notifications")
                        }
                    }

                    AlertToggleRow(
                        title = "LIVE Game Patch Alerts",
                        subtitle = "Alert immediately when a new LIVE patch or hotfix ships",
                        checked = preferences.patchAlertsEnabled,
                        onCheckedChange = { onUpdateAlertSettings(it, null, null, null) }
                    )
                    AlertToggleRow(
                        title = "PBE Patch Deploy Alerts",
                        subtitle = "Alert when experimental PBE cycles update",
                        checked = preferences.pbeAlertsEnabled,
                        onCheckedChange = { onUpdateAlertSettings(null, it, null, null) }
                    )
                    AlertToggleRow(
                        title = "Significant Balance Changes",
                        subtitle = "Champion/trait buffs & nerfs summary alerts",
                        checked = preferences.balanceAlertsEnabled,
                        onCheckedChange = { onUpdateAlertSettings(null, null, it, null) }
                    )
                    AlertToggleRow(
                        title = "Trending S-Tier Comps",
                        subtitle = "Alert when a composition surges in Top-4 rate",
                        checked = preferences.trendingAlertsEnabled,
                        onCheckedChange = { onUpdateAlertSettings(null, null, null, it) }
                    )

                    OutlinedButton(
                        onClick = {
                            if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val patch = patches.firstOrNull()
                                val posted = TftNotificationHelper.postPatchOrBalanceNotification(
                                    context = context,
                                    title = "${environment.label} Patch ${patch?.version ?: "18.6b"} Alert",
                                    shortBody = patch?.headline ?: "New balance updates live.",
                                    expandedBody = buildString {
                                        append(patch?.headline ?: "Balance updates ready.")
                                        patch?.balanceChanges?.take(3)?.forEach {
                                            append("\n• ${it.deltaType.label} ${it.targetName}: ${it.previousValue} -> ${it.newValue}")
                                        }
                                    },
                                    environment = environment
                                )
                                onShowMessage(
                                    if (posted) "Dispatched ${environment.label} patch notification!"
                                    else "Notification blocked — check Android notification permissions."
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("test_patch_notification_button")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Send Instant ${environment.label} Patch Alert")
                    }
                }
            }
        }

        // Live Match Overlay Card
        item {
            HextechPanelCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text(
                            text = "Live-Match Floating Overlay",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Shows a draggable Hextech bubble over TFT Mobile that expands into your pinned favorites & 4×7 hex boards.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!canDrawOverlay) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("grant_overlay_permission_button")
                        ) {
                            Text("Grant 'Display Over Other Apps' Permission")
                        }
                    } else {
                        Button(
                            onClick = onToggleOverlay,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("overlay_service_toggle_button")
                        ) {
                            Text(if (isOverlayRunning) "Stop Live Match Overlay" else "Launch Floating Bubble Overlay")
                        }
                    }
                }
            }
        }

        // Patch Notes & Balance Deltas for selected LIVE / PBE
        item {
            AnimatedContent(
                targetState = environment,
                transitionSpec = environmentSlideTransitionSpec(),
                label = "patchFeedHeaderTransition"
            ) { activeEnv ->
                Text(
                    text = if (activeEnv == TftEnvironment.LIVE) "LIVE Patch & Balance Feed" else "PBE Experimental Balance Feed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        items(patches, key = { it.id }) { patch ->
            PatchUpdateCard(
                patch = patch,
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(220),
                    fadeOutSpec = tween(160),
                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
            )
        }

        // Appearance Mode Selector
        item {
            HextechPanelCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Appearance Theme",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                label = { Text(mode.label.substringBefore(" ")) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Optional Google Sign-In Cloud Sync
        item {
            CloudSyncStatusCard(
                currentUser = currentUser,
                onAuthSuccess = onAuthSuccess,
                onAuthError = onAuthError,
                onSignedOut = onSignedOut
            )
        }

        // App Credits & Live Sync Cadence Footer
        item {
            HextechPanelCard(accentBorder = true) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MadeByAttributionBadge()
                    Text(
                        text = stringResource(R.string.auto_sync_cadence_summary),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Patches: tftips.app/en/patches • LIVE: tftacademy.com • PBE: tftactics.gg",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AlertToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PatchUpdateCard(
    patch: PatchUpdateRecord,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    HextechPanelCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${patch.environment.label} ${patch.version}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = patch.releaseDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(patch.sourceUrl)))
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "tftips.app",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open patch notes on tftips.app/en/patches",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = patch.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = patch.headline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            patch.highlights.forEach { hl ->
                Text(
                    text = "• $hl",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (patch.balanceChanges.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                patch.balanceChanges.forEach { change ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            DeltaBadge(change.deltaType)
                            when {
                                change.category.contains("Champion", ignoreCase = true) ||
                                    change.category.contains("Unit", ignoreCase = true) -> {
                                    TftChampionAvatar(
                                        name = change.targetName,
                                        cost = 4,
                                        size = 24.dp
                                    )
                                }
                                change.category.contains("Item", ignoreCase = true) -> {
                                    TftItemIcon(
                                        itemName = change.targetName,
                                        size = 22.dp
                                    )
                                }
                                change.category.contains("Augment", ignoreCase = true) -> {
                                    TftAugmentIcon(
                                        augmentName = change.targetName,
                                        size = 22.dp
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "${change.targetName} (${change.category})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = change.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "${change.previousValue} -> ${change.newValue}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

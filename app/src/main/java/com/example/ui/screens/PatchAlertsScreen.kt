package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.R
import com.example.data.model.PatchUpdateRecord
import com.example.data.model.TftEnvironment
import com.example.data.model.ThemeMode
import com.example.data.model.UserPreferencesDoc
import com.example.notifications.PatchMonitorWorker
import com.example.notifications.TftNotificationHelper
import com.example.overlay.OverlayController
import com.example.ui.auth.CloudSyncStatusCard
import com.example.ui.components.AppUpdateSettingsCard
import com.example.ui.components.HextechPanelCard
import com.example.ui.components.MadeByAttributionBadge
import com.example.update.AppUpdateUiState
import com.example.update.GitHubReleaseInfo
import com.google.firebase.auth.FirebaseUser

@Composable
fun AboutScreen(
    environment: TftEnvironment,
    patches: List<PatchUpdateRecord>,
    preferences: UserPreferencesDoc,
    themeMode: ThemeMode,
    isOverlayRunning: Boolean,
    currentUser: FirebaseUser?,
    appUpdateState: AppUpdateUiState,
    onCheckAppUpdate: () -> Unit,
    onSaveGithubRepo: (String) -> Unit,
    onToggleAppUpdateAlerts: (Boolean) -> Unit,
    onDownloadAndInstallUpdate: (GitHubReleaseInfo) -> Unit = {},
    onInstallReadyUpdate: () -> Unit = {},
    onOpenInstallPermission: () -> Unit = {},
    onOpenPlayStoreUpdate: () -> Unit,
    onOpenGitHubReleaseUpdate: (GitHubReleaseInfo?) -> Unit,
    onOpenAppUpdateDialog: () -> Unit,
    onUpdateAlertSettings: (Boolean?, Boolean?, Boolean?, Boolean?) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onToggleOverlay: () -> Unit,
    onShowMessage: (String) -> Unit,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onSignedOut: () -> Unit,
    onClearLocalData: () -> Unit = {},
    onDeleteAccountData: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasNotifPerm by remember { mutableStateOf(TftNotificationHelper.hasNotificationPermission(context)) }
    var deniedPromptOnce by rememberSaveable { mutableStateOf(false) }
    var showPrivacyDialog by rememberSaveable { mutableStateOf(false) }
    var showClearLocalDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteAccountDialog by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifPerm = TftNotificationHelper.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val openNotificationSettings: () -> Unit = {
        val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fallbackIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(settingsIntent) }
            .onFailure { runCatching { context.startActivity(fallbackIntent) } }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasNotifPerm = granted && TftNotificationHelper.hasNotificationPermission(context)
            if (granted) {
                deniedPromptOnce = false
                PatchMonitorWorker.schedulePeriodicAndChainedBackgroundChecks(context)
                PatchMonitorWorker.enqueueImmediateCheck(context, expedited = true)
                onShowMessage("Notifications enabled.")
            } else {
                deniedPromptOnce = true
                onShowMessage("Notifications disabled — tap again to open Android Notification Settings.")
            }
        }
    )

    val requestOrOpenNotificationSettings: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !deniedPromptOnce && !hasNotifPerm) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
        }
    }

    val canDrawOverlay = OverlayController.canDrawOverlays(context)

    val notificationsCard: @Composable (Modifier) -> Unit = { cardModifier ->
        HextechPanelCard(
            accentBorder = true,
            modifier = cardModifier
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                        Icon(
                            imageVector = if (hasNotifPerm) {
                                Icons.Default.NotificationsActive
                            } else {
                                Icons.Default.NotificationsOff
                            },
                            contentDescription = null,
                            tint = if (hasNotifPerm) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = "Notifications",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (hasNotifPerm) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        }
                    ) {
                        Text(
                            text = if (hasNotifPerm) "Enabled" else "Off",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (hasNotifPerm) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (!hasNotifPerm) {
                    Button(
                        onClick = requestOrOpenNotificationSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("grant_notifications_button")
                    ) {
                        Text(
                            if (deniedPromptOnce || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                "Open Notification Settings"
                            } else {
                                "Enable Notifications"
                            }
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = openNotificationSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("manage_notifications_button")
                    ) {
                        Text("Manage System Notification Settings")
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
                        if (!hasNotifPerm) {
                            requestOrOpenNotificationSettings()
                        } else {
                            val patch = patches.firstOrNull()
                            val posted = TftNotificationHelper.postPatchOrBalanceNotification(
                                context = context,
                                title = "${environment.label} Patch ${patch?.version ?: "18.7"} Alert",
                                shortBody = patch?.headline ?: "New balance updates live.",
                                expandedBody = buildString {
                                    append(patch?.headline ?: "Balance updates ready.")
                                    patch?.balanceChanges?.take(4)?.forEach {
                                        val valSuffix = if (it.previousValue.isNotBlank() && it.newValue.isNotBlank()) {
                                            "${it.previousValue} → ${it.newValue}"
                                        } else it.summary
                                        append("\n• ${it.deltaType.label} ${it.targetName}: $valSuffix")
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
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Send Test ${environment.label} Notification")
                }
            }
        }
    }

    val overlayCard: @Composable (Modifier) -> Unit = { cardModifier ->
        HextechPanelCard(modifier = cardModifier) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text(
                        text = "Live-Match Floating Overlay",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Shows a draggable TFT's Helper bubble over TFT Mobile that expands into your pinned favorites, unit items & level guides.",
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

    val themeCard: @Composable () -> Unit = {
        HextechPanelCard {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideLandscape = maxWidth >= 680.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // About Header
            item {
                HextechPanelCard(accentBorder = true) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_tfts_helper_emblem),
                            contentDescription = "TFT's Helper Emblem",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "About ${stringResource(R.string.app_name)}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • TFT Companion",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = stringResource(R.string.auto_sync_cadence_summary),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // App Updates & Notifications (side-by-side in wide landscape)
            if (isWideLandscape) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        notificationsCard(Modifier.weight(1f))
                        AppUpdateSettingsCard(
                            updateState = appUpdateState,
                            onCheckForUpdate = onCheckAppUpdate,
                            onSaveGithubRepo = onSaveGithubRepo,
                            onToggleUpdateAlerts = onToggleAppUpdateAlerts,
                            onDownloadAndInstall = onDownloadAndInstallUpdate,
                            onInstallReady = onInstallReadyUpdate,
                            onOpenInstallPermission = onOpenInstallPermission,
                            onOpenPlayStore = onOpenPlayStoreUpdate,
                            onOpenGitHubUpdate = onOpenGitHubReleaseUpdate,
                            onOpenUpdateDialog = onOpenAppUpdateDialog,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                item { notificationsCard(Modifier.fillMaxWidth()) }
                item {
                    AppUpdateSettingsCard(
                        updateState = appUpdateState,
                        onCheckForUpdate = onCheckAppUpdate,
                        onSaveGithubRepo = onSaveGithubRepo,
                        onToggleUpdateAlerts = onToggleAppUpdateAlerts,
                        onDownloadAndInstall = onDownloadAndInstallUpdate,
                        onInstallReady = onInstallReadyUpdate,
                        onOpenInstallPermission = onOpenInstallPermission,
                        onOpenPlayStore = onOpenPlayStoreUpdate,
                        onOpenGitHubUpdate = onOpenGitHubReleaseUpdate,
                        onOpenUpdateDialog = onOpenAppUpdateDialog
                    )
                }
            }

            // Live Match Overlay + Appearance / Cloud Sync
            if (isWideLandscape) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        overlayCard(Modifier.weight(1f))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            themeCard()
                            CloudSyncStatusCard(
                                currentUser = currentUser,
                                onAuthSuccess = onAuthSuccess,
                                onAuthError = onAuthError,
                                onSignedOut = onSignedOut
                            )
                        }
                    }
                }
            } else {
                item { overlayCard(Modifier.fillMaxWidth()) }
                item { themeCard() }
                item {
                    CloudSyncStatusCard(
                        currentUser = currentUser,
                        onAuthSuccess = onAuthSuccess,
                        onAuthError = onAuthError,
                        onSignedOut = onSignedOut
                    )
                }
            }

            // Privacy & Account Data Controls (Google Play & App Store Data Safety / Account Deletion)
            item {
                HextechPanelCard(
                    modifier = Modifier.testTag("privacy_data_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Policy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.privacy_data_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = stringResource(R.string.privacy_data_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { showPrivacyDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("view_privacy_policy_button")
                        ) {
                            Text(stringResource(R.string.view_privacy_policy))
                        }
                        OutlinedButton(
                            onClick = { showClearLocalDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("clear_local_data_button")
                        ) {
                            Text(stringResource(R.string.clear_local_data))
                        }
                        if (currentUser != null) {
                            OutlinedButton(
                                onClick = { showDeleteAccountDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("delete_account_data_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.delete_account_data))
                            }
                        }
                    }
                }
            }

            // Riot Games Terms of Service & Fair Play Compliance
            item {
                HextechPanelCard(
                    modifier = Modifier.testTag("riot_tos_compliance_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = stringResource(R.string.riot_tos_fair_play_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = stringResource(R.string.riot_tos_fair_play_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.riot_legal_disclaimer),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Credits Footer
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
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Policy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(stringResource(R.string.privacy_data_title)) },
            text = {
                Text(
                    text = stringResource(R.string.privacy_policy_details),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showClearLocalDialog) {
        AlertDialog(
            onDismissRequest = { showClearLocalDialog = false },
            title = { Text("Clear Local Data?") },
            text = {
                Text(
                    text = "This resets local theme, overlay opacity, and guest favorites stored on this device. Cloud-synced favorites are unaffected.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearLocalDialog = false
                        onClearLocalData()
                    }
                ) {
                    Text("Clear Local Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLocalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Account & Cloud Data?") },
            text = {
                Text(
                    text = "This permanently deletes your Firebase account, all cloud-synced favorite compositions, and saved cloud preferences. This action cannot be undone.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        onDeleteAccountData()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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

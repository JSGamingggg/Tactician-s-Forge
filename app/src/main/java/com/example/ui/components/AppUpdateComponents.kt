package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.update.AppUpdateCoordinator
import com.example.update.AppUpdateStatus
import com.example.update.AppUpdateUiState
import com.example.update.GitHubReleaseInfo
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@Composable
fun AppUpdateInAppBanner(
    updateState: AppUpdateUiState,
    onOpenDetails: () -> Unit,
    onStartUpdate: (GitHubReleaseInfo) -> Unit,
    onInstallReadyApk: (File, GitHubReleaseInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        scope.launch {
            AppUpdateCoordinator.resumePendingInstallIfPermitted(context)
        }
    }

    val status = updateState.status
    val visible = status is AppUpdateStatus.UpdateAvailable ||
        status is AppUpdateStatus.Downloading ||
        status is AppUpdateStatus.ReadyToInstall

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            onClick = onOpenDetails,
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("in_app_update_banner")
        ) {
            when (status) {
                is AppUpdateStatus.UpdateAvailable -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "App update available",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Update Available • ${status.release.displayVersionLabel}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "New GitHub release ready — updates in-place without losing data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = { onStartUpdate(status.release) },
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("banner_update_now_button")
                        ) {
                            Text("Update")
                        }
                    }
                }

                is AppUpdateStatus.Downloading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Downloading ${status.release.displayVersionLabel}...",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${(status.progress * 100f).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                is AppUpdateStatus.ReadyToInstall -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.InstallMobile,
                            contentDescription = "Ready to install update",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (status.needsInstallPermission) {
                                    "Allow app installs to finish updating"
                                } else {
                                    "Update Ready: ${status.release.displayVersionLabel}"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Tap Install to apply over current app cleanly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        Button(
                            onClick = {
                                if (status.needsInstallPermission) {
                                    installPermissionLauncher.launch(
                                        AppUpdateCoordinator.buildInstallPermissionSettingsIntent(context)
                                    )
                                } else {
                                    onInstallReadyApk(File(status.apkFilePath), status.release)
                                }
                            },
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(if (status.needsInstallPermission) "Allow" else "Install")
                        }
                    }
                }

                else -> Unit
            }
        }
    }
}

@Composable
fun AppUpdatePromptDialog(
    updateState: AppUpdateUiState,
    onDismissLater: () -> Unit,
    onDownloadAndInstall: (GitHubReleaseInfo) -> Unit,
    onInstallReadyApk: (File, GitHubReleaseInfo) -> Unit
) {
    if (!updateState.showPromptModal) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        scope.launch {
            AppUpdateCoordinator.resumePendingInstallIfPermitted(context)
        }
    }

    val status = updateState.status
    val release = when (status) {
        is AppUpdateStatus.UpdateAvailable -> status.release
        is AppUpdateStatus.Downloading -> status.release
        is AppUpdateStatus.ReadyToInstall -> status.release
        is AppUpdateStatus.Error -> status.release
        else -> null
    } ?: return

    val sizeLabel = remember(release.apkSizeBytes) {
        AppUpdateCoordinator.formatMegabytes(release.apkSizeBytes)
    }

    AlertDialog(
        onDismissRequest = {
            if (status !is AppUpdateStatus.Downloading) onDismissLater()
        },
        icon = {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "New GitHub Release Available",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${release.displayVersionLabel}  •  ${release.apkAssetName}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Installed: ${updateState.installedVersionLabel}" +
                                if (sizeLabel.isNotBlank()) " • Download: $sizeLabel" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Updates directly in-place inside the app — your saved comps, favorites, and overlay settings stay intact.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (release.body.isNotBlank()) {
                    Text(
                        text = release.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when (status) {
                    is AppUpdateStatus.Downloading -> {
                        val dlMb = AppUpdateCoordinator.formatMegabytes(status.bytesDownloaded)
                        val totMb = AppUpdateCoordinator.formatMegabytes(status.totalBytes)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                progress = { status.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Downloading inside app: ${(status.progress * 100f).roundToInt()}%" +
                                    if (dlMb.isNotBlank() && totMb.isNotBlank()) " ($dlMb / $totMb)" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    is AppUpdateStatus.ReadyToInstall -> {
                        Text(
                            text = if (status.needsInstallPermission) {
                                "Tap 'Allow & Install' and enable 'Allow from this source' once so Android can apply the update in-place."
                            } else {
                                "Package verified! Tap 'Install Update' to apply without uninstalling."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    is AppUpdateStatus.Error -> {
                        Text(
                            text = status.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (status) {
                is AppUpdateStatus.Downloading -> {
                    Button(onClick = {}, enabled = false) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Downloading...")
                    }
                }

                is AppUpdateStatus.ReadyToInstall -> {
                    Button(
                        onClick = {
                            if (status.needsInstallPermission) {
                                installPermissionLauncher.launch(
                                    AppUpdateCoordinator.buildInstallPermissionSettingsIntent(context)
                                )
                            } else {
                                onInstallReadyApk(File(status.apkFilePath), status.release)
                            }
                        },
                        modifier = Modifier.testTag("dialog_install_update_button")
                    ) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (status.needsInstallPermission) "Allow & Install" else "Install Update")
                    }
                }

                else -> {
                    Button(
                        onClick = { onDownloadAndInstall(release) },
                        modifier = Modifier.testTag("dialog_update_now_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (status is AppUpdateStatus.Error) "Retry Update" else "Update Now")
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (release.htmlUrl.isNotBlank() && status !is AppUpdateStatus.Downloading) {
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
                            }
                        }
                    ) {
                        Text("Release Page")
                    }
                }
                if (status !is AppUpdateStatus.Downloading) {
                    TextButton(
                        onClick = onDismissLater,
                        modifier = Modifier.testTag("dialog_update_later_button")
                    ) {
                        Text("Later")
                    }
                }
            }
        }
    )
}

@Composable
fun AppUpdateSettingsCard(
    updateState: AppUpdateUiState,
    onCheckForUpdate: () -> Unit,
    onSaveGithubRepo: (String) -> Unit,
    onToggleUpdateAlerts: (Boolean) -> Unit,
    onStartUpdate: (GitHubReleaseInfo) -> Unit,
    onOpenUpdateDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingRepo by remember(updateState.configuredRepo) {
        mutableStateOf(updateState.configuredRepo.isNullOrBlank())
    }
    var repoInput by remember(updateState.configuredRepo) {
        mutableStateOf(updateState.configuredRepo.orEmpty())
    }

    HextechPanelCard(accentBorder = true, modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "In-App GitHub Self-Updater",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val commitTag = updateState.installedCommitShort?.let { " • $it" }.orEmpty()
                        Text(
                            text = "Installed: ${updateState.installedVersionLabel}$commitTag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                if (!updateState.configuredRepo.isNullOrBlank() && !editingRepo) {
                    IconButton(
                        onClick = { editingRepo = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit GitHub repository slug",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (editingRepo || updateState.configuredRepo.isNullOrBlank()) {
                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text("GitHub Repository (owner/repo or URL)") },
                    placeholder = { Text("username/tacticians-forge") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_repo_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onSaveGithubRepo(repoInput)
                            editingRepo = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("save_github_repo_button")
                    ) {
                        Text("Save & Check Releases")
                    }
                    if (!updateState.configuredRepo.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { editingRepo = false },
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            } else {
                Text(
                    text = "Source: github.com/${updateState.configuredRepo}/releases",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "GitHub Release Update Alerts",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Notify inside the app on open & send OS push notification when a new release is pushed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = updateState.appUpdateAlertsEnabled,
                    onCheckedChange = onToggleUpdateAlerts
                )
            }

            when (val status = updateState.status) {
                is AppUpdateStatus.Checking -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Checking GitHub Releases...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                is AppUpdateStatus.UpToDate -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Up to date on latest GitHub release (${status.currentVersionLabel})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                is AppUpdateStatus.UpdateAvailable -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "New Release Ready: ${status.release.displayVersionLabel}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onStartUpdate(status.release) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Update Now")
                                }
                                OutlinedButton(
                                    onClick = onOpenUpdateDialog,
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text("Details")
                                }
                            }
                        }
                    }
                }

                is AppUpdateStatus.Downloading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Downloading update: ${(status.progress * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                is AppUpdateStatus.ReadyToInstall -> {
                    Button(
                        onClick = onOpenUpdateDialog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Finish Installing ${status.release.displayVersionLabel}")
                    }
                }

                is AppUpdateStatus.Error -> {
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                AppUpdateStatus.RepoNotConfigured -> {
                    Text(
                        text = "Enter your GitHub repository above (or build via GitHub Actions to auto-configure it).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AppUpdateStatus.Idle -> Unit
            }

            if (!updateState.configuredRepo.isNullOrBlank()) {
                OutlinedButton(
                    onClick = onCheckForUpdate,
                    enabled = updateState.status !is AppUpdateStatus.Checking &&
                        updateState.status !is AppUpdateStatus.Downloading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("check_app_update_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Check GitHub for App Update")
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.update.AppUpdateCoordinator
import com.example.update.AppUpdateStatus
import com.example.update.AppUpdateUiState
import com.example.update.GitHubReleaseInfo
import kotlinx.coroutines.launch

@Composable
fun AppUpdateInAppBanner(
    updateState: AppUpdateUiState,
    onOpenDetails: () -> Unit,
    onDownloadAndInstall: (GitHubReleaseInfo) -> Unit,
    onInstallReady: () -> Unit,
    onOpenStoreUpdate: (GitHubReleaseInfo) -> Unit,
    modifier: Modifier = Modifier
) {
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
                                text = if (updateState.installedFromGooglePlay) {
                                    "Update via Google Play or install directly in-app."
                                } else {
                                    "Download & install directly in-app or open GitHub."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = {
                                if (updateState.installedFromGooglePlay) {
                                    onOpenStoreUpdate(status.release)
                                } else {
                                    onDownloadAndInstall(status.release)
                                }
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("banner_update_now_button")
                        ) {
                            Text("Update")
                        }
                    }
                }

                is AppUpdateStatus.Downloading -> {
                    val pct = (status.progress * 100f).toInt().coerceIn(0, 100)
                    val downloadedLabel = AppUpdateCoordinator.formatMegabytes(status.bytesDownloaded)
                    val totalLabel = AppUpdateCoordinator.formatMegabytes(status.totalBytes)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Downloading update",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Downloading ${status.release.displayVersionLabel} • $pct%",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (downloadedLabel.isNotBlank() && totalLabel.isNotBlank()) {
                                Text(
                                    text = "$downloadedLabel / $totalLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                        if (status.totalBytes > 0L) {
                            LinearProgressIndicator(
                                progress = { status.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
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
                            contentDescription = "Update ready to install",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ready to Install • ${status.release.displayVersionLabel}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (status.needsInstallPermission) {
                                    "Allow install permission to finish updating."
                                } else {
                                    "APK verified — tap Install to update in-app."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = onInstallReady,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("banner_install_now_button")
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
    onInstallReady: () -> Unit,
    onOpenPlayStore: () -> Unit,
    onOpenGitHubUpdate: (GitHubReleaseInfo) -> Unit
) {
    if (!updateState.showPromptModal) return

    val status = updateState.status
    val release = when (status) {
        is AppUpdateStatus.UpdateAvailable -> status.release
        is AppUpdateStatus.Downloading -> status.release
        is AppUpdateStatus.ReadyToInstall -> status.release
        is AppUpdateStatus.Error -> status.release
        else -> null
    } ?: return

    val fromPlay = updateState.installedFromGooglePlay
    val apkSizeLabel = AppUpdateCoordinator.formatMegabytes(release.apkSizeBytes)

    AlertDialog(
        onDismissRequest = {
            if (status !is AppUpdateStatus.Downloading) {
                onDismissLater()
            }
        },
        icon = {
            Icon(
                imageVector = when (status) {
                    is AppUpdateStatus.Downloading -> Icons.Default.Download
                    is AppUpdateStatus.ReadyToInstall -> Icons.Default.InstallMobile
                    else -> Icons.Default.SystemUpdate
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = when (status) {
                    is AppUpdateStatus.Downloading -> "Downloading Update"
                    is AppUpdateStatus.ReadyToInstall -> "Ready to Install"
                    else -> "App Update Available"
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
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
                        val sizeSuffix = if (apkSizeLabel.isNotBlank()) " • $apkSizeLabel" else ""
                        Text(
                            text = "${release.displayVersionLabel}$sizeSuffix",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Installed: ${updateState.installedVersionLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Install directly in-app from GitHub Releases or open in your browser — your saved comps, favorites, and overlay settings stay intact.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (status is AppUpdateStatus.Downloading) {
                    val pct = (status.progress * 100f).toInt().coerceIn(0, 100)
                    val dlLabel = AppUpdateCoordinator.formatMegabytes(status.bytesDownloaded)
                    val totLabel = AppUpdateCoordinator.formatMegabytes(status.totalBytes)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (dlLabel.isNotBlank() && totLabel.isNotBlank()) {
                                "Downloading APK… $pct% ($dlLabel / $totLabel)"
                            } else {
                                "Downloading APK… $pct%"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (status.totalBytes > 0L) {
                            LinearProgressIndicator(
                                progress = { status.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                if (status is AppUpdateStatus.ReadyToInstall) {
                    if (status.signatureMismatchDetected) {
                        Text(
                            text = "Note: This APK uses a different signing key than the currently installed build. Android may require uninstalling the current build first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (status.needsInstallPermission) {
                        Text(
                            text = "Download complete! Tap Allow & Install to grant Android permission to install updates from TFT's Helper.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        Text(
                            text = "Download complete and verified! Tap Install Update to launch Android's package installer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
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

                if (status is AppUpdateStatus.Error) {
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            when (status) {
                is AppUpdateStatus.Downloading -> {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.testTag("dialog_update_downloading_button")
                    ) {
                        Text("Downloading…")
                    }
                }

                is AppUpdateStatus.ReadyToInstall -> {
                    Button(
                        onClick = onInstallReady,
                        modifier = Modifier.testTag("dialog_install_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.InstallMobile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (status.needsInstallPermission) "Allow & Install" else "Install Update")
                    }
                }

                else -> {
                    Button(
                        onClick = {
                            if (fromPlay) onOpenPlayStore() else onDownloadAndInstall(release)
                        },
                        modifier = Modifier.testTag("dialog_update_now_button")
                    ) {
                        Icon(
                            imageVector = if (fromPlay) Icons.AutoMirrored.Filled.OpenInNew else Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (fromPlay) "Google Play" else "Download & Install")
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onOpenGitHubUpdate(release) },
                    modifier = Modifier.testTag("dialog_update_alt_source_button")
                ) {
                    Text("GitHub Web")
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
    onDownloadAndInstall: (GitHubReleaseInfo) -> Unit,
    onInstallReady: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    onOpenPlayStore: () -> Unit,
    onOpenGitHubUpdate: (GitHubReleaseInfo?) -> Unit,
    onOpenUpdateDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editingRepo by remember(updateState.configuredRepo) {
        mutableStateOf(updateState.configuredRepo.isNullOrBlank())
    }
    var repoInput by remember(updateState.configuredRepo) {
        mutableStateOf(updateState.configuredRepo.orEmpty())
    }
    var tokenInput by remember(updateState.configuredToken) {
        mutableStateOf(updateState.configuredToken.orEmpty())
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
                            text = "App Updates",
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
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit GitHub release feed repository",
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
                    label = { Text("GitHub Repository (owner/repo)") },
                    placeholder = { Text("JSGamingggg/Tactician-s-Forge") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_repo_input")
                )
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("Read Token (Optional, Private repos only)") },
                    placeholder = { Text("github_pat_... (Contents: Read)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_token_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                AppUpdateCoordinator.saveGithubRepoSlug(
                                    context = context,
                                    rawInput = repoInput,
                                    rawTokenInput = tokenInput
                                )
                                onSaveGithubRepo(repoInput)
                                editingRepo = false
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_github_repo_button")
                    ) {
                        Text("Save & Check")
                    }
                    if (!updateState.configuredRepo.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { editingRepo = false },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            } else {
                Text(
                    text = "Source: github.com/${updateState.configuredRepo}/releases • In-app APK updater & GitHub Web",
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
                        text = "App Release Alerts",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Automatically notify when a newer version of TFT's Helper is released",
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
                            text = "Checking for app updates…",
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
                            text = "Up to date (${status.currentVersionLabel})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                is AppUpdateStatus.UpdateAvailable -> {
                    val sizeLabel = AppUpdateCoordinator.formatMegabytes(status.release.apkSizeBytes)
                    val sizeSuffix = if (sizeLabel.isNotBlank()) " • $sizeLabel" else ""
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
                                text = "New Version Available: ${status.release.displayVersionLabel}$sizeSuffix",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = { onDownloadAndInstall(status.release) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("card_update_in_app_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Update In-App")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onOpenGitHubUpdate(status.release) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("card_update_github_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("GitHub Web")
                                }
                                OutlinedButton(
                                    onClick = onOpenPlayStore,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("card_update_play_store_button")
                                ) {
                                    Text("Google Play")
                                }
                            }
                            TextButton(
                                onClick = onOpenUpdateDialog,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("View Release Notes")
                            }
                        }
                    }
                }

                is AppUpdateStatus.Downloading -> {
                    val pct = (status.progress * 100f).toInt().coerceIn(0, 100)
                    val dlLabel = AppUpdateCoordinator.formatMegabytes(status.bytesDownloaded)
                    val totLabel = AppUpdateCoordinator.formatMegabytes(status.totalBytes)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Downloading ${status.release.displayVersionLabel} • $pct%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            if (status.totalBytes > 0L) {
                                LinearProgressIndicator(
                                    progress = { status.progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            if (dlLabel.isNotBlank() && totLabel.isNotBlank()) {
                                Text(
                                    text = "$dlLabel / $totLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                is AppUpdateStatus.ReadyToInstall -> {
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
                                text = "Ready to Install: ${status.release.displayVersionLabel}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            if (status.signatureMismatchDetected) {
                                Text(
                                    text = "Signing key differs from the current install; Android may ask to uninstall first.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onInstallReady,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("card_install_ready_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.InstallMobile,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (status.needsInstallPermission) "Allow & Install" else "Install Update")
                                }
                                if (status.needsInstallPermission) {
                                    OutlinedButton(
                                        onClick = onOpenInstallPermission,
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("card_open_install_permission_button")
                                    ) {
                                        Text("Settings")
                                    }
                                }
                            }
                        }
                    }
                }

                is AppUpdateStatus.Error -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = status.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        if (status.release != null) {
                            Button(
                                onClick = { onDownloadAndInstall(status.release) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("card_retry_in_app_update_button")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry In-App Update")
                            }
                        }
                        if (status.needsTokenOrPublicRepo && !editingRepo) {
                            Button(
                                onClick = { editingRepo = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("add_github_token_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Configure Release Feed", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                AppUpdateStatus.RepoNotConfigured -> {
                    Text(
                        text = "Configure your GitHub repository above to enable version checks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> Unit
            }

            if (!updateState.configuredRepo.isNullOrBlank()) {
                OutlinedButton(
                    onClick = onCheckForUpdate,
                    enabled = updateState.status !is AppUpdateStatus.Checking &&
                        updateState.status !is AppUpdateStatus.Downloading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("check_app_update_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.check_for_app_updates))
                }
                OutlinedButton(
                    onClick = {
                        val currentRelease = when (val st = updateState.status) {
                            is AppUpdateStatus.UpdateAvailable -> st.release
                            is AppUpdateStatus.Downloading -> st.release
                            is AppUpdateStatus.ReadyToInstall -> st.release
                            is AppUpdateStatus.Error -> st.release
                            else -> null
                        }
                        onOpenGitHubUpdate(currentRelease)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_github_releases_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Open GitHub Releases")
                }
            }
        }
    }
}

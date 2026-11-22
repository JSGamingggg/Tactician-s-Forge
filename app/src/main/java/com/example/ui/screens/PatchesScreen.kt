package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.LiveVersionInfo
import com.example.data.model.PatchUpdateRecord
import com.example.data.model.TftEnvironment
import com.example.notifications.PatchMonitorWorker
import com.example.ui.components.DeltaBadge
import com.example.ui.components.EnvironmentToggleBar
import com.example.ui.components.HextechPanelCard
import com.example.ui.components.PatchTierAlertBanner
import com.example.ui.components.PatchTierDetailsDialog
import com.example.ui.components.TftAugmentIcon
import com.example.ui.components.TftChampionAvatar
import com.example.ui.components.TftItemIcon
import com.example.ui.components.environmentSlideTransitionSpec
import com.example.ui.components.sanitizeExternalSourceMentions
import com.example.ui.theme.LocalHextechColors

@Composable
fun PatchesScreen(
    environment: TftEnvironment,
    onSelectEnvironment: (TftEnvironment) -> Unit,
    patches: List<PatchUpdateRecord>,
    liveVersionInfo: LiveVersionInfo?,
    isRefreshingVersions: Boolean,
    onRefreshVersions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uniquePatches = remember(patches) {
        patches.distinctBy { it.id.trim().lowercase().ifBlank { "${it.environment.name}_${it.version.lowercase()}" } }
    }
    val latestPatch = uniquePatches.firstOrNull()
    var showPatchDetailsDialog by remember { mutableStateOf(false) }

    if (showPatchDetailsDialog) {
        PatchTierDetailsDialog(
            patch = latestPatch,
            onDismiss = { showPatchDetailsDialog = false }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideLandscape = maxWidth >= 680.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Patch Header (moved off Meta header onto dedicated Patches tab)
            item {
                val badgeColor = if (environment == TftEnvironment.LIVE) {
                    LocalHextechColors.current.liveBadge
                } else {
                    LocalHextechColors.current.pbeBadge
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Patches",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = badgeColor.copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, badgeColor),
                                modifier = Modifier.testTag("patches_active_version_badge")
                            ) {
                                Text(
                                    text = if (environment == TftEnvironment.LIVE) {
                                        "LIVE • ${liveVersionInfo?.livePatchVersion ?: "18.7"}"
                                    } else {
                                        "PBE • ${liveVersionInfo?.pbePatchVersion ?: "19.1-PBE"}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(
                            text = liveVersionInfo?.let {
                                "LIVE ${it.livePatchVersion} • PBE ${it.pbePatchVersion}"
                            } ?: "LIVE & PBE patch notes and balance changes",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            onRefreshVersions()
                            PatchMonitorWorker.enqueueImmediateCheck(
                                context = context,
                                expedited = true,
                                replaceExisting = true
                            )
                        },
                        enabled = !isRefreshingVersions,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("refresh_patches_button")
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Check for patch updates",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Sync")
                    }
                }
            }

            // Latest Patch Spotlight Banner (moved off Meta header)
            if (latestPatch != null) {
                item {
                    PatchTierAlertBanner(
                        latestPatch = latestPatch,
                        environment = environment,
                        onOpenDetails = { showPatchDetailsDialog = true }
                    )
                }
            }

            // LIVE vs PBE Patch Feed Switcher
            item {
                EnvironmentToggleBar(
                    selected = environment,
                    onSelect = onSelectEnvironment,
                    liveLabel = liveVersionInfo?.livePatchVersion ?: "18.7 Live",
                    pbeLabel = liveVersionInfo?.pbePatchVersion ?: "19.1-PBE",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Section Title
            item {
                Text(
                    text = if (environment == TftEnvironment.LIVE) {
                        "LIVE Patch & Balance Feed"
                    } else {
                        "PBE Experimental Balance Feed"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Patch Update Cards
            if (isWideLandscape && uniquePatches.size > 1) {
                itemsIndexed(
                    items = uniquePatches.chunked(2),
                    key = { rowIndex, row -> "row_${rowIndex}_${row.joinToString("_") { it.id }}" }
                ) { _, rowPatches ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        rowPatches.forEach { patch ->
                            PatchUpdateCard(
                                patch = patch,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowPatches.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = uniquePatches,
                    key = { index, patch -> "${patch.id}_$index" }
                ) { _, patch ->
                    PatchUpdateCard(patch = patch)
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
internal fun PatchUpdateCard(
    patch: PatchUpdateRecord,
    modifier: Modifier = Modifier
) {
    val cleanReleaseDate = remember(patch.releaseDate) {
        sanitizeExternalSourceMentions(patch.releaseDate).ifBlank { patch.releaseDate }
    }
    val cleanHeadline = remember(patch.headline, patch.version) {
        sanitizeExternalSourceMentions(patch.headline).ifBlank { "Patch ${patch.version} balance updates." }
    }
    val cleanHighlights = remember(patch.highlights) {
        patch.highlights.map { sanitizeExternalSourceMentions(it) }.filter { it.isNotBlank() }
    }
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
                Text(
                    text = cleanReleaseDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = patch.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = cleanHeadline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            cleanHighlights.forEach { hl ->
                Text(
                    text = "• $hl",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (patch.balanceChanges.isNotEmpty()) {
                val maxInitialChanges = 12
                var showAllChanges by rememberSaveable(patch.id) { mutableStateOf(false) }
                val displayedChanges = if (showAllChanges || patch.balanceChanges.size <= maxInitialChanges) {
                    patch.balanceChanges
                } else {
                    patch.balanceChanges.take(maxInitialChanges)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                displayedChanges.forEach { change ->
                    val diffText = remember(change.previousValue, change.newValue) {
                        val prev = change.previousValue.replace(Regex("\\s+"), " ").trim()
                        val next = change.newValue.replace(Regex("\\s+"), " ").trim()
                        when {
                            prev.isNotBlank() && next.isNotBlank() -> "$prev → $next"
                            next.isNotBlank() -> next
                            prev.isNotBlank() -> prev
                            else -> ""
                        }
                    }
                    val cleanSummary = remember(change.summary) {
                        change.summary.replace(Regex("\\s+"), " ").trim()
                    }
                    val showInlineDiff = diffText.isNotBlank() && diffText.length <= 20

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            DeltaBadge(change.deltaType)
                            when {
                                change.category.contains("Champion", ignoreCase = true) ||
                                    change.category.contains("Unit", ignoreCase = true) -> {
                                    TftChampionAvatar(
                                        name = change.targetName,
                                        cost = 4,
                                        size = 24.dp,
                                        environment = patch.environment
                                    )
                                }
                                change.category.contains("Item", ignoreCase = true) -> {
                                    TftItemIcon(
                                        itemName = change.targetName,
                                        size = 22.dp,
                                        environment = patch.environment
                                    )
                                }
                                change.category.contains("Augment", ignoreCase = true) -> {
                                    TftAugmentIcon(
                                        augmentName = change.targetName,
                                        size = 22.dp
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${change.targetName} (${change.category})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (showInlineDiff) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                        modifier = Modifier.widthIn(max = 132.dp)
                                    ) {
                                        Text(
                                            text = diffText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (diffText.isNotBlank() && !showInlineDiff) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                ) {
                                    Text(
                                        text = diffText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (cleanSummary.isNotBlank()) {
                                Text(
                                    text = cleanSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (patch.balanceChanges.size > maxInitialChanges) {
                    TextButton(
                        onClick = { showAllChanges = !showAllChanges },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("toggle_all_patch_changes_${patch.id}")
                    ) {
                        Text(
                            text = if (showAllChanges) {
                                "Show fewer changes"
                            } else {
                                "Show all ${patch.balanceChanges.size} changes"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

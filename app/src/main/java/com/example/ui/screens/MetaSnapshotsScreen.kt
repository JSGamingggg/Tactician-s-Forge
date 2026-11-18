package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.FavoriteCompDoc
import com.example.data.model.LiveVersionInfo
import com.example.data.model.MetaComp
import com.example.data.model.OfflineCacheStatus
import com.example.data.model.PatchUpdateRecord
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank
import com.example.ui.components.EnvironmentToggleBar
import com.example.ui.components.HextechEmptyState
import com.example.ui.components.HextechPanelCard
import com.example.ui.components.OfflineCacheStatusBanner
import com.example.ui.components.PatchTierAlertBanner
import com.example.ui.components.PatchTierDetailsDialog
import com.example.ui.components.TftAugmentChip
import com.example.ui.components.TftChampionAvatar
import com.example.ui.components.TftChampionTile
import com.example.ui.components.TftHexBoard
import com.example.ui.components.TftItemChip
import com.example.ui.components.TftTraitChip
import com.example.ui.components.TierBadge
import com.example.ui.components.environmentSlideTransitionSpec
import com.example.ui.theme.LocalHextechColors
import com.example.ui.viewmodel.UiState
import java.util.Locale

internal fun groupCompsByTier(comps: List<MetaComp>): List<Pair<TierRank, List<MetaComp>>> {
    if (comps.isEmpty()) return emptyList()
    return TierRank.entries
        .sortedBy { it.sortOrder }
        .mapNotNull { tier ->
            val tierComps = comps
                .filter { it.tier == tier }
                .sortedWith(compareBy<MetaComp> { it.avgPlacement }.thenBy { it.title })
            if (tierComps.isEmpty()) null else tier to tierComps
        }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MetaSnapshotsScreen(
    environment: TftEnvironment,
    onSelectEnvironment: (TftEnvironment) -> Unit,
    comps: List<MetaComp>,
    favoritesState: UiState<List<FavoriteCompDoc>>,
    liveVersionInfo: LiveVersionInfo?,
    offlineCacheStatus: OfflineCacheStatus,
    isRefreshing: Boolean,
    isGuestMode: Boolean,
    isOverlayRunning: Boolean,
    onRefreshSync: () -> Unit,
    onSignInRequested: () -> Unit,
    onToggleOverlay: () -> Unit,
    onToggleFavorite: (MetaComp) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onCreateCustomComp: (String, TftEnvironment, TierRank, String, String, String, String) -> Unit,
    patches: List<PatchUpdateRecord> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var inspectedComp by remember { mutableStateOf<MetaComp?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showPatchDetailsDialog by remember { mutableStateOf(false) }
    val latestPatch = remember(patches, environment) {
        patches.firstOrNull { it.environment == environment } ?: patches.firstOrNull()
    }
    // All tier sub-headers start closed until explicitly opened by the user
    var expandedTierNames by rememberSaveable { mutableStateOf(emptyList<String>()) }

    val favorites = (favoritesState as? UiState.Success)?.data.orEmpty()
    val favoriteIds = remember(favorites) { favorites.map { it.compId.ifBlank { it.id } }.toSet() }
    val pinnedIds = remember(favorites) { favorites.filter { it.pinnedToOverlay }.map { it.compId.ifBlank { it.id } }.toSet() }

    val displayedComps = remember(comps, favorites, showFavoritesOnly, searchQuery, selectedFilter, environment) {
        val baseList = if (showFavoritesOnly) {
            favorites.filter { it.environment == environment.name }.map { it.toMetaComp() }
        } else {
            comps
        }
        baseList.filter { comp ->
            val matchesSearch = searchQuery.isBlank() ||
                comp.title.contains(searchQuery, ignoreCase = true) ||
                comp.carryName.contains(searchQuery, ignoreCase = true) ||
                comp.traits.any { it.contains(searchQuery, ignoreCase = true) } ||
                comp.coreUnits.any { it.contains(searchQuery, ignoreCase = true) }
            val matchesFilter = when (selectedFilter) {
                "S" -> comp.tier == TierRank.S
                "A" -> comp.tier == TierRank.A
                "B" -> comp.tier == TierRank.B
                "C" -> comp.tier == TierRank.C
                "X" -> comp.tier == TierRank.X
                "REROLL" -> comp.playstyle.contains("Reroll", ignoreCase = true)
                "FAST8" -> comp.playstyle.contains("Fast", ignoreCase = true)
                "TRENDING" -> comp.isTrending
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    val compsByTier = remember(displayedComps) {
        groupCompsByTier(displayedComps)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useSideDetail = maxWidth >= 680.dp && inspectedComp != null
        val cardColumns = when {
            useSideDetail -> 1
            maxWidth >= 1080.dp -> 3
            maxWidth >= 680.dp -> 2
            else -> 1
        }
        val compactHeroHeight = if (maxWidth >= 600.dp && maxHeight < 540.dp) 108.dp else 148.dp

        if (showPatchDetailsDialog) {
            PatchTierDetailsDialog(
                patch = latestPatch,
                onDismiss = { showPatchDetailsDialog = false }
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Clean Top Header Bar (No big banner, overlay & custom buttons at top)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val badgeColor = if (environment == TftEnvironment.LIVE) {
                            LocalHextechColors.current.liveBadge
                        } else {
                            LocalHextechColors.current.pbeBadge
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, badgeColor)
                        ) {
                            Text(
                                text = if (environment == TftEnvironment.LIVE) {
                                    "LIVE • ${liveVersionInfo?.livePatchVersion ?: "18.6b"}"
                                } else {
                                    "PBE • ${liveVersionInfo?.pbePatchVersion ?: "19.1-PBE"}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "Meta Comps",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { showCreateDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("create_custom_comp_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Create custom composition", modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Custom", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = onToggleOverlay,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isOverlayRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                contentColor = if (isOverlayRunning) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("toggle_live_overlay_button")
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = "Toggle live match overlay", modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isOverlayRunning) "Overlay On" else "Overlay", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    modifier = if (useSideDetail) {
                        Modifier
                            .weight(0.46f)
                            .fillMaxHeight()
                    } else {
                        Modifier.fillMaxSize()
                    },
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (favorites.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Favorites & Pinned (${favorites.count { it.pinnedToOverlay }} pinned)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(favorites, key = { it.compId.ifBlank { it.id } }) { fav ->
                                        Surface(
                                            onClick = { inspectedComp = fav.toMetaComp() },
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            border = BorderStroke(
                                                1.dp,
                                                if (fav.pinnedToOverlay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (fav.carryName.isNotBlank()) {
                                                    TftChampionAvatar(
                                                        name = fav.carryName,
                                                        cost = 4,
                                                        isCarry = true,
                                                        size = 18.dp
                                                    )
                                                }
                                                Text(
                                                    text = "${fav.environment} • ${fav.title}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Icon(
                                                    imageVector = if (fav.pinnedToOverlay) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                                    contentDescription = if (fav.pinnedToOverlay) "Pinned to overlay" else "Not pinned",
                                                    tint = if (fav.pinnedToOverlay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (latestPatch != null) {
                        item {
                            PatchTierAlertBanner(
                                latestPatch = latestPatch,
                                environment = environment,
                                onOpenDetails = { showPatchDetailsDialog = true }
                            )
                        }
                    }

                    // Guest & Offline Cache Banner
                    item {
                        OfflineCacheStatusBanner(
                            isGuestMode = isGuestMode,
                            cacheStatus = offlineCacheStatus,
                            isRefreshing = isRefreshing,
                            onRefreshClick = onRefreshSync,
                            onSignInClick = onSignInRequested
                        )
                    }

                    // LIVE vs PBE Switcher
                    item {
                        EnvironmentToggleBar(
                            selected = environment,
                            onSelect = onSelectEnvironment,
                            liveLabel = liveVersionInfo?.livePatchVersion ?: "18.6b Live",
                            pbeLabel = liveVersionInfo?.pbePatchVersion ?: "18.7 PBE",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Search & Filter Bar
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search comp, trait, carry, or unit...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search compositions") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("meta_search_input")
                            )

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = showFavoritesOnly,
                                        onClick = { showFavoritesOnly = !showFavoritesOnly },
                                        label = { Text("Favorites") },
                                        leadingIcon = {
                                            Icon(
                                                if (showFavoritesOnly) Icons.Default.Star else Icons.Outlined.StarOutline,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                                val filters = listOf(
                                    "ALL" to "All Tiers",
                                    "TRENDING" to "Trending",
                                    "S" to "S-Tier",
                                    "A" to "A-Tier",
                                    "B" to "B-Tier",
                                    "C" to "C-Tier",
                                    "X" to "X-Tier",
                                    "FAST8" to "Fast 8/9",
                                    "REROLL" to "Reroll"
                                )
                                items(filters) { (key, label) ->
                                    FilterChip(
                                        selected = selectedFilter == key,
                                        onClick = { selectedFilter = key },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }

                    if (compsByTier.isEmpty()) {
                        item(key = "empty_comps_${environment.name}", contentType = "empty_state") {
                            val awaitingInitialSync = !showFavoritesOnly && comps.isEmpty()
                            val isPbeEnv = environment == TftEnvironment.PBE
                            val sourceSiteLabel = if (isPbeEnv) "TFTactics" else "TFTAcademy"
                            val sourceSiteUrlText = if (isPbeEnv) "tftactics.gg/tierlist/team-comps/" else "tftacademy.com/tierlist/comps"
                            HextechEmptyState(
                                icon = Icons.Default.AutoAwesome,
                                title = when {
                                    showFavoritesOnly -> "No Saved Favorites in ${environment.label}"
                                    awaitingInitialSync && isRefreshing -> "Syncing Comps from $sourceSiteLabel..."
                                    awaitingInitialSync -> "$sourceSiteLabel Comps Not Cached Yet"
                                    else -> "No Matching Compositions"
                                },
                                subtitle = when {
                                    showFavoritesOnly -> "Star a ${environment.label} composition or tap Custom to craft your own board."
                                    awaitingInitialSync -> "Compositions load from $sourceSiteUrlText. Tap Sync to pull the latest tier list."
                                    else -> "Try clearing your search or switching between LIVE and PBE snapshots."
                                },
                                action = if (awaitingInitialSync && !isRefreshing) {
                                    {
                                        Button(
                                            onClick = onRefreshSync,
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Text("Sync $sourceSiteLabel Comps")
                                        }
                                    }
                                } else {
                                    null
                                },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(220),
                                    fadeOutSpec = tween(160),
                                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            )
                        }
                    } else {
                        item(key = "tier_summary_controls_${environment.name}", contentType = "tier_controls") {
                            val anyTierOpen = compsByTier.any { (tier, _) -> expandedTierNames.contains(tier.name) }
                            val hextech = LocalHextechColors.current
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${displayedComps.size} Comps • ${compsByTier.size} Tiers • Tap Tier to open",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = {
                                            expandedTierNames = if (anyTierOpen) {
                                                emptyList()
                                            } else {
                                                compsByTier.map { it.first.name }
                                            }
                                        },
                                        modifier = Modifier.testTag("toggle_all_tiers_button")
                                    ) {
                                        Text(
                                            text = if (anyTierOpen) "Close all" else "Open all",
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(compsByTier, key = { "pill_${it.first.name}" }) { (tier, tierComps) ->
                                        val isOpen = expandedTierNames.contains(tier.name)
                                        val tierColor = when (tier) {
                                            TierRank.S -> hextech.tierS
                                            TierRank.A -> hextech.tierA
                                            TierRank.B -> hextech.tierB
                                            TierRank.C -> hextech.tierC
                                            TierRank.X -> hextech.tierX
                                        }
                                        Surface(
                                            onClick = {
                                                expandedTierNames = if (isOpen) {
                                                    expandedTierNames - tier.name
                                                } else {
                                                    expandedTierNames + tier.name
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isOpen) tierColor.copy(alpha = 0.24f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                                            border = BorderStroke(
                                                width = if (isOpen) 1.4.dp else 1.dp,
                                                color = if (isOpen) tierColor else tierColor.copy(alpha = 0.45f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(7.dp)
                                                        .clip(CircleShape)
                                                        .background(tierColor)
                                                )
                                                Text(
                                                    text = "${tier.label} (${tierComps.size})",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (isOpen) tierColor else MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        compsByTier.forEach { (tier, tierComps) ->
                            val isTierExpanded = expandedTierNames.contains(tier.name)
                            item(
                                key = "tier_header_${environment.name}_${tier.name}",
                                contentType = "tier_header"
                            ) {
                                TierSubHeaderCard(
                                    tier = tier,
                                    comps = tierComps,
                                    expanded = isTierExpanded,
                                    environment = environment,
                                    onToggle = {
                                        expandedTierNames = if (isTierExpanded) {
                                            expandedTierNames - tier.name
                                        } else {
                                            expandedTierNames + tier.name
                                        }
                                    }
                                )
                            }

                            if (isTierExpanded) {
                                if (cardColumns > 1) {
                                    items(
                                        items = tierComps.chunked(cardColumns),
                                        key = { row -> "${tier.name}_row_${row.joinToString("_") { it.id }}" },
                                        contentType = { "comp_card_row" }
                                    ) { rowComps ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            rowComps.forEach { comp ->
                                                val isFav = favoriteIds.contains(comp.id)
                                                val isPinned = pinnedIds.contains(comp.id)
                                                MetaCompCard(
                                                    comp = comp,
                                                    isFavorite = isFav,
                                                    isPinned = isPinned,
                                                    onCardClick = { inspectedComp = comp },
                                                    onToggleFavorite = { onToggleFavorite(comp) },
                                                    onTogglePin = {
                                                        if (!isFav) onToggleFavorite(comp)
                                                        else onTogglePin(comp.id, !isPinned)
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            repeat(cardColumns - rowComps.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                } else {
                                    items(
                                        items = tierComps,
                                        key = { "${tier.name}_${it.id}" },
                                        contentType = { "comp_card" }
                                    ) { comp ->
                                        val isFav = favoriteIds.contains(comp.id)
                                        val isPinned = pinnedIds.contains(comp.id)
                                        MetaCompCard(
                                            comp = comp,
                                            isFavorite = isFav,
                                            isPinned = isPinned,
                                            onCardClick = { inspectedComp = comp },
                                            onToggleFavorite = { onToggleFavorite(comp) },
                                            onTogglePin = {
                                                if (!isFav) onToggleFavorite(comp)
                                                else onTogglePin(comp.id, !isPinned)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }

                if (useSideDetail) {
                    val sideComp = inspectedComp
                    if (sideComp != null) {
                        val isFav = favoriteIds.contains(sideComp.id)
                        val isPinned = pinnedIds.contains(sideComp.id)
                        HextechPanelCard(
                            accentBorder = true,
                            modifier = Modifier
                                .weight(0.54f)
                                .fillMaxHeight()
                                .padding(top = 16.dp, end = 16.dp, bottom = 16.dp)
                                .testTag("meta_side_detail_pane")
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Composition Details",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { inspectedComp = null },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .testTag("meta_side_detail_close")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close detail panel",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                CompDetailSheetContent(
                                    comp = sideComp,
                                    isFavorite = isFav,
                                    isPinned = isPinned,
                                    onToggleFavorite = { onToggleFavorite(sideComp) },
                                    onTogglePin = {
                                        if (!isFav) onToggleFavorite(sideComp)
                                        else onTogglePin(sideComp.id, !isPinned)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!useSideDetail) {
            inspectedComp?.let { comp ->
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val isFav = favoriteIds.contains(comp.id)
                val isPinned = pinnedIds.contains(comp.id)
                ModalBottomSheet(
                    onDismissRequest = { inspectedComp = null },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    CompDetailSheetContent(
                        comp = comp,
                        isFavorite = isFav,
                        isPinned = isPinned,
                        onToggleFavorite = { onToggleFavorite(comp) },
                        onTogglePin = {
                            if (!isFav) onToggleFavorite(comp)
                            else onTogglePin(comp.id, !isPinned)
                        }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateCustomCompDialog(
            defaultEnvironment = environment,
            onDismiss = { showCreateDialog = false },
            onSave = { title, env, tier, playstyle, carry, units, notes ->
                onCreateCustomComp(title, env, tier, playstyle, carry, units, notes)
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaCompCard(
    comp: MetaComp,
    isFavorite: Boolean,
    isPinned: Boolean,
    onCardClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hextech = LocalHextechColors.current
    val placementCostByUnit = remember(comp.id, comp.hexPlacements) {
        comp.hexPlacements.associate { it.unitName to it.cost }
    }
    val carryItems = remember(comp.id, comp.hexPlacements) {
        comp.hexPlacements.firstOrNull { it.isCarry }?.items.orEmpty()
    }

    HextechPanelCard(
        accentBorder = comp.tier == TierRank.S,
        onClick = onCardClick,
        modifier = modifier.testTag("comp_card_${comp.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                    TierBadge(comp.tier)
                    Text(
                        text = comp.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("pin_comp_${comp.id}")
                    ) {
                        Icon(
                            imageVector = if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (isPinned) "Unpin from Live Overlay" else "Pin to Live Overlay",
                            tint = if (isPinned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("fav_comp_${comp.id}")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = if (isFavorite) "Remove favorite" else "Save favorite",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${comp.playstyle} • Carry: ${comp.carryName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                if (comp.isTrending) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = "Trending composition",
                            tint = hextech.buff,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = comp.trendDelta,
                            style = MaterialTheme.typography.labelSmall,
                            color = hextech.buff
                        )
                    }
                }
            }

            if (comp.traits.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    comp.traits.forEach { trait ->
                        TftTraitChip(traitLabel = trait, environment = comp.environment)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Vibrant color-coded metrics strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        ),
                        RoundedCornerShape(10.dp)
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCell(
                    label = "AVG PLACE",
                    value = String.format(Locale.US, "%.2f", comp.avgPlacement),
                    valueColor = if (comp.avgPlacement <= 3.9) hextech.buff else MaterialTheme.colorScheme.primary
                )
                StatCell("TOP 4", "${comp.top4Rate}%", valueColor = MaterialTheme.colorScheme.secondary)
                StatCell("WIN RATE", "${comp.winRate}%", valueColor = MaterialTheme.colorScheme.tertiary)
                StatCell("PATCH", comp.patchTag)
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Team Roster",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(5.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                comp.coreUnits.forEach { unit ->
                    val isCarry = unit.equals(comp.carryName, ignoreCase = true)
                    val cost = placementCostByUnit[unit] ?: if (isCarry) 4 else 3
                    TftChampionTile(
                        name = unit,
                        cost = cost,
                        isCarry = isCarry,
                        size = 30.dp,
                        environment = comp.environment
                    )
                }
            }

            if (carryItems.isNotEmpty() || comp.augmentPriorities.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    carryItems.forEach { item ->
                        TftItemChip(itemName = item, compact = true, environment = comp.environment)
                    }
                    comp.augmentPriorities.take(2).forEach { aug ->
                        TftAugmentChip(augmentName = aug, compact = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompDetailSheetContent(
    comp: MetaComp,
    isFavorite: Boolean,
    isPinned: Boolean,
    onToggleFavorite: () -> Unit,
    onTogglePin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TierBadge(comp.tier)
                    Text(
                        text = "${comp.environment.label} • ${comp.setName} • Patch ${comp.patchTag}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = comp.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (comp.traits.isNotEmpty()) {
            Text(
                text = "Active Synergies & Traits",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                comp.traits.forEach { trait ->
                    TftTraitChip(traitLabel = trait, environment = comp.environment)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(
                    if (isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isFavorite) "Favorited" else "Save Favorite")
            }
            OutlinedButton(
                onClick = onTogglePin,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(
                    if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isPinned) "Pinned Overlay" else "Pin to Overlay")
            }
        }

        if (comp.hexPlacements.isNotEmpty()) {
            Text(
                text = "Board Positioning (4×7 Hex Grid)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            TftHexBoard(placements = comp.hexPlacements, environment = comp.environment)
        }

        val carryItems = comp.hexPlacements.firstOrNull { it.isCarry }?.items.orEmpty()
        if (carryItems.isNotEmpty()) {
            Text(
                text = "Carry Best-In-Slot Items (${comp.carryName})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                carryItems.forEach { item ->
                    TftItemChip(itemName = item, environment = comp.environment)
                }
            }
        }

        if (comp.augmentPriorities.isNotEmpty()) {
            Text(
                text = "Recommended Augments",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                comp.augmentPriorities.forEach { aug ->
                    TftAugmentChip(augmentName = aug)
                }
            }
        }

        HextechPanelCard {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Leveling & Tempo Guide",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = comp.levelingGuide,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (comp.notes.isNotBlank()) {
                    Text(
                        text = comp.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateCustomCompDialog(
    defaultEnvironment: TftEnvironment,
    onDismiss: () -> Unit,
    onSave: (String, TftEnvironment, TierRank, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var env by remember { mutableStateOf(defaultEnvironment) }
    var tier by remember { mutableStateOf(TierRank.S) }
    var playstyle by remember { mutableStateOf("Fast 8") }
    var carry by remember { mutableStateOf("") }
    var unitsCsv by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Favorite Comp") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EnvironmentToggleBar(selected = env, onSelect = { env = it })
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Composition Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = carry,
                        onValueChange = { carry = it },
                        label = { Text("Main Carry") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = playstyle,
                        onValueChange = { playstyle = it },
                        label = { Text("Tempo") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TierRank.entries.forEach { t ->
                        FilterChip(
                            selected = tier == t,
                            onClick = { tier = t },
                            label = { Text(t.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = unitsCsv,
                    onValueChange = { unitsCsv = it },
                    label = { Text("Units (comma-separated)") },
                    placeholder = { Text("Ahri, Ornn, Lillia, Syndra") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("In-Game Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title, env, tier, playstyle, carry, unitsCsv, notes) }) {
                Text("Save & Pin")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TierSubHeaderCard(
    tier: TierRank,
    comps: List<MetaComp>,
    expanded: Boolean,
    environment: TftEnvironment,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hextech = LocalHextechColors.current
    val tierColor = when (tier) {
        TierRank.S -> hextech.tierS
        TierRank.A -> hextech.tierA
        TierRank.B -> hextech.tierB
        TierRank.C -> hextech.tierC
        TierRank.X -> hextech.tierX
    }
    val tierSubtitle = when (tier) {
        TierRank.S -> "Meta Leaders"
        TierRank.A -> "Strong Contenders"
        TierRank.B -> "Situational Picks"
        TierRank.C -> "Niche & Flex Comps"
        TierRank.X -> "Experimental & Craft"
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tierChevronRotation_${tier.name}"
    )
    val previewCarries = remember(comps) {
        comps.map { it.carryName }.filter { it.isNotBlank() }.distinct().take(4)
    }
    val previewTitles = remember(comps) {
        comps.take(3).joinToString(" • ") { it.title }
    }

    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(16.dp),
        color = if (expanded) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (expanded) 1.5.dp else 1.1.dp,
            color = if (expanded) tierColor else tierColor.copy(alpha = 0.45f)
        ),
        tonalElevation = if (expanded) 5.dp else 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 62.dp)
            .testTag("tier_section_header_${environment.name}_${tier.label}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            tierColor.copy(alpha = if (expanded) 0.16f else 0.08f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(tierColor)
                    )
                    TierBadge(tier = tier)
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${tier.label}-Tier",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = tierColor.copy(alpha = 0.20f)
                            ) {
                                Text(
                                    text = "${comps.size} ${if (comps.size == 1) "Comp" else "Comps"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tierColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (expanded || previewTitles.isBlank()) {
                                "$tierSubtitle • Tap to collapse"
                            } else {
                                previewTitles
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!expanded && previewCarries.isNotEmpty()) {
                        val stackWidth = (26 + (previewCarries.size - 1) * 15).dp
                        Box(
                            modifier = Modifier
                                .width(stackWidth)
                                .height(26.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            previewCarries.forEachIndexed { idx, carry ->
                                TftChampionAvatar(
                                    name = carry,
                                    cost = 4,
                                    isCarry = false,
                                    size = 24.dp,
                                    modifier = Modifier.offset(x = (idx * 15).dp)
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse ${tier.label}-Tier compositions" else "Expand ${tier.label}-Tier compositions",
                        tint = if (expanded) tierColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(chevronRotation)
                    )
                }
            }
        }
    }
}

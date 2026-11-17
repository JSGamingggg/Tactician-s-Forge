package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AugmentTier
import com.example.data.model.AugmentTierEntry
import com.example.data.model.ChampionTierEntry
import com.example.data.model.ItemCategory
import com.example.data.model.ItemTierEntry
import com.example.data.model.LiveVersionInfo
import com.example.data.model.OfflineCacheStatus
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank
import com.example.ui.components.DeltaBadge
import com.example.ui.components.EnvironmentToggleBar
import com.example.ui.components.HextechEmptyState
import com.example.ui.components.HextechPanelCard
import com.example.ui.components.OfflineCacheStatusBanner
import com.example.ui.components.TftAugmentChip
import com.example.ui.components.TftChampionAvatar
import com.example.ui.components.TftItemChip
import com.example.ui.components.TftTraitChip
import com.example.ui.components.TierBadge
import com.example.ui.components.environmentSlideTransitionSpec

private enum class TierCatalogTab(val label: String) {
    CHAMPIONS("Champions"),
    ITEMS("Items"),
    AUGMENTS("Augments")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChampionTierListScreen(
    environment: TftEnvironment,
    onSelectEnvironment: (TftEnvironment) -> Unit,
    champions: List<ChampionTierEntry>,
    items: List<ItemTierEntry>,
    augments: List<AugmentTierEntry>,
    liveVersionInfo: LiveVersionInfo?,
    offlineCacheStatus: OfflineCacheStatus,
    isRefreshing: Boolean,
    isGuestMode: Boolean,
    onRefreshSync: () -> Unit,
    onSignInRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSubTab by rememberSaveable { mutableStateOf(TierCatalogTab.CHAMPIONS) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTier by remember { mutableStateOf<TierRank?>(null) }
    var selectedCost by remember { mutableStateOf<Int?>(null) }
    var selectedItemCategory by remember { mutableStateOf<ItemCategory?>(null) }
    var selectedAugmentTier by remember { mutableStateOf<AugmentTier?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    val filteredChampions = remember(champions, searchQuery, selectedCost, selectedTier) {
        champions
            .filter { champ ->
                val matchesSearch = searchQuery.isBlank() ||
                    champ.name.contains(searchQuery, ignoreCase = true) ||
                    champ.traits.any { it.contains(searchQuery, ignoreCase = true) } ||
                    champ.bestItems.any { it.contains(searchQuery, ignoreCase = true) }
                val matchesCost = selectedCost == null || champ.cost == selectedCost
                val matchesTier = selectedTier == null || champ.tier == selectedTier
                matchesSearch && matchesCost && matchesTier
            }
            .sortedWith(compareBy<ChampionTierEntry> { it.tier.sortOrder }.thenBy { it.avgPlacement })
    }

    val filteredItems = remember(items, searchQuery, selectedTier, selectedItemCategory) {
        items
            .filter { item ->
                val matchesSearch = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.components.any { it.contains(searchQuery, ignoreCase = true) } ||
                    item.bestHolders.any { it.contains(searchQuery, ignoreCase = true) }
                val matchesTier = selectedTier == null || item.tier == selectedTier
                val matchesCat = selectedItemCategory == null || item.category == selectedItemCategory
                matchesSearch && matchesTier && matchesCat
            }
            .sortedWith(compareBy<ItemTierEntry> { it.tier.sortOrder }.thenBy { it.avgPlacement })
    }

    val filteredAugments = remember(augments, searchQuery, selectedTier, selectedAugmentTier) {
        augments
            .filter { aug ->
                val matchesSearch = searchQuery.isBlank() ||
                    aug.name.contains(searchQuery, ignoreCase = true) ||
                    aug.description.contains(searchQuery, ignoreCase = true) ||
                    aug.category.contains(searchQuery, ignoreCase = true)
                val matchesTier = selectedTier == null || aug.tier == selectedTier
                val matchesRank = selectedAugmentTier == null || aug.augmentTier == selectedAugmentTier
                matchesSearch && matchesTier && matchesRank
            }
            .sortedWith(compareBy<AugmentTierEntry> { it.tier.sortOrder }.thenBy { it.avgPlacement })
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AnimatedContent(
                targetState = environment,
                transitionSpec = environmentSlideTransitionSpec(),
                label = "tierListHeaderEnvTransition"
            ) { activeEnv ->
                Column {
                    Text(
                        text = "${activeSubTab.label} Tier List (${activeEnv.label})",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${if (activeEnv == TftEnvironment.LIVE) liveVersionInfo?.liveSetName ?: activeEnv.setName else liveVersionInfo?.pbeSetName ?: activeEnv.setName} • Champions, item recipes & augments.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            OfflineCacheStatusBanner(
                isGuestMode = isGuestMode,
                cacheStatus = offlineCacheStatus,
                isRefreshing = isRefreshing,
                onRefreshClick = onRefreshSync,
                onSignInClick = onSignInRequested
            )
        }

        item {
            EnvironmentToggleBar(
                selected = environment,
                onSelect = onSelectEnvironment,
                liveLabel = liveVersionInfo?.livePatchVersion ?: "18.6b Live",
                pbeLabel = liveVersionInfo?.pbePatchVersion ?: "18.7 PBE",
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Champions | Items | Augments Sub-tab Switcher
        item {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tier_catalog_segmented_row")
            ) {
                TierCatalogTab.entries.forEachIndexed { idx, tab ->
                    SegmentedButton(
                        selected = activeSubTab == tab,
                        onClick = { activeSubTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = idx,
                            count = TierCatalogTab.entries.size
                        ),
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    TierCatalogTab.CHAMPIONS -> Icons.Default.MilitaryTech
                                    TierCatalogTab.ITEMS -> Icons.Default.Build
                                    TierCatalogTab.AUGMENTS -> Icons.Default.AutoAwesome
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("tier_tab_${tab.name.lowercase()}")
                    ) {
                        Text(tab.label, maxLines = 1)
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            when (activeSubTab) {
                                TierCatalogTab.CHAMPIONS -> "Filter champion, trait, or BIS item..."
                                TierCatalogTab.ITEMS -> "Filter item, component, or holder..."
                                TierCatalogTab.AUGMENTS -> "Filter augment, effect, or category..."
                            }
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search tier list") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("champion_search_input")
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedTier == null,
                            onClick = { selectedTier = null },
                            label = { Text("All Tiers") }
                        )
                    }
                    items(TierRank.entries) { tier ->
                        FilterChip(
                            selected = selectedTier == tier,
                            onClick = { selectedTier = if (selectedTier == tier) null else tier },
                            label = { Text("${tier.label}-Tier") }
                        )
                    }
                    when (activeSubTab) {
                        TierCatalogTab.CHAMPIONS -> {
                            items((1..5).toList()) { cost ->
                                FilterChip(
                                    selected = selectedCost == cost,
                                    onClick = { selectedCost = if (selectedCost == cost) null else cost },
                                    label = { Text("${cost}g") }
                                )
                            }
                        }
                        TierCatalogTab.ITEMS -> {
                            items(ItemCategory.entries) { cat ->
                                FilterChip(
                                    selected = selectedItemCategory == cat,
                                    onClick = { selectedItemCategory = if (selectedItemCategory == cat) null else cat },
                                    label = { Text(cat.label) }
                                )
                            }
                        }
                        TierCatalogTab.AUGMENTS -> {
                            items(AugmentTier.entries) { rank ->
                                FilterChip(
                                    selected = selectedAugmentTier == rank,
                                    onClick = { selectedAugmentTier = if (selectedAugmentTier == rank) null else rank },
                                    label = { Text(rank.label) }
                                )
                            }
                        }
                    }
                }
            }
        }

        when (activeSubTab) {
            TierCatalogTab.CHAMPIONS -> {
                if (filteredChampions.isEmpty()) {
                    item(key = "empty_champs_${environment.name}") {
                        HextechEmptyState(
                            icon = Icons.Default.MilitaryTech,
                            title = "No Champions Match Filters",
                            subtitle = "Clear cost or tier filters to inspect the full ${environment.label} roster.",
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(220),
                                fadeOutSpec = tween(160),
                                placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            )
                        )
                    }
                } else {
                    items(filteredChampions, key = { it.id }) { champ ->
                        val isExpanded = expandedId == champ.id
                        ChampionTierCard(
                            champ = champ,
                            expanded = isExpanded,
                            onClick = { expandedId = if (isExpanded) null else champ.id },
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(220),
                                fadeOutSpec = tween(160),
                                placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            )
                        )
                    }
                }
            }
            TierCatalogTab.ITEMS -> {
                if (filteredItems.isEmpty()) {
                    item(key = "empty_items_${environment.name}") {
                        HextechEmptyState(
                            icon = Icons.Default.Build,
                            title = "No Items Match Filters",
                            subtitle = "Clear category or tier filters to view all Set 18 items and recipes.",
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(220),
                                fadeOutSpec = tween(160),
                                placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            )
                        )
                    }
                } else {
                    items(filteredItems, key = { it.id }) { itemEntry ->
                        ItemTierCard(
                            item = itemEntry,
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(220),
                                fadeOutSpec = tween(160),
                                placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            )
                        )
                    }
                }
            }
            TierCatalogTab.AUGMENTS -> {
                if (filteredAugments.isEmpty()) {
                    item(key = "empty_augs_${environment.name}") {
                        HextechEmptyState(
                            icon = Icons.Default.AutoAwesome,
                            title = "No Augments Match Filters",
                            subtitle = "Clear Silver/Gold/Prismatic filters to view all cached augments.",
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(220),
                                fadeOutSpec = tween(160),
                                placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            )
                        )
                    }
                } else {
                    items(filteredAugments, key = { it.id }) { augment ->
                        AugmentTierCard(
                            augment = augment,
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(220),
                                fadeOutSpec = tween(160),
                                placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            )
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChampionTierCard(
    champ: ChampionTierEntry,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HextechPanelCard(
        accentBorder = champ.tier == TierRank.S,
        onClick = onClick,
        modifier = modifier.testTag("champ_tier_${champ.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    TftChampionAvatar(
                        name = champ.name,
                        cost = champ.cost,
                        isCarry = champ.tier == TierRank.S,
                        size = 40.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = champ.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${champ.cost}g",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            TierBadge(champ.tier)
                            champ.patchDelta?.let { DeltaBadge(it) }
                        }
                        Text(
                            text = champ.role,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse details" else "Expand details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                champ.traits.forEach { trait ->
                    TftTraitChip(traitLabel = trait)
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Avg Place: ${String.format("%.2f", champ.avgPlacement)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Top 4: ${champ.top4Rate}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (champ.patchNote.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = champ.patchNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Text(
                        text = "Recommended Best-In-Slot Items",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        champ.bestItems.forEach { item ->
                            TftItemChip(itemName = item)
                        }
                    }

                    if (champ.synergyComps.isNotEmpty()) {
                        Text(
                            text = "Core Comps: ${champ.synergyComps.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemTierCard(
    item: ItemTierEntry,
    modifier: Modifier = Modifier
) {
    HextechPanelCard(
        accentBorder = item.tier == TierRank.S,
        modifier = modifier.testTag("item_tier_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TftItemChip(
                    itemName = item.name,
                    categoryOverride = item.category
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.patchDelta?.let { DeltaBadge(it) }
                    TierBadge(item.tier)
                }
            }

            if (item.components.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${item.category.label} • Recipe:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    item.components.forEach { compName ->
                        TftItemChip(itemName = compName, compact = true)
                    }
                }
            }
            Text(
                text = item.statsSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Avg Place: ${String.format("%.2f", item.avgPlacement)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Top 4: ${item.top4Rate}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (item.patchNote.isNotBlank()) {
                Text(
                    text = item.patchNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (item.bestHolders.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Best Holders:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    item.bestHolders.forEach { holder ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TftChampionAvatar(name = holder, cost = 4, size = 18.dp)
                                Text(
                                    text = holder,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AugmentTierCard(
    augment: AugmentTierEntry,
    modifier: Modifier = Modifier
) {
    HextechPanelCard(
        accentBorder = augment.tier == TierRank.S,
        modifier = modifier.testTag("augment_tier_${augment.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TftAugmentChip(
                    augmentName = augment.name,
                    augmentTierOverride = augment.augmentTier,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    augment.patchDelta?.let { DeltaBadge(it) }
                    TierBadge(augment.tier)
                }
            }

            Text(
                text = "${augment.augmentTier.label} • ${augment.category}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = augment.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Avg Place: ${String.format("%.2f", augment.avgPlacement)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Top 4: ${augment.top4Rate}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (augment.patchNote.isNotBlank()) {
                Text(
                    text = augment.patchNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (augment.synergyComps.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    augment.synergyComps.forEach { compTitle ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = compTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AugmentTier
import com.example.data.model.AugmentTierEntry
import com.example.data.model.ChampionTierEntry
import com.example.data.model.ItemCategory
import com.example.data.model.ItemTierEntry
import com.example.data.model.LiveVersionInfo
import com.example.data.model.MetaComp
import com.example.data.model.OfflineCacheStatus
import com.example.data.model.PatchUpdateRecord
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank
import com.example.ui.components.ChampionDetailModalSheet
import com.example.ui.components.DeltaBadge
import com.example.ui.components.EnvironmentToggleBar
import com.example.ui.components.HextechEmptyState
import com.example.ui.components.HextechPanelCard
import com.example.ui.components.CheckForUpdatesButton
import com.example.ui.components.TftAssetIconResolver
import com.example.ui.components.TftAugmentChip
import com.example.ui.components.TftChampionAvatar
import com.example.ui.components.TftItemChip
import com.example.ui.components.TftTraitChip
import com.example.ui.components.TierBadge
import com.example.ui.components.augmentTierColor
import com.example.ui.components.championCostColor
import com.example.ui.components.environmentSlideTransitionSpec
import com.example.ui.components.itemCategoryColor
import java.util.Locale

private enum class TierCatalogTab(val label: String) {
    CHAMPIONS("Champions"),
    ITEMS("Items"),
    AUGMENTS("Augments")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    patches: List<PatchUpdateRecord> = emptyList(),
    comps: List<MetaComp> = emptyList(),
    favoriteIds: Set<String> = emptySet(),
    pinnedIds: Set<String> = emptySet(),
    onToggleFavorite: ((MetaComp) -> Unit)? = null,
    onTogglePin: ((String, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var activeSubTab by rememberSaveable { mutableStateOf(TierCatalogTab.CHAMPIONS) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTier by remember { mutableStateOf<TierRank?>(null) }

    // Champions filter state
    var selectedCosts by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var selectedTraits by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAllTraits by rememberSaveable { mutableStateOf(false) }

    // Items filter state
    var selectedItemCategories by remember { mutableStateOf<Set<ItemCategory>>(emptySet()) }
    var selectedItemComponents by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAllItemComponents by rememberSaveable { mutableStateOf(false) }

    // Augments filter state
    var selectedAugmentTiers by remember { mutableStateOf<Set<AugmentTier>>(emptySet()) }
    var selectedAugmentCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAllAugmentCategories by rememberSaveable { mutableStateOf(false) }

    var expandedId by remember { mutableStateOf<String?>(null) }
    var inspectedChampion by remember { mutableStateOf<Pair<String, Int?>?>(null) }
    var inspectedSynergyComp by remember { mutableStateOf<MetaComp?>(null) }

    val availableCosts = remember(champions) { extractChampionCosts(champions) }
    val availableTraitOptions = remember(champions, TftAssetIconResolver.catalogRevision) {
        extractChampionTraitOptions(champions)
    }
    val availableItemCategories = remember(items) { extractItemCategoryOptions(items) }
    val availableItemComponents = remember(items, TftAssetIconResolver.catalogRevision) {
        extractItemComponentOptions(items)
    }
    val availableAugmentRarities = remember(augments) { extractAugmentRarityOptions(augments) }
    val availableAugmentCategories = remember(augments) { extractAugmentCategoryOptions(augments) }

    LaunchedEffect(
        environment,
        availableTraitOptions,
        availableCosts,
        availableItemCategories,
        availableItemComponents,
        availableAugmentRarities,
        availableAugmentCategories
    ) {
        val validTraitKeys = availableTraitOptions.map { it.name.lowercase(Locale.US) }.toSet()
        val prunedTraits = selectedTraits.filterTo(linkedSetOf()) {
            normalizeFilterLabel(it).lowercase(Locale.US) in validTraitKeys
        }
        if (prunedTraits != selectedTraits) {
            selectedTraits = prunedTraits
        }
        val validCosts = availableCosts.toSet()
        val prunedCosts = selectedCosts.filterTo(linkedSetOf()) { it in validCosts }
        if (prunedCosts != selectedCosts) {
            selectedCosts = prunedCosts
        }

        val validCategories = availableItemCategories.map { it.category }.toSet()
        val prunedItemCategories = selectedItemCategories.filterTo(linkedSetOf()) { it in validCategories }
        if (prunedItemCategories != selectedItemCategories) {
            selectedItemCategories = prunedItemCategories
        }
        val validComponentKeys = availableItemComponents.map { it.name.lowercase(Locale.US) }.toSet()
        val prunedComponents = selectedItemComponents.filterTo(linkedSetOf()) {
            normalizeFilterLabel(it).lowercase(Locale.US) in validComponentKeys
        }
        if (prunedComponents != selectedItemComponents) {
            selectedItemComponents = prunedComponents
        }

        val validRarities = availableAugmentRarities.map { it.augmentTier }.toSet()
        val prunedRarities = selectedAugmentTiers.filterTo(linkedSetOf()) { it in validRarities }
        if (prunedRarities != selectedAugmentTiers) {
            selectedAugmentTiers = prunedRarities
        }
        val validAugCategoryKeys = availableAugmentCategories.map { it.name.lowercase(Locale.US) }.toSet()
        val prunedAugCategories = selectedAugmentCategories.filterTo(linkedSetOf()) {
            it.trim().lowercase(Locale.US) in validAugCategoryKeys
        }
        if (prunedAugCategories != selectedAugmentCategories) {
            selectedAugmentCategories = prunedAugCategories
        }
    }

    val toggleCostSelection: (Int) -> Unit = { cost ->
        selectedCosts = if (cost in selectedCosts) selectedCosts - cost else selectedCosts + cost
    }
    val toggleTraitSelection: (String) -> Unit = { rawTrait ->
        val display = normalizeFilterLabel(rawTrait)
        val existing = selectedTraits.firstOrNull { it.equals(display, ignoreCase = true) }
        selectedTraits = if (existing != null) selectedTraits - existing else selectedTraits + display
    }
    val clearChampionFilters = {
        searchQuery = ""
        selectedTier = null
        selectedCosts = emptySet()
        selectedTraits = emptySet()
    }
    val hasActiveChampionFilters =
        searchQuery.isNotBlank() || selectedTier != null || selectedCosts.isNotEmpty() || selectedTraits.isNotEmpty()

    val toggleItemCategorySelection: (ItemCategory) -> Unit = { category ->
        selectedItemCategories =
            if (category in selectedItemCategories) selectedItemCategories - category
            else selectedItemCategories + category
    }
    val toggleItemComponentSelection: (String) -> Unit = { rawComp ->
        val display = normalizeFilterLabel(rawComp)
        val existing = selectedItemComponents.firstOrNull { it.equals(display, ignoreCase = true) }
        selectedItemComponents =
            if (existing != null) selectedItemComponents - existing else selectedItemComponents + display
    }
    val clearItemFilters = {
        searchQuery = ""
        selectedTier = null
        selectedItemCategories = emptySet()
        selectedItemComponents = emptySet()
    }
    val hasActiveItemFilters =
        searchQuery.isNotBlank() ||
            selectedTier != null ||
            selectedItemCategories.isNotEmpty() ||
            selectedItemComponents.isNotEmpty()

    val toggleAugmentRaritySelection: (AugmentTier) -> Unit = { rarity ->
        selectedAugmentTiers =
            if (rarity in selectedAugmentTiers) selectedAugmentTiers - rarity
            else selectedAugmentTiers + rarity
    }
    val toggleAugmentCategorySelection: (String) -> Unit = { rawCategory ->
        val cleaned = rawCategory.trim()
        val existing = selectedAugmentCategories.firstOrNull { it.equals(cleaned, ignoreCase = true) }
        selectedAugmentCategories =
            if (existing != null) selectedAugmentCategories - existing else selectedAugmentCategories + cleaned
    }
    val clearAugmentFilters = {
        searchQuery = ""
        selectedTier = null
        selectedAugmentTiers = emptySet()
        selectedAugmentCategories = emptySet()
    }
    val hasActiveAugmentFilters =
        searchQuery.isNotBlank() ||
            selectedTier != null ||
            selectedAugmentTiers.isNotEmpty() ||
            selectedAugmentCategories.isNotEmpty()

    val filteredChampions = remember(activeSubTab, champions, searchQuery, selectedCosts, selectedTier, selectedTraits) {
        if (activeSubTab != TierCatalogTab.CHAMPIONS) {
            emptyList()
        } else {
            filterChampionTiers(
                champions = champions,
                searchQuery = searchQuery,
                selectedTier = selectedTier,
                selectedCosts = selectedCosts,
                selectedTraits = selectedTraits
            )
        }
    }

    val filteredItems = remember(
        activeSubTab,
        items,
        searchQuery,
        selectedTier,
        selectedItemCategories,
        selectedItemComponents
    ) {
        if (activeSubTab != TierCatalogTab.ITEMS) {
            emptyList()
        } else {
            filterItemTiers(
                items = items,
                searchQuery = searchQuery,
                selectedTier = selectedTier,
                selectedCategories = selectedItemCategories,
                selectedComponents = selectedItemComponents
            )
        }
    }

    val filteredAugments = remember(
        activeSubTab,
        augments,
        searchQuery,
        selectedTier,
        selectedAugmentTiers,
        selectedAugmentCategories
    ) {
        if (activeSubTab != TierCatalogTab.AUGMENTS) {
            emptyList()
        } else {
            filterAugmentTiers(
                augments = augments,
                searchQuery = searchQuery,
                selectedTier = selectedTier,
                selectedAugmentTiers = selectedAugmentTiers,
                selectedCategories = selectedAugmentCategories
            )
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 1040.dp -> 3
            maxWidth >= 660.dp -> 2
            else -> 1
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
            CheckForUpdatesButton(
                isRefreshing = isRefreshing,
                onCheckUpdatesClick = onRefreshSync
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
                        val count = when (tab) {
                            TierCatalogTab.CHAMPIONS -> champions.size
                            TierCatalogTab.ITEMS -> items.size
                            TierCatalogTab.AUGMENTS -> augments.size
                        }
                        Text(
                            text = if (count > 0) "${tab.label} ($count)" else tab.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            when (activeSubTab) {
                                TierCatalogTab.CHAMPIONS -> "Filter champion, trait, role, or BIS item..."
                                TierCatalogTab.ITEMS -> "Filter item, component, or holder..."
                                TierCatalogTab.AUGMENTS -> "Filter augment, effect, or category..."
                            }
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search tier list") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.testTag("champion_search_clear_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search query")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("champion_search_input")
                )

                when (activeSubTab) {
                    TierCatalogTab.CHAMPIONS -> {
                        ChampionFilterPanel(
                            environment = environment,
                            totalChampions = champions.size,
                            filteredCount = filteredChampions.size,
                            availableCosts = availableCosts,
                            availableTraits = availableTraitOptions,
                            selectedTier = selectedTier,
                            onSelectTier = { tier ->
                                selectedTier = if (tier != null && selectedTier == tier) null else tier
                            },
                            selectedCosts = selectedCosts,
                            onToggleCost = toggleCostSelection,
                            onClearCosts = { selectedCosts = emptySet() },
                            selectedTraits = selectedTraits,
                            onToggleTrait = toggleTraitSelection,
                            onClearTraits = { selectedTraits = emptySet() },
                            showAllTraits = showAllTraits,
                            onToggleShowAllTraits = { showAllTraits = !showAllTraits },
                            hasActiveFilters = hasActiveChampionFilters,
                            onClearAllFilters = clearChampionFilters
                        )
                    }
                    TierCatalogTab.ITEMS -> {
                        ItemFilterPanel(
                            environment = environment,
                            totalItems = items.size,
                            filteredCount = filteredItems.size,
                            availableCategories = availableItemCategories,
                            availableComponents = availableItemComponents,
                            selectedTier = selectedTier,
                            onSelectTier = { tier ->
                                selectedTier = if (tier != null && selectedTier == tier) null else tier
                            },
                            selectedCategories = selectedItemCategories,
                            onToggleCategory = toggleItemCategorySelection,
                            onClearCategories = { selectedItemCategories = emptySet() },
                            selectedComponents = selectedItemComponents,
                            onToggleComponent = toggleItemComponentSelection,
                            onClearComponents = { selectedItemComponents = emptySet() },
                            showAllComponents = showAllItemComponents,
                            onToggleShowAllComponents = { showAllItemComponents = !showAllItemComponents },
                            hasActiveFilters = hasActiveItemFilters,
                            onClearAllFilters = clearItemFilters
                        )
                    }
                    TierCatalogTab.AUGMENTS -> {
                        AugmentFilterPanel(
                            totalAugments = augments.size,
                            filteredCount = filteredAugments.size,
                            availableRarities = availableAugmentRarities,
                            availableCategories = availableAugmentCategories,
                            selectedTier = selectedTier,
                            onSelectTier = { tier ->
                                selectedTier = if (tier != null && selectedTier == tier) null else tier
                            },
                            selectedRarities = selectedAugmentTiers,
                            onToggleRarity = toggleAugmentRaritySelection,
                            onClearRarities = { selectedAugmentTiers = emptySet() },
                            selectedCategories = selectedAugmentCategories,
                            onToggleCategory = toggleAugmentCategorySelection,
                            onClearCategories = { selectedAugmentCategories = emptySet() },
                            showAllCategories = showAllAugmentCategories,
                            onToggleShowAllCategories = { showAllAugmentCategories = !showAllAugmentCategories },
                            hasActiveFilters = hasActiveAugmentFilters,
                            onClearAllFilters = clearAugmentFilters
                        )
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
                            subtitle = "Clear cost, trait, search, or tier filters to inspect the full ${environment.label} roster.",
                            action = if (hasActiveChampionFilters) {
                                {
                                    FilledTonalButton(
                                        onClick = clearChampionFilters,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("champ_empty_reset_filters_button")
                                    ) {
                                        Text("Reset Filters")
                                    }
                                }
                            } else null,
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(220),
                                fadeOutSpec = tween(160),
                                placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            )
                        )
                    }
                } else if (columns > 1) {
                    items(
                        items = filteredChampions.chunked(columns),
                        key = { row -> "champ_row_${row.joinToString("_") { it.id }}" },
                        contentType = { "champion_tier_row" }
                    ) { rowChamps ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            rowChamps.forEach { champ ->
                                val isExpanded = expandedId == champ.id
                                ChampionTierCard(
                                    champ = champ,
                                    expanded = isExpanded,
                                    selectedTraits = selectedTraits,
                                    onTraitClick = toggleTraitSelection,
                                    onChampionAvatarClick = { inspectedChampion = champ.name to champ.cost },
                                    onClick = { expandedId = if (isExpanded) null else champ.id },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - rowChamps.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items(filteredChampions, key = { it.id }, contentType = { "champion_tier" }) { champ ->
                        val isExpanded = expandedId == champ.id
                        ChampionTierCard(
                            champ = champ,
                            expanded = isExpanded,
                            selectedTraits = selectedTraits,
                            onTraitClick = toggleTraitSelection,
                            onChampionAvatarClick = { inspectedChampion = champ.name to champ.cost },
                            onClick = { expandedId = if (isExpanded) null else champ.id }
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
                            subtitle = "Clear category, component, search, or tier filters to view all ${environment.label} items and recipes.",
                            action = if (hasActiveItemFilters) {
                                {
                                    FilledTonalButton(
                                        onClick = clearItemFilters,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("item_empty_reset_filters_button")
                                    ) {
                                        Text("Reset Filters")
                                    }
                                }
                            } else null,
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(220),
                                fadeOutSpec = tween(160),
                                placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            )
                        )
                    }
                } else if (columns > 1) {
                    items(
                        items = filteredItems.chunked(columns),
                        key = { row -> "item_row_${row.joinToString("_") { it.id }}" },
                        contentType = { "item_tier_row" }
                    ) { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            rowItems.forEach { itemEntry ->
                                ItemTierCard(
                                    item = itemEntry,
                                    selectedCategories = selectedItemCategories,
                                    selectedComponents = selectedItemComponents,
                                    onCategoryClick = toggleItemCategorySelection,
                                    onComponentClick = toggleItemComponentSelection,
                                    onHolderClick = { holder, holderCost ->
                                        inspectedChampion = holder to holderCost
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items(filteredItems, key = { it.id }, contentType = { "item_tier" }) { itemEntry ->
                        ItemTierCard(
                            item = itemEntry,
                            selectedCategories = selectedItemCategories,
                            selectedComponents = selectedItemComponents,
                            onCategoryClick = toggleItemCategorySelection,
                            onComponentClick = toggleItemComponentSelection,
                            onHolderClick = { holder, holderCost ->
                                inspectedChampion = holder to holderCost
                            }
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
                            subtitle = "Clear Silver/Gold/Prismatic, category, search, or tier filters to view all ${environment.label} augments.",
                            action = if (hasActiveAugmentFilters) {
                                {
                                    FilledTonalButton(
                                        onClick = clearAugmentFilters,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("augment_empty_reset_filters_button")
                                    ) {
                                        Text("Reset Filters")
                                    }
                                }
                            } else null,
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(220),
                                fadeOutSpec = tween(160),
                                placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            )
                        )
                    }
                } else if (columns > 1) {
                    items(
                        items = filteredAugments.chunked(columns),
                        key = { row -> "aug_row_${row.joinToString("_") { it.id }}" },
                        contentType = { "augment_tier_row" }
                    ) { rowAugs ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            rowAugs.forEach { augment ->
                                AugmentTierCard(
                                    augment = augment,
                                    selectedRarities = selectedAugmentTiers,
                                    selectedCategories = selectedAugmentCategories,
                                    onRarityClick = toggleAugmentRaritySelection,
                                    onCategoryClick = toggleAugmentCategorySelection,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - rowAugs.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items(filteredAugments, key = { it.id }, contentType = { "augment_tier" }) { augment ->
                        AugmentTierCard(
                            augment = augment,
                            selectedRarities = selectedAugmentTiers,
                            selectedCategories = selectedAugmentCategories,
                            onRarityClick = toggleAugmentRaritySelection,
                            onCategoryClick = toggleAugmentCategorySelection
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
        }

        inspectedChampion?.let { (champName, champCost) ->
            ChampionDetailModalSheet(
                championName = champName,
                environment = environment,
                champions = champions,
                comps = comps,
                items = items,
                fallbackCost = champCost,
                onDismiss = { inspectedChampion = null },
                onSelectChampion = { nextName, nextCost ->
                    inspectedChampion = nextName to nextCost
                },
                onOpenComp = { targetComp ->
                    inspectedChampion = null
                    inspectedSynergyComp = targetComp
                },
                onTraitClick = { trait ->
                    activeSubTab = TierCatalogTab.CHAMPIONS
                    val normalized = normalizeFilterLabel(trait)
                    selectedTraits = selectedTraits + normalized
                    inspectedChampion = null
                }
            )
        }

        inspectedSynergyComp?.let { comp ->
            val compSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val isFav = favoriteIds.contains(comp.id)
            val isPinned = pinnedIds.contains(comp.id)
            ModalBottomSheet(
                onDismissRequest = { inspectedSynergyComp = null },
                sheetState = compSheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                CompDetailSheetContent(
                    comp = comp,
                    isFavorite = isFav,
                    isPinned = isPinned,
                    onToggleFavorite = { onToggleFavorite?.invoke(comp) },
                    onTogglePin = {
                        if (!isFav) onToggleFavorite?.invoke(comp)
                        else onTogglePin?.invoke(comp.id, !isPinned)
                    },
                    onChampionClick = { unitName, cost ->
                        inspectedSynergyComp = null
                        inspectedChampion = unitName to cost
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChampionTierCard(
    champ: ChampionTierEntry,
    expanded: Boolean,
    selectedTraits: Set<String> = emptySet(),
    onTraitClick: ((String) -> Unit)? = null,
    onChampionAvatarClick: (() -> Unit)? = null,
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
                        size = 40.dp,
                        environment = champ.environment,
                        onClick = onChampionAvatarClick
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
                                color = championCostColor(champ.cost),
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
                    val isTraitSelected = selectedTraits.any { championTraitMatches(trait, it) }
                    TftTraitChip(
                        traitLabel = trait,
                        environment = champ.environment,
                        selected = isTraitSelected,
                        onClick = onTraitClick?.let { callback -> { callback(trait) } }
                    )
                }
            }

            if (champ.abilityName.isNotBlank() && !expanded) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Ability: ${champ.abilityName} — ${champ.abilityDescription}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Avg Place: ${String.format(Locale.US, "%.2f", champ.avgPlacement)}",
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
                    if (champ.abilityName.isNotBlank() || champ.abilityDescription.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (champ.abilityName.isNotBlank()) {
                                    Text(
                                        text = "Ability: ${champ.abilityName}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (champ.abilityDescription.isNotBlank()) {
                                    Text(
                                        text = champ.abilityDescription,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

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
                            TftItemChip(itemName = item, environment = champ.environment)
                        }
                    }

                    if (champ.synergyComps.isNotEmpty()) {
                        Text(
                            text = "Core Comps: ${champ.synergyComps.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (onChampionAvatarClick != null) {
                        FilledTonalButton(
                            onClick = onChampionAvatarClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("inspect_champ_${champ.id}")
                        ) {
                            Text("View Full Stats, Optimal Items & Synergy Comps")
                        }
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
    selectedCategories: Set<ItemCategory> = emptySet(),
    selectedComponents: Set<String> = emptySet(),
    onCategoryClick: ((ItemCategory) -> Unit)? = null,
    onComponentClick: ((String) -> Unit)? = null,
    onHolderClick: ((String, Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val catTint = itemCategoryColor(item.category)
    val isCategorySelected = item.category in selectedCategories

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
                    categoryOverride = item.category,
                    environment = item.environment
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.patchDelta?.let { DeltaBadge(it) }
                    TierBadge(item.tier)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = { onCategoryClick?.invoke(item.category) },
                    enabled = onCategoryClick != null,
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCategorySelected) catTint.copy(alpha = 0.26f) else catTint.copy(alpha = 0.12f),
                    border = BorderStroke(
                        width = if (isCategorySelected) 1.5.dp else 1.dp,
                        color = if (isCategorySelected) catTint else catTint.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = item.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCategorySelected) MaterialTheme.colorScheme.onSurface else catTint,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (item.components.isNotEmpty()) {
                    item.components.forEach { compName ->
                        val isCompSelected = selectedComponents.any { itemComponentMatches(compName, it) }
                        TftItemChip(
                            itemName = compName,
                            compact = true,
                            environment = item.environment,
                            selected = isCompSelected,
                            onClick = onComponentClick?.let { cb -> { cb(compName) } }
                        )
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
                    text = "Avg Place: ${String.format(Locale.US, "%.2f", item.avgPlacement)}",
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
                        val holderCost = remember(holder, item.environment, TftAssetIconResolver.catalogRevision) {
                            TftAssetIconResolver.getChampionCost(holder, item.environment) ?: 4
                        }
                        if (onHolderClick != null) {
                            Surface(
                                onClick = { onHolderClick(holder, holderCost) },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    TftChampionAvatar(
                                        name = holder,
                                        cost = holderCost,
                                        size = 18.dp,
                                        environment = item.environment
                                    )
                                    Text(
                                        text = holder,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    TftChampionAvatar(
                                        name = holder,
                                        cost = holderCost,
                                        size = 18.dp,
                                        environment = item.environment
                                    )
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AugmentTierCard(
    augment: AugmentTierEntry,
    selectedRarities: Set<AugmentTier> = emptySet(),
    selectedCategories: Set<String> = emptySet(),
    onRarityClick: ((AugmentTier) -> Unit)? = null,
    onCategoryClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rarityTint = augmentTierColor(augment.augmentTier)
    val isRaritySelected = augment.augmentTier in selectedRarities
    val isCategorySelected = selectedCategories.any { augmentCategoryMatches(augment.category, it) }
    val secondaryColor = MaterialTheme.colorScheme.secondary

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

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = { onRarityClick?.invoke(augment.augmentTier) },
                    enabled = onRarityClick != null,
                    shape = RoundedCornerShape(8.dp),
                    color = if (isRaritySelected) rarityTint.copy(alpha = 0.28f) else rarityTint.copy(alpha = 0.14f),
                    border = BorderStroke(
                        width = if (isRaritySelected) 1.5.dp else 1.dp,
                        color = if (isRaritySelected) rarityTint else rarityTint.copy(alpha = 0.45f)
                    )
                ) {
                    Text(
                        text = augment.augmentTier.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRaritySelected) MaterialTheme.colorScheme.onSurface else rarityTint,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (augment.category.isNotBlank()) {
                    Surface(
                        onClick = { onCategoryClick?.invoke(augment.category) },
                        enabled = onCategoryClick != null,
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCategorySelected) secondaryColor.copy(alpha = 0.24f) else secondaryColor.copy(alpha = 0.10f),
                        border = BorderStroke(
                            width = if (isCategorySelected) 1.5.dp else 1.dp,
                            color = if (isCategorySelected) secondaryColor else secondaryColor.copy(alpha = 0.35f)
                        )
                    ) {
                        Text(
                            text = augment.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCategorySelected) MaterialTheme.colorScheme.onSurface else secondaryColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
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
                    text = "Avg Place: ${String.format(Locale.US, "%.2f", augment.avgPlacement)}",
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

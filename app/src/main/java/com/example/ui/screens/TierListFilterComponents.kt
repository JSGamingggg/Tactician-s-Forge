package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AugmentTier
import com.example.data.model.AugmentTierEntry
import com.example.data.model.ChampionTierEntry
import com.example.data.model.ItemCategory
import com.example.data.model.ItemTierEntry
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank
import com.example.ui.components.HextechPanelCard
import com.example.ui.components.TftItemIcon
import com.example.ui.components.TftTraitIcon
import com.example.ui.components.augmentTierColor
import com.example.ui.components.championCostColor
import com.example.ui.components.formatRiotAssetLabel
import com.example.ui.components.itemCategoryColor
import com.example.ui.components.traitAccentColor
import java.util.Locale

internal data class ChampionTraitOption(
    val name: String,
    val championCount: Int
)

internal data class ItemCategoryOption(
    val category: ItemCategory,
    val itemCount: Int
)

internal data class ItemComponentOption(
    val name: String,
    val itemCount: Int
)

internal data class AugmentRarityOption(
    val augmentTier: AugmentTier,
    val augmentCount: Int
)

internal data class AugmentCategoryOption(
    val name: String,
    val augmentCount: Int
)

internal fun normalizeFilterLabel(rawLabel: String): String {
    val formatted = formatRiotAssetLabel(rawLabel).trim()
    return formatted.ifEmpty { rawLabel.trim() }
}

internal fun championTraitMatches(rawTrait: String, selectedTrait: String): Boolean {
    val normalizedTarget = normalizeFilterLabel(selectedTrait)
    val normalizedTrait = normalizeFilterLabel(rawTrait)
    return rawTrait.trim().equals(selectedTrait.trim(), ignoreCase = true) ||
        normalizedTrait.equals(normalizedTarget, ignoreCase = true)
}

internal fun itemComponentMatches(rawComponent: String, selectedComponent: String): Boolean {
    val normalizedTarget = normalizeFilterLabel(selectedComponent)
    val normalizedComponent = normalizeFilterLabel(rawComponent)
    return rawComponent.trim().equals(selectedComponent.trim(), ignoreCase = true) ||
        normalizedComponent.equals(normalizedTarget, ignoreCase = true)
}

internal fun augmentCategoryMatches(rawCategory: String, selectedCategory: String): Boolean =
    rawCategory.trim().equals(selectedCategory.trim(), ignoreCase = true)

internal fun filterTestTagSlug(label: String): String =
    label.lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifEmpty { "filter" }

internal fun extractChampionCosts(champions: List<ChampionTierEntry>): List<Int> =
    champions.map { it.cost }.filter { it > 0 }.distinct().sorted().ifEmpty { (1..5).toList() }

internal fun extractChampionTraitOptions(champions: List<ChampionTierEntry>): List<ChampionTraitOption> {
    if (champions.isEmpty()) return emptyList()
    val countsByKey = linkedMapOf<String, Pair<String, MutableSet<String>>>()
    champions.forEach { champ ->
        val uniqueChampKey = champ.id.ifBlank { champ.name }
        champ.traits.forEach { rawTrait ->
            val display = normalizeFilterLabel(rawTrait)
            if (display.isNotBlank()) {
                val key = display.lowercase(Locale.US)
                val existing = countsByKey[key]
                if (existing == null) {
                    countsByKey[key] = display to linkedSetOf(uniqueChampKey)
                } else {
                    existing.second.add(uniqueChampKey)
                }
            }
        }
    }
    return countsByKey.values
        .map { (display, ids) -> ChampionTraitOption(name = display, championCount = ids.size) }
        .sortedWith(
            compareByDescending<ChampionTraitOption> { it.championCount }
                .thenBy { it.name.lowercase(Locale.US) }
        )
}

internal fun filterChampionTiers(
    champions: List<ChampionTierEntry>,
    searchQuery: String,
    selectedTier: TierRank?,
    selectedCosts: Set<Int>,
    selectedTraits: Set<String>
): List<ChampionTierEntry> {
    val query = searchQuery.trim()
    return champions
        .filter { champ ->
            val matchesSearch = query.isEmpty() ||
                champ.name.contains(query, ignoreCase = true) ||
                champ.role.contains(query, ignoreCase = true) ||
                champ.traits.any {
                    it.contains(query, ignoreCase = true) ||
                        normalizeFilterLabel(it).contains(query, ignoreCase = true)
                } ||
                champ.bestItems.any { it.contains(query, ignoreCase = true) } ||
                champ.synergyComps.any { it.contains(query, ignoreCase = true) }
            val matchesCost = selectedCosts.isEmpty() || champ.cost in selectedCosts
            val matchesTier = selectedTier == null || champ.tier == selectedTier
            val matchesTraits = selectedTraits.isEmpty() || selectedTraits.all { selectedTrait ->
                champ.traits.any { champTrait -> championTraitMatches(champTrait, selectedTrait) }
            }
            matchesSearch && matchesCost && matchesTier && matchesTraits
        }
        .sortedWith(
            compareBy<ChampionTierEntry> { it.tier.sortOrder }
                .thenBy { it.avgPlacement }
                .thenBy { it.cost }
                .thenBy { it.name.lowercase(Locale.US) }
        )
}

internal fun extractItemCategoryOptions(items: List<ItemTierEntry>): List<ItemCategoryOption> {
    if (items.isEmpty()) return ItemCategory.entries.map { ItemCategoryOption(it, 0) }
    val counts = items.groupingBy { it.category }.eachCount()
    return ItemCategory.entries
        .mapNotNull { cat -> counts[cat]?.let { ItemCategoryOption(cat, it) } }
}

internal fun extractItemComponentOptions(items: List<ItemTierEntry>): List<ItemComponentOption> {
    if (items.isEmpty()) return emptyList()
    val countsByKey = linkedMapOf<String, Pair<String, MutableSet<String>>>()
    items.forEach { item ->
        val uniqueItemKey = item.id.ifBlank { item.name }
        item.components.forEach { rawComp ->
            val display = normalizeFilterLabel(rawComp)
            if (display.isNotBlank()) {
                val key = display.lowercase(Locale.US)
                val existing = countsByKey[key]
                if (existing == null) {
                    countsByKey[key] = display to linkedSetOf(uniqueItemKey)
                } else {
                    existing.second.add(uniqueItemKey)
                }
            }
        }
    }
    return countsByKey.values
        .map { (display, ids) -> ItemComponentOption(name = display, itemCount = ids.size) }
        .sortedWith(
            compareByDescending<ItemComponentOption> { it.itemCount }
                .thenBy { it.name.lowercase(Locale.US) }
        )
}

internal fun filterItemTiers(
    items: List<ItemTierEntry>,
    searchQuery: String,
    selectedTier: TierRank?,
    selectedCategories: Set<ItemCategory>,
    selectedComponents: Set<String>
): List<ItemTierEntry> {
    val query = searchQuery.trim()
    return items
        .filter { item ->
            val matchesSearch = query.isEmpty() ||
                item.name.contains(query, ignoreCase = true) ||
                item.category.label.contains(query, ignoreCase = true) ||
                item.statsSummary.contains(query, ignoreCase = true) ||
                item.components.any {
                    it.contains(query, ignoreCase = true) ||
                        normalizeFilterLabel(it).contains(query, ignoreCase = true)
                } ||
                item.bestHolders.any { it.contains(query, ignoreCase = true) }
            val matchesTier = selectedTier == null || item.tier == selectedTier
            val matchesCategory = selectedCategories.isEmpty() || item.category in selectedCategories
            val matchesComponents = selectedComponents.isEmpty() || selectedComponents.all { targetComp ->
                item.components.any { itemComp -> itemComponentMatches(itemComp, targetComp) }
            }
            matchesSearch && matchesTier && matchesCategory && matchesComponents
        }
        .sortedWith(
            compareBy<ItemTierEntry> { it.tier.sortOrder }
                .thenBy { it.avgPlacement }
                .thenBy { it.name.lowercase(Locale.US) }
        )
}

internal fun extractAugmentRarityOptions(augments: List<AugmentTierEntry>): List<AugmentRarityOption> {
    if (augments.isEmpty()) return AugmentTier.entries.map { AugmentRarityOption(it, 0) }
    val counts = augments.groupingBy { it.augmentTier }.eachCount()
    return AugmentTier.entries
        .mapNotNull { rank -> counts[rank]?.let { AugmentRarityOption(rank, it) } }
}

internal fun extractAugmentCategoryOptions(augments: List<AugmentTierEntry>): List<AugmentCategoryOption> {
    if (augments.isEmpty()) return emptyList()
    val countsByKey = linkedMapOf<String, Pair<String, MutableSet<String>>>()
    augments.forEach { aug ->
        val uniqueAugKey = aug.id.ifBlank { aug.name }
        val display = aug.category.trim()
        if (display.isNotBlank()) {
            val key = display.lowercase(Locale.US)
            val existing = countsByKey[key]
            if (existing == null) {
                countsByKey[key] = display to linkedSetOf(uniqueAugKey)
            } else {
                existing.second.add(uniqueAugKey)
            }
        }
    }
    return countsByKey.values
        .map { (display, ids) -> AugmentCategoryOption(name = display, augmentCount = ids.size) }
        .sortedWith(
            compareByDescending<AugmentCategoryOption> { it.augmentCount }
                .thenBy { it.name.lowercase(Locale.US) }
        )
}

internal fun filterAugmentTiers(
    augments: List<AugmentTierEntry>,
    searchQuery: String,
    selectedTier: TierRank?,
    selectedAugmentTiers: Set<AugmentTier>,
    selectedCategories: Set<String>
): List<AugmentTierEntry> {
    val query = searchQuery.trim()
    return augments
        .filter { aug ->
            val matchesSearch = query.isEmpty() ||
                aug.name.contains(query, ignoreCase = true) ||
                aug.description.contains(query, ignoreCase = true) ||
                aug.category.contains(query, ignoreCase = true) ||
                aug.augmentTier.label.contains(query, ignoreCase = true) ||
                aug.synergyComps.any { it.contains(query, ignoreCase = true) }
            val matchesTier = selectedTier == null || aug.tier == selectedTier
            val matchesRarity = selectedAugmentTiers.isEmpty() || aug.augmentTier in selectedAugmentTiers
            val matchesCategories = selectedCategories.isEmpty() || selectedCategories.any { targetCat ->
                augmentCategoryMatches(aug.category, targetCat)
            }
            matchesSearch && matchesTier && matchesRarity && matchesCategories
        }
        .sortedWith(
            compareBy<AugmentTierEntry> { it.tier.sortOrder }
                .thenBy { it.avgPlacement }
                .thenBy { it.name.lowercase(Locale.US) }
        )
}

@Composable
private fun FilterPanelHeaderRow(
    resultLabel: String,
    resultTag: String,
    activeFilterCount: Int,
    hasActiveFilters: Boolean,
    clearButtonTag: String,
    onClearAllFilters: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = resultLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag(resultTag)
            )
            if (activeFilterCount > 0) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "$activeFilterCount active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        if (hasActiveFilters) {
            TextButton(
                onClick = onClearAllFilters,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag(clearButtonTag)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Clear")
            }
        }
    }
}

@Composable
private fun TierRankFilterRow(
    selectedTier: TierRank?,
    onSelectTier: (TierRank?) -> Unit,
    tagPrefix: String
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterChip(
                selected = selectedTier == null,
                onClick = { onSelectTier(null) },
                label = { Text("All Tiers") },
                modifier = Modifier.testTag("${tagPrefix}_all")
            )
        }
        items(TierRank.entries, key = { it.name }) { tier ->
            FilterChip(
                selected = selectedTier == tier,
                onClick = { onSelectTier(tier) },
                label = { Text("${tier.label}-Tier") },
                modifier = Modifier.testTag("${tagPrefix}_${tier.label.lowercase(Locale.US)}")
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChampionFilterPanel(
    environment: TftEnvironment,
    totalChampions: Int,
    filteredCount: Int,
    availableCosts: List<Int>,
    availableTraits: List<ChampionTraitOption>,
    selectedTier: TierRank?,
    onSelectTier: (TierRank?) -> Unit,
    selectedCosts: Set<Int>,
    onToggleCost: (Int) -> Unit,
    onClearCosts: () -> Unit,
    selectedTraits: Set<String>,
    onToggleTrait: (String) -> Unit,
    onClearTraits: () -> Unit,
    showAllTraits: Boolean,
    onToggleShowAllTraits: () -> Unit,
    hasActiveFilters: Boolean,
    onClearAllFilters: () -> Unit
) {
    val activeFilterCount =
        (if (selectedTier != null) 1 else 0) + selectedCosts.size + selectedTraits.size
    val orderedTraits = remember(availableTraits, selectedTraits) {
        if (selectedTraits.isEmpty()) {
            availableTraits
        } else {
            availableTraits.sortedByDescending { option ->
                selectedTraits.any { it.equals(option.name, ignoreCase = true) }
            }
        }
    }

    HextechPanelCard(
        accentBorder = hasActiveFilters,
        modifier = Modifier.testTag("champion_filter_panel")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterPanelHeaderRow(
                resultLabel = "$filteredCount of $totalChampions champions",
                resultTag = "champion_filter_result_count",
                activeFilterCount = activeFilterCount,
                hasActiveFilters = hasActiveFilters,
                clearButtonTag = "champion_clear_filters_button",
                onClearAllFilters = onClearAllFilters
            )

            TierRankFilterRow(
                selectedTier = selectedTier,
                onSelectTier = onSelectTier,
                tagPrefix = "champ_filter_tier"
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = selectedCosts.isEmpty(),
                        onClick = onClearCosts,
                        label = { Text("All Costs") },
                        modifier = Modifier.testTag("champ_filter_cost_all")
                    )
                }
                items(availableCosts, key = { it }) { cost ->
                    val isSelected = cost in selectedCosts
                    val costTint = championCostColor(cost)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleCost(cost) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(costTint)
                            )
                        },
                        label = {
                            Text(
                                text = "${cost}g",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = costTint.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = costTint
                        ),
                        modifier = Modifier.testTag("champ_filter_cost_$cost")
                    )
                }
            }

            if (orderedTraits.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedTraits.isEmpty()) {
                            "Traits (${orderedTraits.size})"
                        } else {
                            "Traits (${selectedTraits.size} selected)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = onToggleShowAllTraits,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("champ_filter_traits_expand_toggle")
                    ) {
                        Text(if (showAllTraits) "Compact" else "All Traits")
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (showAllTraits) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showAllTraits) "Collapse trait grid" else "Expand all traits",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (showAllTraits) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("champ_filter_traits_grid")
                    ) {
                        FilterChip(
                            selected = selectedTraits.isEmpty(),
                            onClick = onClearTraits,
                            label = { Text("All Traits") },
                            modifier = Modifier.testTag("champ_filter_trait_all")
                        )
                        orderedTraits.forEach { option ->
                            val isSelected = selectedTraits.any { it.equals(option.name, ignoreCase = true) }
                            ChampionTraitFilterChip(
                                option = option,
                                selected = isSelected,
                                environment = environment,
                                onClick = { onToggleTrait(option.name) }
                            )
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("champ_filter_traits_row")
                    ) {
                        item {
                            FilterChip(
                                selected = selectedTraits.isEmpty(),
                                onClick = onClearTraits,
                                label = { Text("All Traits") },
                                modifier = Modifier.testTag("champ_filter_trait_all")
                            )
                        }
                        items(orderedTraits, key = { it.name }) { option ->
                            val isSelected = selectedTraits.any { it.equals(option.name, ignoreCase = true) }
                            ChampionTraitFilterChip(
                                option = option,
                                selected = isSelected,
                                environment = environment,
                                onClick = { onToggleTrait(option.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChampionTraitFilterChip(
    option: ChampionTraitOption,
    selected: Boolean,
    environment: TftEnvironment,
    onClick: () -> Unit
) {
    val traitTint = remember(option.name) { traitAccentColor(option.name) }
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            TftTraitIcon(
                traitName = option.name,
                environment = environment,
                size = 16.dp
            )
        },
        label = {
            Text(
                text = "${option.name} (${option.championCount})",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = traitTint.copy(alpha = 0.22f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = traitTint.copy(alpha = 0.35f),
            selectedBorderColor = traitTint
        ),
        modifier = Modifier.testTag("champ_filter_trait_${filterTestTagSlug(option.name)}")
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ItemFilterPanel(
    environment: TftEnvironment,
    totalItems: Int,
    filteredCount: Int,
    availableCategories: List<ItemCategoryOption>,
    availableComponents: List<ItemComponentOption>,
    selectedTier: TierRank?,
    onSelectTier: (TierRank?) -> Unit,
    selectedCategories: Set<ItemCategory>,
    onToggleCategory: (ItemCategory) -> Unit,
    onClearCategories: () -> Unit,
    selectedComponents: Set<String>,
    onToggleComponent: (String) -> Unit,
    onClearComponents: () -> Unit,
    showAllComponents: Boolean,
    onToggleShowAllComponents: () -> Unit,
    hasActiveFilters: Boolean,
    onClearAllFilters: () -> Unit
) {
    val activeFilterCount =
        (if (selectedTier != null) 1 else 0) + selectedCategories.size + selectedComponents.size
    val orderedComponents = remember(availableComponents, selectedComponents) {
        if (selectedComponents.isEmpty()) {
            availableComponents
        } else {
            availableComponents.sortedByDescending { option ->
                selectedComponents.any { it.equals(option.name, ignoreCase = true) }
            }
        }
    }

    HextechPanelCard(
        accentBorder = hasActiveFilters,
        modifier = Modifier.testTag("item_filter_panel")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterPanelHeaderRow(
                resultLabel = "$filteredCount of $totalItems items",
                resultTag = "item_filter_result_count",
                activeFilterCount = activeFilterCount,
                hasActiveFilters = hasActiveFilters,
                clearButtonTag = "item_clear_filters_button",
                onClearAllFilters = onClearAllFilters
            )

            TierRankFilterRow(
                selectedTier = selectedTier,
                onSelectTier = onSelectTier,
                tagPrefix = "item_filter_tier"
            )

            // Item Category filter strip (multi-select with semantic category dots)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = selectedCategories.isEmpty(),
                        onClick = onClearCategories,
                        label = { Text("All Types") },
                        modifier = Modifier.testTag("item_filter_category_all")
                    )
                }
                items(availableCategories, key = { it.category.name }) { option ->
                    val isSelected = option.category in selectedCategories
                    val catTint = itemCategoryColor(option.category)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleCategory(option.category) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(catTint)
                            )
                        },
                        label = {
                            Text(
                                text = if (option.itemCount > 0) {
                                    "${option.category.label} (${option.itemCount})"
                                } else {
                                    option.category.label
                                },
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = catTint.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = catTint
                        ),
                        modifier = Modifier.testTag("item_filter_category_${option.category.name.lowercase(Locale.US)}")
                    )
                }
            }

            // Item Recipe Component filter strip + expandable full component grid
            if (orderedComponents.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedComponents.isEmpty()) {
                            "Components (${orderedComponents.size})"
                        } else {
                            "Components (${selectedComponents.size} selected)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = onToggleShowAllComponents,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("item_filter_components_expand_toggle")
                    ) {
                        Text(if (showAllComponents) "Compact" else "All Parts")
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (showAllComponents) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showAllComponents) "Collapse components grid" else "Expand all components",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (showAllComponents) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("item_filter_components_grid")
                    ) {
                        FilterChip(
                            selected = selectedComponents.isEmpty(),
                            onClick = onClearComponents,
                            label = { Text("All Parts") },
                            modifier = Modifier.testTag("item_filter_component_all")
                        )
                        orderedComponents.forEach { option ->
                            val isSelected = selectedComponents.any { it.equals(option.name, ignoreCase = true) }
                            ItemComponentFilterChip(
                                option = option,
                                selected = isSelected,
                                environment = environment,
                                onClick = { onToggleComponent(option.name) }
                            )
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("item_filter_components_row")
                    ) {
                        item {
                            FilterChip(
                                selected = selectedComponents.isEmpty(),
                                onClick = onClearComponents,
                                label = { Text("All Parts") },
                                modifier = Modifier.testTag("item_filter_component_all")
                            )
                        }
                        items(orderedComponents, key = { it.name }) { option ->
                            val isSelected = selectedComponents.any { it.equals(option.name, ignoreCase = true) }
                            ItemComponentFilterChip(
                                option = option,
                                selected = isSelected,
                                environment = environment,
                                onClick = { onToggleComponent(option.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemComponentFilterChip(
    option: ItemComponentOption,
    selected: Boolean,
    environment: TftEnvironment,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            TftItemIcon(
                itemName = option.name,
                size = 16.dp,
                environment = environment
            )
        },
        label = {
            Text(
                text = "${option.name} (${option.itemCount})",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = 0.22f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = accent.copy(alpha = 0.35f),
            selectedBorderColor = accent
        ),
        modifier = Modifier.testTag("item_filter_component_${filterTestTagSlug(option.name)}")
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AugmentFilterPanel(
    totalAugments: Int,
    filteredCount: Int,
    availableRarities: List<AugmentRarityOption>,
    availableCategories: List<AugmentCategoryOption>,
    selectedTier: TierRank?,
    onSelectTier: (TierRank?) -> Unit,
    selectedRarities: Set<AugmentTier>,
    onToggleRarity: (AugmentTier) -> Unit,
    onClearRarities: () -> Unit,
    selectedCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    onClearCategories: () -> Unit,
    showAllCategories: Boolean,
    onToggleShowAllCategories: () -> Unit,
    hasActiveFilters: Boolean,
    onClearAllFilters: () -> Unit
) {
    val activeFilterCount =
        (if (selectedTier != null) 1 else 0) + selectedRarities.size + selectedCategories.size
    val orderedCategories = remember(availableCategories, selectedCategories) {
        if (selectedCategories.isEmpty()) {
            availableCategories
        } else {
            availableCategories.sortedByDescending { option ->
                selectedCategories.any { it.equals(option.name, ignoreCase = true) }
            }
        }
    }

    HextechPanelCard(
        accentBorder = hasActiveFilters,
        modifier = Modifier.testTag("augment_filter_panel")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterPanelHeaderRow(
                resultLabel = "$filteredCount of $totalAugments augments",
                resultTag = "augment_filter_result_count",
                activeFilterCount = activeFilterCount,
                hasActiveFilters = hasActiveFilters,
                clearButtonTag = "augment_clear_filters_button",
                onClearAllFilters = onClearAllFilters
            )

            TierRankFilterRow(
                selectedTier = selectedTier,
                onSelectTier = onSelectTier,
                tagPrefix = "augment_filter_tier"
            )

            // Augment Rarity filter strip (Silver / Gold / Prismatic multi-select)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = selectedRarities.isEmpty(),
                        onClick = onClearRarities,
                        label = { Text("All Rarities") },
                        modifier = Modifier.testTag("augment_filter_rarity_all")
                    )
                }
                items(availableRarities, key = { it.augmentTier.name }) { option ->
                    val isSelected = option.augmentTier in selectedRarities
                    val rarityTint = augmentTierColor(option.augmentTier)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleRarity(option.augmentTier) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(rarityTint)
                            )
                        },
                        label = {
                            Text(
                                text = if (option.augmentCount > 0) {
                                    "${option.augmentTier.label} (${option.augmentCount})"
                                } else {
                                    option.augmentTier.label
                                },
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = rarityTint.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = rarityTint
                        ),
                        modifier = Modifier.testTag("augment_filter_rarity_${option.augmentTier.name.lowercase(Locale.US)}")
                    )
                }
            }

            // Augment Category filter strip + expandable full grid
            if (orderedCategories.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedCategories.isEmpty()) {
                            "Categories (${orderedCategories.size})"
                        } else {
                            "Categories (${selectedCategories.size} selected)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = onToggleShowAllCategories,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("augment_filter_categories_expand_toggle")
                    ) {
                        Text(if (showAllCategories) "Compact" else "All Types")
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (showAllCategories) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showAllCategories) "Collapse categories grid" else "Expand all categories",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (showAllCategories) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("augment_filter_categories_grid")
                    ) {
                        FilterChip(
                            selected = selectedCategories.isEmpty(),
                            onClick = onClearCategories,
                            label = { Text("All Types") },
                            modifier = Modifier.testTag("augment_filter_category_all")
                        )
                        orderedCategories.forEach { option ->
                            val isSelected = selectedCategories.any { it.equals(option.name, ignoreCase = true) }
                            AugmentCategoryFilterChip(
                                option = option,
                                selected = isSelected,
                                onClick = { onToggleCategory(option.name) }
                            )
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("augment_filter_categories_row")
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategories.isEmpty(),
                                onClick = onClearCategories,
                                label = { Text("All Types") },
                                modifier = Modifier.testTag("augment_filter_category_all")
                            )
                        }
                        items(orderedCategories, key = { it.name }) { option ->
                            val isSelected = selectedCategories.any { it.equals(option.name, ignoreCase = true) }
                            AugmentCategoryFilterChip(
                                option = option,
                                selected = isSelected,
                                onClick = { onToggleCategory(option.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AugmentCategoryFilterChip(
    option: AugmentCategoryOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val secondaryAccent = MaterialTheme.colorScheme.secondary
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = "${option.name} (${option.augmentCount})",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = secondaryAccent.copy(alpha = 0.22f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = secondaryAccent.copy(alpha = 0.35f),
            selectedBorderColor = secondaryAccent
        ),
        modifier = Modifier.testTag("augment_filter_category_${filterTestTagSlug(option.name)}")
    )
}

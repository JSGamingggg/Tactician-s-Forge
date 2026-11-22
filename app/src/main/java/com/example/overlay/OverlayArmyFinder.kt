package com.example.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChampionTierEntry
import com.example.data.model.ItemTierEntry
import com.example.data.model.MetaComp
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank
import com.example.ui.components.TftChampionAvatar
import com.example.ui.components.TftItemIcon
import com.example.ui.components.formatChampionDisplayName
import com.example.ui.components.formatRiotAssetLabel
import com.example.ui.theme.ArcaneCyan
import com.example.ui.theme.BrightGold
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SlateElevated
import kotlinx.coroutines.delay
import java.util.Locale

data class OverlayChampionChoice(
    val name: String,
    val cost: Int,
    val compCount: Int,
    val normalizedKey: String = OverlayArmySearchEngine.normalize(name),
    val lowerName: String = name.lowercase(Locale.US)
)

data class OverlayItemChoice(
    val name: String,
    val isComponent: Boolean,
    val compCount: Int,
    val normalizedKey: String = OverlayArmySearchEngine.normalize(name),
    val lowerName: String = name.lowercase(Locale.US)
)

/**
 * Precomputed search index for a set of compositions so overlay search keystrokes and
 * champion/item chip toggles execute in sub-millisecond O(N) time with zero regex or
 * catalog scans on the UI thread.
 */
data class IndexedOverlayArmy(
    val comp: MetaComp,
    val champKeys: Set<String>,
    val exactItemKeys: Set<String>,
    val componentKeys: Set<String>,
    val searchableLowerBlob: String,
    val searchableNormBlob: String
)

data class OverlayArmySearchIndex(
    val entries: List<IndexedOverlayArmy>
)

/**
 * Pure matching & indexing engine for searching overlay team compositions (armies)
 * by one or more Champions, Completed Items, Base Item Components, or text queries.
 */
object OverlayArmySearchEngine {

    private val baseComponentDisplayNames = listOf(
        "B.F. Sword",
        "Recurve Bow",
        "Needlessly Large Rod",
        "Tear of the Goddess",
        "Chain Vest",
        "Negatron Cloak",
        "Giant's Belt",
        "Sparring Gloves",
        "Spatula",
        "Frying Pan"
    )

    private val baseComponentKeys: Set<String> = baseComponentDisplayNames.map(::normalize).toSet()

    private val summonTokenPatterns = listOf(
        "stonebark", "lifeblossom", "deepwoodprotector", "protector", "trainingdummy", "golem", "turret", "summon"
    )

    /**
     * Standard TFT completed item -> component recipes so component filtering works reliably
     * across both LIVE (Set 18) and PBE (Set 19), even if an item entry omits `components`.
     */
    private val builtInItemRecipes: Map<String, List<String>> = mapOf(
        "deathblade" to listOf("B.F. Sword", "B.F. Sword"),
        "giantslayer" to listOf("B.F. Sword", "Recurve Bow"),
        "hextechgunblade" to listOf("B.F. Sword", "Needlessly Large Rod"),
        "spearofshojin" to listOf("B.F. Sword", "Tear of the Goddess"),
        "edgeofnight" to listOf("B.F. Sword", "Chain Vest"),
        "bloodthirster" to listOf("B.F. Sword", "Negatron Cloak"),
        "steraksgage" to listOf("B.F. Sword", "Giant's Belt"),
        "infinityedge" to listOf("B.F. Sword", "Sparring Gloves"),

        "redbuff" to listOf("Recurve Bow", "Recurve Bow"),
        "guinsoosrageblade" to listOf("Recurve Bow", "Needlessly Large Rod"),
        "voidstaff" to listOf("Recurve Bow", "Tear of the Goddess"),
        "statikkshiv" to listOf("Recurve Bow", "Tear of the Goddess"),
        "titansresolve" to listOf("Recurve Bow", "Chain Vest"),
        "krakensfury" to listOf("Recurve Bow", "Negatron Cloak"),
        "runaanshurricane" to listOf("Recurve Bow", "Negatron Cloak"),
        "nashorstooth" to listOf("Recurve Bow", "Giant's Belt"),
        "lastwhisper" to listOf("Recurve Bow", "Sparring Gloves"),

        "rabadonsdeathcap" to listOf("Needlessly Large Rod", "Needlessly Large Rod"),
        "archangelsstaff" to listOf("Needlessly Large Rod", "Tear of the Goddess"),
        "crownguard" to listOf("Needlessly Large Rod", "Chain Vest"),
        "ionicspark" to listOf("Needlessly Large Rod", "Negatron Cloak"),
        "morellonomicon" to listOf("Needlessly Large Rod", "Giant's Belt"),
        "jeweledgauntlet" to listOf("Needlessly Large Rod", "Sparring Gloves"),

        "bluebuff" to listOf("Tear of the Goddess", "Tear of the Goddess"),
        "protectorsvow" to listOf("Tear of the Goddess", "Chain Vest"),
        "adaptivehelm" to listOf("Tear of the Goddess", "Negatron Cloak"),
        "spiritvisage" to listOf("Tear of the Goddess", "Giant's Belt"),
        "redemption" to listOf("Tear of the Goddess", "Giant's Belt"),
        "handofjustice" to listOf("Tear of the Goddess", "Sparring Gloves"),

        "bramblevest" to listOf("Chain Vest", "Chain Vest"),
        "gargoylestoneplate" to listOf("Chain Vest", "Negatron Cloak"),
        "sunfirecape" to listOf("Chain Vest", "Giant's Belt"),
        "steadfastheart" to listOf("Chain Vest", "Sparring Gloves"),

        "dragonsclaw" to listOf("Negatron Cloak", "Negatron Cloak"),
        "evenshroud" to listOf("Negatron Cloak", "Giant's Belt"),
        "quicksilver" to listOf("Negatron Cloak", "Sparring Gloves"),

        "warmogsarmor" to listOf("Giant's Belt", "Giant's Belt"),
        "strikersflail" to listOf("Giant's Belt", "Sparring Gloves"),
        "guardbreaker" to listOf("Giant's Belt", "Sparring Gloves"),

        "thiefsgloves" to listOf("Sparring Gloves", "Sparring Gloves"),
        "tacticianscrown" to listOf("Spatula", "Spatula"),
        "tacticianscape" to listOf("Spatula", "Frying Pan"),
        "tacticiansshield" to listOf("Frying Pan", "Frying Pan")
    )

    private val builtInComponentKeyRecipes: Map<String, List<String>> =
        builtInItemRecipes.mapValues { (_, comps) -> comps.map(::normalize) }

    /**
     * Zero-regex ASCII alphanumeric normalizer — orders of magnitude faster than allocating
     * `Regex("[^a-z0-9]")` on every item/champion comparison during overlay search.
     */
    fun normalize(raw: String): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder(raw.length)
        for (i in 0 until raw.length) {
            val ch = raw[i]
            when {
                ch in 'a'..'z' || ch in '0'..'9' -> sb.append(ch)
                ch in 'A'..'Z' -> sb.append((ch.code + 32).toChar())
            }
        }
        return sb.toString()
    }

    fun isBaseComponent(itemName: String): Boolean =
        baseComponentKeys.contains(normalize(formatRiotAssetLabel(itemName)))

    private fun buildCatalogRecipeKeyMap(catalogItems: List<ItemTierEntry>): Map<String, List<String>> {
        if (catalogItems.isEmpty()) return emptyMap()
        val map = HashMap<String, List<String>>(catalogItems.size * 2)
        for (item in catalogItems) {
            if (item.components.isEmpty()) continue
            val normComponents = item.components.mapNotNull { c ->
                val n = normalize(c)
                if (n.isNotEmpty()) n else null
            }
            if (normComponents.isEmpty()) continue
            val key = normalize(item.name)
            if (key.isNotEmpty()) {
                map.putIfAbsent(key, normComponents)
            }
        }
        return map
    }

    private fun resolveComponentKeysFast(
        cleanLabel: String,
        normalizedKey: String,
        catalogRecipeKeyMap: Map<String, List<String>>
    ): List<String> {
        if (baseComponentKeys.contains(normalizedKey)) return listOf(normalizedKey)
        catalogRecipeKeyMap[normalizedKey]?.let { return it }
        builtInComponentKeyRecipes[normalizedKey]?.let { return it }
        if (normalizedKey.contains("emblem")) return listOf("spatula")
        return emptyList()
    }

    fun resolveComponentsForItem(
        itemName: String,
        catalogItems: List<ItemTierEntry>
    ): List<String> {
        val clean = formatRiotAssetLabel(itemName)
        val key = normalize(clean)
        if (baseComponentKeys.contains(key)) return listOf(clean)

        val fromCatalog = catalogItems.firstOrNull { normalize(it.name) == key }
            ?.components
            ?.filter { it.isNotBlank() }
        if (!fromCatalog.isNullOrEmpty()) return fromCatalog

        builtInItemRecipes[key]?.let { return it }

        if (key.contains("emblem")) {
            return listOf("Spatula")
        }
        return emptyList()
    }

    fun extractCompChampionKeys(comp: MetaComp): Set<String> {
        val keys = LinkedHashSet<String>()
        if (comp.carryName.isNotBlank()) {
            keys.add(normalize(formatChampionDisplayName(comp.carryName)))
        }
        comp.hexPlacements.forEach { p ->
            if (p.unitName.isNotBlank()) {
                keys.add(normalize(formatChampionDisplayName(p.unitName)))
            }
        }
        comp.coreUnits.forEach { u ->
            if (u.isNotBlank()) {
                keys.add(normalize(formatChampionDisplayName(u)))
            }
        }
        return keys
    }

    fun extractCompItemLabels(comp: MetaComp): List<String> {
        val items = LinkedHashSet<String>()
        comp.hexPlacements.forEach { placement ->
            placement.items.forEach { rawItem ->
                val clean = formatRiotAssetLabel(rawItem).trim()
                if (clean.isNotBlank()) {
                    items.add(clean)
                }
            }
        }
        return items.toList()
    }

    fun extractCompItemKeysAndComponents(
        comp: MetaComp,
        catalogItems: List<ItemTierEntry>
    ): Pair<Set<String>, Set<String>> {
        val recipeMap = buildCatalogRecipeKeyMap(catalogItems)
        return extractCompItemKeysAndComponentsFast(comp, recipeMap)
    }

    private fun extractCompItemKeysAndComponentsFast(
        comp: MetaComp,
        catalogRecipeKeyMap: Map<String, List<String>>
    ): Pair<Set<String>, Set<String>> {
        val exactItemKeys = HashSet<String>()
        val componentKeys = HashSet<String>()
        comp.hexPlacements.forEach { placement ->
            placement.items.forEach { rawItem ->
                val clean = formatRiotAssetLabel(rawItem).trim()
                if (clean.isEmpty()) return@forEach
                val norm = normalize(clean)
                if (norm.isEmpty()) return@forEach
                if (exactItemKeys.add(norm)) {
                    resolveComponentKeysFast(clean, norm, catalogRecipeKeyMap).forEach { cKey ->
                        componentKeys.add(cKey)
                    }
                }
            }
        }
        return exactItemKeys to componentKeys
    }

    /**
     * Builds a precomputed [OverlayArmySearchIndex] once per `(comps, catalogItems)` change so
     * filtering on every search keystroke or chip tap performs zero parsing or catalog lookups.
     */
    fun buildSearchIndex(
        comps: List<MetaComp>,
        catalogItems: List<ItemTierEntry>
    ): OverlayArmySearchIndex {
        if (comps.isEmpty()) return OverlayArmySearchIndex(emptyList())
        val recipeMap = buildCatalogRecipeKeyMap(catalogItems)
        val indexed = comps.map { comp ->
            val champKeys = extractCompChampionKeys(comp)
            val (exactItemKeys, componentKeys) = extractCompItemKeysAndComponentsFast(comp, recipeMap)
            val lowerBlob = buildString(128) {
                append(comp.title.lowercase(Locale.US))
                append('\n')
                append(comp.carryName.lowercase(Locale.US))
                comp.traits.forEach { t ->
                    append('\n')
                    append(t.lowercase(Locale.US))
                }
            }
            val normBlob = buildString(192) {
                append(normalize(comp.title))
                append(' ')
                append(normalize(comp.carryName))
                comp.traits.forEach { t ->
                    append(' ')
                    append(normalize(t))
                }
                champKeys.forEach { k ->
                    append(' ')
                    append(k)
                }
                exactItemKeys.forEach { k ->
                    append(' ')
                    append(k)
                }
                componentKeys.forEach { k ->
                    append(' ')
                    append(k)
                }
            }
            IndexedOverlayArmy(
                comp = comp,
                champKeys = champKeys,
                exactItemKeys = exactItemKeys,
                componentKeys = componentKeys,
                searchableLowerBlob = lowerBlob,
                searchableNormBlob = normBlob
            )
        }
        return OverlayArmySearchIndex(indexed)
    }

    /**
     * Fast sub-millisecond filtering over a precomputed [OverlayArmySearchIndex].
     */
    fun filterIndexedArmies(
        index: OverlayArmySearchIndex,
        tierFilter: TierRank?,
        selectedChampions: Set<String>,
        selectedItems: Set<String>,
        query: String
    ): List<MetaComp> {
        val normalizedChamps = if (selectedChampions.isEmpty()) {
            emptyList()
        } else {
            selectedChampions.mapNotNull { c ->
                val n = normalize(formatChampionDisplayName(c))
                if (n.isNotEmpty()) n else null
            }
        }
        val normalizedItems = if (selectedItems.isEmpty()) {
            emptyList()
        } else {
            selectedItems.mapNotNull { item ->
                val n = normalize(formatRiotAssetLabel(item))
                if (n.isNotEmpty()) n to baseComponentKeys.contains(n) else null
            }
        }
        val trimmedQuery = query.trim().lowercase(Locale.US)
        val normalizedQuery = if (trimmedQuery.isEmpty()) "" else normalize(trimmedQuery)

        val result = ArrayList<MetaComp>(index.entries.size)
        for (entry in index.entries) {
            if (tierFilter != null && entry.comp.tier != tierFilter) continue
            if (normalizedChamps.isNotEmpty() && !normalizedChamps.all { entry.champKeys.contains(it) }) continue
            if (normalizedItems.isNotEmpty()) {
                var allItemsMatch = true
                for ((wantedKey, isComp) in normalizedItems) {
                    val matched = if (isComp) {
                        entry.componentKeys.contains(wantedKey) || entry.exactItemKeys.contains(wantedKey)
                    } else {
                        entry.exactItemKeys.contains(wantedKey)
                    }
                    if (!matched) {
                        allItemsMatch = false
                        break
                    }
                }
                if (!allItemsMatch) continue
            }
            if (trimmedQuery.isNotEmpty()) {
                val matchesText = entry.searchableLowerBlob.contains(trimmedQuery) ||
                    (normalizedQuery.isNotEmpty() && entry.searchableNormBlob.contains(normalizedQuery))
                if (!matchesText) continue
            }
            result.add(entry.comp)
        }
        return result
    }

    fun extractChampionChoices(
        comps: List<MetaComp>,
        catalogChampions: List<ChampionTierEntry>,
        environment: TftEnvironment
    ): List<OverlayChampionChoice> {
        val catalogCostByKey = HashMap<String, Int>(catalogChampions.size)
        for (champ in catalogChampions) {
            if (champ.environment == environment) {
                catalogCostByKey[normalize(formatChampionDisplayName(champ.name))] = champ.cost
            }
        }

        val countsByKey = LinkedHashMap<String, Int>()
        val displayByKey = LinkedHashMap<String, String>()
        val costByKey = LinkedHashMap<String, Int>()

        comps.forEach { comp ->
            val seenInComp = HashSet<String>()
            comp.hexPlacements.forEach { p ->
                val label = formatChampionDisplayName(p.unitName).trim()
                val key = normalize(label)
                if (key.isBlank() || isLikelySummonToken(key)) return@forEach
                displayByKey.putIfAbsent(key, label)
                val resolvedCost = catalogCostByKey[key] ?: p.cost.takeIf { it > 0 } ?: 1
                costByKey[key] = maxOf(costByKey[key] ?: 0, resolvedCost)
                if (seenInComp.add(key)) {
                    countsByKey[key] = (countsByKey[key] ?: 0) + 1
                }
            }
            comp.coreUnits.forEach { rawUnit ->
                val label = formatChampionDisplayName(rawUnit).trim()
                val key = normalize(label)
                if (key.isBlank() || isLikelySummonToken(key)) return@forEach
                displayByKey.putIfAbsent(key, label)
                costByKey.putIfAbsent(key, catalogCostByKey[key] ?: 1)
                if (seenInComp.add(key)) {
                    countsByKey[key] = (countsByKey[key] ?: 0) + 1
                }
            }
        }

        return countsByKey.entries
            .map { (key, count) ->
                val name = displayByKey[key] ?: key
                OverlayChampionChoice(
                    name = name,
                    cost = (costByKey[key] ?: 1).coerceIn(1, 7),
                    compCount = count,
                    normalizedKey = key,
                    lowerName = name.lowercase(Locale.US)
                )
            }
            .sortedWith(
                compareByDescending<OverlayChampionChoice> { it.compCount }
                    .thenByDescending { it.cost }
                    .thenBy { it.lowerName }
            )
    }

    fun extractItemChoices(
        comps: List<MetaComp>,
        catalogItems: List<ItemTierEntry>,
        environment: TftEnvironment
    ): List<OverlayItemChoice> {
        val envCatalogItems = catalogItems.filter { it.environment == environment }
        val recipeMap = buildCatalogRecipeKeyMap(envCatalogItems)
        val exactCounts = LinkedHashMap<String, Int>()
        val displayByKey = LinkedHashMap<String, String>()
        val componentCompCounts = LinkedHashMap<String, Int>()

        comps.forEach { comp ->
            val exactInComp = HashSet<String>()
            val componentsInComp = HashSet<String>()
            comp.hexPlacements.forEach { placement ->
                placement.items.forEach { rawItem ->
                    val clean = formatRiotAssetLabel(rawItem).trim()
                    if (clean.isEmpty()) return@forEach
                    val key = normalize(clean)
                    if (key.isEmpty()) return@forEach
                    displayByKey.putIfAbsent(key, clean)
                    if (exactInComp.add(key)) {
                        resolveComponentKeysFast(clean, key, recipeMap).forEach { cKey ->
                            componentsInComp.add(cKey)
                        }
                    }
                }
            }
            exactInComp.forEach { key ->
                exactCounts[key] = (exactCounts[key] ?: 0) + 1
            }
            componentsInComp.forEach { cKey ->
                componentCompCounts[cKey] = (componentCompCounts[cKey] ?: 0) + 1
            }
        }

        val components = baseComponentDisplayNames.mapNotNull { compName ->
            val key = normalize(compName)
            val count = componentCompCounts[key] ?: 0
            if (count > 0 || comps.isNotEmpty()) {
                OverlayItemChoice(
                    name = compName,
                    isComponent = true,
                    compCount = count,
                    normalizedKey = key,
                    lowerName = compName.lowercase(Locale.US)
                )
            } else {
                null
            }
        }

        val completed = exactCounts.entries
            .filterNot { baseComponentKeys.contains(it.key) }
            .map { (key, count) ->
                val name = displayByKey[key] ?: key
                OverlayItemChoice(
                    name = name,
                    isComponent = false,
                    compCount = count,
                    normalizedKey = key,
                    lowerName = name.lowercase(Locale.US)
                )
            }
            .sortedWith(
                compareByDescending<OverlayItemChoice> { it.compCount }
                    .thenBy { it.lowerName }
            )

        return components + completed
    }

    /**
     * Filters [comps] to armies matching ALL [selectedChampions], ALL [selectedItems]
     * (supporting both exact completed items and base component recipe matching),
     * and optional free-text [query] (matching title, carry, champions, items, or traits).
     */
    fun filterArmies(
        comps: List<MetaComp>,
        selectedChampions: Set<String>,
        selectedItems: Set<String>,
        query: String,
        catalogItems: List<ItemTierEntry> = emptyList()
    ): List<MetaComp> {
        if (selectedChampions.isEmpty() && selectedItems.isEmpty() && query.isBlank()) {
            return comps
        }
        val index = buildSearchIndex(comps, catalogItems)
        return filterIndexedArmies(
            index = index,
            tierFilter = null,
            selectedChampions = selectedChampions,
            selectedItems = selectedItems,
            query = query
        )
    }

    private fun isLikelySummonToken(normalizedKey: String): Boolean =
        summonTokenPatterns.any { normalizedKey.contains(it) }
}

@Composable
internal fun OverlayActiveArmyFiltersBar(
    selectedChampions: Set<String>,
    selectedItems: Set<String>,
    finderQuery: String,
    matchedCount: Int,
    environment: TftEnvironment,
    onRemoveChampion: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearQuery: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedChampions.isEmpty() && selectedItems.isEmpty() && finderQuery.isBlank()) return

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = DeepSlate,
        border = BorderStroke(1.dp, ArcaneCyan.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("overlay_active_army_filters_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = BrightGold.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, BrightGold.copy(alpha = 0.55f))
            ) {
                Text(
                    text = "$matchedCount ${if (matchedCount == 1) "army" else "armies"}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrightGold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            if (finderQuery.isNotBlank()) {
                OverlayRemovableFilterChip(
                    label = "\"$finderQuery\"",
                    accentColor = ArcaneCyan,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = ArcaneCyan,
                            modifier = Modifier.size(12.dp)
                        )
                    },
                    onRemove = onClearQuery,
                    testTag = "overlay_active_query_chip"
                )
            }
            selectedChampions.forEach { champ ->
                OverlayRemovableFilterChip(
                    label = champ,
                    accentColor = BrightGold,
                    leadingContent = {
                        TftChampionAvatar(
                            name = champ,
                            cost = 3,
                            size = 16.dp,
                            environment = environment,
                            showBadge = false
                        )
                    },
                    onRemove = { onRemoveChampion(champ) },
                    testTag = "overlay_selected_champ_${OverlayArmySearchEngine.normalize(champ)}"
                )
            }
            selectedItems.forEach { item ->
                OverlayRemovableFilterChip(
                    label = item,
                    accentColor = ArcaneCyan,
                    leadingContent = {
                        TftItemIcon(
                            itemName = item,
                            size = 15.dp,
                            environment = environment
                        )
                    },
                    onRemove = { onRemoveItem(item) },
                    testTag = "overlay_selected_item_${OverlayArmySearchEngine.normalize(item)}"
                )
            }
            Surface(
                onClick = onClearAll,
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF3A1A22),
                border = BorderStroke(1.dp, Color(0xFFE74C3C).copy(alpha = 0.6f)),
                modifier = Modifier.testTag("overlay_clear_army_filters_button")
            ) {
                Text(
                    text = "Clear",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF8E82),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun OverlayRemovableFilterChip(
    label: String,
    accentColor: Color,
    leadingContent: @Composable () -> Unit,
    onRemove: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onRemove,
        shape = RoundedCornerShape(7.dp),
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.65f)),
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            leadingContent()
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove $label filter",
                tint = accentColor,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}

@Composable
internal fun OverlayArmyFinderPanel(
    environment: TftEnvironment,
    sessionState: OverlaySessionState,
    championChoices: List<OverlayChampionChoice>,
    itemChoices: List<OverlayItemChoice>,
    matchedArmiesCount: Int,
    onSearchFocusChanged: (Boolean) -> Unit,
    onClosePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val activeTab = sessionState.finderTab
    val sessionQuery = sessionState.finderQuery
    val costFilter = sessionState.finderCostFilter
    val itemScope = sessionState.finderItemScope
    val selectedChampions = sessionState.selectedChampions
    val selectedItems = sessionState.selectedItems

    // Keep text input locally responsive at 60fps and debounce propagation into sessionState
    // so rapid typing never forces full overlay comp-list recomposition on every single keystroke.
    var localQuery by remember { mutableStateOf(sessionQuery) }
    LaunchedEffect(sessionQuery) {
        if (sessionQuery != localQuery) {
            localQuery = sessionQuery
        }
    }
    LaunchedEffect(localQuery) {
        if (localQuery != sessionState.finderQuery) {
            if (localQuery.isEmpty()) {
                sessionState.updateFinderQuery("")
            } else {
                delay(90L)
                sessionState.updateFinderQuery(localQuery)
            }
        }
    }

    val filteredChampions = remember(championChoices, localQuery, costFilter, activeTab) {
        if (activeTab != OverlayFinderTab.CHAMPIONS) return@remember emptyList()
        val q = localQuery.trim().lowercase(Locale.US)
        val normQ = if (q.isEmpty()) "" else OverlayArmySearchEngine.normalize(q)
        championChoices.filter { c ->
            val matchesCost = when (costFilter) {
                null -> true
                5 -> c.cost >= 5
                else -> c.cost == costFilter
            }
            val matchesQ = q.isEmpty() || c.lowerName.contains(q) ||
                (normQ.isNotEmpty() && c.normalizedKey.contains(normQ))
            matchesCost && matchesQ
        }
    }

    val filteredItems = remember(itemChoices, localQuery, itemScope, activeTab) {
        if (activeTab != OverlayFinderTab.ITEMS) return@remember emptyList()
        val q = localQuery.trim().lowercase(Locale.US)
        val normQ = if (q.isEmpty()) "" else OverlayArmySearchEngine.normalize(q)
        itemChoices.filter { item ->
            val matchesScope = when (itemScope) {
                OverlayItemFilterScope.ALL -> true
                OverlayItemFilterScope.COMPONENTS -> item.isComponent
                OverlayItemFilterScope.COMPLETED -> !item.isComponent
            }
            val matchesQ = q.isEmpty() || item.lowerName.contains(q) ||
                (normQ.isNotEmpty() && item.normalizedKey.contains(normQ))
            matchesScope && matchesQ
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DeepSlate,
        border = BorderStroke(1.2.dp, ArcaneCyan.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("overlay_army_finder_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Search box + Done button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ObsidianVoid,
                    border = BorderStroke(1.dp, SlateElevated),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            onSearchFocusChanged(true)
                            runCatching { focusRequester.requestFocus() }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search champions or items",
                            tint = ArcaneCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (localQuery.isEmpty()) {
                                Text(
                                    text = "Search champion, item, or comp…",
                                    fontSize = 11.sp,
                                    color = Color(0xFF7D8DA6),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = localQuery,
                                onValueChange = { localQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(BrightGold),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        sessionState.updateFinderQuery(localQuery)
                                        focusManager.clearFocus()
                                        onSearchFocusChanged(false)
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { state ->
                                        onSearchFocusChanged(state.isFocused)
                                    }
                                    .testTag("overlay_army_search_input")
                            )
                        }
                        if (localQuery.isNotEmpty()) {
                            Surface(
                                onClick = {
                                    localQuery = ""
                                    sessionState.updateFinderQuery("")
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = SlateElevated,
                                modifier = Modifier.testTag("overlay_army_search_clear_text")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search text",
                                    tint = Color(0xFFB7C4D8),
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .size(12.dp)
                                )
                            }
                        }
                    }
                }
                Surface(
                    onClick = {
                        sessionState.updateFinderQuery(localQuery)
                        focusManager.clearFocus()
                        onSearchFocusChanged(false)
                        onClosePanel()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = BrightGold.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, BrightGold.copy(alpha = 0.65f)),
                    modifier = Modifier.testTag("overlay_army_finder_done_button")
                ) {
                    Text(
                        text = "Done ($matchedArmiesCount)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrightGold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            // Tab switcher (Champions | Items) + sub-filters in one compact horizontal row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                OverlayFinderPill(
                    label = if (selectedChampions.isNotEmpty()) "Champions (${selectedChampions.size})" else "Champions",
                    selected = activeTab == OverlayFinderTab.CHAMPIONS,
                    accentColor = BrightGold,
                    icon = Icons.Default.Groups,
                    onClick = { sessionState.selectFinderTab(OverlayFinderTab.CHAMPIONS) },
                    testTag = "overlay_finder_tab_champions"
                )
                OverlayFinderPill(
                    label = if (selectedItems.isNotEmpty()) "Items (${selectedItems.size})" else "Items",
                    selected = activeTab == OverlayFinderTab.ITEMS,
                    accentColor = ArcaneCyan,
                    icon = Icons.Default.Shield,
                    onClick = { sessionState.selectFinderTab(OverlayFinderTab.ITEMS) },
                    testTag = "overlay_finder_tab_items"
                )

                Spacer(Modifier.width(4.dp))

                if (activeTab == OverlayFinderTab.CHAMPIONS) {
                    listOf(null to "All", 1 to "1g", 2 to "2g", 3 to "3g", 4 to "4g", 5 to "5g+").forEach { (cost, label) ->
                        OverlayFinderPill(
                            label = label,
                            selected = costFilter == cost,
                            accentColor = Color(0xFFB8C7DC),
                            onClick = {
                                if (cost == null) {
                                    if (costFilter != null) sessionState.selectFinderCostFilter(costFilter)
                                } else {
                                    sessionState.selectFinderCostFilter(cost)
                                }
                            },
                            testTag = "overlay_finder_cost_${cost ?: "all"}"
                        )
                    }
                } else {
                    listOf(
                        OverlayItemFilterScope.ALL to "All",
                        OverlayItemFilterScope.COMPONENTS to "Components",
                        OverlayItemFilterScope.COMPLETED to "Completed"
                    ).forEach { (scope, label) ->
                        OverlayFinderPill(
                            label = label,
                            selected = itemScope == scope,
                            accentColor = ArcaneCyan,
                            onClick = { sessionState.selectFinderItemScope(scope) },
                            testTag = "overlay_finder_item_scope_${scope.name.lowercase(Locale.US)}"
                        )
                    }
                }
            }

            // Virtualized LazyVerticalGrid — only composes & loads icons for ~9-12 visible chips
            // instead of mounting 80-120 Coil AsyncImages in a single frame!
            if (activeTab == OverlayFinderTab.CHAMPIONS) {
                if (filteredChampions.isEmpty()) {
                    Text(
                        text = "No ${environment.label} champions match that filter.",
                        fontSize = 10.sp,
                        color = Color(0xFF93A1B8),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 104.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(
                            items = filteredChampions,
                            key = { it.normalizedKey },
                            contentType = { "overlay_champ_pick" }
                        ) { choice ->
                            val isSelected = selectedChampions.contains(choice.name)
                            Surface(
                                onClick = { sessionState.toggleSelectedChampion(choice.name) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) BrightGold.copy(alpha = 0.2f) else ObsidianVoid,
                                border = BorderStroke(
                                    width = if (isSelected) 1.3.dp else 1.dp,
                                    color = if (isSelected) BrightGold else SlateElevated
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                                    .testTag("overlay_pick_champ_${choice.normalizedKey}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    TftChampionAvatar(
                                        name = choice.name,
                                        cost = choice.cost,
                                        size = 20.dp,
                                        environment = environment,
                                        showBadge = false
                                    )
                                    Text(
                                        text = choice.name,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) BrightGold else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${choice.compCount}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) BrightGold else Color(0xFF7F91AB)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = BrightGold,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (filteredItems.isEmpty()) {
                    Text(
                        text = "No ${environment.label} items match that filter.",
                        fontSize = 10.sp,
                        color = Color(0xFF93A1B8),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 122.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(
                            items = filteredItems,
                            key = { "${if (it.isComponent) "c_" else "f_"}${it.normalizedKey}" },
                            contentType = { "overlay_item_pick" }
                        ) { choice ->
                            val isSelected = selectedItems.contains(choice.name)
                            val accent = if (choice.isComponent) BrightGold else ArcaneCyan
                            Surface(
                                onClick = { sessionState.toggleSelectedItem(choice.name) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) accent.copy(alpha = 0.2f) else ObsidianVoid,
                                border = BorderStroke(
                                    width = if (isSelected) 1.3.dp else 1.dp,
                                    color = if (isSelected) accent else SlateElevated
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                                    .testTag("overlay_pick_item_${choice.normalizedKey}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    TftItemIcon(
                                        itemName = choice.name,
                                        size = 18.dp,
                                        environment = environment
                                    )
                                    Text(
                                        text = choice.name,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) accent else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${choice.compCount}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) accent else Color(0xFF7F91AB)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(11.dp)
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
}

@Composable
private fun OverlayFinderPill(
    label: String,
    selected: Boolean,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(7.dp),
        color = if (selected) accentColor.copy(alpha = 0.2f) else ObsidianVoid,
        border = BorderStroke(1.dp, if (selected) accentColor else SlateElevated),
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) accentColor else Color(0xFF93A1B8),
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) accentColor else Color(0xFFC5D0E0),
                maxLines = 1
            )
        }
    }
}

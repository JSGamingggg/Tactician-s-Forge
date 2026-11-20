package com.example.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.ui.components.TftChampionAvatar
import com.example.ui.components.TftItemIcon
import com.example.ui.components.formatChampionDisplayName
import com.example.ui.components.formatRiotAssetLabel
import com.example.ui.theme.ArcaneCyan
import com.example.ui.theme.BrightGold
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SlateElevated
import java.util.Locale

data class OverlayChampionChoice(
    val name: String,
    val cost: Int,
    val compCount: Int
)

data class OverlayItemChoice(
    val name: String,
    val isComponent: Boolean,
    val compCount: Int
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

    fun normalize(raw: String): String =
        raw.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

    fun isBaseComponent(itemName: String): Boolean =
        baseComponentKeys.contains(normalize(formatRiotAssetLabel(itemName)))

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
        val exactItemKeys = HashSet<String>()
        val componentKeys = HashSet<String>()
        extractCompItemLabels(comp).forEach { label ->
            val norm = normalize(label)
            exactItemKeys.add(norm)
            resolveComponentsForItem(label, catalogItems).forEach { component ->
                componentKeys.add(normalize(component))
            }
        }
        return exactItemKeys to componentKeys
    }

    fun extractChampionChoices(
        comps: List<MetaComp>,
        catalogChampions: List<ChampionTierEntry>,
        environment: TftEnvironment
    ): List<OverlayChampionChoice> {
        val catalogCostByKey = catalogChampions
            .filter { it.environment == environment }
            .associate { normalize(formatChampionDisplayName(it.name)) to it.cost }

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
                OverlayChampionChoice(
                    name = displayByKey[key] ?: key,
                    cost = (costByKey[key] ?: 1).coerceIn(1, 7),
                    compCount = count
                )
            }
            .sortedWith(
                compareByDescending<OverlayChampionChoice> { it.compCount }
                    .thenByDescending { it.cost }
                    .thenBy { it.name.lowercase(Locale.US) }
            )
    }

    fun extractItemChoices(
        comps: List<MetaComp>,
        catalogItems: List<ItemTierEntry>,
        environment: TftEnvironment
    ): List<OverlayItemChoice> {
        val envCatalogItems = catalogItems.filter { it.environment == environment }
        val exactCounts = LinkedHashMap<String, Int>()
        val displayByKey = LinkedHashMap<String, String>()
        val componentCompCounts = LinkedHashMap<String, Int>()

        comps.forEach { comp ->
            val (exactKeys, compComponentKeys) = extractCompItemKeysAndComponents(comp, envCatalogItems)
            extractCompItemLabels(comp).forEach { label ->
                val key = normalize(label)
                if (key.isNotBlank()) {
                    displayByKey.putIfAbsent(key, label)
                }
            }
            exactKeys.forEach { key ->
                exactCounts[key] = (exactCounts[key] ?: 0) + 1
            }
            compComponentKeys.forEach { cKey ->
                componentCompCounts[cKey] = (componentCompCounts[cKey] ?: 0) + 1
            }
        }

        val components = baseComponentDisplayNames.mapNotNull { compName ->
            val key = normalize(compName)
            val count = componentCompCounts[key] ?: 0
            if (count > 0 || comps.isNotEmpty()) {
                OverlayItemChoice(name = compName, isComponent = true, compCount = count)
            } else {
                null
            }
        }

        val completed = exactCounts.entries
            .filterNot { baseComponentKeys.contains(it.key) }
            .map { (key, count) ->
                OverlayItemChoice(
                    name = displayByKey[key] ?: key,
                    isComponent = false,
                    compCount = count
                )
            }
            .sortedWith(
                compareByDescending<OverlayItemChoice> { it.compCount }
                    .thenBy { it.name.lowercase(Locale.US) }
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
        val normalizedChamps = selectedChampions.map { normalize(formatChampionDisplayName(it)) }.filter { it.isNotBlank() }
        val normalizedItems = selectedItems.map { item ->
            val clean = formatRiotAssetLabel(item)
            val norm = normalize(clean)
            norm to baseComponentKeys.contains(norm)
        }.filter { it.first.isNotBlank() }
        val trimmedQuery = query.trim().lowercase(Locale.US)
        val normalizedQuery = normalize(trimmedQuery)

        if (normalizedChamps.isEmpty() && normalizedItems.isEmpty() && trimmedQuery.isBlank()) {
            return comps
        }

        return comps.filter { comp ->
            val champKeys = extractCompChampionKeys(comp)
            val matchesChamps = normalizedChamps.all { wanted -> champKeys.contains(wanted) }
            if (!matchesChamps) return@filter false

            val (exactItemKeys, componentKeys) = extractCompItemKeysAndComponents(comp, catalogItems)
            val matchesItems = normalizedItems.all { (wantedKey, isComp) ->
                if (isComp) {
                    componentKeys.contains(wantedKey) || exactItemKeys.contains(wantedKey)
                } else {
                    exactItemKeys.contains(wantedKey)
                }
            }
            if (!matchesItems) return@filter false

            if (trimmedQuery.isBlank()) {
                true
            } else {
                val titleMatch = comp.title.lowercase(Locale.US).contains(trimmedQuery) ||
                    normalize(comp.title).contains(normalizedQuery)
                val carryMatch = comp.carryName.lowercase(Locale.US).contains(trimmedQuery) ||
                    normalize(comp.carryName).contains(normalizedQuery)
                val traitMatch = comp.traits.any {
                    it.lowercase(Locale.US).contains(trimmedQuery) || normalize(it).contains(normalizedQuery)
                }
                val champMatch = champKeys.any { it.contains(normalizedQuery) }
                val itemMatch = exactItemKeys.any { it.contains(normalizedQuery) } ||
                    componentKeys.any { it.contains(normalizedQuery) }
                titleMatch || carryMatch || traitMatch || champMatch || itemMatch
            }
        }
    }

    private fun isLikelySummonToken(normalizedKey: String): Boolean =
        summonTokenPatterns.any { normalizedKey.contains(it) }
}

@OptIn(ExperimentalLayoutApi::class)
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

@OptIn(ExperimentalLayoutApi::class)
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
    val activeTab = sessionState.finderTab
    val query = sessionState.finderQuery
    val costFilter = sessionState.finderCostFilter
    val itemScope = sessionState.finderItemScope
    val selectedChampions = sessionState.selectedChampions
    val selectedItems = sessionState.selectedItems

    val filteredChampions = remember(championChoices, query, costFilter) {
        val q = query.trim().lowercase(Locale.US)
        championChoices.filter { c ->
            val matchesCost = when (costFilter) {
                null -> true
                5 -> c.cost >= 5
                else -> c.cost == costFilter
            }
            val matchesQ = q.isBlank() || c.name.lowercase(Locale.US).contains(q)
            matchesCost && matchesQ
        }
    }

    val filteredItems = remember(itemChoices, query, itemScope) {
        val q = query.trim().lowercase(Locale.US)
        itemChoices.filter { item ->
            val matchesScope = when (itemScope) {
                OverlayItemFilterScope.ALL -> true
                OverlayItemFilterScope.COMPONENTS -> item.isComponent
                OverlayItemFilterScope.COMPLETED -> !item.isComponent
            }
            val matchesQ = q.isBlank() || item.name.lowercase(Locale.US).contains(q)
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
            // Search box + tabs + Done button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ObsidianVoid,
                    border = BorderStroke(1.dp, SlateElevated),
                    modifier = Modifier.weight(1f)
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
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search champion, item, or comp…",
                                    fontSize = 11.sp,
                                    color = Color(0xFF7D8DA6),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { sessionState.updateFinderQuery(it) },
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
                                        focusManager.clearFocus()
                                        onSearchFocusChanged(false)
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { state ->
                                        onSearchFocusChanged(state.isFocused)
                                    }
                                    .testTag("overlay_army_search_input")
                            )
                        }
                        if (query.isNotEmpty()) {
                            Surface(
                                onClick = { sessionState.updateFinderQuery("") },
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

            // Tap-to-pick icon grid (works with zero keyboard over TFT!)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 136.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (activeTab == OverlayFinderTab.CHAMPIONS) {
                    if (filteredChampions.isEmpty()) {
                        Text(
                            text = "No ${environment.label} champions match that filter.",
                            fontSize = 10.sp,
                            color = Color(0xFF93A1B8),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            filteredChampions.forEach { choice ->
                                val isSelected = selectedChampions.contains(choice.name)
                                Surface(
                                    onClick = { sessionState.toggleSelectedChampion(choice.name) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) BrightGold.copy(alpha = 0.2f) else ObsidianVoid,
                                    border = BorderStroke(
                                        width = if (isSelected) 1.3.dp else 1.dp,
                                        color = if (isSelected) BrightGold else SlateElevated
                                    ),
                                    modifier = Modifier.testTag(
                                        "overlay_pick_champ_${OverlayArmySearchEngine.normalize(choice.name)}"
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
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
                                            overflow = TextOverflow.Ellipsis
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
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            filteredItems.forEach { choice ->
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
                                    modifier = Modifier.testTag(
                                        "overlay_pick_item_${OverlayArmySearchEngine.normalize(choice.name)}"
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
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
                                            overflow = TextOverflow.Ellipsis
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

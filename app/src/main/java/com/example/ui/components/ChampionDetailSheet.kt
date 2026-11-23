package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChampionTierEntry
import com.example.data.model.DeltaType
import com.example.data.model.ItemTierEntry
import com.example.data.model.MetaComp
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank
import com.example.ui.theme.LocalHextechColors
import java.util.Locale
import kotlin.math.roundToInt

data class ResolvedChampionItemUsage(
    val itemName: String,
    val itemEntry: ItemTierEntry? = null,
    val compCount: Int = 0,
    val fromChampionBis: Boolean = false
)

data class ChampionSynergyCompMatch(
    val comp: MetaComp,
    val roleInComp: String,
    val isCarry: Boolean,
    val equippedItems: List<String>,
    val stars: Int
)

data class ChampionDetailProfile(
    val name: String,
    val cost: Int,
    val tier: TierRank,
    val environment: TftEnvironment,
    val traits: List<String>,
    val role: String,
    val avgPlacement: Double,
    val top4Rate: Double,
    val abilityName: String,
    val abilityDescription: String,
    val patchDelta: DeltaType? = null,
    val patchNote: String = "",
    val optimalItems: List<ResolvedChampionItemUsage>,
    val synergyComps: List<ChampionSynergyCompMatch>,
    val additionalSynergyCompNames: List<String>,
    val hasExactCatalogStats: Boolean
)

internal fun normalizeChampionLookupKey(raw: String): String {
    val formatted = formatChampionDisplayName(raw)
    return formatted.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
}

private fun normalizeItemLookupKey(raw: String): String {
    val formatted = formatRiotAssetLabel(raw)
    return formatted.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
}

private fun normalizeCompTitleKey(raw: String): String {
    return raw.trim().lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
}

fun resolveChampionDetailProfile(
    championName: String,
    environment: TftEnvironment,
    champions: List<ChampionTierEntry>,
    comps: List<MetaComp>,
    items: List<ItemTierEntry>,
    fallbackCost: Int? = null
): ChampionDetailProfile {
    val cleanName = formatChampionDisplayName(championName).ifBlank { championName.trim() }
    val targetKey = normalizeChampionLookupKey(cleanName)

    val matchedChamp = champions.firstOrNull {
        it.environment == environment && normalizeChampionLookupKey(it.name) == targetKey
    } ?: champions.firstOrNull {
        normalizeChampionLookupKey(it.name) == targetKey
    }

    val synergyTitleKeys = matchedChamp?.synergyComps
        ?.map { normalizeCompTitleKey(it) }
        ?.filter { it.isNotBlank() }
        ?.toSet()
        .orEmpty()

    val itemFreq = LinkedHashMap<String, Pair<String, Int>>()
    val matchedComps = LinkedHashMap<String, ChampionSynergyCompMatch>()
    var placementCostFallback: Int? = null

    comps.forEach { comp ->
        val matchingPlacement = comp.hexPlacements.firstOrNull {
            normalizeChampionLookupKey(it.unitName) == targetKey
        }
        val isCarryMatch = normalizeChampionLookupKey(comp.carryName) == targetKey ||
            matchingPlacement?.isCarry == true
        val inCoreUnits = comp.coreUnits.any { normalizeChampionLookupKey(it) == targetKey }
        val inNamedSynergy = synergyTitleKeys.contains(normalizeCompTitleKey(comp.title))

        if (matchingPlacement != null && placementCostFallback == null && matchingPlacement.cost in 1..9) {
            placementCostFallback = matchingPlacement.cost
        }

        if (isCarryMatch || inCoreUnits || matchingPlacement != null || inNamedSynergy) {
            matchingPlacement?.items.orEmpty().forEach { rawItem ->
                val formattedItem = formatRiotAssetLabel(rawItem).ifBlank { rawItem.trim() }
                if (formattedItem.isNotBlank()) {
                    val ik = normalizeItemLookupKey(formattedItem)
                    val prev = itemFreq[ik]
                    itemFreq[ik] = (prev?.first ?: formattedItem) to ((prev?.second ?: 0) + 1)
                }
            }

            val roleInComp = when {
                isCarryMatch -> "Primary Carry"
                matchingPlacement != null && matchingPlacement.items.isNotEmpty() && matchingPlacement.row <= 1 -> "Itemized Tank"
                matchingPlacement != null && matchingPlacement.items.isNotEmpty() -> "Secondary Carry"
                matchingPlacement != null && matchingPlacement.row <= 1 -> "Frontline"
                matchingPlacement != null && matchingPlacement.row >= 2 -> "Backline"
                else -> "Core Unit"
            }
            val stars = matchingPlacement?.stars
                ?: if (isCarryMatch && comp.playstyle.contains("reroll", ignoreCase = true)) 3 else 2

            val existingMatch = matchedComps[comp.id]
            if (existingMatch == null || comp.environment == environment) {
                matchedComps[comp.id] = ChampionSynergyCompMatch(
                    comp = comp,
                    roleInComp = roleInComp,
                    isCarry = isCarryMatch,
                    equippedItems = matchingPlacement?.items
                        ?.map { formatRiotAssetLabel(it).ifBlank { it } }
                        .orEmpty(),
                    stars = stars
                )
            }
        }
    }

    val sortedSynergyComps = matchedComps.values.sortedWith(
        compareByDescending<ChampionSynergyCompMatch> { it.isCarry }
            .thenBy { it.comp.tier.sortOrder }
            .thenBy { it.comp.avgPlacement }
            .thenBy { it.comp.title }
    )

    val itemEntryByKey = LinkedHashMap<String, ItemTierEntry>()
    items.forEach { entry ->
        val key = normalizeItemLookupKey(entry.name)
        val existing = itemEntryByKey[key]
        if (existing == null || entry.environment == environment) {
            itemEntryByKey[key] = entry
        }
    }

    val bisKeys = LinkedHashSet<String>()
    val optimalItemOrder = LinkedHashMap<String, ResolvedChampionItemUsage>()

    matchedChamp?.bestItems.orEmpty().forEach { rawBis ->
        val cleanItem = formatRiotAssetLabel(rawBis).ifBlank { rawBis.trim() }
        if (cleanItem.isNotBlank()) {
            val ik = normalizeItemLookupKey(cleanItem)
            bisKeys.add(ik)
            val freq = itemFreq[ik]?.second ?: 0
            optimalItemOrder[ik] = ResolvedChampionItemUsage(
                itemName = itemEntryByKey[ik]?.name ?: cleanItem,
                itemEntry = itemEntryByKey[ik],
                compCount = freq,
                fromChampionBis = true
            )
        }
    }

    itemFreq.entries
        .sortedByDescending { it.value.second }
        .forEach { (ik, pair) ->
            val existing = optimalItemOrder[ik]
            if (existing == null) {
                optimalItemOrder[ik] = ResolvedChampionItemUsage(
                    itemName = itemEntryByKey[ik]?.name ?: pair.first,
                    itemEntry = itemEntryByKey[ik],
                    compCount = pair.second,
                    fromChampionBis = ik in bisKeys
                )
            }
        }

    val resolvedOptimalItems = optimalItemOrder.values
        .sortedWith(
            compareByDescending<ResolvedChampionItemUsage> { it.fromChampionBis && it.compCount > 0 }
                .thenByDescending { it.compCount }
                .thenByDescending { it.fromChampionBis }
                .thenBy { it.itemEntry?.tier?.sortOrder ?: 99 }
        )
        .take(6)

    val resolvedCost = matchedChamp?.cost
        ?: TftAssetIconResolver.getChampionCost(cleanName, environment)
        ?: placementCostFallback
        ?: fallbackCost?.coerceIn(1, 5)
        ?: 3

    val resolvedTraits = matchedChamp?.traits?.takeIf { it.isNotEmpty() }
        ?: TftAssetIconResolver.getChampionTraits(cleanName, environment)

    val resolvedTier = matchedChamp?.tier ?: when {
        sortedSynergyComps.any { it.isCarry && it.comp.tier == TierRank.S } -> TierRank.S
        sortedSynergyComps.isNotEmpty() -> sortedSynergyComps.minBy { it.comp.tier.sortOrder }.comp.tier
        resolvedCost >= 5 -> TierRank.A
        else -> TierRank.B
    }

    val resolvedAvgPlacement = matchedChamp?.avgPlacement ?: if (sortedSynergyComps.isNotEmpty()) {
        val rawAvg = sortedSynergyComps.map { it.comp.avgPlacement }.average()
        (rawAvg.coerceIn(3.15, 4.85) * 100.0).roundToInt() / 100.0
    } else {
        when (resolvedCost) {
            5 -> 3.92
            4 -> 4.08
            3 -> 4.24
            else -> 4.38
        }
    }

    val resolvedTop4 = matchedChamp?.top4Rate ?: if (sortedSynergyComps.isNotEmpty()) {
        val rawTop4 = sortedSynergyComps.map { it.comp.top4Rate }.average()
        (rawTop4.coerceIn(42.0, 74.0) * 10.0).roundToInt() / 10.0
    } else {
        ((100.0 - (resolvedAvgPlacement - 1.5) * 18.0).coerceIn(42.0, 74.0) * 10.0).roundToInt() / 10.0
    }

    val resolvedRole = matchedChamp?.role?.takeIf { it.isNotBlank() } ?: when {
        sortedSynergyComps.any { it.isCarry } -> "${resolvedCost}-Cost Carry"
        resolvedTraits.isNotEmpty() -> "${resolvedTraits.first()} Unit"
        else -> "${resolvedCost}-Cost Unit"
    }

    val matchedTitleKeys = sortedSynergyComps.map { normalizeCompTitleKey(it.comp.title) }.toSet()
    val extraSynergyNames = matchedChamp?.synergyComps
        ?.filter { name -> normalizeCompTitleKey(name) !in matchedTitleKeys }
        ?.distinct()
        .orEmpty()

    val resolvedName = formatChampionDisplayName(matchedChamp?.name ?: cleanName).ifBlank { cleanName }
    val resolvedEnv = matchedChamp?.environment ?: environment
    val catalogAbility = TftAssetIconResolver.resolveChampionAbility(resolvedName, resolvedEnv)
    val resolvedAbilityName = matchedChamp?.abilityName.orEmpty().trim()
        .ifBlank { catalogAbility?.first.orEmpty() }
    val resolvedAbilityDescription = sanitizeTftDescription(matchedChamp?.abilityDescription.orEmpty())
        .ifBlank { catalogAbility?.second.orEmpty() }

    return ChampionDetailProfile(
        name = resolvedName,
        cost = resolvedCost,
        tier = resolvedTier,
        environment = resolvedEnv,
        traits = resolvedTraits,
        role = resolvedRole,
        avgPlacement = resolvedAvgPlacement,
        top4Rate = resolvedTop4,
        abilityName = resolvedAbilityName,
        abilityDescription = resolvedAbilityDescription,
        patchDelta = matchedChamp?.patchDelta,
        patchNote = sanitizeExternalSourceMentions(matchedChamp?.patchNote.orEmpty()),
        optimalItems = resolvedOptimalItems,
        synergyComps = sortedSynergyComps,
        additionalSynergyCompNames = extraSynergyNames,
        hasExactCatalogStats = matchedChamp != null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChampionDetailModalSheet(
    championName: String,
    environment: TftEnvironment,
    champions: List<ChampionTierEntry>,
    comps: List<MetaComp>,
    items: List<ItemTierEntry>,
    fallbackCost: Int? = null,
    onDismiss: () -> Unit,
    onSelectChampion: (String, Int?) -> Unit,
    onOpenComp: ((MetaComp) -> Unit)? = null,
    onTraitClick: ((String) -> Unit)? = null
) {
    val profile = remember(championName, environment, champions, comps, items, fallbackCost) {
        resolveChampionDetailProfile(
            championName = championName,
            environment = environment,
            champions = champions,
            comps = comps,
            items = items,
            fallbackCost = fallbackCost
        )
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("champion_detail_sheet")
    ) {
        ChampionDetailSheetContent(
            profile = profile,
            onClose = onDismiss,
            onSelectChampion = onSelectChampion,
            onOpenComp = onOpenComp,
            onTraitClick = onTraitClick
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChampionDetailSheetContent(
    profile: ChampionDetailProfile,
    onClose: (() -> Unit)? = null,
    onSelectChampion: ((String, Int?) -> Unit)? = null,
    onOpenComp: ((MetaComp) -> Unit)? = null,
    onTraitClick: ((String) -> Unit)? = null
) {
    val hextech = LocalHextechColors.current
    val costColor = championCostColor(profile.cost)
    var inspectedTrait by remember(profile.name) { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                TftChampionAvatar(
                    name = profile.name,
                    cost = profile.cost,
                    isCarry = profile.tier == TierRank.S,
                    size = 64.dp,
                    environment = profile.environment
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = costColor.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, costColor.copy(alpha = 0.75f))
                        ) {
                            Text(
                                text = "${profile.cost}g",
                                style = MaterialTheme.typography.labelMedium,
                                color = costColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TierBadge(tier = profile.tier)
                        profile.patchDelta?.let { DeltaBadge(it) }
                        Text(
                            text = "${profile.role} • ${profile.environment.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (onClose != null) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("champion_detail_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close champion details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Traits Strip
        if (profile.traits.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    profile.traits.forEach { trait ->
                        val selected = inspectedTrait == trait
                        TftTraitChip(
                            traitLabel = trait,
                            environment = profile.environment,
                            selected = selected,
                            onClick = {
                                if (onTraitClick != null) {
                                    onTraitClick(trait)
                                } else {
                                    inspectedTrait = if (selected) null else trait
                                }
                            }
                        )
                    }
                }
                val activeTraitDesc = inspectedTrait?.let { TftAssetIconResolver.resolveTraitDescription(it) }
                AnimatedVisibility(visible = !activeTraitDesc.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = inspectedTrait.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = activeTraitDesc.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Detailed Stats Summary Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ),
                    RoundedCornerShape(12.dp)
                )
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ChampionStatMetric(
                label = "COST",
                value = "${profile.cost} Gold",
                valueColor = costColor
            )
            ChampionStatMetric(
                label = "TIER",
                value = "${profile.tier.label}-Tier",
                valueColor = when (profile.tier) {
                    TierRank.S -> hextech.tierS
                    TierRank.A -> hextech.tierA
                    TierRank.B -> hextech.tierB
                    TierRank.C -> hextech.tierC
                    TierRank.X -> hextech.tierX
                }
            )
            ChampionStatMetric(
                label = "AVG PLACE",
                value = String.format(Locale.US, "%.2f", profile.avgPlacement),
                valueColor = if (profile.avgPlacement <= 3.9) hextech.buff else MaterialTheme.colorScheme.primary
            )
            ChampionStatMetric(
                label = "TOP 4",
                value = "${profile.top4Rate}%",
                valueColor = MaterialTheme.colorScheme.secondary
            )
        }

        // Champion Ability & Patch Notes
        HextechPanelCard(accentBorder = profile.tier == TierRank.S) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = profile.abilityName.ifBlank { "${profile.name} Ability" },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = profile.abilityDescription.ifBlank {
                        "Signature combat ability details sync automatically with ${profile.environment.label} patch updates."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (profile.patchNote.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = profile.patchNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // Optimal Items Section (tft.ninja Recommendations)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Recommended Items (tft.ninja)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }

            if (profile.optimalItems.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = "No dedicated item build listed for ${profile.name} yet — sync ${profile.environment.label} comps from tft.ninja or flex utility/frontline items.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profile.optimalItems.forEach { usage ->
                        val itemMeta = usage.itemEntry
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (usage.fromChampionBis) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.testTag("champion_optimal_item_${normalizeItemLookupKey(usage.itemName)}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TftItemIcon(
                                    itemName = usage.itemName,
                                    categoryOverride = itemMeta?.category,
                                    size = 34.dp,
                                    environment = profile.environment
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = usage.itemName,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (usage.fromChampionBis) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = "BIS",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        if (usage.compCount > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = "${usage.compCount} ${if (usage.compCount == 1) "Comp" else "Comps"}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    val resolvedItemSummary = itemMeta?.statsSummary?.takeIf { it.isNotBlank() }
                                        ?: TftAssetIconResolver.resolveItemDescription(usage.itemName, profile.environment).orEmpty()
                                    val resolvedItemComponents = itemMeta?.components?.takeIf { it.isNotEmpty() }
                                        ?: TftAssetIconResolver.resolveItemComponents(usage.itemName, profile.environment)
                                    if (resolvedItemSummary.isNotBlank()) {
                                        Text(
                                            text = resolvedItemSummary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (resolvedItemComponents.isNotEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Recipe:",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            resolvedItemComponents.forEach { compName ->
                                                TftItemIcon(
                                                    itemName = compName,
                                                    size = 15.dp,
                                                    environment = profile.environment
                                                )
                                            }
                                        }
                                    }
                                }
                                itemMeta?.tier?.let { TierBadge(it) }
                            }
                        }
                    }
                }
            }
        }

        // Recommended Compositions Section (tft.ninja)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Recommended Compositions (${profile.synergyComps.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }

            if (profile.synergyComps.isEmpty() && profile.additionalSynergyCompNames.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = "No tft.ninja compositions synced for ${profile.name} in ${profile.environment.label} yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    profile.synergyComps.forEach { match ->
                        val comp = match.comp
                        val unitCosts = remember(comp.id, comp.hexPlacements) {
                            comp.hexPlacements.associate { normalizeChampionLookupKey(it.unitName) to it.cost }
                        }
                        HextechPanelCard(
                            accentBorder = match.isCarry || comp.tier == TierRank.S,
                            onClick = onOpenComp?.let { callback -> { callback(comp) } },
                            modifier = Modifier.testTag("champion_synergy_comp_${comp.id}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
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
                                        TierBadge(comp.tier)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = comp.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${comp.playstyle} • Avg ${String.format(Locale.US, "%.2f", comp.avgPlacement)} • Top 4 ${comp.top4Rate}% • Win ${comp.winRate}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (onOpenComp != null) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = "Open ${comp.title} composition",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Champion role & recommended items inside this comp
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (match.isCarry) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (match.isCarry) {
                                                Icon(
                                                    imageVector = Icons.Default.WorkspacePremium,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                            Text(
                                                text = "${match.roleInComp} • ${"★".repeat(match.stars.coerceIn(1, 3))}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (match.isCarry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    if (match.equippedItems.isNotEmpty()) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            match.equippedItems.take(3).forEach { itemName ->
                                                TftItemChip(
                                                    itemName = itemName,
                                                    compact = true,
                                                    environment = comp.environment
                                                )
                                            }
                                        }
                                    }
                                }

                                // Interactive Teammate Roster Strip
                                if (comp.coreUnits.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val sortedRoster = remember(comp.coreUnits, comp.hexPlacements, comp.environment) {
                                            sortCompUnitsByCostAscending(comp)
                                        }
                                        sortedRoster.forEach { unitName ->
                                            val unitKey = normalizeChampionLookupKey(unitName)
                                            val unitCost = unitCosts[unitKey]
                                                ?: resolveCompUnitCost(unitName, comp)
                                            val isUnitCarry = unitKey == normalizeChampionLookupKey(comp.carryName)
                                            val isCurrentUnit = unitKey == normalizeChampionLookupKey(profile.name)
                                            TftChampionAvatar(
                                                name = unitName,
                                                cost = unitCost,
                                                isCarry = isUnitCarry,
                                                size = if (isCurrentUnit) 32.dp else 28.dp,
                                                environment = comp.environment,
                                                onClick = if (onSelectChampion != null && !isCurrentUnit) {
                                                    { onSelectChampion(unitName, unitCost) }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (profile.additionalSynergyCompNames.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            profile.additionalSynergyCompNames.forEach { compTitle ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Text(
                                        text = compTitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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

@Composable
private fun ChampionStatMetric(
    label: String,
    value: String,
    valueColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}

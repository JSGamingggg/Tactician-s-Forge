package com.example.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ChampionTierEntry
import com.example.data.model.DeltaType
import com.example.data.model.HexUnitPlacement
import com.example.data.model.LevelTransitionStage
import com.example.data.model.MetaComp
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank
import com.example.ui.theme.LocalHextechColors
import java.util.Locale

fun environmentSlideTransitionSpec(): AnimatedContentTransitionScope<TftEnvironment>.() -> ContentTransform = {
    fadeIn(tween(90)) togetherWith fadeOut(tween(60))
}

@Composable
fun EnvironmentToggleBar(
    selected: TftEnvironment,
    onSelect: (TftEnvironment) -> Unit,
    liveLabel: String = "18.6b Live",
    pbeLabel: String = "18.7 PBE",
    modifier: Modifier = Modifier
) {
    val hextech = LocalHextechColors.current
    val activeAccent by animateColorAsState(
        targetValue = if (selected == TftEnvironment.LIVE) hextech.liveBadge else hextech.pbeBadge,
        animationSpec = tween(110),
        label = "envIndicatorColor"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.78f)),
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            val segmentGap = 4.dp
            val segmentWidth = ((maxWidth - segmentGap) / 2f).coerceAtLeast(0.dp)
            val pillOffsetX by animateDpAsState(
                targetValue = if (selected == TftEnvironment.LIVE) 0.dp else segmentWidth + segmentGap,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessHigh
                ),
                label = "envPillSlide"
            )

            // Sliding vibrant active capsule indicator
            Box(
                modifier = Modifier
                    .offset(x = pillOffsetX)
                    .width(segmentWidth)
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                activeAccent.copy(alpha = if (hextech.isDark) 0.28f else 0.20f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .border(
                        width = 1.6.dp,
                        color = activeAccent.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(12.dp)
                    )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(segmentGap)
            ) {
                TftEnvironment.entries.forEach { env ->
                    val isSelected = env == selected
                    val indicatorColor = if (env == TftEnvironment.LIVE) hextech.liveBadge else hextech.pbeBadge
                    val titleColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(90),
                        label = "envTitleColor"
                    )
                    val subtitleColor by animateColorAsState(
                        targetValue = if (isSelected) indicatorColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(90),
                        label = "envSubtitleColor"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(env) }
                            .testTag("env_toggle_${env.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 10.dp else 7.dp)
                                    .clip(CircleShape)
                                    .background(indicatorColor.copy(alpha = if (isSelected) 1f else 0.6f))
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = env.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = titleColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (env == TftEnvironment.LIVE) liveLabel else pbeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = subtitleColor,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TierBadge(tier: TierRank, modifier: Modifier = Modifier) {
    val hextech = LocalHextechColors.current
    val badgeColor = when (tier) {
        TierRank.S -> hextech.tierS
        TierRank.A -> hextech.tierA
        TierRank.B -> hextech.tierB
        TierRank.C -> hextech.tierC
        TierRank.X -> hextech.tierX
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        badgeColor.copy(alpha = 0.32f),
                        badgeColor.copy(alpha = 0.14f)
                    )
                )
            )
            .border(1.2.dp, badgeColor.copy(alpha = 0.88f), RoundedCornerShape(9.dp))
    ) {
        Text(
            text = "${tier.label}-TIER",
            style = MaterialTheme.typography.labelSmall,
            color = badgeColor,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun DeltaBadge(delta: DeltaType, modifier: Modifier = Modifier) {
    val hextech = LocalHextechColors.current
    val color = when (delta) {
        DeltaType.BUFF -> hextech.buff
        DeltaType.NERF -> hextech.nerf
        DeltaType.ADJUST -> hextech.adjust
        DeltaType.NEW -> hextech.pbeBadge
    }
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = color.copy(alpha = 0.22f),
        border = BorderStroke(1.1.dp, color.copy(alpha = 0.78f)),
        modifier = modifier
    ) {
        Text(
            text = delta.label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
        )
    }
}

@Composable
fun HextechPanelCard(
    modifier: Modifier = Modifier,
    accentBorder: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surface
    val elevatedTint = MaterialTheme.colorScheme.surfaceVariant
    val isDark = LocalHextechColors.current.isDark

    val borderStroke = remember(accentBorder, primary, secondary, outline) {
        if (accentBorder) {
            BorderStroke(
                width = 1.35.dp,
                brush = Brush.linearGradient(
                    listOf(
                        primary.copy(alpha = 0.88f),
                        secondary.copy(alpha = 0.62f)
                    )
                )
            )
        } else {
            BorderStroke(1.dp, outline.copy(alpha = if (isDark) 0.72f else 0.85f))
        }
    }

    val panelBackground = remember(surfaceColor, elevatedTint, isDark, accentBorder) {
        if (isDark) {
            Brush.verticalGradient(
                listOf(
                    elevatedTint.copy(alpha = if (accentBorder) 0.45f else 0.26f),
                    surfaceColor
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    surfaceColor,
                    elevatedTint.copy(alpha = if (accentBorder) 0.24f else 0.10f)
                )
            )
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.medium,
            color = surfaceColor,
            shadowElevation = if (isDark) 2.dp else 3.dp,
            border = borderStroke,
            modifier = modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.background(panelBackground)) {
                content()
            }
        }
    } else {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = surfaceColor,
            shadowElevation = if (isDark) 2.dp else 3.dp,
            border = borderStroke,
            modifier = modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.background(panelBackground)) {
                content()
            }
        }
    }
}

/**
 * Resolves EVERY champion in a composition (combining all [MetaComp.hexPlacements],
 * [MetaComp.coreUnits], and [MetaComp.carryName] without truncation, while filtering out any
 * stray non-champion/item names) along with:
 * - `items`: website composition items ONLY on the composition's carries
 * - `otherItems`: additional viable/alternative items ONLY on the composition's carries
 * Non-carry champions are always returned as champions with empty `items` and `otherItems`.
 */
fun resolveFullBoardRosterUnits(
    comp: MetaComp,
    champions: List<ChampionTierEntry> = emptyList(),
    allComps: List<MetaComp> = emptyList()
): List<HexUnitPlacement> {
    fun normKey(raw: String): String =
        formatRiotAssetLabel(raw).lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

    fun cleanItemList(raw: List<String>): List<String> = raw
        .map { formatRiotAssetLabel(it).ifBlank { it.trim() } }
        .filter { it.isNotBlank() }
        .distinctBy { normKey(it) }

    val champBestItemsByKey = LinkedHashMap<String, List<String>>()
    champions.forEach { entry ->
        val k = normKey(entry.name)
        if (k.isBlank() || entry.bestItems.isEmpty()) return@forEach
        val cleaned = cleanItemList(entry.bestItems)
        if (cleaned.isNotEmpty() && (!champBestItemsByKey.containsKey(k) || entry.environment == comp.environment)) {
            champBestItemsByKey[k] = cleaned
        }
    }

    val crossCompItemsByKey = LinkedHashMap<String, MutableMap<String, Pair<String, Int>>>()
    allComps.forEach { otherComp ->
        if (otherComp.id == comp.id) return@forEach
        otherComp.hexPlacements.forEach { p ->
            val uKey = normKey(p.unitName)
            if (uKey.isBlank()) return@forEach
            val bucket = crossCompItemsByKey.getOrPut(uKey) { LinkedHashMap() }
            (p.items + p.otherItems).forEach { rawItem ->
                val clean = formatRiotAssetLabel(rawItem).ifBlank { rawItem.trim() }
                val iKey = normKey(clean)
                if (iKey.isNotBlank()) {
                    val prev = bucket[iKey]
                    bucket[iKey] = (prev?.first ?: clean) to ((prev?.second ?: 0) + 1)
                }
            }
        }
    }

    val carryKey = if (TftAssetIconResolver.isKnownNonChampionAsset(comp.carryName, comp.environment)) {
        ""
    } else {
        normKey(comp.carryName)
    }
    val byKey = LinkedHashMap<String, HexUnitPlacement>()

    comp.hexPlacements.forEach { placement ->
        if (TftAssetIconResolver.isKnownNonChampionAsset(placement.unitName, comp.environment)) return@forEach
        val cleanName = formatChampionDisplayName(placement.unitName).ifBlank { placement.unitName.trim() }
        val key = normKey(cleanName)
        if (key.isBlank() || TftAssetIconResolver.isKnownNonChampionAsset(cleanName, comp.environment)) return@forEach
        val isCarry = placement.isCarry || (carryKey.isNotBlank() && key == carryKey)
        val cost = placement.cost.takeIf { it in 1..5 }
            ?: resolveCompUnitCost(cleanName, comp)
        val cleanPrimary = cleanItemList(placement.items)
        val cleanOther = cleanItemList(placement.otherItems)
        val existing = byKey[key]
        if (existing == null) {
            byKey[key] = placement.copy(
                unitName = cleanName,
                cost = cost,
                isCarry = isCarry,
                items = cleanPrimary,
                otherItems = cleanOther
            )
        } else {
            val mergedPrimary = cleanItemList(existing.items + cleanPrimary)
            val mergedOther = cleanItemList(existing.otherItems + cleanOther)
            byKey[key] = existing.copy(
                cost = existing.cost.takeIf { it in 1..5 } ?: cost,
                stars = maxOf(existing.stars, placement.stars),
                isCarry = existing.isCarry || isCarry,
                items = mergedPrimary,
                otherItems = mergedOther
            )
        }
    }

    comp.coreUnits.forEach { rawUnit ->
        if (TftAssetIconResolver.isKnownNonChampionAsset(rawUnit, comp.environment)) return@forEach
        val cleanName = formatChampionDisplayName(rawUnit).ifBlank { rawUnit.trim() }
        val key = normKey(cleanName)
        if (key.isBlank() || byKey.containsKey(key) || TftAssetIconResolver.isKnownNonChampionAsset(cleanName, comp.environment)) return@forEach
        val isCarry = carryKey.isNotBlank() && key == carryKey
        val cost = resolveCompUnitCost(cleanName, comp)
        byKey[key] = HexUnitPlacement(
            unitName = cleanName,
            row = 0,
            col = 0,
            cost = cost,
            stars = if (isCarry && comp.playstyle.contains("reroll", ignoreCase = true)) 3 else 2,
            isCarry = isCarry,
            items = emptyList(),
            otherItems = emptyList()
        )
    }

    if (carryKey.isNotBlank() && !byKey.containsKey(carryKey)) {
        val cleanCarry = formatChampionDisplayName(comp.carryName).ifBlank { comp.carryName.trim() }
        byKey[carryKey] = HexUnitPlacement(
            unitName = cleanCarry,
            row = 0,
            col = 0,
            cost = resolveCompUnitCost(cleanCarry, comp),
            stars = if (comp.playstyle.contains("reroll", ignoreCase = true)) 3 else 2,
            isCarry = true,
            items = emptyList(),
            otherItems = emptyList()
        )
    }

    // Identify ONLY the composition's carries (main carry + up to 2 itemized secondary/frontline carries)
    val prioritizedCarryKeys = byKey.entries
        .filter { (key, unit) ->
            unit.isCarry || (carryKey.isNotBlank() && key == carryKey) || unit.items.isNotEmpty()
        }
        .sortedWith(
            compareByDescending<Map.Entry<String, HexUnitPlacement>> { it.key == carryKey }
                .thenByDescending { it.value.isCarry }
                .thenByDescending { it.value.items.size }
                .thenByDescending { it.value.cost }
        )
        .take(3)
        .map { it.key }
        .toSet()

    // Finalize items & Other Items STRICTLY for composition carries; non-carry units stay item-free
    val finalized = byKey.entries.map { (key, unit) ->
        if (key !in prioritizedCarryKeys) {
            unit.copy(isCarry = false, items = emptyList(), otherItems = emptyList())
        } else {
            val primaryItems = unit.items.take(3)
            val primaryKeys = primaryItems.map { normKey(it) }.toSet()
            val fromChampCatalog = champBestItemsByKey[key].orEmpty()
            val fromOtherComps = crossCompItemsByKey[key]?.values
                ?.sortedByDescending { it.second }
                ?.map { it.first }
                .orEmpty()
            val resolvedOthers = cleanItemList(unit.otherItems + fromChampCatalog + fromOtherComps)
                .filter { normKey(it) !in primaryKeys }
                .take(4)
            unit.copy(
                items = primaryItems,
                otherItems = resolvedOthers
            )
        }
    }

    return finalized.sortedWith(
        compareByDescending<HexUnitPlacement> { it.isCarry }
            .thenByDescending { it.items.isNotEmpty() }
            .thenByDescending { it.items.size }
            .thenByDescending { it.otherItems.isNotEmpty() }
            .thenByDescending { it.cost }
            .thenBy { it.unitName }
    )
}

/**
 * Resolves a structured stage-by-stage Level & Transition Guide (what to play at each level and
 * when to transition / roll) for a composition.
 *
 * - Preserves genuinely progressive explicit website stages when present (rejecting degenerate
 *   scraped boards that repeat the exact same champions across levels).
 * - Derives authentic TFT stage boards (Early Lv 4–5 -> Mid Lv 6–7 -> Final Lv 8–9) using
 *   legally obtainable low-cost units from the comp plus real same-set, trait-matching 1–2g
 *   openers/item holders and 3g mid-game bridges when the final board is 4–5 cost heavy.
 */
fun resolveCompLevelGuideStages(
    comp: MetaComp,
    champions: List<ChampionTierEntry> = emptyList()
): List<LevelTransitionStage> {
    fun normUnitKey(raw: String): String =
        formatChampionDisplayName(raw)
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]"), "")

    fun normTraitKey(raw: String): String =
        raw.replace(Regex("""^\d+\s+"""), "")
            .trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]"), "")

    if (comp.levelGuideStages.isNotEmpty()) {
        val cleaned = comp.levelGuideStages.map { stage ->
            stage.copy(
                units = stage.units
                    .filterNot { TftAssetIconResolver.isKnownNonChampionAsset(it, comp.environment) }
                    .distinctBy { normUnitKey(it) }
            )
        }
        val nonEmptySets = cleaned
            .map { stage -> stage.units.map(::normUnitKey).filter { it.isNotEmpty() }.toSet() }
            .filter { it.isNotEmpty() }
        val hasGenuineProgression = when {
            cleaned.size == 1 -> nonEmptySets.size == 1
            nonEmptySets.size >= 2 -> nonEmptySets.distinct().size == nonEmptySets.size
            else -> false
        }
        if (hasGenuineProgression) {
            return cleaned
        }
    }

    val roster = resolveFullBoardRosterUnits(comp = comp, champions = champions)
    val sortedByCost = roster
        .sortedWith(
            compareBy<HexUnitPlacement> { it.cost }
                .thenByDescending { it.isCarry }
                .thenBy { it.unitName }
        )
        .distinctBy { normUnitKey(it.unitName) }
    val lateUnits = sortedByCost.map { it.unitName }
    if (lateUnits.isEmpty()) return emptyList()

    val costByKey = HashMap<String, Int>(sortedByCost.size * 2)
    sortedByCost.forEach { u ->
        costByKey[normUnitKey(u.unitName)] = u.cost
    }

    // Build same-set champion pool (combining passed ChampionTierEntry list + preloaded set catalog)
    val catalogPoolMap = LinkedHashMap<String, TftAssetIconResolver.CatalogChampionInfo>()
    champions
        .filter { it.environment == comp.environment && it.cost in 1..9 && it.traits.isNotEmpty() }
        .forEach { champ ->
            val key = normUnitKey(champ.name)
            if (key.isNotEmpty() && !champ.name.contains("(") && !catalogPoolMap.containsKey(key)) {
                catalogPoolMap[key] = TftAssetIconResolver.CatalogChampionInfo(
                    name = champ.name,
                    cost = champ.cost,
                    traits = champ.traits
                )
            }
        }
    TftAssetIconResolver.getCatalogChampions(comp.environment).forEach { champ ->
        val key = normUnitKey(champ.name)
        if (key.isNotEmpty() && !champ.name.contains("(") && !catalogPoolMap.containsKey(key)) {
            catalogPoolMap[key] = champ
        }
    }
    val catalogPool = catalogPoolMap.values.toList()

    fun traitsForUnit(unitName: String): List<String> {
        val key = normUnitKey(unitName)
        return catalogPoolMap[key]?.traits?.takeIf { it.isNotEmpty() }
            ?: TftAssetIconResolver.getChampionTraits(unitName, comp.environment)
    }

    fun costForUnit(unitName: String): Int {
        val key = normUnitKey(unitName)
        return costByKey[key]
            ?: catalogPoolMap[key]?.cost
            ?: resolveCompUnitCost(unitName, comp)
    }

    val carry = comp.carryName.ifBlank {
        roster.firstOrNull { it.isCarry }?.unitName ?: lateUnits.last()
    }
    val carryKey = normUnitKey(carry)
    val carryCost = costForUnit(carry)
    val styleLower = comp.playstyle.lowercase(Locale.US)
    val primaryTrait = comp.traits.firstOrNull()?.replace(Regex("""^\d+\s+"""), "")?.trim().orEmpty()
    val traitHint = if (primaryTrait.isNotBlank()) "$primaryTrait opener" else "strongest early opener"

    val isSlowRollLow = styleLower.contains("slow roll") || styleLower.contains("hyper") ||
        (styleLower.contains("reroll") && carryCost <= 2)
    val isThreeCostReroll = !isSlowRollLow &&
        (styleLower.contains("7") || (styleLower.contains("reroll") && carryCost == 3))
    val isFast9 = !isSlowRollLow && !isThreeCostReroll &&
        (styleLower.contains("fast 9") || styleLower.contains("fast9") || carryCost >= 5)

    // Gather normalized trait keys for the comp and for the main carry
    val compTraitKeys = LinkedHashSet<String>()
    comp.traits.forEach { t ->
        val k = normTraitKey(t)
        if (k.isNotEmpty()) compTraitKeys.add(k)
    }
    sortedByCost.forEach { u ->
        traitsForUnit(u.unitName).forEach { t ->
            val k = normTraitKey(t)
            if (k.isNotEmpty()) compTraitKeys.add(k)
        }
    }
    val carryTraitKeys = traitsForUnit(carry).map(::normTraitKey).filter { it.isNotEmpty() }.toSet()

    fun pickSynergyBridges(
        initial: List<String>,
        maxCost: Int,
        targetSize: Int,
        preferCarryTrait: Boolean,
        preferMidCost: Boolean = false,
        excludeKeys: Set<String> = emptySet()
    ): List<String> {
        val result = initial.distinctBy(::normUnitKey).toMutableList()
        if (result.size >= targetSize || catalogPool.isEmpty()) return result

        while (result.size < targetSize) {
            val currentKeys = result.map(::normUnitKey).toSet()
            val boardTraitCounts = HashMap<String, Int>()
            result.forEach { u ->
                traitsForUnit(u).forEach { t ->
                    val tk = normTraitKey(t)
                    if (tk.isNotEmpty()) boardTraitCounts[tk] = (boardTraitCounts[tk] ?: 0) + 1
                }
            }

            val best = catalogPool
                .asSequence()
                .filter { c ->
                    val ck = normUnitKey(c.name)
                    c.cost in 1..maxCost && ck.isNotEmpty() && ck !in currentKeys && ck !in excludeKeys
                }
                .maxWithOrNull(
                    compareBy<TftAssetIconResolver.CatalogChampionInfo> { c ->
                        val cTraits = c.traits.map(::normTraitKey).filter { it.isNotEmpty() }
                        var score = 0
                        cTraits.forEach { tk ->
                            if (preferCarryTrait && tk in carryTraitKeys) score += 7
                            if (tk in compTraitKeys) score += 4
                            if ((boardTraitCounts[tk] ?: 0) > 0) score += 3
                        }
                        if (preferMidCost && c.cost == 3) score += 4
                        if (!preferMidCost && c.cost <= 2) score += 2
                        score
                    }.thenBy { c ->
                        if (preferMidCost) -c.cost else c.cost
                    }.thenByDescending { c ->
                        // Deterministic tie-breaker
                        -c.name.hashCode()
                    }
                ) ?: break

            result.add(best.name)
        }
        return result
    }

    val compLowUnits = sortedByCost.filter { it.cost <= 2 }.map { it.unitName }
    val compThreeUnits = sortedByCost.filter { it.cost == 3 }.map { it.unitName }
    val compFourUnits = sortedByCost.filter { it.cost == 4 }.map { it.unitName }
    val compHighKeys = sortedByCost.filter { it.cost >= 4 }.map { normUnitKey(it.unitName) }.toSet()
    val lateKeys = lateUnits.map(::normUnitKey).toSet()

    // --- STAGE 1: Early Opener (Level 4-5, max 2-cost units when set catalog is available) ---
    val earlyTarget = if (lateUnits.size >= 5) 4 else maxOf(2, lateUnits.size - 2)
    val earlyCoreCap = minOf(earlyTarget, maxOf(1, lateUnits.size - 2))
    val earlyCoreSeed = buildList {
        if (carryCost <= 2) {
            compLowUnits.firstOrNull { normUnitKey(it) == carryKey }?.let { add(it) }
        }
        compLowUnits.forEach { u ->
            if (size < earlyCoreCap && none { normUnitKey(it) == normUnitKey(u) }) {
                add(u)
            }
        }
    }
    var earlyUnits = if (catalogPool.isNotEmpty()) {
        pickSynergyBridges(
            initial = earlyCoreSeed,
            maxCost = 2,
            targetSize = earlyTarget,
            preferCarryTrait = carryCost >= 3,
            preferMidCost = false,
            excludeKeys = compHighKeys
        )
    } else {
        // Fallback when no set catalog is loaded (e.g. isolated unit test with synthetic names)
        val fallbackSeed = (earlyCoreSeed + compThreeUnits).distinctBy(::normUnitKey)
        if (fallbackSeed.size >= 2) {
            fallbackSeed.take(earlyCoreCap)
        } else {
            lateUnits.take(earlyCoreCap)
        }
    }
    if (earlyUnits.isEmpty()) {
        earlyUnits = lateUnits.take(earlyTarget)
    }
    earlyUnits = earlyUnits.distinctBy(::normUnitKey)
        .sortedWith(compareBy<String> { costForUnit(it) }.thenBy { it })

    // Identify the best Early item holder when the main carry is a 3/4/5-cost unit not yet on board
    val earlyKeys = earlyUnits.map(::normUnitKey).toSet()
    val earlyItemHolder: String? = if (carryKey !in earlyKeys) {
        earlyUnits.maxWithOrNull(
            compareBy<String> { u ->
                val uTraits = traitsForUnit(u).map(::normTraitKey).toSet()
                uTraits.intersect(carryTraitKeys).size * 10 + costForUnit(u)
            }
        )
    } else {
        null
    }

    // --- STAGE 2: Mid-Game / Roll Board (Level 6-7, adds 3-cost core/bridges; never full 5-cost cap) ---
    val midTarget = when {
        lateUnits.size <= 4 -> maxOf(earlyUnits.size + 1, lateUnits.size - 1)
        isSlowRollLow -> minOf(6, maxOf(earlyUnits.size + 1, lateUnits.size - 1))
        isThreeCostReroll -> minOf(7, maxOf(earlyUnits.size + 2, lateUnits.size - 1))
        isFast9 -> minOf(7, maxOf(earlyUnits.size + 2, lateUnits.size - 1))
        else -> minOf(6, maxOf(earlyUnits.size + 2, lateUnits.size - 1))
    }

    val midSeed = mutableListOf<String>()
    fun addUniqueToMid(unit: String) {
        val k = normUnitKey(unit)
        if (k.isNotEmpty() && midSeed.none { normUnitKey(it) == k }) {
            midSeed.add(unit)
        }
    }

    // Carry enters in Mid for 1-2g Slow Roll and 3g Reroll; for Fast 8 / Fast 9 keep the item holder until transition
    if (isSlowRollLow || isThreeCostReroll) {
        sortedByCost.filter { it.cost <= 3 }.forEach { addUniqueToMid(it.unitName) }
    } else if (isFast9) {
        // Keep early item holder + comp 1-3g units + at most 1-2 comp 4-cost stabilizers (never 5-costs!)
        earlyItemHolder?.let { addUniqueToMid(it) }
        compLowUnits.forEach { addUniqueToMid(it) }
        compThreeUnits.forEach { addUniqueToMid(it) }
        // Keep one additional trait-linked early opener if we have few comp low-cost units
        earlyUnits.take(2).forEach { addUniqueToMid(it) }
        compFourUnits
            .filter { normUnitKey(it) != carryKey }
            .take(if (midSeed.size <= 4) 2 else 1)
            .forEach { addUniqueToMid(it) }
    } else {
        // Fast 8 / Standard: keep item holder + comp 1-3g core + at most 1 non-carry 4g frontline
        earlyItemHolder?.let { addUniqueToMid(it) }
        compLowUnits.forEach { addUniqueToMid(it) }
        compThreeUnits.forEach { addUniqueToMid(it) }
        earlyUnits.take(2).forEach { addUniqueToMid(it) }
        if (midSeed.size < midTarget) {
            compFourUnits
                .firstOrNull { normUnitKey(it) != carryKey }
                ?.let { addUniqueToMid(it) }
        }
    }

    var midUnits = if (midSeed.size > midTarget) {
        midSeed.take(midTarget)
    } else if (catalogPool.isNotEmpty()) {
        // Fill remaining Level 6-7 slots with real 2-3g trait bridges from the same set
        pickSynergyBridges(
            initial = midSeed,
            maxCost = 3,
            targetSize = midTarget,
            preferCarryTrait = carryCost >= 4,
            preferMidCost = true,
            excludeKeys = compHighKeys
        )
    } else {
        // Synthetic fallback without loaded set catalog
        val nextCompUnits = lateUnits.filter { normUnitKey(it) !in midSeed.map(::normUnitKey).toSet() }
        (midSeed + nextCompUnits).take(minOf(midTarget, maxOf(earlyUnits.size + 1, lateUnits.size - 1)))
    }

    // Guarantee Stage 1 != Stage 2 != Stage 3 (no two levels ever show the identical champion set)
    var midKeys = midUnits.map(::normUnitKey).toSet()
    if (midKeys == lateKeys && lateUnits.size >= 3) {
        val trimCount = if (midUnits.size > earlyUnits.size + 1) 1 else 1
        midUnits = midUnits
            .sortedWith(compareBy<String> { costForUnit(it) }.thenBy { it })
            .dropLast(trimCount)
        midKeys = midUnits.map(::normUnitKey).toSet()
    }
    if (midKeys == earlyKeys) {
        val extraFromComp = lateUnits.firstOrNull {
            val k = normUnitKey(it)
            k !in earlyKeys && (lateUnits.size <= earlyUnits.size + 1 || (earlyKeys + k) != lateKeys)
        }
        val extraFromCatalog = if (extraFromComp == null && catalogPool.isNotEmpty()) {
            pickSynergyBridges(
                initial = midUnits,
                maxCost = 3,
                targetSize = midUnits.size + 1,
                preferCarryTrait = true,
                preferMidCost = true,
                excludeKeys = lateKeys
            ).firstOrNull { normUnitKey(it) !in earlyKeys }
        } else {
            null
        }
        val extra = extraFromComp ?: extraFromCatalog
        if (extra != null) {
            midUnits = (midUnits + extra).distinctBy(::normUnitKey)
            midKeys = midUnits.map(::normUnitKey).toSet()
        } else if (earlyUnits.size >= 3) {
            earlyUnits = earlyUnits.dropLast(1)
        }
    }
    if (midKeys == lateKeys && lateUnits.size >= 3 && catalogPool.isNotEmpty()) {
        // Swap out the priciest cap unit in Mid for a temporary 3-cost bridge so Final is a real transition
        val withoutCap = midUnits.sortedBy { costForUnit(it) }.dropLast(1)
        val bridge = pickSynergyBridges(
            initial = withoutCap,
            maxCost = 3,
            targetSize = withoutCap.size + 1,
            preferCarryTrait = true,
            preferMidCost = true,
            excludeKeys = lateKeys
        )
        if (bridge.map(::normUnitKey).toSet() != earlyUnits.map(::normUnitKey).toSet()) {
            midUnits = bridge
        }
    }

    earlyUnits = earlyUnits.distinctBy(::normUnitKey)
        .sortedWith(compareBy<String> { costForUnit(it) }.thenBy { it })
    midUnits = midUnits.distinctBy(::normUnitKey)
        .sortedWith(compareBy<String> { costForUnit(it) }.thenBy { it })
    val finalUnits = lateUnits.distinctBy(::normUnitKey)
        .sortedWith(compareBy<String> { costForUnit(it) }.thenBy { it })

    val finalEarlyKeys = earlyUnits.map(::normUnitKey).toSet()
    val finalMidKeys = midUnits.map(::normUnitKey).toSet()
    val finalLateKeys = finalUnits.map(::normUnitKey).toSet()

    val addedInMid = midUnits.filter { normUnitKey(it) !in finalEarlyKeys }
    val addedInLate = finalUnits.filter { normUnitKey(it) !in finalMidKeys }
    val swappedOutInLate = midUnits.filter { normUnitKey(it) !in finalLateKeys }

    val midAddHint = addedInMid.take(3).joinToString(", ")
    val lateAddHint = addedInLate.take(3).joinToString(", ")
    val lateSwapHint = swappedOutInLate.take(2).joinToString(", ")

    return when {
        isSlowRollLow -> {
            val rollLevel = if (carryCost <= 1) "Level 5" else "Level 6"
            listOf(
                LevelTransitionStage(
                    levelLabel = "Level 4–5 • Early",
                    stageTiming = "Stage 2-1 → 3-1",
                    units = earlyUnits,
                    actionTip = "Avoid buying XP early. Play $traitHint, collect $carry copies, and build 50g interest."
                ),
                LevelTransitionStage(
                    levelLabel = "$rollLevel • Transition & Roll",
                    stageTiming = "Stage 3-2 → 4-1",
                    units = midUnits,
                    actionTip = buildString {
                        if (midAddHint.isNotBlank()) append("Add $midAddHint and ") else append("Transition onto core synergies and ")
                        append("slow-roll above 50g at $rollLevel until $carry hits 3★.")
                    }
                ),
                LevelTransitionStage(
                    levelLabel = "Level 7–8 • Cap Board",
                    stageTiming = "Stage 4-5+",
                    units = finalUnits,
                    actionTip = buildString {
                        append("Once $carry is 3★, push levels")
                        if (lateAddHint.isNotBlank()) append(" to add $lateAddHint")
                        if (lateSwapHint.isNotBlank()) append(" (drop $lateSwapHint)")
                        append(" and finish late-game traits.")
                    }
                )
            )
        }
        isThreeCostReroll -> {
            listOf(
                LevelTransitionStage(
                    levelLabel = "Level 4–5 • Early",
                    stageTiming = "Stage 2-1 → 2-6",
                    units = earlyUnits,
                    actionTip = buildString {
                        append("Level to 4 on 2-1 and play $traitHint. ")
                        if (earlyItemHolder != null) {
                            append("Slam $carry's items on $earlyItemHolder to save HP.")
                        } else {
                            append("Slam carry items on a temporary holder to save HP.")
                        }
                    }
                ),
                LevelTransitionStage(
                    levelLabel = "Level 6–7 • Transition & Roll",
                    stageTiming = "Stage 3-2 → 4-1",
                    units = midUnits,
                    actionTip = buildString {
                        append("Push Level 7 on 3-5 / 4-1")
                        if (midAddHint.isNotBlank()) append(" to add $midAddHint")
                        append(", move items onto $carry, and slow-roll above 50g for 3★.")
                    }
                ),
                LevelTransitionStage(
                    levelLabel = "Level 8–9 • Cap Board",
                    stageTiming = "Stage 5-1+",
                    units = finalUnits,
                    actionTip = buildString {
                        append("After hitting 3★ $carry, push Level 8/9")
                        if (lateAddHint.isNotBlank()) append(" to slot $lateAddHint")
                        if (lateSwapHint.isNotBlank()) append(" and swap out $lateSwapHint")
                        append(".")
                    }
                )
            )
        }
        isFast9 -> {
            listOf(
                LevelTransitionStage(
                    levelLabel = "Level 4–6 • Early Tempo",
                    stageTiming = "Stage 2-1 → 3-5",
                    units = earlyUnits,
                    actionTip = buildString {
                        append("Level aggressively on 2-1 / 2-5 / 3-2 with $traitHint. ")
                        if (earlyItemHolder != null) {
                            append("Hold $carry's items on $earlyItemHolder to preserve HP.")
                        } else {
                            append("Slam flexible items on an early holder to preserve HP.")
                        }
                    }
                ),
                LevelTransitionStage(
                    levelLabel = "Level 7–8 • Stabilize",
                    stageTiming = "Stage 4-1 → 4-5",
                    units = midUnits,
                    actionTip = buildString {
                        if (midAddHint.isNotBlank()) append("Add $midAddHint to stabilize mid-game. ")
                        append("Roll lightly at Level 8 only if below 50 HP; protect econ to push Level 9 cleanly.")
                    }
                ),
                LevelTransitionStage(
                    levelLabel = "Level 9 • Full Transition",
                    stageTiming = "Stage 5-1+",
                    units = finalUnits,
                    actionTip = buildString {
                        append("Transition at Level 9: ")
                        if (lateSwapHint.isNotBlank()) {
                            append("sell $lateSwapHint to move items onto $carry")
                        } else {
                            append("transfer BIS items onto $carry")
                        }
                        if (lateAddHint.isNotBlank()) append(" and slot $lateAddHint")
                        append(".")
                    }
                )
            )
        }
        else -> {
            listOf(
                LevelTransitionStage(
                    levelLabel = "Level 4–5 • Early",
                    stageTiming = "Stage 2-1 → 2-6",
                    units = earlyUnits,
                    actionTip = buildString {
                        append("Level to 4 on 2-1 and 5 on 2-5 with $traitHint. ")
                        if (earlyItemHolder != null) {
                            append("Hold $carry's items on $earlyItemHolder to save HP.")
                        } else {
                            append("Use an early item holder for $carry's items.")
                        }
                    }
                ),
                LevelTransitionStage(
                    levelLabel = "Level 6–7 • Mid Game",
                    stageTiming = "Stage 3-2 → 3-5",
                    units = midUnits,
                    actionTip = buildString {
                        append("Level to 6 on 3-2 and 7 on 3-5")
                        if (midAddHint.isNotBlank()) append(", adding $midAddHint")
                        append(". Hold 50g econ without over-rolling.")
                    }
                ),
                LevelTransitionStage(
                    levelLabel = "Level 8–9 • Transition",
                    stageTiming = "Stage 4-1 / 4-2",
                    units = finalUnits,
                    actionTip = buildString {
                        append("Push Level 8 on 4-1/4-2 and roll down")
                        if (lateAddHint.isNotBlank()) append(" for $lateAddHint")
                        if (lateSwapHint.isNotBlank()) append("; sell $lateSwapHint to move items onto $carry")
                        append(" and cap at Level 9.")
                    }
                )
            )
        }
    }
}

/**
 * Displays a clear, actionable Leveling & Transition Guide (what to play at each level and
 * when to transition / roll), replacing the old hex board in both the App and Live Overlay.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompLevelTransitionGuideSection(
    comp: MetaComp,
    champions: List<ChampionTierEntry> = emptyList(),
    onChampionClick: ((String, Int) -> Unit)? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outline
    val hextech = LocalHextechColors.current
    val catalogRev = TftAssetIconResolver.catalogRevision
    val stages = remember(
        comp.id,
        comp.levelGuideStages,
        comp.playstyle,
        comp.coreUnits,
        comp.hexPlacements,
        comp.carryName,
        comp.traits,
        comp.environment,
        champions,
        catalogRev
    ) {
        resolveCompLevelGuideStages(comp = comp, champions = champions)
    }
    val champCostLookup = remember(champions, comp.environment) {
        champions
            .filter { it.environment == comp.environment && it.cost > 0 }
            .associateBy(
                keySelector = { formatChampionDisplayName(it.name).lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "") },
                valueTransform = { it.cost }
            )
    }
    val cleanSiteGuide = remember(comp.levelingGuide) {
        sanitizeExternalSourceMentions(comp.levelingGuide).trim()
    }
    val cleanSiteNotes = remember(comp.notes) {
        sanitizeExternalSourceMentions(comp.notes).trim()
    }
    val showSiteGuideBanner = cleanSiteGuide.isNotBlank() &&
        !cleanSiteGuide.equals("${comp.playstyle} • ${comp.setName} (${comp.patchTag})", ignoreCase = true)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("level_transition_guide_${comp.id}"),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Level Guide & Transitions",
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                color = secondary,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = RoundedCornerShape(7.dp),
                color = primary.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, primary.copy(alpha = 0.65f))
            ) {
                Text(
                    text = comp.playstyle,
                    style = MaterialTheme.typography.labelSmall,
                    color = primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
            stages.forEachIndexed { idx, stage ->
                val isTransitionStep = idx == stages.lastIndex ||
                    stage.levelLabel.contains("Transition", ignoreCase = true) ||
                    stage.levelLabel.contains("Roll", ignoreCase = true)
                val stepAccent = when {
                    idx == 0 -> secondary
                    isTransitionStep -> primary
                    else -> MaterialTheme.colorScheme.tertiary
                }
                val prevUnits = if (idx > 0) stages[idx - 1].units else emptyList()
                val prevKeySet = prevUnits
                    .map { formatChampionDisplayName(it).lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "") }
                    .toSet()
                val currKeySet = stage.units
                    .map { formatChampionDisplayName(it).lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "") }
                    .toSet()
                val addedUnits = if (idx > 0) {
                    stage.units.filter {
                        formatChampionDisplayName(it).lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "") !in prevKeySet
                    }
                } else {
                    emptyList()
                }
                val swappedOutUnits = if (idx > 0) {
                    prevUnits.filter {
                        formatChampionDisplayName(it).lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "") !in currKeySet
                    }
                } else {
                    emptyList()
                }

                Surface(
                    shape = RoundedCornerShape(if (compact) 10.dp else 12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isTransitionStep) 0.68f else 0.48f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isTransitionStep) stepAccent.copy(alpha = 0.72f) else outline.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("level_guide_stage_${comp.id}_$idx")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (compact) 8.dp else 10.dp),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (compact) 18.dp else 20.dp)
                                        .clip(CircleShape)
                                        .background(stepAccent.copy(alpha = 0.24f))
                                        .border(1.dp, stepAccent.copy(alpha = 0.85f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        fontSize = if (compact) 10.sp else 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = stepAccent
                                    )
                                }
                                Text(
                                    text = stage.levelLabel,
                                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (stage.stageTiming.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = stepAccent.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = stage.stageTiming,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = stepAccent,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (stage.actionTip.isNotBlank()) {
                            Text(
                                text = stage.actionTip,
                                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = if (compact) 11.sp else 13.sp,
                                lineHeight = if (compact) 15.sp else 18.sp
                            )
                        }

                        if (stage.units.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val boardLabel = when (idx) {
                                        0 -> "Early Board (${stage.units.size} units)"
                                        stages.lastIndex -> "Final Board (${stage.units.size} units)"
                                        else -> "Mid Board (${stage.units.size} units)"
                                    }
                                    Text(
                                        text = boardLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = stepAccent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (addedUnits.isNotEmpty() || swappedOutUnits.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (addedUnits.isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = hextech.buff.copy(alpha = 0.16f),
                                                border = BorderStroke(0.8.dp, hextech.buff.copy(alpha = 0.65f))
                                            ) {
                                                Text(
                                                    text = "+ Add: ${addedUnits.joinToString(", ")}",
                                                    fontSize = if (compact) 9.5.sp else 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = hextech.buff,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (swappedOutUnits.isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = primary.copy(alpha = 0.15f),
                                                border = BorderStroke(0.8.dp, primary.copy(alpha = 0.65f))
                                            ) {
                                                Text(
                                                    text = "Swap out: ${swappedOutUnits.joinToString(", ")}",
                                                    fontSize = if (compact) 9.5.sp else 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp),
                                    verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)
                                ) {
                                    stage.units.forEach { unitName ->
                                        val normU = formatChampionDisplayName(unitName)
                                            .lowercase(Locale.US)
                                            .replace(Regex("[^a-z0-9]"), "")
                                        val cost = champCostLookup[normU] ?: resolveCompUnitCost(unitName, comp)
                                        val isCarry = unitName.equals(comp.carryName, ignoreCase = true)
                                        TftChampionTile(
                                            name = unitName,
                                            cost = cost,
                                            isCarry = isCarry,
                                            size = if (compact) 28.dp else 34.dp,
                                            labelFontSize = if (compact) 9.sp else 10.sp,
                                            environment = comp.environment,
                                            onClick = onChampionClick?.let { cb -> { cb(unitName, cost) } }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showSiteGuideBanner || cleanSiteNotes.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                border = BorderStroke(1.dp, outline.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(if (compact) 8.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (showSiteGuideBanner) {
                        Text(
                            text = cleanSiteGuide,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = if (compact) 10.5.sp else 12.sp
                        )
                    }
                    if (cleanSiteNotes.isNotBlank()) {
                        Text(
                            text = cleanSiteNotes,
                            style = MaterialTheme.typography.labelSmall,
                            color = secondary,
                            fontSize = if (compact) 10.sp else 11.5.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Displays ALL champions in a clicked composition under the sub-header
 * "Full Board Roster & Unit Items":
 * - Every champion in the composition is displayed as a champion tile, with website composition
 *   item icons tucked directly UNDER the champion's icon ONLY for the composition's carries.
 * - Below the board roster, ONLY the composition's carries show their itemized build details
 *   (Comp Items + side-by-side "Other Items"). Non-carry champions show zero items.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompFullBoardRosterSection(
    comp: MetaComp,
    champions: List<ChampionTierEntry> = emptyList(),
    allComps: List<MetaComp> = emptyList(),
    onChampionClick: ((String, Int) -> Unit)? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outline
    val rosterUnits = remember(comp.id, comp.coreUnits, comp.hexPlacements, comp.carryName, comp.environment, champions, allComps) {
        resolveFullBoardRosterUnits(comp = comp, champions = champions, allComps = allComps)
    }
    if (rosterUnits.isEmpty()) return

    val carryUnitsWithItems = remember(rosterUnits) {
        rosterUnits.filter { it.items.isNotEmpty() || it.otherItems.isNotEmpty() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("full_board_roster_section_${comp.id}"),
        verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Full Board Roster & Unit Items (${rosterUnits.size} Units)",
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                color = primary,
                fontWeight = FontWeight.Bold
            )
            if (onChampionClick != null) {
                Text(
                    text = "Carry items under champion icon • Other Items on the side • Tap unit for details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Carry Item Builds + side-by-side "Other Items" at the top (ONLY shown for the composition's carries)
        if (carryUnitsWithItems.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)) {
                Text(
                    text = "Carry Items & Other Items",
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    color = secondary,
                    fontWeight = FontWeight.Bold
                )
                carryUnitsWithItems.forEach { unit ->
                    val slug = unit.unitName.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "_")
                    val hasCompItems = unit.items.isNotEmpty()
                    val hasOtherItems = unit.otherItems.isNotEmpty()
                    Surface(
                        onClick = { onChampionClick?.invoke(unit.unitName, unit.cost) },
                        enabled = onChampionClick != null,
                        shape = RoundedCornerShape(if (compact) 10.dp else 12.dp),
                        color = primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, primary.copy(alpha = 0.72f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = if (compact) 52.dp else 60.dp)
                            .testTag("roster_unit_row_$slug")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = if (compact) 8.dp else 10.dp,
                                    vertical = if (compact) 7.dp else 9.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
                        ) {
                            // Carry Champion Icon with its Comp Items tucked directly underneath
                            TftChampionTile(
                                name = unit.unitName,
                                cost = unit.cost,
                                isCarry = true,
                                stars = unit.stars,
                                size = if (compact) 34.dp else 40.dp,
                                labelFontSize = if (compact) 9.5.sp else 10.5.sp,
                                environment = comp.environment,
                                equippedItems = unit.items,
                                itemIconSize = if (compact) 12.dp else 14.dp
                            )

                            // Comp Items + side-by-side "Other Items" strictly for this carry
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                if (hasCompItems) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = "Comp Items",
                                            fontSize = if (compact) 9.sp else 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primary,
                                            maxLines = 1
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            unit.items.forEach { itemName ->
                                                TftItemChip(
                                                    itemName = itemName,
                                                    compact = true,
                                                    environment = comp.environment
                                                )
                                            }
                                        }
                                    }
                                }

                                if (hasOtherItems) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = secondary.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, secondary.copy(alpha = 0.35f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("roster_other_items_$slug")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(
                                                horizontal = if (compact) 5.dp else 7.dp,
                                                vertical = if (compact) 4.dp else 5.dp
                                            ),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = "Other Items",
                                                fontSize = if (compact) 9.sp else 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = secondary,
                                                maxLines = 1
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                unit.otherItems.forEach { altItem ->
                                                    TftItemChip(
                                                        itemName = altItem,
                                                        compact = true,
                                                        environment = comp.environment
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

        // 2. Full Board Champions grid: ONLY carries show their composition items right under their champion icon
        Surface(
            shape = RoundedCornerShape(if (compact) 10.dp else 12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, outline.copy(alpha = 0.7f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("full_board_champions_grid_${comp.id}")
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 8.dp else 10.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
            ) {
                rosterUnits.forEach { unit ->
                    val slug = unit.unitName.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "_")
                    TftChampionTile(
                        name = unit.unitName,
                        cost = unit.cost,
                        isCarry = unit.isCarry || unit.items.isNotEmpty(),
                        stars = unit.stars,
                        size = if (compact) 34.dp else 42.dp,
                        labelFontSize = if (compact) 9.5.sp else 11.sp,
                        environment = comp.environment,
                        equippedItems = unit.items,
                        itemIconSize = if (compact) 12.dp else 14.dp,
                        onClick = onChampionClick?.let { cb -> { cb(unit.unitName, unit.cost) } },
                        modifier = Modifier.testTag("roster_champ_tile_$slug")
                    )
                }
            }
        }
    }
}

@Composable
fun HextechEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    HextechPanelCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                Spacer(Modifier.height(16.dp))
                action()
            }
        }
    }
}

@Composable
fun MadeByAttributionBadge(
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hextech = LocalHextechColors.current
    Surface(
        shape = RoundedCornerShape(if (compact) 8.dp else 12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (compact) 0.55f else 0.72f),
        border = BorderStroke(1.dp, hextech.goldBorder.copy(alpha = 0.65f)),
        modifier = modifier.testTag("made_by_credits_badge")
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 5.dp else 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 6.dp else 7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
            Text(
                text = stringResource(R.string.made_by_credits),
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

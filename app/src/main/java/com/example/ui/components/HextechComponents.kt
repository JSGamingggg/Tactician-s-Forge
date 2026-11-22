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
 * Resolves a structured stage-by-stage Level & Transition Guide (what to play and when to transition)
 * for a composition, using explicit website stage boards when present or deriving an accurate
 * gameplan from the composition's playstyle, roster costs, carry, and leveling notes.
 */
fun resolveCompLevelGuideStages(comp: MetaComp): List<LevelTransitionStage> {
    if (comp.levelGuideStages.isNotEmpty()) {
        return comp.levelGuideStages.map { stage ->
            stage.copy(
                units = stage.units.filterNot { TftAssetIconResolver.isKnownNonChampionAsset(it, comp.environment) }
            )
        }
    }

    val roster = resolveFullBoardRosterUnits(comp)
    val sortedByCost = roster.sortedWith(compareBy<HexUnitPlacement> { it.cost }.thenByDescending { it.isCarry })
    val allNames = sortedByCost.map { it.unitName }

    val lowCostUnits = sortedByCost.filter { it.cost <= 2 }.map { it.unitName }
    val midCostUnits = sortedByCost.filter { it.cost <= 3 }.map { it.unitName }
    val highCostUnits = sortedByCost.filter { it.cost >= 4 }.map { it.unitName }

    val earlyUnits = when {
        lowCostUnits.size >= 3 -> lowCostUnits.take(5)
        midCostUnits.isNotEmpty() -> midCostUnits.take(4)
        else -> allNames.take(4)
    }
    val midUnits = when {
        midCostUnits.size >= 4 -> midCostUnits.take(7)
        else -> allNames.take(6)
    }
    val lateUnits = if (highCostUnits.isNotEmpty()) {
        (highCostUnits + allNames).distinct()
    } else {
        allNames
    }

    val carry = comp.carryName.ifBlank { roster.firstOrNull { it.isCarry }?.unitName ?: "Main Carry" }
    val carryCost = resolveCompUnitCost(carry, comp)
    val styleLower = comp.playstyle.lowercase(Locale.US)
    val primaryTrait = comp.traits.firstOrNull()?.replace(Regex("""^\d+\s+"""), "")?.trim().orEmpty()
    val traitHint = if (primaryTrait.isNotBlank()) "$primaryTrait opener" else "strongest early opener"

    return when {
        styleLower.contains("slow roll") || styleLower.contains("hyper") ||
            (styleLower.contains("reroll") && carryCost <= 2) -> {
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
                    actionTip = "Transition onto core synergies and slow-roll above 50g at $rollLevel until $carry hits 3★."
                ),
                LevelTransitionStage(
                    levelLabel = "Level 7–8 • Cap Board",
                    stageTiming = "Stage 4-5+",
                    units = lateUnits,
                    actionTip = "Once $carry is 3★, push levels to slot remaining high-cost units and finish trait breakpoints."
                )
            )
        }
        styleLower.contains("7") || (styleLower.contains("reroll") && carryCost == 3) -> {
            listOf(
                LevelTransitionStage(
                    levelLabel = "Level 4–5 • Early",
                    stageTiming = "Stage 2-1 → 2-6",
                    units = earlyUnits,
                    actionTip = "Level to 4 on 2-1, play $traitHint, and slam carry items on a temporary holder to save HP."
                ),
                LevelTransitionStage(
                    levelLabel = "Level 6–7 • Transition & Roll",
                    stageTiming = "Stage 3-2 → 4-1",
                    units = midUnits,
                    actionTip = "Push Level 7 on 3-5 / 4-1, roll to stabilize 2★ board & move items onto $carry, then slow-roll above 50g for 3★."
                ),
                LevelTransitionStage(
                    levelLabel = "Level 8–9 • Cap Board",
                    stageTiming = "Stage 5-1+",
                    units = lateUnits,
                    actionTip = "After hitting 3★ $carry, push Level 8/9 to add late-game frontline and trait finishers."
                )
            )
        }
        styleLower.contains("fast 9") || styleLower.contains("fast9") || carryCost >= 5 -> {
            listOf(
                LevelTransitionStage(
                    levelLabel = "Level 4–6 • Early Tempo",
                    stageTiming = "Stage 2-1 → 3-5",
                    units = earlyUnits,
                    actionTip = "Level aggressively on 2-1 / 2-5 / 3-2 with $traitHint. Slam flexible items on an early item holder to preserve HP."
                ),
                LevelTransitionStage(
                    levelLabel = "Level 7–8 • Stabilize",
                    stageTiming = "Stage 4-1 → 4-5",
                    units = midUnits,
                    actionTip = "Roll lightly at Level 8 only if below 50 HP; keep economy healthy to push Level 9 cleanly."
                ),
                LevelTransitionStage(
                    levelLabel = "Level 9 • Full Transition",
                    stageTiming = "Stage 5-1+",
                    units = lateUnits,
                    actionTip = "Transition at Level 9: sell your item holder, transfer BIS items onto $carry, and cap with 5-cost units."
                )
            )
        }
        else -> {
            listOf(
                LevelTransitionStage(
                    levelLabel = "Level 4–5 • Early",
                    stageTiming = "Stage 2-1 → 2-6",
                    units = earlyUnits,
                    actionTip = "Level to 4 on 2-1 and 5 on 2-5. Play $traitHint and use an early item holder for $carry's items."
                ),
                LevelTransitionStage(
                    levelLabel = "Level 6–7 • Mid Game",
                    stageTiming = "Stage 3-2 → 3-5",
                    units = midUnits,
                    actionTip = "Level to 6 on 3-2 and 7 on 3-5. Hold 50g econ and add mid-game synergies without over-rolling."
                ),
                LevelTransitionStage(
                    levelLabel = "Level 8–9 • Transition",
                    stageTiming = "Stage 4-1 / 4-2",
                    units = lateUnits,
                    actionTip = "Push Level 8 on 4-1/4-2 and roll down to hit 2★ $carry & frontline; sell item holder and cap at Level 9."
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
    onChampionClick: ((String, Int) -> Unit)? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outline
    val stages = remember(comp.id, comp.levelGuideStages, comp.playstyle, comp.coreUnits, comp.hexPlacements, comp.carryName) {
        resolveCompLevelGuideStages(comp)
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
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "What to Play:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = stepAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp),
                                    verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)
                                ) {
                                    stage.units.forEach { unitName ->
                                        val cost = resolveCompUnitCost(unitName, comp)
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

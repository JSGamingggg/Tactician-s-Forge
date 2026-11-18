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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.DeltaType
import com.example.data.model.HexUnitPlacement
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank
import com.example.ui.theme.LocalHextechColors
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

fun environmentSlideTransitionSpec(): AnimatedContentTransitionScope<TftEnvironment>.() -> ContentTransform = {
    val toPbe = targetState == TftEnvironment.PBE
    (fadeIn(tween(220)) + slideInHorizontally(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    ) { fullWidth ->
        val delta = (fullWidth / 14).coerceIn(14, 36)
        if (toPbe) delta else -delta
    }) togetherWith (fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { fullWidth ->
        val delta = (fullWidth / 14).coerceIn(14, 36)
        if (toPbe) -delta else delta
    })
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
        animationSpec = tween(240),
        label = "envIndicatorColor"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "envPillSlide"
            )

            // Sliding vibrant active capsule indicator
            Box(
                modifier = Modifier
                    .offset(x = pillOffsetX)
                    .width(segmentWidth)
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface,
                                activeAccent.copy(alpha = 0.18f)
                            )
                        )
                    )
                    .border(
                        width = 1.4.dp,
                        color = activeAccent.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(10.dp)
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
                        animationSpec = tween(200),
                        label = "envTitleColor"
                    )
                    val subtitleColor by animateColorAsState(
                        targetValue = if (isSelected) indicatorColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(200),
                        label = "envSubtitleColor"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp))
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
                                    .size(if (isSelected) 9.dp else 7.dp)
                                    .clip(CircleShape)
                                    .background(indicatorColor.copy(alpha = if (isSelected) 1f else 0.65f))
                            )
                            Spacer(Modifier.width(6.dp))
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
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        badgeColor.copy(alpha = 0.28f),
                        badgeColor.copy(alpha = 0.12f)
                    )
                )
            )
            .border(1.1.dp, badgeColor.copy(alpha = 0.82f), RoundedCornerShape(8.dp))
    ) {
        Text(
            text = "${tier.label}-TIER",
            style = MaterialTheme.typography.labelSmall,
            color = badgeColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.7f)),
        modifier = modifier
    ) {
        Text(
            text = delta.label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
    val borderStroke = remember(accentBorder, primary, secondary, outline) {
        if (accentBorder) {
            BorderStroke(
                width = 1.25.dp,
                brush = Brush.linearGradient(
                    listOf(
                        primary.copy(alpha = 0.82f),
                        secondary.copy(alpha = 0.55f)
                    )
                )
            )
        } else {
            BorderStroke(1.dp, outline)
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = if (accentBorder) 4.dp else 2.dp,
            border = borderStroke,
            modifier = modifier.fillMaxWidth()
        ) {
            content()
        }
    } else {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = if (accentBorder) 4.dp else 2.dp,
            border = borderStroke,
            modifier = modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TftHexBoard(
    placements: List<HexUnitPlacement>,
    environment: TftEnvironment? = null,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outline
    val surfaceBg = MaterialTheme.colorScheme.surfaceVariant
    val occupied = remember(placements) { placements.associateBy { it.row to it.col } }
    val sortedPlacements = remember(placements) {
        placements.sortedWith(compareByDescending<HexUnitPlacement> { it.isCarry }.thenBy { it.row }.thenBy { it.col })
    }
    val density = LocalDensity.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                )
            )
            .border(1.dp, outline, RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        // Board Legend & Orientation Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Frontline (Row 1) ↓ Backline (Row 4)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendDot("Carry", primary)
                LegendDot("Ally", secondary)
            }
        }
        Spacer(Modifier.height(6.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
        ) {
            val cols = 7
            val rows = 4
            val radiusDp = (maxWidth.value / (cols * 2.15f)).coerceAtMost(maxHeight.value / (rows * 2.10f))
            val rPx = with(density) { radiusDp.dp.toPx() }
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val dx = rPx * 1.88f
            val dy = rPx * 1.65f
            val totalGridWidth = (cols - 1) * dx + dx * 0.5f + rPx * 2f
            val totalGridHeight = (rows - 1) * dy + rPx * 2f
            val startX = (widthPx - totalGridWidth) / 2f + rPx
            val startY = (heightPx - totalGridHeight) / 2f + rPx

            val hexCells = remember(widthPx, heightPx, rPx) {
                buildList(rows * cols) {
                    for (r in 0 until rows) {
                        for (c in 0 until cols) {
                            val rowOffset = if (r % 2 == 1) dx * 0.5f else 0f
                            val cx = startX + c * dx + rowOffset
                            val cy = startY + r * dy
                            val path = Path().apply {
                                for (i in 0 until 6) {
                                    val angle = Math.toRadians((60 * i - 30).toDouble())
                                    val px = cx + rPx * 0.92f * cos(angle).toFloat()
                                    val py = cy + rPx * 0.92f * sin(angle).toFloat()
                                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                                }
                                close()
                            }
                            add(Triple(r, c, path))
                        }
                    }
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                for ((r, c, path) in hexCells) {
                    val unit = occupied[r to c]
                    when {
                        unit?.isCarry == true -> {
                            drawPath(path, color = primary.copy(alpha = 0.30f))
                            drawPath(path, color = primary, style = Stroke(width = 3.5f))
                        }
                        unit != null -> {
                            val costTint = championCostColor(unit.cost)
                            drawPath(path, color = costTint.copy(alpha = 0.22f))
                            drawPath(path, color = costTint, style = Stroke(width = 2.5f))
                        }
                        else -> {
                            drawPath(path, color = surfaceBg.copy(alpha = 0.45f))
                            drawPath(path, color = outline.copy(alpha = 0.7f), style = Stroke(width = 1.5f))
                        }
                    }
                }
            }

            // Champion portrait icons centered directly on each occupied hex cell
            val avatarSizePx = (rPx * 1.48f).roundToInt().coerceIn(24, 68)
            val avatarSizeDp = with(density) { avatarSizePx.toDp() }
            placements.forEach { unit ->
                val rowOffset = if (unit.row % 2 == 1) dx * 0.5f else 0f
                val cx = startX + unit.col * dx + rowOffset
                val cy = startY + unit.row * dy
                val leftPx = (cx - avatarSizePx / 2f).roundToInt()
                val topPx = (cy - avatarSizePx / 2f).roundToInt()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(leftPx, topPx) }
                        .size(avatarSizeDp),
                    contentAlignment = Alignment.Center
                ) {
                    TftChampionAvatar(
                        name = unit.unitName,
                        cost = unit.cost,
                        isCarry = unit.isCarry,
                        stars = unit.stars,
                        size = avatarSizeDp,
                        environment = environment,
                        exactSize = true
                    )
                }
            }
        }

        if (placements.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Full Board Roster & Unit Items (${placements.size} Units)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sortedPlacements.forEach { unit ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (unit.isCarry) primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (unit.isCarry) primary else outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        TftChampionAvatar(
                                            name = unit.unitName,
                                            cost = unit.cost,
                                            isCarry = unit.isCarry,
                                            stars = unit.stars,
                                            size = 28.dp,
                                            environment = environment
                                        )
                                        Column {
                                            Text(
                                                text = unit.unitName + if (unit.isCarry) " (Carry)" else "",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (unit.isCarry) primary else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Row ${unit.row + 1} • Col ${unit.col + 1} • ${unit.cost}-Cost",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = "★".repeat(unit.stars.coerceIn(1, 3)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (unit.stars >= 3 || unit.isCarry) primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (unit.items.isNotEmpty()) {
                                    Spacer(Modifier.height(5.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        unit.items.forEach { itemName ->
                                            TftItemChip(itemName = itemName, compact = true)
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
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

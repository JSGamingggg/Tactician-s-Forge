package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import com.example.R
import com.example.data.model.AugmentTier
import com.example.data.model.ItemCategory
import com.example.data.model.MetaComp
import com.example.data.model.TftEnvironment
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

fun championCostColor(cost: Int): Color = when (cost) {
    5 -> Color(0xFFF0B949)
    4 -> Color(0xFFA855F7)
    3 -> Color(0xFF38BDF8)
    2 -> Color(0xFF2ECC71)
    else -> Color(0xFF94A3B8)
}

fun championMonogram(name: String): String {
    val clean = name.replace("'", "").trim()
    if (clean.isEmpty()) return "?"
    val parts = clean.split(" ", "-").filter { it.isNotBlank() }
    return if (parts.size >= 2) {
        "${parts[0].first()}${parts[1].first()}".uppercase()
    } else {
        clean.take(2).uppercase()
    }
}

private val REGEX_PREFIX_TFT = Regex("^(?:DA|TFT\\d*)_(?:\\d+_)?(?:Item|Augment|Trait|Champion|Artifact|Component)?_?", RegexOption.IGNORE_CASE)
private val REGEX_PREFIX_SET = Regex("^Set\\d+_", RegexOption.IGNORE_CASE)
private val REGEX_SUFFIX_ROLE = Regex("_(?:AD|AP|Tank|Carry|Base|Small|Indie|RisingStar|Superstar)$", RegexOption.IGNORE_CASE)
private val REGEX_PRESERVE_COMPOUND_CHAMP = Regex(
    "^(?:Akali_(?:KDA|TrueDamage)|Dr_Mundo|Lee_Sin|Miss_Fortune|Renata_Glasc|Twisted_Fate|Baron_Nashor|Master_Yi|Tahm_Kench|Xin_Zhao|Aurelion_Sol|Jarvan_IV)$",
    RegexOption.IGNORE_CASE
)
private val REGEX_SUFFIX_SMALL = Regex("(Small)$")
private val REGEX_SUFFIX_DIGITS = Regex("(\\D)\\d{2}$")
private val REGEX_CAMEL = Regex("([a-z])([A-Z])")
private val REGEX_ALPHA_DIGIT = Regex("([A-Za-z])(\\d)")
private val REGEX_ROMAN = Regex("\\s+(II|III)$")
private val REGEX_TRAIT_COUNT = Regex("^(\\d+)\\s+(.+)$")

private val formattedLabelCache = ConcurrentHashMap<String, String>(512)

@Volatile
private var formattedLabelCacheRevision: Int = -1

/**
 * Formats raw Riot Data/API identifiers (e.g. "TFT18_Item_JeweledGauntlet",
 * "TFT19_Seraphine_Indie", "TFT18_Augment_ElderwoodCrest", "Set18_Elderwood")
 * into readable display labels.
 */
fun formatRiotAssetLabel(rawId: String): String {
    val trimmed = rawId.trim()
    if (trimmed.isEmpty()) return ""
    val rev = TftAssetIconResolver.catalogRevision
    if (formattedLabelCacheRevision != rev) {
        formattedLabelCache.clear()
        formattedLabelCacheRevision = rev
    } else {
        formattedLabelCache[trimmed]?.let { return it }
    }

    // Always resolve exact Riot internal codenames first (e.g. TFT_Item_RedBuff -> Sunfire Cape,
    // TFT_Item_RapidFireCannon -> Red Buff, Leviathan -> Nashor's Tooth, BFSword -> B.F. Sword)
    TftAssetIconResolver.resolveDisplayName(trimmed)?.let { resolved ->
        if (formattedLabelCache.size < 1200) formattedLabelCache[trimmed] = resolved
        return resolved
    }

    val isHyphenSlug = trimmed.contains('-') && !trimmed.contains(' ') &&
        trimmed.any { it.isLowerCase() }
    val isAllLowerSingleToken = !trimmed.contains(' ') && !trimmed.contains('_') &&
        trimmed.length >= 2 && trimmed.all { it.isLowerCase() || it.isDigit() }

    // Fast path for ordinary human-readable names like "Jeweled Gauntlet" or "Ahri"
    val startsWithRiotPrefix = trimmed.startsWith("DA", ignoreCase = false) ||
        trimmed.startsWith("TFT", ignoreCase = false) ||
        trimmed.startsWith("Set", ignoreCase = false)
    if (!trimmed.contains('_') && !startsWithRiotPrefix && !isHyphenSlug && !isAllLowerSingleToken) {
        if (trimmed.any { it == ' ' } || !REGEX_CAMEL.containsMatchIn(trimmed)) {
            val normalizedHuman = normalizeInGamePrepositions(trimmed)
            if (formattedLabelCache.size < 1200) formattedLabelCache[trimmed] = normalizedHuman
            return normalizedHuman
        }
    }
    if (!trimmed.contains("_") && trimmed.any { it == ' ' }) {
        val normalizedHuman = normalizeInGamePrepositions(trimmed)
        if (formattedLabelCache.size < 1200) formattedLabelCache[trimmed] = normalizedHuman
        return normalizedHuman
    }

    val working = if (isHyphenSlug) {
        trimmed.split('-')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercaseChar() } }
    } else if (isAllLowerSingleToken) {
        trimmed.replaceFirstChar { it.uppercaseChar() }
    } else {
        trimmed
    }

    val withoutPrefixAndRole = working
        .replace(REGEX_PREFIX_TFT, "")
        .replace(REGEX_PREFIX_SET, "")
        .replace(REGEX_SUFFIX_ROLE, "")
    var stripped = if (REGEX_PRESERVE_COMPOUND_CHAMP.matches(withoutPrefixAndRole)) {
        withoutPrefixAndRole.replace('_', ' ')
    } else {
        withoutPrefixAndRole.substringAfterLast("_")
    }
        .replace(REGEX_SUFFIX_SMALL, "")
        .replace(REGEX_SUFFIX_DIGITS, "$1")

    var radiantPrefix = false
    if (stripped.endsWith("Radiant", ignoreCase = true) && stripped.length > 7) {
        stripped = stripped.dropLast(7).trim()
        radiantPrefix = true
    } else if (stripped.startsWith("Radiant ", ignoreCase = true) && stripped.length > 8) {
        stripped = stripped.drop(8).trim()
        radiantPrefix = true
    }

    if (stripped.endsWith("EmblemItem", ignoreCase = true) && stripped.length > 10) {
        stripped = stripped.dropLast(4)
    } else if (stripped.startsWith("Emblem") && stripped.length > 6) {
        stripped = stripped.removePrefix("Emblem") + "Emblem"
    }

    val spaced = stripped
        .replace(REGEX_CAMEL, "$1 $2")
        .replace(REGEX_ALPHA_DIGIT, "$1 $2")
        .replace(REGEX_ROMAN, " $1")
        .replace("B F Sword", "B.F. Sword")
        .replace("Bf Sword", "B.F. Sword")
        .replace("BFSword", "B.F. Sword")
        .replace("Guinsoos", "Guinsoo's")
        .replace("Dragons", "Dragon's")
        .replace("Thiefs", "Thief's")
        .replace("Nashors", "Nashor's")
        .replace("Steraks", "Sterak's")
        .replace("Warmogs", "Warmog's")
        .replace("Archangels", "Archangel's")
        .replace("Runaans", "Runaan's")
        .replace("Protectors", "Protector's")
        .replace("Titans", "Titan's")
        .replace("Krakens", "Kraken's")
        .replace("Strikers", "Striker's")
        .replace("Rabadons", "Rabadon's")
        .replace("Giants", "Giant's")
        .replace("Tacticians", "Tactician's")
        .replace("Pandoras", "Pandora's")
        .replace("Snipers", "Sniper's")
        .replace("Wits", "Wit's")
        .replace("Deaths", "Death's")
        .replace("Moguls", "Mogul's")
        .replace("Tricksters", "Trickster's")
        .replace("Zhonyas", "Zhonya's")
        .replace("Ludens", "Luden's")
        .replace("Prowlers", "Prowler's")
        .replace("Seekers", "Seeker's")
        .replace("Blacksmiths", "Blacksmith's")
        .replace("Gamblers", "Gambler's")
        .replace("Randuins", "Randuin's")
        .replace("Banshees", "Banshee's")
        .replace("Zekes", "Zeke's")
        .replace("Knights", "Knight's")
        .replace("Kha Zix", "Kha'Zix")
        .replace("Vel Koz", "Vel'Koz")
        .replace("Cho Gath", "Cho'Gath")
        .replace("Kog Maw", "Kog'Maw")
        .replace("Kai Sa", "Kai'Sa")
        .replace("Rek Sai", "Rek'Sai")
        .replace("Bel Veth", "Bel'Veth")
        .replace("K Sante", "K'Sante")
        .trim()
        .ifBlank { trimmed }

    val baseResolved = TftAssetIconResolver.resolveDisplayName(spaced)
        ?: normalizeInGamePrepositions(spaced)
    val formatted = if (radiantPrefix && !baseResolved.startsWith("Radiant ", ignoreCase = true)) {
        "Radiant $baseResolved"
    } else {
        baseResolved
    }
    if (formattedLabelCache.size < 1200) {
        formattedLabelCache[trimmed] = formatted
    }
    return formatted
}

private fun normalizeInGamePrepositions(label: String): String {
    if (!label.contains(' ')) return label
    return label
        .replace(" Of The ", " of the ")
        .replace(" Of ", " of ")
        .replace(" The ", " the ")
}

/**
 * Normalizes a champion unit identifier into its clean display name.
 * Handles Set 19 Seraphine form IDs (`TFT19_Seraphine_Indie`, cached `"Indie"` unit slots,
 * `"Seraphine Indie"`, `"Seraphine Rising Star"`, `"Seraphine Superstar"`) without affecting
 * trait chips where `"Indie"` is a valid trait name.
 */
fun formatChampionDisplayName(rawId: String): String {
    val label = formatRiotAssetLabel(rawId)
    return when (label.lowercase()) {
        "indie", "seraphine indie", "seraphine rising star", "seraphine superstar" -> "Seraphine"
        else -> label
    }
}

/**
 * Resolves the gold cost (1g..5g/7g) of a champion within a [MetaComp], checking hex placements
 * first (by raw ID and normalized display name) and then Riot's live/preloaded catalog.
 */
fun resolveCompUnitCost(unitName: String, comp: MetaComp): Int {
    val cleanTarget = formatChampionDisplayName(unitName).lowercase()
    val rawTarget = unitName.trim().lowercase()
    val placementCost = comp.hexPlacements.firstOrNull { placement ->
        val pRaw = placement.unitName.trim().lowercase()
        val pClean = formatChampionDisplayName(placement.unitName).lowercase()
        pRaw == rawTarget || pClean == cleanTarget
    }?.cost?.takeIf { it > 0 }
    if (placementCost != null) return placementCost

    val resolverCost = TftAssetIconResolver.getChampionCost(unitName, comp.environment)
        ?: TftAssetIconResolver.getChampionCost(formatChampionDisplayName(unitName), comp.environment)
    if (resolverCost != null && resolverCost > 0) return resolverCost

    return if (unitName.equals(comp.carryName, ignoreCase = true) ||
        cleanTarget == formatChampionDisplayName(comp.carryName).lowercase()
    ) 4 else 3
}

/**
 * Orders a composition's core champions from 1-cost upwards (1g -> 2g -> 3g -> 4g -> 5g -> 7g),
 * preserving original order among units of the same gold cost.
 */
fun sortCompUnitsByCostAscending(comp: MetaComp): List<String> {
    val cleanUnits = comp.coreUnits
        .filterNot { TftAssetIconResolver.isKnownNonChampionAsset(it, comp.environment) }
        .distinctBy { formatChampionDisplayName(it).lowercase() }
    if (cleanUnits.size <= 1) return cleanUnits
    return cleanUnits
        .withIndex()
        .sortedWith(
            compareBy<IndexedValue<String>> { indexed ->
                val cost = resolveCompUnitCost(indexed.value, comp)
                if (cost in 1..10) cost else 99
            }.thenBy { it.index }
        )
        .map { it.value }
}

private val EXTERNAL_SOURCE_SENTENCE_REGEX = Regex(
    """(?i)(?:\s*•\s*)?(?:sourced|synced|tracked(?:\s+live)?)\s+(?:live\s+)?(?:from|on|via)\s+(?:https?://)?(?:tftacademy\.com|tftactics\.gg|tftips\.app)[^\n•.]*\.?"""
)
private val EXTERNAL_SOURCE_BRAND_REGEX = Regex(
    """(?i)\b(?:TFTAcademy|TFTactics|TFTips|tftacademy\.com(?:/tierlist/comps(?:/set\d+)?)?|tftactics\.gg(?:/tierlist/team-comps/?)?|tftips\.app(?:/en/patches)?|Data\s*Dragon)\b\s*"""
)

fun sanitizeExternalSourceMentions(raw: String): String {
    if (raw.isBlank()) return ""
    val cleaned = raw
        .replace(EXTERNAL_SOURCE_SENTENCE_REGEX, "")
        .replace(EXTERNAL_SOURCE_BRAND_REGEX, "")
        .replace(Regex("""\(\s*\)"""), "")
        .replace(Regex("""\s*•\s*•\s*"""), " • ")
        .trim()
        .trim('•', ' ', '-')
        .trim()
    return cleaned
}

val PointedHexagonShape: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.5f, 0f)
    lineTo(w, h * 0.25f)
    lineTo(w, h * 0.75f)
    lineTo(w * 0.5f, h)
    lineTo(0f, h * 0.75f)
    lineTo(0f, h * 0.25f)
    close()
}

private const val SQRT_3 = 1.7320508f

/**
 * Computes the exact bounding-box width and height in pixels for a pointed-top regular hexagon
 * of circumradius [hexRadiusPx] so champion portraits fill the entire drawn hex cell edge-to-edge
 * across portrait, landscape, and high-density displays without artificial pixel upper caps.
 */
fun computeHexAvatarPixelBounds(
    hexRadiusPx: Float,
    minWidthPx: Int = 18,
    minHeightPx: Int = 20
): Pair<Int, Int> {
    val safeRadius = hexRadiusPx.coerceAtLeast(1f)
    val widthPx = ceil(SQRT_3 * safeRadius).toInt().coerceAtLeast(minWidthPx)
    val heightPx = ceil(2.0f * safeRadius).toInt().coerceAtLeast(minHeightPx)
    return widthPx to heightPx
}

@Composable
fun TftChampionAvatar(
    name: String,
    cost: Int,
    isCarry: Boolean = false,
    stars: Int = 2,
    size: Dp = 36.dp,
    height: Dp = size,
    environment: TftEnvironment? = null,
    exactSize: Boolean = false,
    useHexShape: Boolean = false,
    showBadge: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rev = TftAssetIconResolver.catalogRevision
    val cleanName = remember(name, rev) { formatChampionDisplayName(name) }
    val primaryUrl = remember(cleanName, environment, TftAssetIconResolver.ddragonVersion, rev) {
        TftAssetIconResolver.resolveSquareChampionPortraitUrl(cleanName, environment)
    }
    val fallbackUrl = remember(cleanName, environment, TftAssetIconResolver.ddragonVersion, rev) {
        TftAssetIconResolver.resolveChampionIconUrl(cleanName, environment)
    }
    var useFallbackUrl by remember(primaryUrl, fallbackUrl) { mutableStateOf(false) }
    val activeUrl = if (useFallbackUrl && !fallbackUrl.isNullOrBlank() && fallbackUrl != primaryUrl) {
        fallbackUrl
    } else {
        primaryUrl ?: fallbackUrl
    }
    val targetPx = with(LocalDensity.current) { maxOf(size, height).roundToPx().coerceAtLeast(64) }
    val color = championCostColor(cost)
    val borderColor = if (isCarry) MaterialTheme.colorScheme.primary else color
    val shape: Shape = if (useHexShape) {
        PointedHexagonShape
    } else {
        RoundedCornerShape(if (exactSize) (size * 0.28f).coerceIn(6.dp, 10.dp) else 8.dp)
    }
    val outerW = if (exactSize) size else (size + 4.dp)
    val outerH = if (exactSize) height else (height + 4.dp)

    Box(
        modifier = modifier.size(width = outerW, height = outerH),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = size, height = height)
                .align(Alignment.Center)
                .clip(shape)
                .background(color.copy(alpha = 0.24f))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = "Inspect $cleanName details",
                            onClick = onClick
                        )
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (useHexShape) {
                        Modifier
                    } else {
                        Modifier.border(
                            width = if (isCarry) 2.dp else 1.5.dp,
                            color = borderColor,
                            shape = shape
                        )
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            ChampionMonogramFallback(cleanName, isCarry, color, size)
            if (activeUrl != null) {
                val request = remember(activeUrl, targetPx, context) {
                    ImageRequest.Builder(context)
                        .data(activeUrl)
                        .size(targetPx)
                        .scale(Scale.FILL)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = "$cleanName champion icon",
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    onError = {
                        if (!useFallbackUrl && !fallbackUrl.isNullOrBlank() && fallbackUrl != primaryUrl) {
                            useFallbackUrl = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                )
            }
            if (useHexShape) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = this.size.width
                    val h = this.size.height
                    val path = Path().apply {
                        moveTo(w * 0.5f, 0f)
                        lineTo(w, h * 0.25f)
                        lineTo(w, h * 0.75f)
                        lineTo(w * 0.5f, h)
                        lineTo(0f, h * 0.75f)
                        lineTo(0f, h * 0.25f)
                        close()
                    }
                    drawPath(
                        path = path,
                        color = borderColor,
                        style = Stroke(width = if (isCarry) 4.2f else 2.8f)
                    )
                }
            }
        }

        if (showBadge) {
            if (isCarry) {
                val badgeSize = (minOf(size, height) * 0.36f).coerceIn(10.dp, 15.dp)
                Box(
                    modifier = Modifier
                        .size(badgeSize)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = "Main Carry",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size((badgeSize * 0.68f).coerceIn(7.dp, 11.dp))
                    )
                }
            } else if (stars >= 3) {
                val badgeSize = (minOf(size, height) * 0.34f).coerceIn(10.dp, 14.dp)
                Box(
                    modifier = Modifier
                        .size(badgeSize)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(Color(0xFFF0B949)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "3-Star Unit",
                        tint = Color(0xFF0B0E14),
                        modifier = Modifier.size((badgeSize * 0.68f).coerceIn(7.dp, 10.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun TftChampionTile(
    name: String,
    cost: Int,
    isCarry: Boolean = false,
    stars: Int = 2,
    size: Dp = 40.dp,
    labelFontSize: TextUnit = 10.5.sp,
    useHexShape: Boolean = false,
    environment: TftEnvironment? = null,
    equippedItems: List<String> = emptyList(),
    itemIconSize: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cleanName = remember(name) { formatChampionDisplayName(name) }
    val shownItems = remember(equippedItems) { equippedItems.filter { it.isNotBlank() }.take(3) }
    val itemRowMinWidth = if (shownItems.isNotEmpty()) {
        (itemIconSize * shownItems.size) + (2.dp * (shownItems.size - 1)) + 8.dp
    } else {
        0.dp
    }
    val tileMinWidth = maxOf(size + 12.dp, itemRowMinWidth)
    val tileMaxWidth = maxOf(size + 28.dp, itemRowMinWidth + 14.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .widthIn(min = tileMinWidth, max = tileMaxWidth)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Inspect $cleanName stats, items and comps",
                            onClick = onClick
                        )
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                } else {
                    Modifier
                }
            )
    ) {
        TftChampionAvatar(
            name = name,
            cost = cost,
            isCarry = isCarry,
            stars = stars,
            size = size,
            useHexShape = useHexShape,
            environment = environment
        )
        if (shownItems.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            val slug = remember(cleanName) {
                cleanName.lowercase().replace(Regex("[^a-z0-9]"), "_")
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag("champion_under_items_$slug")
            ) {
                shownItems.forEach { itemName ->
                    TftItemIcon(
                        itemName = itemName,
                        size = itemIconSize,
                        environment = environment
                    )
                }
            }
        }
        Spacer(Modifier.height(if (shownItems.isNotEmpty()) 2.dp else 3.dp))
        Text(
            text = cleanName,
            fontSize = labelFontSize,
            lineHeight = (labelFontSize.value + 1.5f).sp,
            fontWeight = if (isCarry) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isCarry) Color(0xFFFFD700) else Color(0xFFF1F5F9),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ChampionMonogramFallback(
    name: String,
    isCarry: Boolean,
    costColor: Color,
    size: Dp
) {
    Text(
        text = championMonogram(name),
        fontSize = (size.value * 0.34f).sp,
        color = if (isCarry) MaterialTheme.colorScheme.primary else costColor,
        fontWeight = FontWeight.Bold,
        maxLines = 1
    )
}

private fun traitVisual(traitName: String): Pair<ImageVector, Color> {
    val t = traitName.lowercase()
    return when {
        t.contains("elderwood") || t.contains("grove") || t.contains("flora") -> Icons.Default.Park to Color(0xFF2ECC71)
        t.contains("arcanist") || t.contains("invoker") || t.contains("spellweaver") || t.contains("battlemage") -> Icons.Default.AutoFixHigh to Color(0xFF00E5D4)
        t.contains("vanguard") || t.contains("bastion") || t.contains("brawler") || t.contains("defender") || t.contains("juggernaut") -> Icons.Default.Shield to Color(0xFF38BDF8)
        t.contains("sniper") || t.contains("artillery") || t.contains("trickshot") || t.contains("hunter") || t.contains("rapidfire") -> Icons.Default.GpsFixed to Color(0xFFF59E0B)
        t.contains("slayer") || t.contains("ravager") || t.contains("inferno") || t.contains("predator") || t.contains("reaper") -> Icons.Default.FlashOn to Color(0xFFFF5C8A)
        t.contains("blossom") || t.contains("enchanted") || t.contains("fae") || t.contains("starlight") -> Icons.Default.Favorite to Color(0xFFF472B6)
        t.contains("chrono") || t.contains("lunar") || t.contains("solar") -> Icons.Default.Schedule to Color(0xFF60A5FA)
        t.contains("blackthorn") || t.contains("coven") || t.contains("mystic") || t.contains("sprykin") -> Icons.Default.Spa to Color(0xFFA855F7)
        t.contains("riftbeast") || t.contains("primal") -> Icons.Default.LocalFireDepartment to Color(0xFFFF7A45)
        t.contains("adaptor") || t.contains("avatar") || t.contains("ascendant") || t.contains("bounty") -> Icons.Default.AutoAwesome to Color(0xFFF5B731)
        else -> Icons.Default.MilitaryTech to Color(0xFFF5B731)
    }
}

fun traitAccentColor(traitName: String): Color = traitVisual(traitName).second

@Composable
fun TftTraitIcon(
    traitName: String,
    environment: TftEnvironment? = null,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rev = TftAssetIconResolver.catalogRevision
    val cleanName = remember(traitName, rev) { formatRiotAssetLabel(traitName) }
    val (fallbackIcon, tint) = remember(cleanName) { traitVisual(cleanName) }
    val iconUrl = remember(cleanName, environment, TftAssetIconResolver.ddragonVersion, rev) {
        TftAssetIconResolver.resolveTraitIconUrl(cleanName, environment)
    }
    val targetPx = with(LocalDensity.current) { (size + 4.dp).roundToPx().coerceAtLeast(20) }
    var imageLoaded by remember(iconUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (!imageLoaded) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = "$cleanName trait icon",
                tint = tint,
                modifier = Modifier.size((size * 0.875f).coerceAtLeast(12.dp))
            )
        }
        if (iconUrl != null) {
            val request = remember(iconUrl, targetPx, context) {
                ImageRequest.Builder(context)
                    .data(iconUrl)
                    .size(targetPx)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = "$cleanName trait icon",
                contentScale = ContentScale.Fit,
                onSuccess = { imageLoaded = true },
                onError = { imageLoaded = false },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun TftTraitChip(
    traitLabel: String,
    environment: TftEnvironment? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rev = TftAssetIconResolver.catalogRevision
    val (count, name) = remember(traitLabel, rev) {
        val formatted = formatRiotAssetLabel(traitLabel)
        val countMatch = REGEX_TRAIT_COUNT.find(formatted)
        countMatch?.groupValues?.getOrNull(1) to (countMatch?.groupValues?.getOrNull(2) ?: formatted)
    }
    val tint = remember(name) { traitAccentColor(name) }
    val containerColor = if (selected) tint.copy(alpha = 0.28f) else tint.copy(alpha = 0.14f)
    val borderStroke = BorderStroke(
        width = if (selected) 1.5.dp else 1.dp,
        color = if (selected) tint else tint.copy(alpha = 0.45f)
    )

    val chipContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            TftTraitIcon(
                traitName = name,
                environment = environment,
                size = 16.dp
            )
            if (!count.isNullOrBlank()) {
                Text(
                    text = count,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            color = containerColor,
            border = borderStroke,
            modifier = modifier
        ) {
            chipContent()
        }
    } else {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = containerColor,
            border = borderStroke,
            modifier = modifier
        ) {
            chipContent()
        }
    }
}

fun inferItemCategory(itemName: String): ItemCategory {
    val n = itemName.lowercase()
    return when {
        n.contains("blue buff") || n.contains("shojin") || n.contains("jeweled") ||
            n.contains("rabadon") || n.contains("archangel") || n.contains("morello") ||
            n.contains("ionic") || n.contains("crownguard") || n.contains("adaptive") ||
            n.contains("hextech") || n.contains("void staff") || n.contains("rod") || n.contains("tear") -> ItemCategory.AP
        n.contains("infinity") || n.contains("last whisper") || n.contains("giant slayer") ||
            n.contains("runaan") || n.contains("deathblade") || n.contains("striker") ||
            n.contains("guardbreaker") || n.contains("sword") -> ItemCategory.AD
        n.contains("guinsoo") || n.contains("nashor") || n.contains("statikk") ||
            n.contains("red buff") || n.contains("kraken") || n.contains("bow") -> ItemCategory.ATTACK_SPEED
        n.contains("warmog") || n.contains("gargoyle") || n.contains("bramble") ||
            n.contains("dragon") || n.contains("redemption") || n.contains("spirit visage") ||
            n.contains("sunfire") || n.contains("steadfast") || n.contains("protector") ||
            n.contains("evenshroud") || n.contains("quicksilver") ||
            n.contains("vest") || n.contains("cloak") || n.contains("belt") -> ItemCategory.TANK
        else -> ItemCategory.FIGHTER
    }
}

fun itemCategoryColor(category: ItemCategory): Color = when (category) {
    ItemCategory.AP -> Color(0xFF0AC8B9)
    ItemCategory.AD -> Color(0xFFEF4444)
    ItemCategory.TANK -> Color(0xFF2ECC71)
    ItemCategory.ATTACK_SPEED -> Color(0xFFF59E0B)
    ItemCategory.FIGHTER -> Color(0xFFC89B3C)
}

private fun inferItemFallbackIcon(itemName: String, category: ItemCategory): ImageVector {
    val n = itemName.lowercase()
    return when {
        n.contains("blue buff") || n.contains("tear") -> Icons.Default.WaterDrop
        n.contains("jeweled") || n.contains("rabadon") || n.contains("crown") ||
            n.contains("gloves") || n.contains("thief") -> Icons.Default.Diamond
        n.contains("sunfire") || n.contains("guinsoo") || n.contains("morello") -> Icons.Default.LocalFireDepartment
        n.contains("red buff") || n.contains("ember") -> Icons.Default.Whatshot
        n.contains("warmog") || n.contains("belt") || n.contains("spirit visage") -> Icons.Default.Favorite
        n.contains("redemption") || n.contains("bloodthirster") || n.contains("hextech") -> Icons.Default.Spa
        n.contains("statikk") || n.contains("ionic") || n.contains("shojin") || n.contains("void staff") -> Icons.Default.Bolt
        n.contains("gargoyle") || n.contains("bramble") || n.contains("vest") ||
            n.contains("protector") || n.contains("steadfast") || n.contains("evenshroud") -> Icons.Default.Shield
        n.contains("dragon") || n.contains("cloak") || n.contains("edge of night") ||
            n.contains("adaptive") || n.contains("quicksilver") -> Icons.Default.Security
        category == ItemCategory.AP -> Icons.Default.AutoFixHigh
        category == ItemCategory.AD -> Icons.Default.GpsFixed
        category == ItemCategory.TANK -> Icons.Default.Shield
        category == ItemCategory.ATTACK_SPEED -> Icons.Default.Speed
        else -> Icons.Default.HealthAndSafety
    }
}

@Composable
fun TftItemIcon(
    itemName: String,
    categoryOverride: ItemCategory? = null,
    size: Dp = 18.dp,
    environment: TftEnvironment? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rev = TftAssetIconResolver.catalogRevision
    val cleanName = remember(itemName, rev) { formatRiotAssetLabel(itemName) }
    val category = remember(cleanName, categoryOverride) { categoryOverride ?: inferItemCategory(cleanName) }
    val tint = itemCategoryColor(category)
    val fallbackIcon = remember(cleanName, category) { inferItemFallbackIcon(cleanName, category) }
    val fallbackUrls = remember(itemName, environment, TftAssetIconResolver.ddragonVersion, rev) {
        TftAssetIconResolver.resolveItemIconFallbackUrls(itemName, environment)
    }
    val targetPx = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(36) }
    var attemptIndex by remember(fallbackUrls) { mutableIntStateOf(0) }
    val activeIconUrl = fallbackUrls.getOrNull(attemptIndex)
    var imageLoaded by remember(activeIconUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(tint.copy(alpha = 0.18f))
            .border(0.8.dp, tint.copy(alpha = 0.55f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!imageLoaded) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = "$cleanName item icon",
                tint = tint,
                modifier = Modifier.size(size * 0.72f)
            )
        }
        if (activeIconUrl != null) {
            val request = remember(activeIconUrl, targetPx, context) {
                ImageRequest.Builder(context)
                    .data(activeIconUrl)
                    .size(targetPx)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = "$cleanName item icon",
                contentScale = ContentScale.Crop,
                onSuccess = { imageLoaded = true },
                onError = {
                    imageLoaded = false
                    if (attemptIndex + 1 < fallbackUrls.size) {
                        attemptIndex += 1
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
fun TftItemChip(
    itemName: String,
    categoryOverride: ItemCategory? = null,
    compact: Boolean = false,
    environment: TftEnvironment? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rev = TftAssetIconResolver.catalogRevision
    val cleanName = remember(itemName, rev) { formatRiotAssetLabel(itemName) }
    val category = remember(cleanName, categoryOverride) { categoryOverride ?: inferItemCategory(cleanName) }
    val tint = itemCategoryColor(category)
    val containerColor = if (selected) tint.copy(alpha = 0.28f) else tint.copy(alpha = 0.14f)
    val borderStroke = BorderStroke(
        width = if (selected) 1.5.dp else 1.dp,
        color = if (selected) tint else tint.copy(alpha = 0.45f)
    )

    val chipContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 3.dp else 5.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            TftItemIcon(
                itemName = itemName,
                categoryOverride = category,
                size = if (compact) 15.dp else 20.dp,
                environment = environment
            )
            Text(
                text = cleanName,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            color = containerColor,
            border = borderStroke,
            modifier = modifier
        ) {
            chipContent()
        }
    } else {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = containerColor,
            border = borderStroke,
            modifier = modifier
        ) {
            chipContent()
        }
    }
}

fun inferAugmentTier(name: String): AugmentTier {
    val n = name.lowercase()
    return when {
        n.contains("crown") || n.contains("level up") || n.contains("hedge fund") ||
            n.contains("prismatic") || n.contains("ascendant emblem") || n.contains("built different") -> AugmentTier.PRISMATIC
        n.contains("crest") || n.contains("lotus") || n.contains("healthy") ||
            n.contains("blessing") || n.contains("starry") || n.contains("sector") ||
            n.contains("vampirism") || n.contains("heavy hitters") || n.contains("ii") -> AugmentTier.GOLD
        else -> AugmentTier.SILVER
    }
}

fun augmentTierColor(tier: AugmentTier): Color = when (tier) {
    AugmentTier.PRISMATIC -> Color(0xFFC084FC)
    AugmentTier.GOLD -> Color(0xFFF0B949)
    AugmentTier.SILVER -> Color(0xFF94A3B8)
}

private fun augmentVisual(name: String, tier: AugmentTier): Pair<ImageVector, Color> {
    val n = name.lowercase()
    val color = augmentTierColor(tier)
    val icon = when {
        n.contains("crest") || n.contains("crown") || n.contains("emblem") || n.contains("soul") -> Icons.Default.WorkspacePremium
        n.contains("level") || n.contains("fund") || n.contains("budget") || n.contains("ticket") || n.contains("study") || n.contains("cart") -> Icons.Default.AccountBalance
        n.contains("lotus") || n.contains("spark") || n.contains("fire") -> Icons.Default.LocalFireDepartment
        n.contains("mana") || n.contains("uplink") || n.contains("mind") -> Icons.Default.Psychology
        tier == AugmentTier.PRISMATIC -> Icons.Default.Diamond
        else -> Icons.Default.Bolt
    }
    return icon to color
}

@Composable
fun TftAugmentIcon(
    augmentName: String,
    augmentTierOverride: AugmentTier? = null,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rev = TftAssetIconResolver.catalogRevision
    val cleanName = remember(augmentName, rev) { formatRiotAssetLabel(augmentName) }
    val augTier = remember(cleanName, augmentTierOverride) { augmentTierOverride ?: inferAugmentTier(cleanName) }
    val (fallbackIcon, tint) = remember(cleanName, augTier) { augmentVisual(cleanName, augTier) }
    val iconUrl = remember(augmentName, TftAssetIconResolver.ddragonVersion, rev) {
        TftAssetIconResolver.resolveAugmentIconUrl(augmentName)
    }
    val targetPx = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(36) }
    var imageLoaded by remember(iconUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(5.dp))
            .background(tint.copy(alpha = 0.20f))
            .border(0.9.dp, tint.copy(alpha = 0.70f), RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!imageLoaded) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = "$cleanName (${augTier.label} augment)",
                tint = tint,
                modifier = Modifier.size(size * 0.72f)
            )
        }
        if (iconUrl != null) {
            val request = remember(iconUrl, targetPx, context) {
                ImageRequest.Builder(context)
                    .data(iconUrl)
                    .size(targetPx)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = "$cleanName (${augTier.label} augment)",
                contentScale = ContentScale.Crop,
                onSuccess = { imageLoaded = true },
                onError = { imageLoaded = false },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(5.dp))
            )
        }
    }
}

@Composable
fun TftAugmentChip(
    augmentName: String,
    augmentTierOverride: AugmentTier? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val rev = TftAssetIconResolver.catalogRevision
    val cleanName = remember(augmentName, rev) { formatRiotAssetLabel(augmentName) }
    val augTier = remember(cleanName, augmentTierOverride) { augmentTierOverride ?: inferAugmentTier(cleanName) }
    val (_, tint) = remember(cleanName, augTier) { augmentVisual(cleanName, augTier) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.50f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 7.dp else 10.dp,
                vertical = if (compact) 4.dp else 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            TftAugmentIcon(
                augmentName = augmentName,
                augmentTierOverride = augTier,
                size = if (compact) 15.dp else 20.dp
            )
            Text(
                text = cleanName,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!compact) {
                Text(
                    text = augTier.label.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
            }
        }
    }
}

@Composable
fun SplitCheckForUpdatesRow(
    isRefreshingComps: Boolean,
    isCheckingAppUpdate: Boolean,
    onCheckCompUpdates: () -> Unit,
    onCheckAppUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(
            onClick = onCheckCompUpdates,
            enabled = !isRefreshingComps,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
            ),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("check_for_comp_updates_button")
        ) {
            if (isRefreshingComps) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.check_for_comp_updates),
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(
                    if (isRefreshingComps) R.string.checking_for_comp_updates else R.string.check_for_comp_updates
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        }

        FilledTonalButton(
            onClick = onCheckAppUpdates,
            enabled = !isCheckingAppUpdate,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            ),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("check_for_app_updates_button")
        ) {
            if (isCheckingAppUpdate) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = stringResource(R.string.check_for_app_updates),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(
                    if (isCheckingAppUpdate) R.string.checking_for_app_updates else R.string.check_for_app_updates
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

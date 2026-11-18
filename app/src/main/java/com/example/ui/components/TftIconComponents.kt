package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.OfflinePin
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.AugmentTier
import com.example.data.model.ItemCategory
import com.example.data.model.OfflineCacheStatus
import com.example.data.model.TftEnvironment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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
private val REGEX_SUFFIX_ROLE = Regex("_(?:AD|AP|Tank|Carry)$", RegexOption.IGNORE_CASE)
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
 * "TFT18_Augment_ElderwoodCrest", "Set18_Elderwood") into readable display labels.
 */
fun formatRiotAssetLabel(rawId: String): String {
    val trimmed = rawId.trim()
    if (trimmed.isEmpty()) return ""
    // Fast path for ordinary human-readable names like "Jeweled Gauntlet" or "Ahri"
    if (!trimmed.contains('_') && !trimmed.startsWith("DA") && !trimmed.startsWith("TFT") && !trimmed.startsWith("Set")) {
        return trimmed
    }
    val rev = TftAssetIconResolver.catalogRevision
    if (formattedLabelCacheRevision != rev) {
        formattedLabelCache.clear()
        formattedLabelCacheRevision = rev
    } else {
        formattedLabelCache[trimmed]?.let { return it }
    }

    TftAssetIconResolver.resolveDisplayName(trimmed)?.let { resolved ->
        if (formattedLabelCache.size < 1200) formattedLabelCache[trimmed] = resolved
        return resolved
    }
    if (!trimmed.contains("_") && trimmed.any { it == ' ' }) {
        if (formattedLabelCache.size < 1200) formattedLabelCache[trimmed] = trimmed
        return trimmed
    }
    var stripped = trimmed
        .replace(REGEX_PREFIX_TFT, "")
        .replace(REGEX_PREFIX_SET, "")
        .replace(REGEX_SUFFIX_ROLE, "")
        .substringAfterLast("_")
        .replace(REGEX_SUFFIX_SMALL, "")
        .replace(REGEX_SUFFIX_DIGITS, "$1")
    if (stripped.startsWith("Emblem") && stripped.length > 6) {
        stripped = stripped.removePrefix("Emblem") + "Emblem"
    }
    val formatted = stripped
        .replace(REGEX_CAMEL, "$1 $2")
        .replace(REGEX_ALPHA_DIGIT, "$1 $2")
        .replace(REGEX_ROMAN, " $1")
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
    if (formattedLabelCache.size < 1200) {
        formattedLabelCache[trimmed] = formatted
    }
    return formatted
}

@Composable
fun TftChampionAvatar(
    name: String,
    cost: Int,
    isCarry: Boolean = false,
    stars: Int = 2,
    size: Dp = 36.dp,
    environment: TftEnvironment? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rev = TftAssetIconResolver.catalogRevision
    val cleanName = remember(name, rev) { formatRiotAssetLabel(name) }
    val iconUrl = remember(name, environment, TftAssetIconResolver.ddragonVersion, rev) {
        TftAssetIconResolver.resolveChampionIconUrl(name, environment)
    }
    val targetPx = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(48) }
    val color = championCostColor(cost)
    val borderColor = if (isCarry) MaterialTheme.colorScheme.primary else color
    val shape = RoundedCornerShape(8.dp)

    Box(modifier = modifier.size(size + 4.dp)) {
        Box(
            modifier = Modifier
                .size(size)
                .align(Alignment.Center)
                .clip(shape)
                .background(color.copy(alpha = 0.20f))
                .border(
                    width = if (isCarry) 2.dp else 1.5.dp,
                    color = borderColor,
                    shape = shape
                ),
            contentAlignment = Alignment.Center
        ) {
            ChampionMonogramFallback(cleanName, isCarry, color, size)
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
                    contentDescription = "$cleanName champion icon",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                )
            }
        }

        if (isCarry) {
            Box(
                modifier = Modifier
                    .size((size * 0.42f).coerceIn(13.dp, 17.dp))
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = "Main Carry",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size((size * 0.28f).coerceIn(9.dp, 12.dp))
                )
            }
        } else if (stars >= 3) {
            Box(
                modifier = Modifier
                    .size((size * 0.38f).coerceIn(12.dp, 15.dp))
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(Color(0xFFF0B949)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "3-Star Unit",
                    tint = Color(0xFF0B0E14),
                    modifier = Modifier.size((size * 0.26f).coerceIn(8.dp, 10.dp))
                )
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
    size: Dp = 32.dp,
    environment: TftEnvironment? = null,
    modifier: Modifier = Modifier
) {
    val cleanName = remember(name) { formatRiotAssetLabel(name) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        TftChampionAvatar(
            name = name,
            cost = cost,
            isCarry = isCarry,
            stars = stars,
            size = size,
            environment = environment
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = cleanName,
            fontSize = 9.sp,
            fontWeight = if (isCarry) FontWeight.Bold else FontWeight.Medium,
            color = if (isCarry) Color(0xFFFFD700) else Color(0xFFE6ECF5),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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

@Composable
fun TftTraitChip(
    traitLabel: String,
    environment: TftEnvironment? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rev = TftAssetIconResolver.catalogRevision
    val (count, name) = remember(traitLabel, rev) {
        val formatted = formatRiotAssetLabel(traitLabel)
        val countMatch = REGEX_TRAIT_COUNT.find(formatted)
        countMatch?.groupValues?.getOrNull(1) to (countMatch?.groupValues?.getOrNull(2) ?: formatted)
    }
    val (fallbackIcon, tint) = remember(name) { traitVisual(name) }
    val iconUrl = remember(name, environment, TftAssetIconResolver.ddragonVersion, rev) {
        TftAssetIconResolver.resolveTraitIconUrl(name, environment)
    }
    val targetPx = with(LocalDensity.current) { 20.dp.roundToPx() }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier.size(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = "$name trait icon",
                    tint = tint,
                    modifier = Modifier.size(14.dp)
                )
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
                        contentDescription = "$name trait icon",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

private fun itemCategoryColor(category: ItemCategory): Color = when (category) {
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
    val iconUrl = remember(itemName, environment, TftAssetIconResolver.ddragonVersion, rev) {
        TftAssetIconResolver.resolveItemIconUrl(itemName, environment)
    }
    val targetPx = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(36) }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(tint.copy(alpha = 0.18f))
            .border(0.8.dp, tint.copy(alpha = 0.55f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = "$cleanName item icon",
            tint = tint,
            modifier = Modifier.size(size * 0.72f)
        )
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
                contentDescription = "$cleanName item icon",
                contentScale = ContentScale.Crop,
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
    modifier: Modifier = Modifier
) {
    val rev = TftAssetIconResolver.catalogRevision
    val cleanName = remember(itemName, rev) { formatRiotAssetLabel(itemName) }
    val category = remember(cleanName, categoryOverride) { categoryOverride ?: inferItemCategory(cleanName) }
    val tint = itemCategoryColor(category)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
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
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

private fun augmentVisual(name: String, tier: AugmentTier): Pair<ImageVector, Color> {
    val n = name.lowercase()
    val color = when (tier) {
        AugmentTier.PRISMATIC -> Color(0xFFC084FC)
        AugmentTier.GOLD -> Color(0xFFF0B949)
        AugmentTier.SILVER -> Color(0xFF94A3B8)
    }
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

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(5.dp))
            .background(tint.copy(alpha = 0.20f))
            .border(0.9.dp, tint.copy(alpha = 0.70f), RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = "$cleanName (${augTier.label} augment)",
            tint = tint,
            modifier = Modifier.size(size * 0.72f)
        )
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
fun OfflineCacheStatusBanner(
    isGuestMode: Boolean,
    cacheStatus: OfflineCacheStatus,
    isRefreshing: Boolean,
    onRefreshClick: () -> Unit,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeText = remember(cacheStatus.cachedAtMillis) {
        if (cacheStatus.cachedAtMillis <= 0L) "Ready"
        else SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(cacheStatus.cachedAtMillis))
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        border = BorderStroke(
            1.dp,
            if (cacheStatus.isOfflineFallback) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.48f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("offline_cache_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            (if (cacheStatus.isOfflineFallback) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                                .copy(alpha = 0.16f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            cacheStatus.isOfflineFallback -> Icons.Default.CloudOff
                            isGuestMode -> Icons.Default.OfflinePin
                            else -> Icons.Default.CloudDone
                        },
                        contentDescription = "Session and offline cache status",
                        tint = if (cacheStatus.isOfflineFallback) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = when {
                            cacheStatus.isOfflineFallback -> "Offline Mode • Cached Snapshots & Tiers"
                            isGuestMode -> "Pre-loaded • Instant Offline & Live Sync"
                            else -> "Cloud Synced • Instant Cache Ready"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${cacheStatus.compsCount} comps • ${cacheStatus.championsCount} champs • ${cacheStatus.itemsCount} items • ${cacheStatus.augmentsCount} augs • $timeText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isGuestMode) {
                    TextButton(
                        onClick = onSignInClick,
                        modifier = Modifier.testTag("banner_sign_in_button")
                    ) {
                        Text(
                            text = "Sign In",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(
                    onClick = onRefreshClick,
                    enabled = !isRefreshing,
                    modifier = Modifier.size(48.dp)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync patches and offline cache",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

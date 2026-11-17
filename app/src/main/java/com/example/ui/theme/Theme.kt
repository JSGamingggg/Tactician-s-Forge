package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemeMode

private val HextechDarkColorScheme = darkColorScheme(
    primary = BrushedGold,
    onPrimary = DeepGoldOnPrimary,
    primaryContainer = GoldContainerDark,
    onPrimaryContainer = BrightGold,
    secondary = ArcaneCyan,
    onSecondary = DeepCyanOnSecondary,
    secondaryContainer = CyanContainerDark,
    onSecondaryContainer = Color(0xFF7CF5EA),
    tertiary = MythicViolet,
    onTertiary = Color(0xFF140D2E),
    tertiaryContainer = VioletContainerDark,
    onTertiaryContainer = Color(0xFFD5CAFF),
    background = ObsidianVoid,
    onBackground = MistText,
    surface = DeepSlate,
    onSurface = MistText,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = MutedSlateText,
    outline = SlateOutline,
    outlineVariant = Color(0xFF1F293D),
    error = NerfCrimson,
    onError = Color.White
)

private val AlabasterLightColorScheme = lightColorScheme(
    primary = EngravedGold,
    onPrimary = Color.White,
    primaryContainer = EngravedGoldContainer,
    onPrimaryContainer = Color(0xFF3E2A06),
    secondary = DeepArcaneTeal,
    onSecondary = Color.White,
    secondaryContainer = DeepTealContainer,
    onSecondaryContainer = Color(0xFF003831),
    tertiary = Color(0xFF5D46C6),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE7E2FF),
    onTertiaryContainer = Color(0xFF1E1352),
    background = AlabasterCanvas,
    onBackground = InkObsidian,
    surface = AlabasterSurface,
    onSurface = InkObsidian,
    surfaceVariant = AlabasterElevated,
    onSurfaceVariant = InkMuted,
    outline = AlabasterOutline,
    outlineVariant = Color(0xFFE3DDD0),
    error = Color(0xFFC0392B),
    onError = Color.White
)

@Immutable
data class HextechExtendedColors(
    val isDark: Boolean = true,
    val goldBorder: Color = BrushedGold.copy(alpha = 0.45f),
    val tierS: Color = TierSColor,
    val tierA: Color = TierAColor,
    val tierB: Color = TierBColor,
    val tierC: Color = TierCColor,
    val tierX: Color = TierXColor,
    val buff: Color = BuffEmerald,
    val nerf: Color = NerfCrimson,
    val adjust: Color = AdjustAmber,
    val liveBadge: Color = LiveEmerald,
    val pbeBadge: Color = PbePurple
)

val LocalHextechColors = staticCompositionLocalOf { HextechExtendedColors() }

private val HextechShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun TacticiansForgeTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) HextechDarkColorScheme else AlabasterLightColorScheme
    val extendedColors = if (useDark) {
        HextechExtendedColors(
            isDark = true,
            goldBorder = BrushedGold.copy(alpha = 0.56f),
            tierS = TierSColor,
            tierA = TierAColor,
            tierB = TierBColor,
            tierC = TierCColor,
            tierX = TierXColor,
            buff = BuffEmerald,
            nerf = NerfCrimson,
            adjust = AdjustAmber,
            liveBadge = LiveEmerald,
            pbeBadge = PbePurple
        )
    } else {
        HextechExtendedColors(
            isDark = false,
            goldBorder = EngravedGold.copy(alpha = 0.45f),
            tierS = Color(0xFFC9870A),
            tierA = Color(0xFF6B4FE0),
            tierB = DeepArcaneTeal,
            tierC = Color(0xFF2563EB),
            tierX = Color(0xFFE11D48),
            buff = Color(0xFF16A34A),
            nerf = Color(0xFFDC2626),
            adjust = Color(0xFFD97706),
            liveBadge = Color(0xFF16A34A),
            pbeBadge = Color(0xFF7C3AED)
        )
    }

    CompositionLocalProvider(LocalHextechColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = HextechShapes,
            content = content
        )
    }
}

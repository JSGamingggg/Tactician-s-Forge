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
    onSecondaryContainer = Color(0xFF8FFBF0),
    tertiary = MythicViolet,
    onTertiary = Color(0xFF170B33),
    tertiaryContainer = VioletContainerDark,
    onTertiaryContainer = Color(0xFFE0D6FF),
    background = ObsidianVoid,
    onBackground = MistText,
    surface = DeepSlate,
    onSurface = MistText,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = MutedSlateText,
    outline = SlateOutline,
    outlineVariant = Color(0xFF233252),
    error = NerfCrimson,
    onError = Color.White,
    errorContainer = Color(0xFF4C141A),
    onErrorContainer = Color(0xFFFFD6D9)
)

private val AlabasterLightColorScheme = lightColorScheme(
    primary = EngravedGold,
    onPrimary = Color.White,
    primaryContainer = EngravedGoldContainer,
    onPrimaryContainer = Color(0xFF451A03),
    secondary = DeepArcaneTeal,
    onSecondary = Color.White,
    secondaryContainer = DeepTealContainer,
    onSecondaryContainer = Color(0xFF042F2E),
    tertiary = Color(0xFF6D28D9),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF2E1065),
    background = AlabasterCanvas,
    onBackground = InkObsidian,
    surface = AlabasterSurface,
    onSurface = InkObsidian,
    surfaceVariant = AlabasterElevated,
    onSurfaceVariant = InkMuted,
    outline = AlabasterOutline,
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

@Immutable
data class HextechExtendedColors(
    val isDark: Boolean = true,
    val goldBorder: Color = BrushedGold.copy(alpha = 0.55f),
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
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
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
            goldBorder = BrushedGold.copy(alpha = 0.62f),
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
            goldBorder = EngravedGold.copy(alpha = 0.55f),
            tierS = Color(0xFFD97706),
            tierA = Color(0xFF7C3AED),
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

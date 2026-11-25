package com.jzb.tower.android.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jzb.tower.core.model.ThemePreference

@Immutable
data class TowerPalette(
    val isDark: Boolean,
    val groupedBackground: Color,
    val ambientWash: Color,
    val cardSurface: Color,
    val cardSubtleSurface: Color,
    val cardBorder: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val emerald: Color,
    val emeraldSurface: Color,
    val amber: Color,
    val amberSurface: Color,
    val coral: Color,
    val coralSurface: Color,
    val coolSlate: Color,
    val indigo: Color,
    val purple: Color,
    val teal: Color,
)

private val LightPalette = TowerPalette(
    isDark = false,
    groupedBackground = Color(0xFFF3F5F9),
    ambientWash = Color(0x142563EB),
    cardSurface = Color(0xFFFFFFFF),
    cardSubtleSurface = Color(0xFFF6F8FC),
    cardBorder = Color(0xFF0F172A).copy(alpha = 0.09f),
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2563EB).copy(alpha = 0.11f),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textTertiary = Color(0xFF94A3B8),
    divider = Color(0xFF0F172A).copy(alpha = 0.07f),
    emerald = Color(0xFF10B981),
    emeraldSurface = Color(0xFF10B981).copy(alpha = 0.11f),
    amber = Color(0xFFD97706),
    amberSurface = Color(0xFFD97706).copy(alpha = 0.12f),
    coral = Color(0xFFE11D48),
    coralSurface = Color(0xFFE11D48).copy(alpha = 0.11f),
    coolSlate = Color(0xFF6B8299),
    indigo = Color(0xFF4F46E5),
    purple = Color(0xFF7C3AED),
    teal = Color(0xFF0D9488),
)

private val DarkPalette = TowerPalette(
    isDark = true,
    groupedBackground = Color(0xFF0B0F17),
    ambientWash = Color(0x1C4C8BFF),
    cardSurface = Color(0xFF151B27),
    cardSubtleSurface = Color(0xFF1B2333),
    cardBorder = Color(0xFFFFFFFF).copy(alpha = 0.09f),
    primary = Color(0xFF4C8BFF),
    onPrimary = Color(0xFF071329),
    primaryContainer = Color(0xFF4C8BFF).copy(alpha = 0.16f),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    textTertiary = Color(0xFF64748B),
    divider = Color(0xFFFFFFFF).copy(alpha = 0.08f),
    emerald = Color(0xFF34D399),
    emeraldSurface = Color(0xFF34D399).copy(alpha = 0.15f),
    amber = Color(0xFFFBBF24),
    amberSurface = Color(0xFFFBBF24).copy(alpha = 0.15f),
    coral = Color(0xFFFB7185),
    coralSurface = Color(0xFFFB7185).copy(alpha = 0.15f),
    coolSlate = Color(0xFF7E96AE),
    indigo = Color(0xFF818CF8),
    purple = Color(0xFFA78BFA),
    teal = Color(0xFF2DD4BF),
)

val LocalTowerPalette = staticCompositionLocalOf { LightPalette }

object TowerDimens {
    val CardCornerRadius = 22.dp
    val CompactCornerRadius = 16.dp
    val PillCornerRadius = 14.dp
    val CardBorderWidth = 0.75.dp
    val PagePadding = 16.dp
    val CardPadding = 16.dp
    val ActionButtonHeight = 50.dp
}

/**
 * Non-bouncy, crisp motion matching TowerMotion in SwiftUI.
 */
object TowerMotion {
    const val PRESS_IN_MS = 100
    const val PRESS_RELEASE_MS = 160
    const val SELECTION_MS = 180
    const val DISCLOSURE_MS = 220
    const val PRESS_SCALE = 0.97f

    private val CrispEaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    fun <T> selectionSpec() = tween<T>(durationMillis = SELECTION_MS, easing = CrispEaseOut)
    fun <T> disclosureSpec() = tween<T>(durationMillis = DISCLOSURE_MS, easing = CrispEaseOut)
    fun <T> surfaceSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}

private val TowerShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

private val TowerTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

object TowerTheme {
    val palette: TowerPalette
        @Composable
        get() = LocalTowerPalette.current
}

@Composable
fun TowerTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themePreference) {
        ThemePreference.SYSTEM -> systemDark
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val palette = if (useDark) DarkPalette else LightPalette

    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.primary,
            secondary = palette.teal,
            background = palette.groupedBackground,
            onBackground = palette.textPrimary,
            surface = palette.cardSurface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.cardSubtleSurface,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.cardBorder,
            outlineVariant = palette.divider,
            error = palette.coral,
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.primary,
            secondary = palette.teal,
            background = palette.groupedBackground,
            onBackground = palette.textPrimary,
            surface = palette.cardSurface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.cardSubtleSurface,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.cardBorder,
            outlineVariant = palette.divider,
            error = palette.coral,
        )
    }

    CompositionLocalProvider(LocalTowerPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = TowerShapes,
            typography = TowerTypography,
            content = content,
        )
    }
}

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
    val pillNavSurface: Color,
    val pillNavBorder: Color,
    val pillIndicator: Color,
    val segmentedTrack: Color,
    val segmentedThumb: Color,
    val switchOffTrack: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val emerald: Color,
    val emeraldSurface: Color,
    val lakeBlue: Color,
    val amber: Color,
    val amberSurface: Color,
    val coral: Color,
    val coralSurface: Color,
    val unreachableRose: Color,
    val coolSlate: Color,
    val indigo: Color,
    val purple: Color,
    val teal: Color,
    val orange: Color,
    val cyan: Color,
)

private val LightPalette = TowerPalette(
    isDark = false,
    groupedBackground = Color(0xFFF2F4F8),
    ambientWash = Color(0x16007AFF),
    cardSurface = Color(0xFFFFFFFF),
    cardSubtleSurface = Color(0xFFF4F6FA),
    cardBorder = Color(0xFF0F172A).copy(alpha = 0.075f),
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF007AFF).copy(alpha = 0.11f),
    pillNavSurface = Color.White.copy(alpha = 0.94f),
    pillNavBorder = Color(0xFF0F172A).copy(alpha = 0.08f),
    pillIndicator = Color(0xFFEBF0FA),
    segmentedTrack = Color(0xFFEEEEF3),
    segmentedThumb = Color.White,
    switchOffTrack = Color(0xFFE3E4E8),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF64748B),
    textTertiary = Color(0xFF94A3B8),
    divider = Color(0xFF0F172A).copy(alpha = 0.07f),
    emerald = Color(0xFF0FA67D),
    emeraldSurface = Color(0xFF0FA67D).copy(alpha = 0.12f),
    lakeBlue = Color(0xFF3387D6),
    amber = Color(0xFFD49424),
    amberSurface = Color(0xFFD49424).copy(alpha = 0.13f),
    coral = Color(0xFFE35C3D),
    coralSurface = Color(0xFFE35C3D).copy(alpha = 0.12f),
    unreachableRose = Color(0xFFAD477A),
    coolSlate = Color(0xFF7A96B0),
    indigo = Color(0xFF5856D6),
    purple = Color(0xFFAF52DE),
    teal = Color(0xFF0FA67D),
    orange = Color(0xFFFF9500),
    cyan = Color(0xFF32ADE6),
)

private val DarkPalette = TowerPalette(
    isDark = true,
    groupedBackground = Color(0xFF0B0F17),
    ambientWash = Color(0x1E0A84FF),
    cardSurface = Color(0xFF151C28),
    cardSubtleSurface = Color(0xFF1C2536),
    cardBorder = Color(0xFFFFFFFF).copy(alpha = 0.09f),
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A84FF).copy(alpha = 0.18f),
    pillNavSurface = Color(0xFF182030).copy(alpha = 0.94f),
    pillNavBorder = Color(0xFFFFFFFF).copy(alpha = 0.10f),
    pillIndicator = Color(0xFF25324B),
    segmentedTrack = Color(0xFF20293B),
    segmentedThumb = Color(0xFF32405B),
    switchOffTrack = Color(0xFF393E4A),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    textTertiary = Color(0xFF64748B),
    divider = Color(0xFFFFFFFF).copy(alpha = 0.08f),
    emerald = Color(0xFF34D399),
    emeraldSurface = Color(0xFF34D399).copy(alpha = 0.16f),
    lakeBlue = Color(0xFF5FA8F0),
    amber = Color(0xFFFBBF24),
    amberSurface = Color(0xFFFBBF24).copy(alpha = 0.16f),
    coral = Color(0xFFF87171),
    coralSurface = Color(0xFFF87171).copy(alpha = 0.16f),
    unreachableRose = Color(0xFFD96EA3),
    coolSlate = Color(0xFF8EA8C1),
    indigo = Color(0xFF7D7AFF),
    purple = Color(0xFFBF5AF2),
    teal = Color(0xFF2DD4BF),
    orange = Color(0xFFFF9F0A),
    cyan = Color(0xFF64D2FF),
)

val LocalTowerPalette = staticCompositionLocalOf { LightPalette }

object TowerDimens {
    val CardCornerRadius = 22.dp
    val CompactCornerRadius = 16.dp
    val PillCornerRadius = 14.dp
    val CardBorderWidth = 0.75.dp
    val PagePadding = 16.dp
    val CardPadding = 16.dp
    val ActionButtonHeight = 52.dp
    val FloatingNavClearance = 104.dp
    val ExportBarClearance = 176.dp
}

/**
 * Motion vocabulary matching TowerMotion in SwiftUI:
 * crisp, non-bouncy springs and rapid direct press feedback.
 */
object TowerMotion {
    const val PRESS_IN_MS = 100
    const val PRESS_RELEASE_MS = 160
    const val SELECTION_MS = 160
    const val DISCLOSURE_MS = 280
    const val SURFACE_MS = 340
    const val PRESS_SCALE = 0.97f
    const val PRESS_ALPHA = 0.86f
    const val SELECTION_SYMBOL_SCALE = 0.92f
    const val SURFACE_ENTRY_SCALE = 0.96f

    private val CrispEaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    fun <T> pressInSpec() = tween<T>(durationMillis = PRESS_IN_MS, easing = CrispEaseOut)
    fun <T> pressReleaseSpec() = tween<T>(durationMillis = PRESS_RELEASE_MS, easing = CrispEaseOut)
    fun <T> selectionSpec() = tween<T>(durationMillis = SELECTION_MS, easing = CrispEaseOut)
    fun <T> disclosureSpec() = tween<T>(durationMillis = DISCLOSURE_MS, easing = CrispEaseOut)

    fun <T> pillSpring() = spring<T>(
        dampingRatio = 0.88f,
        stiffness = 420f,
    )

    fun <T> capsuleSpring() = spring<T>(
        dampingRatio = 1f,
        stiffness = 380f,
    )

    fun <T> surfaceSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 380f,
    )

    fun <T> mapSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 320f,
    )
}

private val TowerShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val TowerTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.25).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.2).sp,
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

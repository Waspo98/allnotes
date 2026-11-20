package app.overbay.allnotes.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.ThemeMode

internal val AllNotesLightColorScheme = lightColorScheme(
    primary = Color(0xFF3949AB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF001258),
    secondary = Color(0xFF5A5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFE1F9),
    onSecondaryContainer = Color(0xFF171B2C),
    tertiary = Color(0xFF006B5E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9EF2E2),
    onTertiaryContainer = Color(0xFF00201B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainer = Color(0xFFEEEDF4),
    surfaceContainerHigh = Color(0xFFE8E7EF),
    surfaceContainerHighest = Color(0xFFE3E1E9),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    inversePrimary = Color(0xFFB8C3FF),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C5D0),
    scrim = Color(0xFF000000)
)

internal val AllNotesDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB8C3FF),
    onPrimary = Color(0xFF00228B),
    primaryContainer = Color(0xFF1E3093),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC3C5DD),
    onSecondary = Color(0xFF2C2F42),
    secondaryContainer = Color(0xFF424659),
    onSecondaryContainer = Color(0xFFDFE1F9),
    tertiary = Color(0xFF82D5C6),
    onTertiary = Color(0xFF003730),
    tertiaryContainer = Color(0xFF005046),
    onTertiaryContainer = Color(0xFF9EF2E2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C5D0),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF292A30),
    surfaceContainerHighest = Color(0xFF34343B),
    inverseSurface = Color(0xFFE3E1E9),
    inverseOnSurface = Color(0xFF2F3036),
    inversePrimary = Color(0xFF3949AB),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F),
    scrim = Color(0xFF000000)
)

@Immutable
data class AccentPalette(
    val container: Color,
    val onContainer: Color,
    val accent: Color
)

private val LocalIsDarkTheme = staticCompositionLocalOf { false }

private fun SectionAccentKey.resolvePalette(isDark: Boolean): AccentPalette = when (this) {
    SectionAccentKey.INDIGO -> if (isDark) {
        AccentPalette(container = Color(0xFF233288), onContainer = Color(0xFFDDE1FF), accent = Color(0xFF9FA8DA))
    } else {
        AccentPalette(container = Color(0xFFDDE1FF), onContainer = Color(0xFF001258), accent = Color(0xFF3949AB))
    }
    SectionAccentKey.EMERALD -> if (isDark) {
        AccentPalette(container = Color(0xFF0D5238), onContainer = Color(0xFFA7F3D0), accent = Color(0xFF34D399))
    } else {
        AccentPalette(container = Color(0xFFD1FAE5), onContainer = Color(0xFF064E3B), accent = Color(0xFF059669))
    }
    SectionAccentKey.AMBER -> if (isDark) {
        AccentPalette(container = Color(0xFF633B06), onContainer = Color(0xFFFDE68A), accent = Color(0xFFFBBF24))
    } else {
        AccentPalette(container = Color(0xFFFEF3C7), onContainer = Color(0xFF78350F), accent = Color(0xFFD97706))
    }
    SectionAccentKey.ROSE -> if (isDark) {
        AccentPalette(container = Color(0xFF6E1433), onContainer = Color(0xFFFFD9E2), accent = Color(0xFFFB7185))
    } else {
        AccentPalette(container = Color(0xFFFFE4E6), onContainer = Color(0xFF881337), accent = Color(0xFFE11D48))
    }
    SectionAccentKey.TEAL -> if (isDark) {
        AccentPalette(container = Color(0xFF0A4F5C), onContainer = Color(0xFFCCFBF1), accent = Color(0xFF2DD4BF))
    } else {
        AccentPalette(container = Color(0xFFCCFBF1), onContainer = Color(0xFF134E4A), accent = Color(0xFF0D9488))
    }
    SectionAccentKey.VIOLET -> if (isDark) {
        AccentPalette(container = Color(0xFF46237A), onContainer = Color(0xFFEDE9FE), accent = Color(0xFFA78BFA))
    } else {
        AccentPalette(container = Color(0xFFEDE9FE), onContainer = Color(0xFF4C1D95), accent = Color(0xFF7C3AED))
    }
}

/**
 * Calibrated Material 3 Expressive motion specs separating snappy spatial springs
 * from critically damped (zero-bounce) effects specs for fades and colors.
 */
object AllNotesMotion {
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.82f,
        stiffness = 900f
    )

    fun <T> spatialDefault(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.86f,
        stiffness = 550f
    )

    fun <T> effectsFast(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 1600f
    )

    fun <T> effectsDefault(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 900f
    )
}

@Composable
fun SectionAccentKey.containerColor(): Color = resolvePalette(LocalIsDarkTheme.current).container

@Composable
fun SectionAccentKey.contentColor(): Color = resolvePalette(LocalIsDarkTheme.current).onContainer

@Composable
fun SectionAccentKey.accentColor(): Color = resolvePalette(LocalIsDarkTheme.current).accent

@Composable
internal expect fun resolvePlatformColorScheme(darkTheme: Boolean, useDynamicColor: Boolean): ColorScheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AllNotesTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColor: Boolean = true,
    darkThemeOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = darkThemeOverride ?: when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = resolvePlatformColorScheme(darkTheme = darkTheme, useDynamicColor = useDynamicColor)

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}

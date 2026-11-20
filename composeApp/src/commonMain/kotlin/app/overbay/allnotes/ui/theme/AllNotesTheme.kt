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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.ThemeMode

internal val AllNotesLightColorScheme = lightColorScheme(
    primary = Color(0xFF34469E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1E6FF),
    onPrimaryContainer = Color(0xFF0A1B66),
    secondary = Color(0xFF635B52),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEBE4D9),
    onSecondaryContainer = Color(0xFF211C16),
    tertiary = Color(0xFF0E6B5E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC5EFE6),
    onTertiaryContainer = Color(0xFF00201B),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCE2DF),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF8F5F0),
    onBackground = Color(0xFF1E1B18),
    surface = Color(0xFFF8F5F0),
    onSurface = Color(0xFF1E1B18),
    surfaceVariant = Color(0xFFE6E0D6),
    onSurfaceVariant = Color(0xFF5D564E),
    surfaceContainerLowest = Color(0xFFFFFDFA),
    surfaceContainerLow = Color(0xFFF2EEE7),
    surfaceContainer = Color(0xFFECE7DF),
    surfaceContainerHigh = Color(0xFFE4DED4),
    surfaceContainerHighest = Color(0xFFDBD4C8),
    inverseSurface = Color(0xFF2D2A26),
    inverseOnSurface = Color(0xFFF5F1EB),
    inversePrimary = Color(0xFFB6C4FF),
    outline = Color(0xFF847C73),
    outlineVariant = Color(0xFFDAD3C8),
    scrim = Color(0xFF0F0D0B)
)

internal val AllNotesDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB6C4FF),
    onPrimary = Color(0xFF0E2378),
    primaryContainer = Color(0xFF25357A),
    onPrimaryContainer = Color(0xFFE1E6FF),
    secondary = Color(0xFFCFC6BA),
    onSecondary = Color(0xFF352F28),
    secondaryContainer = Color(0xFF433D35),
    onSecondaryContainer = Color(0xFFEFE8DF),
    tertiary = Color(0xFF7FD7C7),
    onTertiary = Color(0xFF003730),
    tertiaryContainer = Color(0xFF0D5247),
    onTertiaryContainer = Color(0xFFC5EFE6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFCE2DF),
    background = Color(0xFF121110),
    onBackground = Color(0xFFEFEBE4),
    surface = Color(0xFF121110),
    onSurface = Color(0xFFEFEBE4),
    surfaceVariant = Color(0xFF3D3833),
    onSurfaceVariant = Color(0xFFB3ACA1),
    surfaceContainerLowest = Color(0xFF171614),
    surfaceContainerLow = Color(0xFF1A1917),
    surfaceContainer = Color(0xFF22201D),
    surfaceContainerHigh = Color(0xFF2B2824),
    surfaceContainerHighest = Color(0xFF35312C),
    inverseSurface = Color(0xFFEFEBE4),
    inverseOnSurface = Color(0xFF2B2824),
    inversePrimary = Color(0xFF34469E),
    outline = Color(0xFF8C847A),
    outlineVariant = Color(0xFF3A3530),
    scrim = Color(0xFF000000)
)

@Immutable
data class AccentPalette(
    val container: Color,
    val onContainer: Color,
    val accent: Color,
    val ambientWash: Color
)

private val LocalIsDarkTheme = staticCompositionLocalOf { false }

private fun SectionAccentKey.resolvePalette(isDark: Boolean): AccentPalette = when (this) {
    SectionAccentKey.INDIGO -> if (isDark) {
        AccentPalette(
            container = Color(0xFF243375),
            onContainer = Color(0xFFE1E6FF),
            accent = Color(0xFF9FB0FF),
            ambientWash = Color(0xFF34469E).copy(alpha = 0.14f)
        )
    } else {
        AccentPalette(
            container = Color(0xFFE1E6FF),
            onContainer = Color(0xFF10206B),
            accent = Color(0xFF3949AB),
            ambientWash = Color(0xFF3949AB).copy(alpha = 0.065f)
        )
    }
    SectionAccentKey.EMERALD -> if (isDark) {
        AccentPalette(
            container = Color(0xFF104935),
            onContainer = Color(0xFFBDF5DB),
            accent = Color(0xFF4ADEA0),
            ambientWash = Color(0xFF10855B).copy(alpha = 0.14f)
        )
    } else {
        AccentPalette(
            container = Color(0xFFD6F5E7),
            onContainer = Color(0xFF08422F),
            accent = Color(0xFF0F7B58),
            ambientWash = Color(0xFF0F7B58).copy(alpha = 0.065f)
        )
    }
    SectionAccentKey.AMBER -> if (isDark) {
        AccentPalette(
            container = Color(0xFF5A390E),
            onContainer = Color(0xFFFDE7B8),
            accent = Color(0xFFF5B84B),
            ambientWash = Color(0xFFC47A16).copy(alpha = 0.15f)
        )
    } else {
        AccentPalette(
            container = Color(0xFFFAE8C5),
            onContainer = Color(0xFF5A3406),
            accent = Color(0xFFB86B11),
            ambientWash = Color(0xFFB86B11).copy(alpha = 0.075f)
        )
    }
    SectionAccentKey.ROSE -> if (isDark) {
        AccentPalette(
            container = Color(0xFF631E32),
            onContainer = Color(0xFFFFDCE5),
            accent = Color(0xFFF57C96),
            ambientWash = Color(0xFFBF3055).copy(alpha = 0.14f)
        )
    } else {
        AccentPalette(
            container = Color(0xFFFBE0E6),
            onContainer = Color(0xFF6B142C),
            accent = Color(0xFFC22D54),
            ambientWash = Color(0xFFC22D54).copy(alpha = 0.065f)
        )
    }
    SectionAccentKey.TEAL -> if (isDark) {
        AccentPalette(
            container = Color(0xFF0E4A54),
            onContainer = Color(0xFFC5F4FA),
            accent = Color(0xFF48D1DF),
            ambientWash = Color(0xFF0E808F).copy(alpha = 0.14f)
        )
    } else {
        AccentPalette(
            container = Color(0xFFD0F2F6),
            onContainer = Color(0xFF09424A),
            accent = Color(0xFF0E7C8A),
            ambientWash = Color(0xFF0E7C8A).copy(alpha = 0.065f)
        )
    }
    SectionAccentKey.VIOLET -> if (isDark) {
        AccentPalette(
            container = Color(0xFF43276E),
            onContainer = Color(0xFFECE2FF),
            accent = Color(0xFFB799FA),
            ambientWash = Color(0xFF7445C2).copy(alpha = 0.14f)
        )
    } else {
        AccentPalette(
            container = Color(0xFFEBE2FD),
            onContainer = Color(0xFF3E1C78),
            accent = Color(0xFF6D40BD),
            ambientWash = Color(0xFF6D40BD).copy(alpha = 0.065f)
        )
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
fun SectionAccentKey.ambientWashColor(): Color = resolvePalette(LocalIsDarkTheme.current).ambientWash

@Composable
internal expect fun resolvePlatformColorScheme(darkTheme: Boolean, useDynamicColor: Boolean): ColorScheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AllNotesTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColor: Boolean = false,
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
    val (displaySerif, uiSans, mono) = rememberEditorialFonts()
    val typography = remember(displaySerif, uiSans) {
        buildAllNotesTypography(displaySerif = displaySerif, uiSans = uiSans)
    }

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalAllNotesMonoFont provides mono
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = AllNotesShapes,
            typography = typography,
            content = content
        )
    }
}

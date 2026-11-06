package app.overbay.allnotes.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.overbay.allnotes.model.SectionAccentKey

private val LightColors = lightColorScheme(
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

private val DarkColors = darkColorScheme(
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

fun <T> allNotesSpring() = spring<T>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessLow
)

@Composable
fun SectionAccentKey.containerColor(): Color {
    val scheme: ColorScheme = MaterialTheme.colorScheme
    return when (this) {
        SectionAccentKey.INDIGO -> scheme.primaryContainer
        SectionAccentKey.EMERALD -> scheme.tertiaryContainer
        SectionAccentKey.AMBER -> scheme.secondaryContainer
        SectionAccentKey.ROSE -> scheme.errorContainer
        SectionAccentKey.TEAL -> scheme.tertiaryContainer
        SectionAccentKey.VIOLET -> scheme.primaryContainer
    }
}

@Composable
fun SectionAccentKey.contentColor(): Color {
    val scheme: ColorScheme = MaterialTheme.colorScheme
    return when (this) {
        SectionAccentKey.INDIGO -> scheme.onPrimaryContainer
        SectionAccentKey.EMERALD -> scheme.onTertiaryContainer
        SectionAccentKey.AMBER -> scheme.onSecondaryContainer
        SectionAccentKey.ROSE -> scheme.onErrorContainer
        SectionAccentKey.TEAL -> scheme.onTertiaryContainer
        SectionAccentKey.VIOLET -> scheme.onPrimaryContainer
    }
}

@Composable
fun SectionAccentKey.accentColor(): Color {
    val scheme: ColorScheme = MaterialTheme.colorScheme
    return when (this) {
        SectionAccentKey.INDIGO -> scheme.primary
        SectionAccentKey.EMERALD -> scheme.tertiary
        SectionAccentKey.AMBER -> scheme.secondary
        SectionAccentKey.ROSE -> scheme.error
        SectionAccentKey.TEAL -> scheme.inversePrimary
        SectionAccentKey.VIOLET -> scheme.primary
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AllNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}

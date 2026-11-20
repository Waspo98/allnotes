package app.overbay.allnotes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.PlatformTextStyle

@Composable
internal actual fun resolvePlatformColorScheme(darkTheme: Boolean, useDynamicColor: Boolean): ColorScheme =
    if (darkTheme) AllNotesDarkColorScheme else AllNotesLightColorScheme

internal actual fun resolvePlatformTextStyle(): PlatformTextStyle? = null

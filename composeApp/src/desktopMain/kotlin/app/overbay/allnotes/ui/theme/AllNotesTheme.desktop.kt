package app.overbay.allnotes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
internal actual fun resolvePlatformColorScheme(darkTheme: Boolean, useDynamicColor: Boolean): ColorScheme =
    if (darkTheme) AllNotesDarkColorScheme else AllNotesLightColorScheme

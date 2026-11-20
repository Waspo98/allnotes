package app.overbay.allnotes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.FontHinting
import androidx.compose.ui.text.FontRasterizationSettings
import androidx.compose.ui.text.FontSmoothing
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle

@Composable
internal actual fun resolvePlatformColorScheme(darkTheme: Boolean, useDynamicColor: Boolean): ColorScheme =
    if (darkTheme) AllNotesDarkColorScheme else AllNotesLightColorScheme

/**
 * Skiko Web renders onto a grayscale (PixelGeometry.UNKNOWN) WebGL surface and our bundled
 * TTF fonts carry no bytecode hinting tables, so enabling full auto-hinting without subpixel
 * horizontal stem offsets prevents blurry stems at desktop DPIs.
 */
@OptIn(ExperimentalTextApi::class)
internal actual fun resolvePlatformTextStyle(): PlatformTextStyle? = PlatformTextStyle(
    spanStyle = null,
    paragraphStyle = PlatformParagraphStyle(
        fontRasterizationSettings = FontRasterizationSettings(
            smoothing = FontSmoothing.AntiAlias,
            hinting = FontHinting.Full,
            subpixelPositioning = false,
            autoHintingForced = true
        )
    )
)

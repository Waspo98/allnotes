package app.overbay.allnotes.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.allnotes.resources.Res
import app.overbay.allnotes.resources.jetbrains_mono_regular
import app.overbay.allnotes.resources.lora_bold
import app.overbay.allnotes.resources.lora_semibold
import app.overbay.allnotes.resources.plus_jakarta_bold
import app.overbay.allnotes.resources.plus_jakarta_medium
import app.overbay.allnotes.resources.plus_jakarta_regular
import app.overbay.allnotes.resources.plus_jakarta_semibold
import org.jetbrains.compose.resources.Font

val LocalAllNotesMonoFont = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

val AllNotesShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
internal fun rememberEditorialFonts(): Triple<FontFamily, FontFamily, FontFamily> {
    val loraSemiBold = Font(Res.font.lora_semibold, weight = FontWeight.SemiBold)
    val loraBold = Font(Res.font.lora_bold, weight = FontWeight.Bold)
    val jakartaRegular = Font(Res.font.plus_jakarta_regular, weight = FontWeight.Normal)
    val jakartaMedium = Font(Res.font.plus_jakarta_medium, weight = FontWeight.Medium)
    val jakartaSemiBold = Font(Res.font.plus_jakarta_semibold, weight = FontWeight.SemiBold)
    val jakartaBold = Font(Res.font.plus_jakarta_bold, weight = FontWeight.Bold)
    val jetBrainsMono = Font(Res.font.jetbrains_mono_regular, weight = FontWeight.Normal)

    return remember(
        loraSemiBold, loraBold,
        jakartaRegular, jakartaMedium, jakartaSemiBold, jakartaBold,
        jetBrainsMono
    ) {
        val displaySerif = FontFamily(loraSemiBold, loraBold)
        val uiSans = FontFamily(jakartaRegular, jakartaMedium, jakartaSemiBold, jakartaBold)
        val mono = FontFamily(jetBrainsMono)
        Triple(displaySerif, uiSans, mono)
    }
}

internal fun buildAllNotesTypography(
    displaySerif: FontFamily,
    uiSans: FontFamily
): Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = displaySerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = displaySerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = displaySerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = uiSans,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.15).sp
    ),
    titleLarge = TextStyle(
        fontFamily = uiSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = uiSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = uiSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.05.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = uiSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.05.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = uiSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = uiSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.15.sp
    ),
    labelLarge = TextStyle(
        fontFamily = uiSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = uiSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp
    ),
    labelSmall = TextStyle(
        fontFamily = uiSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.35.sp
    )
)

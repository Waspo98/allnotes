package app.overbay.allnotes.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Extended optically balanced 24x24 rounded vector icons for studio formatting,
 * callout semantics, attachments, Markdown I/O, and hierarchy controls.
 */
internal object AllNotesStudioIcons {

    private fun stroked(
        name: String,
        strokeWidth: Float = 1.85f,
        block: PathBuilder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block
        )
    }.build()

    private fun filled(
        name: String,
        block: PathBuilder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathBuilder = block
        )
    }.build()

    val PinFilled: ImageVector by lazy {
        filled("PinFilled") {
            moveTo(15.2f, 3.6f)
            lineTo(20.4f, 8.8f)
            lineTo(18.8f, 10.4f)
            lineTo(17.1f, 9.8f)
            lineTo(13.7f, 13.2f)
            lineTo(14.5f, 16.2f)
            lineTo(12.9f, 17.8f)
            lineTo(9.4f, 14.3f)
            lineTo(5.1f, 18.6f)
            lineTo(3.9f, 19.8f)
            lineTo(4.2f, 18.4f)
            lineTo(5.4f, 17.2f)
            lineTo(9.7f, 12.9f)
            lineTo(6.2f, 9.4f)
            lineTo(7.8f, 7.8f)
            lineTo(10.8f, 8.6f)
            lineTo(14.2f, 5.2f)
            lineTo(13.6f, 3.6f)
            close()
        }
    }

    val Link: ImageVector by lazy {
        stroked("Link") {
            moveTo(10.2f, 13.8f)
            arcTo(4.5f, 4.5f, 0f, false, false, 16.6f, 13.8f)
            lineTo(18.8f, 11.6f)
            arcTo(4.5f, 4.5f, 0f, false, false, 12.4f, 5.2f)
            lineTo(11.1f, 6.5f)
            moveTo(13.8f, 10.2f)
            arcTo(4.5f, 4.5f, 0f, false, false, 7.4f, 10.2f)
            lineTo(5.2f, 12.4f)
            arcTo(4.5f, 4.5f, 0f, false, false, 11.6f, 18.8f)
            lineTo(12.9f, 17.5f)
        }
    }

    val Undo: ImageVector by lazy {
        stroked("Undo") {
            moveTo(8.5f, 13.5f)
            lineTo(4.25f, 9.25f)
            lineTo(8.5f, 5f)
            moveTo(4.5f, 9.25f)
            lineTo(14.75f, 9.25f)
            arcTo(4.75f, 4.75f, 0f, false, true, 14.75f, 18.75f)
            lineTo(11f, 18.75f)
        }
    }

    val Redo: ImageVector by lazy {
        stroked("Redo") {
            moveTo(15.5f, 13.5f)
            lineTo(19.75f, 9.25f)
            lineTo(15.5f, 5f)
            moveTo(19.5f, 9.25f)
            lineTo(9.25f, 9.25f)
            arcTo(4.75f, 4.75f, 0f, false, false, 9.25f, 18.75f)
            lineTo(13f, 18.75f)
        }
    }

    val Attach: ImageVector by lazy {
        stroked("Attach") {
            moveTo(20.6f, 11.2f)
            lineTo(12f, 19.8f)
            arcTo(5.25f, 5.25f, 0f, false, true, 4.6f, 12.4f)
            lineTo(13.2f, 3.8f)
            arcTo(3.5f, 3.5f, 0f, false, true, 18.15f, 8.75f)
            lineTo(9.8f, 17.1f)
            arcTo(1.75f, 1.75f, 0f, false, true, 7.3f, 14.6f)
            lineTo(15.1f, 6.8f)
        }
    }

    val Image: ImageVector by lazy {
        stroked("Image") {
            moveTo(5.5f, 4.5f)
            lineTo(18.5f, 4.5f)
            arcTo(2f, 2f, 0f, false, true, 20.5f, 6.5f)
            lineTo(20.5f, 17.5f)
            arcTo(2f, 2f, 0f, false, true, 18.5f, 19.5f)
            lineTo(5.5f, 19.5f)
            arcTo(2f, 2f, 0f, false, true, 3.5f, 17.5f)
            lineTo(3.5f, 6.5f)
            arcTo(2f, 2f, 0f, false, true, 5.5f, 4.5f)
            close()
            moveTo(8.75f, 9.25f)
            arcTo(1.25f, 1.25f, 0f, true, true, 8.74f, 9.25f)
            close()
            moveTo(20.2f, 15.8f)
            lineTo(15.5f, 11.1f)
            arcTo(1.25f, 1.25f, 0f, false, false, 13.7f, 11.1f)
            lineTo(6.2f, 19.3f)
        }
    }

    val Divider: ImageVector by lazy {
        stroked("Divider") {
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(7.5f, 7f)
            lineTo(16.5f, 7f)
            moveTo(7.5f, 17f)
            lineTo(16.5f, 17f)
        }
    }

    val Copy: ImageVector by lazy {
        stroked("Copy") {
            moveTo(10f, 8.5f)
            lineTo(18.25f, 8.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 19.75f, 10f)
            lineTo(19.75f, 18.25f)
            arcTo(1.5f, 1.5f, 0f, false, true, 18.25f, 19.75f)
            lineTo(10f, 19.75f)
            arcTo(1.5f, 1.5f, 0f, false, true, 8.5f, 18.25f)
            lineTo(8.5f, 10f)
            arcTo(1.5f, 1.5f, 0f, false, true, 10f, 8.5f)
            close()
            moveTo(5.75f, 15.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 4.25f, 14f)
            lineTo(4.25f, 5.75f)
            arcTo(1.5f, 1.5f, 0f, false, true, 5.75f, 4.25f)
            lineTo(14f, 4.25f)
            arcTo(1.5f, 1.5f, 0f, false, true, 15.5f, 5.75f)
        }
    }

    val Settings: ImageVector by lazy {
        stroked("Settings") {
            moveTo(12f, 8.75f)
            arcTo(3.25f, 3.25f, 0f, true, true, 11.99f, 8.75f)
            close()
            moveTo(12f, 3.5f)
            lineTo(12f, 5.25f)
            moveTo(12f, 18.75f)
            lineTo(12f, 20.5f)
            moveTo(5.8f, 5.8f)
            lineTo(7.05f, 7.05f)
            moveTo(16.95f, 16.95f)
            lineTo(18.2f, 18.2f)
            moveTo(3.5f, 12f)
            lineTo(5.25f, 12f)
            moveTo(18.75f, 12f)
            lineTo(20.5f, 12f)
            moveTo(5.8f, 18.2f)
            lineTo(7.05f, 16.95f)
            moveTo(16.95f, 7.05f)
            lineTo(18.2f, 5.8f)
        }
    }

    val Markdown: ImageVector by lazy {
        stroked("Markdown") {
            moveTo(4.75f, 6.5f)
            lineTo(19.25f, 6.5f)
            arcTo(1.75f, 1.75f, 0f, false, true, 21f, 8.25f)
            lineTo(21f, 15.75f)
            arcTo(1.75f, 1.75f, 0f, false, true, 19.25f, 17.5f)
            lineTo(4.75f, 17.5f)
            arcTo(1.75f, 1.75f, 0f, false, true, 3f, 15.75f)
            lineTo(3f, 8.25f)
            arcTo(1.75f, 1.75f, 0f, false, true, 4.75f, 6.5f)
            close()
            moveTo(6.25f, 14.25f)
            lineTo(6.25f, 9.75f)
            lineTo(8.75f, 12.25f)
            lineTo(11.25f, 9.75f)
            lineTo(11.25f, 14.25f)
            moveTo(16.25f, 9.75f)
            lineTo(16.25f, 14.25f)
            moveTo(14.25f, 12.25f)
            lineTo(16.25f, 14.25f)
            lineTo(18.25f, 12.25f)
        }
    }

    val ChevronRight: ImageVector by lazy {
        stroked("ChevronRight") {
            moveTo(9.25f, 6.25f)
            lineTo(15f, 12f)
            lineTo(9.25f, 17.75f)
        }
    }

    val ChevronDown: ImageVector by lazy {
        stroked("ChevronDown") {
            moveTo(6.25f, 9.25f)
            lineTo(12f, 15f)
            lineTo(17.75f, 9.25f)
        }
    }

    val Palette: ImageVector by lazy {
        stroked("Palette") {
            moveTo(12f, 3.5f)
            arcTo(8.5f, 8.5f, 0f, true, false, 12f, 20.5f)
            arcTo(2.25f, 2.25f, 0f, false, false, 14.25f, 18.25f)
            arcTo(1.75f, 1.75f, 0f, false, true, 16f, 16.5f)
            lineTo(17.75f, 16.5f)
            arcTo(2.75f, 2.75f, 0f, false, false, 20.5f, 13.75f)
            arcTo(8.5f, 8.5f, 0f, false, false, 12f, 3.5f)
            close()
            moveTo(7.75f, 11.25f)
            lineTo(7.85f, 11.25f)
            moveTo(9.75f, 7.75f)
            lineTo(9.85f, 7.75f)
            moveTo(14.25f, 7.75f)
            lineTo(14.35f, 7.75f)
        }
    }

    val FolderMove: ImageVector by lazy {
        stroked("FolderMove") {
            moveTo(3.5f, 6.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 5f, 5f)
            lineTo(9.25f, 5f)
            lineTo(11.25f, 7.25f)
            lineTo(19f, 7.25f)
            arcTo(1.5f, 1.5f, 0f, false, true, 20.5f, 8.75f)
            lineTo(20.5f, 17.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 19f, 19f)
            lineTo(5f, 19f)
            arcTo(1.5f, 1.5f, 0f, false, true, 3.5f, 17.5f)
            close()
            moveTo(9.5f, 13.25f)
            lineTo(14.75f, 13.25f)
            moveTo(12.75f, 11.25f)
            lineTo(14.75f, 13.25f)
            lineTo(12.75f, 15.25f)
        }
    }

    val Check: ImageVector by lazy {
        stroked("Check") {
            moveTo(5.25f, 12.5f)
            lineTo(9.75f, 17f)
            lineTo(18.75f, 7.5f)
        }
    }

    val ClearFormatting: ImageVector by lazy {
        stroked("ClearFormatting") {
            moveTo(6.5f, 5.5f)
            lineTo(18.5f, 5.5f)
            moveTo(12.75f, 5.5f)
            lineTo(11.35f, 11.25f)
            moveTo(10.15f, 15.5f)
            lineTo(9.25f, 19f)
            moveTo(4.75f, 4.75f)
            lineTo(19.25f, 19.25f)
        }
    }

    val Lightbulb: ImageVector by lazy {
        stroked("Lightbulb") {
            moveTo(9.25f, 17.5f)
            lineTo(14.75f, 17.5f)
            moveTo(10f, 20.25f)
            lineTo(14f, 20.25f)
            moveTo(9.5f, 14.5f)
            curveTo(7.5f, 13.3f, 6.5f, 11.5f, 6.5f, 9.5f)
            arcTo(5.5f, 5.5f, 0f, true, true, 17.5f, 9.5f)
            curveTo(17.5f, 11.5f, 16.5f, 13.3f, 14.5f, 14.5f)
            lineTo(14.25f, 16f)
            lineTo(9.75f, 16f)
            close()
        }
    }

    val Spark: ImageVector by lazy {
        stroked("Spark") {
            moveTo(12f, 3.5f)
            curveTo(12.6f, 8.2f, 15.8f, 11.4f, 20.5f, 12f)
            curveTo(15.8f, 12.6f, 12.6f, 15.8f, 12f, 20.5f)
            curveTo(11.4f, 15.8f, 8.2f, 12.6f, 3.5f, 12f)
            curveTo(8.2f, 11.4f, 11.4f, 8.2f, 12f, 3.5f)
            close()
        }
    }

    val EditNote: ImageVector by lazy {
        stroked("EditNote") {
            moveTo(11f, 4.75f)
            lineTo(6.25f, 4.75f)
            arcTo(2f, 2f, 0f, false, false, 4.25f, 6.75f)
            lineTo(4.25f, 17.75f)
            arcTo(2f, 2f, 0f, false, false, 6.25f, 19.75f)
            lineTo(17.25f, 19.75f)
            arcTo(2f, 2f, 0f, false, false, 19.25f, 17.75f)
            lineTo(19.25f, 13f)
            moveTo(16.75f, 4.25f)
            arcTo(1.8f, 1.8f, 0f, false, true, 19.3f, 6.8f)
            lineTo(12.2f, 13.9f)
            lineTo(9.25f, 14.75f)
            lineTo(10.1f, 11.8f)
            close()
        }
    }

    val GridApps: ImageVector by lazy {
        stroked("GridApps") {
            moveTo(5.5f, 4.5f)
            lineTo(9.25f, 4.5f)
            arcTo(1f, 1f, 0f, false, true, 10.25f, 5.5f)
            lineTo(10.25f, 9.25f)
            arcTo(1f, 1f, 0f, false, true, 9.25f, 10.25f)
            lineTo(5.5f, 10.25f)
            arcTo(1f, 1f, 0f, false, true, 4.5f, 9.25f)
            lineTo(4.5f, 5.5f)
            arcTo(1f, 1f, 0f, false, true, 5.5f, 4.5f)
            close()
            moveTo(14.75f, 4.5f)
            lineTo(18.5f, 4.5f)
            arcTo(1f, 1f, 0f, false, true, 19.5f, 5.5f)
            lineTo(19.5f, 9.25f)
            arcTo(1f, 1f, 0f, false, true, 18.5f, 10.25f)
            lineTo(14.75f, 10.25f)
            arcTo(1f, 1f, 0f, false, true, 13.75f, 9.25f)
            lineTo(13.75f, 5.5f)
            arcTo(1f, 1f, 0f, false, true, 14.75f, 4.5f)
            close()
            moveTo(5.5f, 13.75f)
            lineTo(9.25f, 13.75f)
            arcTo(1f, 1f, 0f, false, true, 10.25f, 14.75f)
            lineTo(10.25f, 18.5f)
            arcTo(1f, 1f, 0f, false, true, 9.25f, 19.5f)
            lineTo(5.5f, 19.5f)
            arcTo(1f, 1f, 0f, false, true, 4.5f, 18.5f)
            lineTo(4.5f, 14.75f)
            arcTo(1f, 1f, 0f, false, true, 5.5f, 13.75f)
            close()
            moveTo(14.75f, 13.75f)
            lineTo(18.5f, 13.75f)
            arcTo(1f, 1f, 0f, false, true, 19.5f, 14.75f)
            lineTo(19.5f, 18.5f)
            arcTo(1f, 1f, 0f, false, true, 18.5f, 19.5f)
            lineTo(14.75f, 19.5f)
            arcTo(1f, 1f, 0f, false, true, 13.75f, 18.5f)
            lineTo(13.75f, 14.75f)
            arcTo(1f, 1f, 0f, false, true, 14.75f, 13.75f)
            close()
        }
    }
}

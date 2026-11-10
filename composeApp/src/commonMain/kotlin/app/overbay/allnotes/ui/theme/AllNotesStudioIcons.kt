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
 * Extended vector icons for rich span editing, undo/redo, attachments,
 * Markdown export/import, and studio navigation.
 */
internal object AllNotesStudioIcons {

    private fun stroked(name: String, block: PathBuilder.() -> Unit): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block
            )
        }.build()
    }

    val Link: ImageVector by lazy {
        stroked("Link") {
            moveTo(10f, 13f)
            arcTo(5f, 5f, 0f, false, false, 17.07f, 13f)
            lineTo(19.07f, 11f)
            arcTo(5f, 5f, 0f, false, false, 12f, 3.93f)
            lineTo(10.8f, 5.13f)
            moveTo(14f, 11f)
            arcTo(5f, 5f, 0f, false, false, 6.93f, 11f)
            lineTo(4.93f, 13f)
            arcTo(5f, 5f, 0f, false, false, 12f, 20.07f)
            lineTo(13.2f, 18.87f)
        }
    }

    val Undo: ImageVector by lazy {
        stroked("Undo") {
            moveTo(9f, 14f)
            lineTo(4f, 9f)
            lineTo(9f, 4f)
            moveTo(4f, 9f)
            lineTo(15f, 9f)
            arcTo(5f, 5f, 0f, false, true, 15f, 19f)
            lineTo(11f, 19f)
        }
    }

    val Redo: ImageVector by lazy {
        stroked("Redo") {
            moveTo(15f, 14f)
            lineTo(20f, 9f)
            lineTo(15f, 4f)
            moveTo(20f, 9f)
            lineTo(9f, 9f)
            arcTo(5f, 5f, 0f, false, false, 9f, 19f)
            lineTo(13f, 19f)
        }
    }

    val Attach: ImageVector by lazy {
        stroked("Attach") {
            moveTo(21.44f, 11.05f)
            lineTo(12.25f, 20.24f)
            arcTo(6f, 6f, 0f, false, true, 3.76f, 11.75f)
            lineTo(12.95f, 2.56f)
            arcTo(4f, 4f, 0f, false, true, 18.61f, 8.22f)
            lineTo(9.41f, 17.41f)
            arcTo(2f, 2f, 0f, false, true, 6.59f, 14.59f)
            lineTo(15.07f, 6.1f)
        }
    }

    val Image: ImageVector by lazy {
        stroked("Image") {
            moveTo(4f, 5f)
            lineTo(20f, 5f)
            lineTo(20f, 19f)
            lineTo(4f, 19f)
            close()
            moveTo(8.5f, 9.5f)
            lineTo(8.51f, 9.5f)
            moveTo(20f, 15f)
            lineTo(15f, 10f)
            lineTo(6f, 19f)
        }
    }

    val Divider: ImageVector by lazy {
        stroked("Divider") {
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(8f, 7f)
            lineTo(16f, 7f)
            moveTo(8f, 17f)
            lineTo(16f, 17f)
        }
    }

    val Copy: ImageVector by lazy {
        stroked("Copy") {
            moveTo(9f, 9f)
            lineTo(19f, 9f)
            lineTo(19f, 19f)
            lineTo(9f, 19f)
            close()
            moveTo(5f, 15f)
            lineTo(5f, 5f)
            lineTo(15f, 5f)
        }
    }

    val Settings: ImageVector by lazy {
        stroked("Settings") {
            moveTo(12f, 9f)
            arcTo(3f, 3f, 0f, true, true, 11.99f, 9f)
            close()
            moveTo(12f, 3f)
            lineTo(12f, 5f)
            moveTo(12f, 19f)
            lineTo(12f, 21f)
            moveTo(4.93f, 4.93f)
            lineTo(6.34f, 6.34f)
            moveTo(17.66f, 17.66f)
            lineTo(19.07f, 19.07f)
            moveTo(3f, 12f)
            lineTo(5f, 12f)
            moveTo(19f, 12f)
            lineTo(21f, 12f)
            moveTo(4.93f, 19.07f)
            lineTo(6.34f, 17.66f)
            moveTo(17.66f, 6.34f)
            lineTo(19.07f, 4.93f)
        }
    }

    val Markdown: ImageVector by lazy {
        stroked("Markdown") {
            moveTo(3f, 7f)
            lineTo(21f, 7f)
            lineTo(21f, 17f)
            lineTo(3f, 17f)
            close()
            moveTo(6f, 14f)
            lineTo(6f, 10f)
            lineTo(8.5f, 12.5f)
            lineTo(11f, 10f)
            lineTo(11f, 14f)
            moveTo(16f, 10f)
            lineTo(16f, 14f)
            moveTo(14f, 12.2f)
            lineTo(16f, 14.2f)
            lineTo(18f, 12.2f)
        }
    }

    val ChevronRight: ImageVector by lazy {
        stroked("ChevronRight") {
            moveTo(9f, 6f)
            lineTo(15f, 12f)
            lineTo(9f, 18f)
        }
    }

    val ChevronDown: ImageVector by lazy {
        stroked("ChevronDown") {
            moveTo(6f, 9f)
            lineTo(12f, 15f)
            lineTo(18f, 9f)
        }
    }

    val Palette: ImageVector by lazy {
        stroked("Palette") {
            moveTo(12f, 3f)
            arcTo(9f, 9f, 0f, true, false, 12f, 21f)
            arcTo(2.5f, 2.5f, 0f, false, false, 14.5f, 18.5f)
            arcTo(2f, 2f, 0f, false, true, 16.5f, 16.5f)
            lineTo(18f, 16.5f)
            arcTo(3f, 3f, 0f, false, false, 21f, 13.5f)
            arcTo(9f, 9f, 0f, false, false, 12f, 3f)
        }
    }

    val FolderMove: ImageVector by lazy {
        stroked("FolderMove") {
            moveTo(3f, 6f)
            lineTo(9f, 6f)
            lineTo(11f, 8f)
            lineTo(21f, 8f)
            lineTo(21f, 19f)
            lineTo(3f, 19f)
            close()
            moveTo(10f, 13.5f)
            lineTo(15f, 13.5f)
            moveTo(13f, 11.5f)
            lineTo(15f, 13.5f)
            lineTo(13f, 15.5f)
        }
    }

    val Check: ImageVector by lazy {
        stroked("Check") {
            moveTo(5f, 12.5f)
            lineTo(9.5f, 17f)
            lineTo(19f, 7.5f)
        }
    }
}

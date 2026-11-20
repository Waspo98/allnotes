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
 * Optically balanced 24x24 rounded vector iconography for AllNotes across
 * Android, Windows Desktop, and Web Wasm.
 */
object AllNotesIcons {

    private fun strokedIcon(
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

    private fun filledIcon(
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

    val Sidebar: ImageVector by lazy {
        strokedIcon("Sidebar") {
            moveTo(5.5f, 4f)
            lineTo(18.5f, 4f)
            arcTo(2f, 2f, 0f, false, true, 20.5f, 6f)
            lineTo(20.5f, 18f)
            arcTo(2f, 2f, 0f, false, true, 18.5f, 20f)
            lineTo(5.5f, 20f)
            arcTo(2f, 2f, 0f, false, true, 3.5f, 18f)
            lineTo(3.5f, 6f)
            arcTo(2f, 2f, 0f, false, true, 5.5f, 4f)
            close()
            moveTo(9.25f, 4f)
            lineTo(9.25f, 20f)
        }
    }

    val Menu: ImageVector by lazy {
        strokedIcon("Menu") {
            moveTo(4f, 6.75f)
            lineTo(20f, 6.75f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 17.25f)
            lineTo(16f, 17.25f)
        }
    }

    val MoreVert: ImageVector by lazy {
        filledIcon("MoreVert") {
            moveTo(12f, 6.5f)
            arcTo(1.5f, 1.5f, 0f, true, true, 12f, 3.5f)
            arcTo(1.5f, 1.5f, 0f, true, true, 12f, 6.5f)
            close()
            moveTo(12f, 13.5f)
            arcTo(1.5f, 1.5f, 0f, true, true, 12f, 10.5f)
            arcTo(1.5f, 1.5f, 0f, true, true, 12f, 13.5f)
            close()
            moveTo(12f, 20.5f)
            arcTo(1.5f, 1.5f, 0f, true, true, 12f, 17.5f)
            arcTo(1.5f, 1.5f, 0f, true, true, 12f, 20.5f)
            close()
        }
    }

    val FormatBold: ImageVector by lazy {
        strokedIcon("FormatBold", strokeWidth = 2.25f) {
            moveTo(7f, 5f)
            lineTo(13f, 5f)
            arcTo(3.25f, 3.25f, 0f, false, true, 13f, 11.5f)
            lineTo(7f, 11.5f)
            close()
            moveTo(7f, 11.5f)
            lineTo(14f, 11.5f)
            arcTo(3.75f, 3.75f, 0f, false, true, 14f, 19f)
            lineTo(7f, 19f)
            close()
        }
    }

    val FormatItalic: ImageVector by lazy {
        strokedIcon("FormatItalic") {
            moveTo(10.5f, 5f)
            lineTo(18f, 5f)
            moveTo(6f, 19f)
            lineTo(13.5f, 19f)
            moveTo(14.25f, 5f)
            lineTo(9.75f, 19f)
        }
    }

    val FormatUnderlined: ImageVector by lazy {
        strokedIcon("FormatUnderlined") {
            moveTo(7f, 4.5f)
            lineTo(7f, 11f)
            arcTo(5f, 5f, 0f, false, false, 17f, 11f)
            lineTo(17f, 4.5f)
            moveTo(5.5f, 19.5f)
            lineTo(18.5f, 19.5f)
        }
    }

    val FormatStrikethrough: ImageVector by lazy {
        strokedIcon("FormatStrikethrough") {
            moveTo(4.5f, 12f)
            lineTo(19.5f, 12f)
            moveTo(16f, 7.2f)
            curveTo(15.6f, 5.4f, 14f, 4.5f, 11.8f, 4.5f)
            curveTo(9.3f, 4.5f, 7.8f, 5.8f, 7.8f, 7.6f)
            curveTo(7.8f, 9f, 8.7f, 9.8f, 10.6f, 10.4f)
            moveTo(14.2f, 14.3f)
            curveTo(15.6f, 14.9f, 16.4f, 15.8f, 16.4f, 17f)
            curveTo(16.4f, 18.7f, 14.6f, 19.8f, 12f, 19.8f)
            curveTo(9.5f, 19.8f, 8f, 18.7f, 7.5f, 17.1f)
        }
    }

    val Highlight: ImageVector by lazy {
        strokedIcon("Highlight") {
            moveTo(15.2f, 4.3f)
            lineTo(19.7f, 8.8f)
            lineTo(10.8f, 17.7f)
            lineTo(6.3f, 17.7f)
            lineTo(6.3f, 13.2f)
            close()
            moveTo(12.8f, 6.7f)
            lineTo(17.3f, 11.2f)
            moveTo(4.5f, 20.5f)
            lineTo(19.5f, 20.5f)
        }
    }

    val NumberedList: ImageVector by lazy {
        strokedIcon("NumberedList") {
            moveTo(10.5f, 6.5f)
            lineTo(20f, 6.5f)
            moveTo(10.5f, 12f)
            lineTo(20f, 12f)
            moveTo(10.5f, 17.5f)
            lineTo(20f, 17.5f)
            // '1'
            moveTo(4.5f, 5.5f)
            lineTo(6f, 4.5f)
            lineTo(6f, 8.5f)
            // '2'
            moveTo(4.2f, 11.1f)
            curveTo(4.2f, 10.1f, 6.6f, 10.1f, 6.6f, 11.4f)
            curveTo(6.6f, 12.2f, 4.2f, 13.2f, 4.2f, 14f)
            lineTo(6.8f, 14f)
            // '3'
            moveTo(4.4f, 16.5f)
            lineTo(6.5f, 16.5f)
            lineTo(5.3f, 17.8f)
            curveTo(6.9f, 17.8f, 7f, 19.7f, 4.4f, 19.4f)
        }
    }

    val Search: ImageVector by lazy {
        strokedIcon("Search") {
            moveTo(11f, 4.25f)
            arcTo(6.75f, 6.75f, 0f, true, true, 10.99f, 4.25f)
            close()
            moveTo(19.75f, 19.75f)
            lineTo(15.85f, 15.85f)
        }
    }

    val Add: ImageVector by lazy {
        strokedIcon("Add") {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }

    val Delete: ImageVector by lazy {
        strokedIcon("Delete") {
            moveTo(4.5f, 6.75f)
            lineTo(19.5f, 6.75f)
            moveTo(9f, 6.75f)
            lineTo(9.5f, 4.5f)
            arcTo(1f, 1f, 0f, false, true, 10.5f, 3.75f)
            lineTo(13.5f, 3.75f)
            arcTo(1f, 1f, 0f, false, true, 14.5f, 4.5f)
            lineTo(15f, 6.75f)
            moveTo(6.5f, 6.75f)
            lineTo(7.4f, 18.6f)
            arcTo(1.75f, 1.75f, 0f, false, false, 9.15f, 20.25f)
            lineTo(14.85f, 20.25f)
            arcTo(1.75f, 1.75f, 0f, false, false, 16.6f, 18.6f)
            lineTo(17.5f, 6.75f)
            moveTo(10.25f, 10.5f)
            lineTo(10.25f, 16.25f)
            moveTo(13.75f, 10.5f)
            lineTo(13.75f, 16.25f)
        }
    }

    val Close: ImageVector by lazy {
        strokedIcon("Close") {
            moveTo(6.25f, 6.25f)
            lineTo(17.75f, 17.75f)
            moveTo(17.75f, 6.25f)
            lineTo(6.25f, 17.75f)
        }
    }

    val Edit: ImageVector by lazy {
        strokedIcon("Edit") {
            moveTo(14.8f, 5.2f)
            lineTo(18.8f, 9.2f)
            moveTo(16.6f, 3.4f)
            arcTo(2.1f, 2.1f, 0f, false, true, 19.6f, 6.4f)
            lineTo(8.5f, 17.5f)
            lineTo(4.25f, 18.75f)
            lineTo(5.5f, 14.5f)
            close()
        }
    }

    val Pin: ImageVector by lazy {
        strokedIcon("Pin") {
            moveTo(14.7f, 4.2f)
            lineTo(19.8f, 9.3f)
            lineTo(18.2f, 10.9f)
            lineTo(16.8f, 10.4f)
            lineTo(13.2f, 14f)
            lineTo(13.8f, 16.4f)
            lineTo(12.2f, 18f)
            lineTo(6f, 11.8f)
            lineTo(7.6f, 10.2f)
            lineTo(10f, 10.8f)
            lineTo(13.6f, 7.2f)
            lineTo(13.1f, 5.8f)
            close()
            moveTo(9.1f, 14.9f)
            lineTo(4.25f, 19.75f)
        }
    }

    val PinFilled: ImageVector get() = AllNotesStudioIcons.PinFilled

    val Sync: ImageVector by lazy {
        strokedIcon("Sync") {
            moveTo(4.5f, 12f)
            arcTo(7.5f, 7.5f, 0f, false, true, 17.5f, 6.9f)
            moveTo(17.75f, 3.75f)
            lineTo(17.75f, 7.25f)
            lineTo(14.25f, 7.25f)
            moveTo(19.5f, 12f)
            arcTo(7.5f, 7.5f, 0f, false, true, 6.5f, 17.1f)
            moveTo(6.25f, 20.25f)
            lineTo(6.25f, 16.75f)
            lineTo(9.75f, 16.75f)
        }
    }

    val ArrowUp: ImageVector by lazy {
        strokedIcon("ArrowUp") {
            moveTo(12f, 19f)
            lineTo(12f, 5.5f)
            moveTo(6.5f, 11f)
            lineTo(12f, 5.5f)
            lineTo(17.5f, 11f)
        }
    }

    val ArrowDown: ImageVector by lazy {
        strokedIcon("ArrowDown") {
            moveTo(12f, 5f)
            lineTo(12f, 18.5f)
            moveTo(6.5f, 13f)
            lineTo(12f, 18.5f)
            lineTo(17.5f, 13f)
        }
    }

    val Indent: ImageVector by lazy {
        strokedIcon("Indent") {
            moveTo(4f, 6f)
            lineTo(20f, 6f)
            moveTo(11f, 12f)
            lineTo(20f, 12f)
            moveTo(11f, 18f)
            lineTo(20f, 18f)
            moveTo(4.5f, 9.25f)
            lineTo(8f, 12f)
            lineTo(4.5f, 14.75f)
        }
    }

    val Outdent: ImageVector by lazy {
        strokedIcon("Outdent") {
            moveTo(4f, 6f)
            lineTo(20f, 6f)
            moveTo(11f, 12f)
            lineTo(20f, 12f)
            moveTo(11f, 18f)
            lineTo(20f, 18f)
            moveTo(8f, 9.25f)
            lineTo(4.5f, 12f)
            lineTo(8f, 14.75f)
        }
    }

    val Heading: ImageVector by lazy {
        strokedIcon("Heading") {
            moveTo(6.5f, 5f)
            lineTo(6.5f, 19f)
            moveTo(17.5f, 5f)
            lineTo(17.5f, 19f)
            moveTo(6.5f, 12f)
            lineTo(17.5f, 12f)
        }
    }

    val Paragraph: ImageVector by lazy {
        strokedIcon("Paragraph") {
            moveTo(4.5f, 7f)
            lineTo(19.5f, 7f)
            moveTo(4.5f, 12f)
            lineTo(19.5f, 12f)
            moveTo(4.5f, 17f)
            lineTo(13.5f, 17f)
        }
    }

    val Checklist: ImageVector by lazy {
        strokedIcon("Checklist") {
            moveTo(4.25f, 7.5f)
            lineTo(6.5f, 9.75f)
            lineTo(10.5f, 5.5f)
            moveTo(13.5f, 8f)
            lineTo(19.75f, 8f)
            moveTo(4.25f, 16f)
            lineTo(6.5f, 18.25f)
            lineTo(10.5f, 14f)
            moveTo(13.5f, 16.5f)
            lineTo(19.75f, 16.5f)
        }
    }

    val BulletList: ImageVector by lazy {
        strokedIcon("BulletList") {
            moveTo(9.5f, 6.5f)
            lineTo(19.75f, 6.5f)
            moveTo(9.5f, 12f)
            lineTo(19.75f, 12f)
            moveTo(9.5f, 17.5f)
            lineTo(19.75f, 17.5f)
            moveTo(5f, 6.5f)
            lineTo(5.15f, 6.5f)
            moveTo(5f, 12f)
            lineTo(5.15f, 12f)
            moveTo(5f, 17.5f)
            lineTo(5.15f, 17.5f)
        }
    }

    val Table: ImageVector by lazy {
        strokedIcon("Table") {
            moveTo(5.25f, 4.5f)
            lineTo(18.75f, 4.5f)
            arcTo(1.75f, 1.75f, 0f, false, true, 20.5f, 6.25f)
            lineTo(20.5f, 17.75f)
            arcTo(1.75f, 1.75f, 0f, false, true, 18.75f, 19.5f)
            lineTo(5.25f, 19.5f)
            arcTo(1.75f, 1.75f, 0f, false, true, 3.5f, 17.75f)
            lineTo(3.5f, 6.25f)
            arcTo(1.75f, 1.75f, 0f, false, true, 5.25f, 4.5f)
            close()
            moveTo(3.5f, 9.75f)
            lineTo(20.5f, 9.75f)
            moveTo(3.5f, 14.75f)
            lineTo(20.5f, 14.75f)
            moveTo(9.75f, 4.5f)
            lineTo(9.75f, 19.5f)
        }
    }

    val Callout: ImageVector by lazy {
        strokedIcon("Callout") {
            moveTo(12f, 3.5f)
            arcTo(8.5f, 8.5f, 0f, true, true, 11.99f, 3.5f)
            close()
            moveTo(12f, 10.5f)
            lineTo(12f, 16f)
            moveTo(12f, 7.5f)
            lineTo(12.01f, 7.5f)
        }
    }

    val Code: ImageVector by lazy {
        strokedIcon("Code") {
            moveTo(8.25f, 8f)
            lineTo(4.25f, 12f)
            lineTo(8.25f, 16f)
            moveTo(15.75f, 8f)
            lineTo(19.75f, 12f)
            lineTo(15.75f, 16f)
            moveTo(13.25f, 5.75f)
            lineTo(10.75f, 18.25f)
        }
    }

    val Link: ImageVector get() = AllNotesStudioIcons.Link
    val Undo: ImageVector get() = AllNotesStudioIcons.Undo
    val Redo: ImageVector get() = AllNotesStudioIcons.Redo
    val Attach: ImageVector get() = AllNotesStudioIcons.Attach
    val Image: ImageVector get() = AllNotesStudioIcons.Image
    val Divider: ImageVector get() = AllNotesStudioIcons.Divider
    val Copy: ImageVector get() = AllNotesStudioIcons.Copy
    val Settings: ImageVector get() = AllNotesStudioIcons.Settings
    val Markdown: ImageVector get() = AllNotesStudioIcons.Markdown
    val ChevronRight: ImageVector get() = AllNotesStudioIcons.ChevronRight
    val ChevronDown: ImageVector get() = AllNotesStudioIcons.ChevronDown
    val Palette: ImageVector get() = AllNotesStudioIcons.Palette
    val FolderMove: ImageVector get() = AllNotesStudioIcons.FolderMove
    val Check: ImageVector get() = AllNotesStudioIcons.Check
    val ClearFormatting: ImageVector get() = AllNotesStudioIcons.ClearFormatting
    val Lightbulb: ImageVector get() = AllNotesStudioIcons.Lightbulb
    val Spark: ImageVector get() = AllNotesStudioIcons.Spark
    val EditNote: ImageVector get() = AllNotesStudioIcons.EditNote
    val GridApps: ImageVector get() = AllNotesStudioIcons.GridApps
}

package app.overbay.allnotes.ui.theme

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Optically balanced 24x24 rounded vector iconography for AllNotes across
 * Android, Windows Desktop, and Web Wasm.
 */
object AllNotesIcons {

    val Sidebar: ImageVector by lazy {
        strokedVectorIcon("Sidebar") {
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
        strokedVectorIcon("Menu") {
            moveTo(4f, 6.75f)
            lineTo(20f, 6.75f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 17.25f)
            lineTo(16f, 17.25f)
        }
    }

    val MoreVert: ImageVector by lazy {
        filledVectorIcon("MoreVert") {
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

    val Search: ImageVector by lazy {
        strokedVectorIcon("Search") {
            moveTo(11f, 4.25f)
            arcTo(6.75f, 6.75f, 0f, true, true, 10.99f, 4.25f)
            close()
            moveTo(19.75f, 19.75f)
            lineTo(15.85f, 15.85f)
        }
    }

    val Add: ImageVector by lazy {
        strokedVectorIcon("Add") {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }

    val Delete: ImageVector by lazy {
        strokedVectorIcon("Delete") {
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
        strokedVectorIcon("Close") {
            moveTo(6.25f, 6.25f)
            lineTo(17.75f, 17.75f)
            moveTo(17.75f, 6.25f)
            lineTo(6.25f, 17.75f)
        }
    }

    val Edit: ImageVector by lazy {
        strokedVectorIcon("Edit") {
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
        strokedVectorIcon("Pin") {
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

    val Sync: ImageVector by lazy {
        strokedVectorIcon("Sync") {
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
        strokedVectorIcon("ArrowUp") {
            moveTo(12f, 19f)
            lineTo(12f, 5.5f)
            moveTo(6.5f, 11f)
            lineTo(12f, 5.5f)
            lineTo(17.5f, 11f)
        }
    }

    val ArrowDown: ImageVector by lazy {
        strokedVectorIcon("ArrowDown") {
            moveTo(12f, 5f)
            lineTo(12f, 18.5f)
            moveTo(6.5f, 13f)
            lineTo(12f, 18.5f)
            lineTo(17.5f, 13f)
        }
    }

    val Indent: ImageVector by lazy {
        strokedVectorIcon("Indent") {
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
        strokedVectorIcon("Outdent") {
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

    val FormatBold: ImageVector get() = AllNotesEditorIcons.FormatBold
    val FormatItalic: ImageVector get() = AllNotesEditorIcons.FormatItalic
    val FormatUnderlined: ImageVector get() = AllNotesEditorIcons.FormatUnderlined
    val FormatStrikethrough: ImageVector get() = AllNotesEditorIcons.FormatStrikethrough
    val Highlight: ImageVector get() = AllNotesEditorIcons.Highlight
    val NumberedList: ImageVector get() = AllNotesEditorIcons.NumberedList
    val Heading: ImageVector get() = AllNotesEditorIcons.Heading
    val Paragraph: ImageVector get() = AllNotesEditorIcons.Paragraph
    val Checklist: ImageVector get() = AllNotesEditorIcons.Checklist
    val BulletList: ImageVector get() = AllNotesEditorIcons.BulletList
    val Table: ImageVector get() = AllNotesEditorIcons.Table
    val Callout: ImageVector get() = AllNotesEditorIcons.Callout
    val Code: ImageVector get() = AllNotesEditorIcons.Code
    val EditNote: ImageVector get() = AllNotesEditorIcons.EditNote
    val PinFilled: ImageVector get() = AllNotesStudioIcons.PinFilled
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
}

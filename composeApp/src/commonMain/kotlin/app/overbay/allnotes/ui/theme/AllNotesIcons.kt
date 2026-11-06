package app.overbay.allnotes.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight, zero-dependency KMP vector icon set for AllNotes.
 */
object AllNotesIcons {

    private fun strokedIcon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector {
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

    val Sidebar: ImageVector by lazy {
        strokedIcon("Sidebar") {
            moveTo(4f, 5f)
            lineTo(20f, 5f)
            lineTo(20f, 19f)
            lineTo(4f, 19f)
            close()
            moveTo(9f, 5f)
            lineTo(9f, 19f)
        }
    }

    val Search: ImageVector by lazy {
        strokedIcon("Search") {
            moveTo(11f, 4f)
            arcTo(7f, 7f, 0f, true, true, 10.99f, 4f)
            close()
            moveTo(20f, 20f)
            lineTo(16.2f, 16.2f)
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
            moveTo(4f, 7f)
            lineTo(20f, 7f)
            moveTo(9f, 7f)
            lineTo(9f, 4f)
            lineTo(15f, 4f)
            lineTo(15f, 7f)
            moveTo(6f, 7f)
            lineTo(7f, 20f)
            lineTo(17f, 20f)
            lineTo(18f, 7f)
        }
    }

    val Close: ImageVector by lazy {
        strokedIcon("Close") {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }

    val Edit: ImageVector by lazy {
        strokedIcon("Edit") {
            moveTo(16f, 4f)
            lineTo(20f, 8f)
            lineTo(8f, 20f)
            lineTo(4f, 20f)
            lineTo(4f, 16f)
            close()
        }
    }

    val Pin: ImageVector by lazy {
        strokedIcon("Pin") {
            moveTo(9f, 4f)
            lineTo(15f, 4f)
            lineTo(14f, 11f)
            lineTo(17f, 14f)
            lineTo(7f, 14f)
            lineTo(10f, 11f)
            close()
            moveTo(12f, 14f)
            lineTo(12f, 21f)
        }
    }

    val Sync: ImageVector by lazy {
        strokedIcon("Sync") {
            moveTo(4f, 12f)
            arcTo(8f, 8f, 0f, false, true, 18f, 6.7f)
            moveTo(18f, 3f)
            lineTo(18f, 7f)
            lineTo(14f, 7f)
            moveTo(20f, 12f)
            arcTo(8f, 8f, 0f, false, true, 6f, 17.3f)
            moveTo(6f, 21f)
            lineTo(6f, 17f)
            lineTo(10f, 17f)
        }
    }

    val ArrowUp: ImageVector by lazy {
        strokedIcon("ArrowUp") {
            moveTo(12f, 19f)
            lineTo(12f, 5f)
            moveTo(6f, 11f)
            lineTo(12f, 5f)
            lineTo(18f, 11f)
        }
    }

    val ArrowDown: ImageVector by lazy {
        strokedIcon("ArrowDown") {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(6f, 13f)
            lineTo(12f, 19f)
            lineTo(18f, 13f)
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
            moveTo(4f, 9f)
            lineTo(8f, 12f)
            lineTo(4f, 15f)
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
            moveTo(8f, 9f)
            lineTo(4f, 12f)
            lineTo(8f, 15f)
        }
    }

    val Heading: ImageVector by lazy {
        strokedIcon("Heading") {
            moveTo(6f, 5f)
            lineTo(6f, 19f)
            moveTo(18f, 5f)
            lineTo(18f, 19f)
            moveTo(6f, 12f)
            lineTo(18f, 12f)
        }
    }

    val Paragraph: ImageVector by lazy {
        strokedIcon("Paragraph") {
            moveTo(4f, 7f)
            lineTo(20f, 7f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 17f)
            lineTo(14f, 17f)
        }
    }

    val Checklist: ImageVector by lazy {
        strokedIcon("Checklist") {
            moveTo(4f, 7f)
            lineTo(7f, 10f)
            lineTo(11f, 5f)
            moveTo(14f, 8f)
            lineTo(20f, 8f)
            moveTo(4f, 16f)
            lineTo(7f, 19f)
            lineTo(11f, 14f)
            moveTo(14f, 17f)
            lineTo(20f, 17f)
        }
    }

    val BulletList: ImageVector by lazy {
        strokedIcon("BulletList") {
            moveTo(9f, 6f)
            lineTo(20f, 6f)
            moveTo(9f, 12f)
            lineTo(20f, 12f)
            moveTo(9f, 18f)
            lineTo(20f, 18f)
            moveTo(5f, 6f)
            lineTo(5.1f, 6f)
            moveTo(5f, 12f)
            lineTo(5.1f, 12f)
            moveTo(5f, 18f)
            lineTo(5.1f, 18f)
        }
    }

    val Table: ImageVector by lazy {
        strokedIcon("Table") {
            moveTo(4f, 5f)
            lineTo(20f, 5f)
            lineTo(20f, 19f)
            lineTo(4f, 19f)
            close()
            moveTo(4f, 10f)
            lineTo(20f, 10f)
            moveTo(4f, 15f)
            lineTo(20f, 15f)
            moveTo(10f, 5f)
            lineTo(10f, 19f)
        }
    }

    val Callout: ImageVector by lazy {
        strokedIcon("Callout") {
            moveTo(12f, 3f)
            arcTo(9f, 9f, 0f, true, true, 11.99f, 3f)
            close()
            moveTo(12f, 8f)
            lineTo(12f, 12f)
            moveTo(12f, 16f)
            lineTo(12.01f, 16f)
        }
    }

    val Code: ImageVector by lazy {
        strokedIcon("Code") {
            moveTo(8f, 8f)
            lineTo(4f, 12f)
            lineTo(8f, 16f)
            moveTo(16f, 8f)
            lineTo(20f, 12f)
            lineTo(16f, 16f)
            moveTo(13.5f, 6f)
            lineTo(10.5f, 18f)
        }
    }
}

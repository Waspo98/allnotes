package app.overbay.allnotes.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.overbay.allnotes.AllNotesApp
import app.overbay.allnotes.ui.workspace.WorkspaceViewModel

fun main() = application {
    val viewModel = remember { WorkspaceViewModel() }
    val windowState = rememberWindowState(width = 1320.dp, height = 860.dp)
    val appIcon = rememberAllNotesWindowIcon()

    Window(
        onCloseRequest = {
            viewModel.flushPendingWrites()
            exitApplication()
        },
        state = windowState,
        icon = appIcon,
        title = "AllNotes — Overbay.app"
    ) {
        AllNotesApp(viewModel = viewModel)
    }
}

@Composable
private fun rememberAllNotesWindowIcon(): Painter = remember {
    object : Painter() {
        override val intrinsicSize: Size = Size(64f, 64f)

        override fun DrawScope.onDraw() {
            val w = size.width
            val h = size.height
            drawRoundRect(
                color = Color(0xFF1E2B7B),
                size = Size(w, h),
                cornerRadius = CornerRadius(w * 0.22f, h * 0.22f)
            )
            drawRoundRect(
                color = Color(0xFFFAF8FF),
                topLeft = Offset(w * 0.22f, h * 0.16f),
                size = Size(w * 0.56f, h * 0.68f),
                cornerRadius = CornerRadius(w * 0.06f, h * 0.06f)
            )
            drawRect(
                color = Color(0xFF3949AB),
                topLeft = Offset(w * 0.30f, h * 0.30f),
                size = Size(w * 0.32f, h * 0.08f)
            )
            drawRect(
                color = Color(0xFF5A5D72),
                topLeft = Offset(w * 0.30f, h * 0.46f),
                size = Size(w * 0.40f, h * 0.06f)
            )
            drawRect(
                color = Color(0xFF0D9488),
                topLeft = Offset(w * 0.30f, h * 0.60f),
                size = Size(w * 0.28f, h * 0.06f)
            )
        }
    }
}

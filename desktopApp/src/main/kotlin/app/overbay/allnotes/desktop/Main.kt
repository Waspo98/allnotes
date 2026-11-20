package app.overbay.allnotes.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.overbay.allnotes.AllNotesApp
import app.overbay.allnotes.ui.workspace.WorkspaceViewModel
import javax.imageio.ImageIO

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
private fun rememberAllNotesWindowIcon(): Painter? = remember {
    val stream = Thread.currentThread().contextClassLoader
        ?.getResourceAsStream("allnotes-window-icon.png")
        ?: return@remember null
    val bitmap = stream.use { ImageIO.read(it) }?.toComposeImageBitmap() ?: return@remember null
    BitmapPainter(bitmap, filterQuality = FilterQuality.High)
}

package app.overbay.allnotes.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.overbay.allnotes.AllNotesApp

fun main() = application {
    val windowState = rememberWindowState(width = 1320.dp, height = 860.dp)
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "AllNotes — Overbay.app"
    ) {
        AllNotesApp()
    }
}

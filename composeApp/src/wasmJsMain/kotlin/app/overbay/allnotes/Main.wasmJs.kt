@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package app.overbay.allnotes

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import app.overbay.allnotes.ui.workspace.WorkspaceViewModel
import kotlinx.browser.document
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val body = document.body ?: return
    val viewModel = WorkspaceViewModel()
    window.addEventListener("beforeunload") {
        viewModel.flushPendingWrites()
    }
    ComposeViewport(body) {
        AllNotesApp(viewModel = viewModel)
    }
}

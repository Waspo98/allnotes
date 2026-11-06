package app.overbay.allnotes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.overbay.allnotes.ui.theme.AllNotesTheme
import app.overbay.allnotes.ui.workspace.WorkspaceScreen
import app.overbay.allnotes.ui.workspace.WorkspaceViewModel

@Composable
fun AllNotesApp(
    viewModel: WorkspaceViewModel = remember { WorkspaceViewModel() }
) {
    AllNotesTheme {
        WorkspaceScreen(viewModel = viewModel)
    }
}

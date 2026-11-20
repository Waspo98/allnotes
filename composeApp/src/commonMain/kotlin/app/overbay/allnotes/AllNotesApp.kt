package app.overbay.allnotes

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.overbay.allnotes.ui.theme.AllNotesTheme
import app.overbay.allnotes.ui.workspace.WorkspaceScreen
import app.overbay.allnotes.ui.workspace.WorkspaceViewModel

@Composable
fun AllNotesApp(
    viewModel: WorkspaceViewModel = remember { WorkspaceViewModel() },
    modifier: Modifier = Modifier
) {
    val prefs by viewModel.preferencesFlow.collectAsState()
    AllNotesTheme(
        themeMode = prefs.themeMode,
        useDynamicColor = prefs.useDynamicColor
    ) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            WorkspaceScreen(viewModel = viewModel)
        }
    }
}

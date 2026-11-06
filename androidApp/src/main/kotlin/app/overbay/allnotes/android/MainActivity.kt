package app.overbay.allnotes.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import app.overbay.allnotes.AllNotesApp
import app.overbay.allnotes.data.AndroidStoragePaths
import app.overbay.allnotes.ui.workspace.WorkspaceViewModel

class RetainedWorkspaceHolder : ViewModel() {
    val workspaceViewModel = WorkspaceViewModel()
}

class MainActivity : ComponentActivity() {
    private val holder: RetainedWorkspaceHolder by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidStoragePaths.filesDirectory = applicationContext.filesDir
        AndroidStoragePaths.context = applicationContext
        enableEdgeToEdge()
        setContent {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                AllNotesApp(viewModel = holder.workspaceViewModel)
            }
        }
    }
}

package app.overbay.allnotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.data.AndroidStoragePaths
import app.overbay.allnotes.ui.theme.AllNotesTheme
import app.overbay.allnotes.ui.workspace.WorkspaceViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidStoragePaths.filesDirectory = applicationContext.filesDir
        AndroidStoragePaths.context = applicationContext
        installCrashLogger()
        enableEdgeToEdge()

        try {
            val vm = getOrCreateViewModel()
            setContent {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                ) {
                    AllNotesApp(viewModel = vm)
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("AllNotes", "Startup error", t)
            setContent {
                AllNotesTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "AllNotes Startup Error:\n\n${t.message ?: t.toString()}\n\n" +
                                t.stackTraceToString().take(800),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    private fun installCrashLogger() {
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current?.javaClass?.name?.contains("MainActivity") == true) return
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("AllNotes", "FATAL CRASH on ${thread.name}", throwable)
            try {
                val crashFile = File(filesDir, "crash_dump.txt")
                crashFile.writeText(
                    "Thread: ${thread.name}\nTime: ${System.currentTimeMillis()}\n\n${throwable.stackTraceToString()}"
                )
            } catch (_: Exception) {
            }
            current?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        @Volatile
        private var retainedViewModel: WorkspaceViewModel? = null

        private fun getOrCreateViewModel(): WorkspaceViewModel {
            return retainedViewModel ?: synchronized(this) {
                retainedViewModel ?: WorkspaceViewModel().also { retainedViewModel = it }
            }
        }
    }
}

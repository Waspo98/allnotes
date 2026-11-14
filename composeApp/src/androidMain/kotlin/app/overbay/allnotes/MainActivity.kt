package app.overbay.allnotes

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
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
import app.overbay.allnotes.ui.workspace.ActiveDialog
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appCtx = applicationContext
        AndroidStoragePaths.filesDirectory = appCtx.filesDir
        AndroidStoragePaths.driverFactory = {
            app.overbay.allnotes.data.DatabaseDriverFactory.createAndroidDriver(appCtx)
        }
        installCrashLogger(appCtx.filesDir)
        registerDynamicShortcuts(appCtx)
        enableEdgeToEdge()

        try {
            val vm = getOrCreateViewModel()
            handleLaunchIntent(intent, vm)
            setContent {
                AllNotesApp(viewModel = vm)
            }
        } catch (t: Throwable) {
            android.util.Log.e("AllNotes", "Startup error", t)
            setContent {
                AllNotesTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        retainedViewModel?.let { handleLaunchIntent(intent, it) }
    }

    override fun onStop() {
        retainedViewModel?.flushPendingWrites()
        super.onStop()
    }

    private fun handleLaunchIntent(intent: Intent?, vm: WorkspaceViewModel) {
        when (intent?.action) {
            ACTION_NEW_PAGE -> {
                vm.dispatch(WorkspaceAction.OpenDialog(ActiveDialog.NEW_PAGE))
                intent.action = Intent.ACTION_MAIN
            }
            ACTION_SEARCH_NOTES -> {
                vm.dispatch(WorkspaceAction.RequestSearchFocus)
                intent.action = Intent.ACTION_MAIN
            }
        }
    }

    private fun registerDynamicShortcuts(appCtx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        runCatching {
            val manager = appCtx.getSystemService(ShortcutManager::class.java) ?: return
            val newPageShortcut = ShortcutInfo.Builder(appCtx, "new_page")
                .setShortLabel("New Page")
                .setLongLabel("Create New Note Page")
                .setIntent(Intent(appCtx, MainActivity::class.java).setAction(ACTION_NEW_PAGE))
                .build()
            val searchShortcut = ShortcutInfo.Builder(appCtx, "search_notes")
                .setShortLabel("Search Notes")
                .setLongLabel("Search All Notebooks")
                .setIntent(Intent(appCtx, MainActivity::class.java).setAction(ACTION_SEARCH_NOTES))
                .build()
            manager.dynamicShortcuts = listOf(newPageShortcut, searchShortcut)
        }
    }

    companion object {
        private const val ACTION_NEW_PAGE = "app.overbay.allnotes.ACTION_NEW_PAGE"
        private const val ACTION_SEARCH_NOTES = "app.overbay.allnotes.ACTION_SEARCH_NOTES"

        @Volatile
        private var crashLoggerInstalled = false

        @Volatile
        private var retainedViewModel: WorkspaceViewModel? = null

        private fun installCrashLogger(crashDir: File) {
            if (crashLoggerInstalled) return
            synchronized(this) {
                if (crashLoggerInstalled) return
                val previous = Thread.getDefaultUncaughtExceptionHandler()
                Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                    android.util.Log.e("AllNotes", "FATAL CRASH on ${thread.name}", throwable)
                    try {
                        val crashFile = File(crashDir, "crash_dump.txt")
                        crashFile.writeText(
                            "Thread: ${thread.name}\nTime: ${System.currentTimeMillis()}\n\n${throwable.stackTraceToString()}"
                        )
                    } catch (_: Exception) {
                    }
                    previous?.uncaughtException(thread, throwable)
                }
                crashLoggerInstalled = true
            }
        }

        private fun getOrCreateViewModel(): WorkspaceViewModel {
            return retainedViewModel ?: synchronized(this) {
                retainedViewModel ?: WorkspaceViewModel().also { retainedViewModel = it }
            }
        }
    }
}

package app.overbay.allnotes.ui.workspace

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit
) {
    // Web handles Escape via onPreviewKeyEvent in WorkspaceScreen.
}

package app.overbay.allnotes.ui.workspace

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit
) {
    // Desktop handles Escape via onPreviewKeyEvent in WorkspaceScreen.
}

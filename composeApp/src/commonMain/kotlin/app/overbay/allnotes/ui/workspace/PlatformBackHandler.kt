package app.overbay.allnotes.ui.workspace

import androidx.compose.runtime.Composable

/**
 * Multiplatform system back navigation handler.
 * Handles Android hardware/predictive back presses while acting as a no-op on Desktop and Web.
 */
@Composable
expect fun PlatformBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit
)

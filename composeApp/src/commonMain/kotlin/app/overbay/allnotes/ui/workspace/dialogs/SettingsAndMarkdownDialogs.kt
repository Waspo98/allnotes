package app.overbay.allnotes.ui.workspace.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.ThemeMode
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

@Composable
internal fun WorkspaceSettingsDialog(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    onDismiss: () -> Unit
) {
    val prefs = state.preferences
    var endpoint by remember { mutableStateOf(state.syncEndpointUrl) }
    var useHttp by remember { mutableStateOf(state.useRemoteHttpSync) }
    var authToken by remember { mutableStateOf(prefs.syncAuthToken) }
    var autoSync by remember { mutableStateOf(prefs.autoSyncOnEdit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AllNotes · Overbay.app Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "APPEARANCE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                val modes = ThemeMode.entries
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { idx, mode ->
                        SegmentedButton(
                            selected = prefs.themeMode == mode,
                            onClick = { onAction(WorkspaceAction.UpdateThemeSettings(mode, prefs.useDynamicColor)) },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = modes.size),
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "System"
                                        ThemeMode.LIGHT -> "Light"
                                        ThemeMode.DARK -> "Dark"
                                    }
                                )
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dynamic Wallpaper Colors", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Material You palette on Android 12+",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.useDynamicColor,
                        onCheckedChange = {
                            onAction(WorkspaceAction.UpdateThemeSettings(prefs.themeMode, it))
                        }
                    )
                }

                HorizontalDivider()

                Text(
                    text = "SELF-HOSTED SYNC (notes.overbay.app)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Status: ${state.syncStatus.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("Sync Server Endpoint") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = authToken,
                    onValueChange = { authToken = it },
                    label = { Text("Authentik / Bearer Token (optional)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Connect to Remote HTTP Server", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = useHttp, onCheckedChange = { useHttp = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-Sync Background Edits", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = autoSync, onCheckedChange = { autoSync = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAction(WorkspaceAction.SaveSyncAndAuthSettings(endpoint, useHttp, authToken, autoSync))
                }
            ) {
                Text("Save & Sync Now", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
internal fun MarkdownExportDialog(
    pageTitle: String,
    markdown: String,
    onCopied: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export \"${pageTitle.ifBlank { "Untitled Page" }}\"") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "GitHub-Flavored Markdown:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = markdown,
                    onValueChange = {},
                    readOnly = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 340.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(markdown))
                    onCopied()
                    onDismiss()
                }
            ) {
                Text("Copy Markdown", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
internal fun MarkdownImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var markdown by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Markdown as New Page") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Paste Markdown (headings, lists, checklists, tables, callouts, code fences):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = markdown,
                    onValueChange = { markdown = it },
                    placeholder = { Text("# Project Plan\n\n- [ ] First milestone") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 340.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = markdown.isNotBlank(),
                onClick = { onImport(markdown) }
            ) {
                Text("Import Page", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

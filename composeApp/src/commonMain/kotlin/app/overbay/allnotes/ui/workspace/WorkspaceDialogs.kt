package app.overbay.allnotes.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.ui.theme.accentColor

@Composable
fun WorkspaceDialogsHost(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit
) {
    when (state.activeDialog) {
        ActiveDialog.NONE -> Unit
        ActiveDialog.NEW_NOTEBOOK -> NotebookDialog(
            initialTitle = "",
            initialSubtitle = "",
            initialAccent = SectionAccentKey.INDIGO,
            isEdit = false,
            onDismiss = { onAction(WorkspaceAction.DismissDialog) },
            onConfirm = { t, s, a -> onAction(WorkspaceAction.CreateNotebook(t, s, a)) },
            onDelete = null
        )
        ActiveDialog.EDIT_NOTEBOOK -> {
            val nb = state.activeNotebook ?: return
            NotebookDialog(
                initialTitle = nb.title,
                initialSubtitle = nb.subtitle,
                initialAccent = nb.accent,
                isEdit = true,
                onDismiss = { onAction(WorkspaceAction.DismissDialog) },
                onConfirm = { t, s, a -> onAction(WorkspaceAction.UpdateActiveNotebook(t, s, a)) },
                onDelete = { onAction(WorkspaceAction.DeleteActiveNotebook) }
            )
        }
        ActiveDialog.NEW_SECTION -> SectionDialog(
            initialTitle = "",
            initialAccent = SectionAccentKey.EMERALD,
            isEdit = false,
            onDismiss = { onAction(WorkspaceAction.DismissDialog) },
            onConfirm = { t, a -> onAction(WorkspaceAction.CreateSection(t, a)) },
            onDelete = null
        )
        ActiveDialog.EDIT_SECTION -> {
            val sec = state.activeSection ?: return
            SectionDialog(
                initialTitle = sec.title,
                initialAccent = sec.accent,
                isEdit = true,
                onDismiss = { onAction(WorkspaceAction.DismissDialog) },
                onConfirm = { t, a -> onAction(WorkspaceAction.UpdateActiveSection(t, a)) },
                onDelete = { onAction(WorkspaceAction.DeleteActiveSection) }
            )
        }
        ActiveDialog.NEW_PAGE -> CreatePageDialog(
            hasActivePage = state.activePage != null,
            onDismiss = { onAction(WorkspaceAction.DismissDialog) },
            onCreate = { title, asSubpage -> onAction(WorkspaceAction.CreatePage(title, asSubpage)) }
        )
        ActiveDialog.SYNC_SETTINGS -> SyncSettingsDialog(
            initialEndpoint = state.syncEndpointUrl,
            initialUseHttp = state.useRemoteHttpSync,
            syncStatusLabel = state.syncStatus.label,
            onDismiss = { onAction(WorkspaceAction.DismissDialog) },
            onSaveAndSync = { url, useHttp ->
                onAction(WorkspaceAction.ConfigureSyncServer(url, useHttp))
            }
        )
    }
}

@Composable
private fun NotebookDialog(
    initialTitle: String,
    initialSubtitle: String,
    initialAccent: SectionAccentKey,
    isEdit: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, SectionAccentKey) -> Unit,
    onDelete: (() -> Unit)?
) {
    var title by remember { mutableStateOf(initialTitle) }
    var subtitle by remember { mutableStateOf(initialSubtitle) }
    var accent by remember { mutableStateOf(initialAccent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Notebook" else "Create Notebook") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Notebook Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Subtitle (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                AccentPickerRow(selected = accent, onSelect = { accent = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, subtitle, accent) }) {
                Text(if (isEdit) "Save" else "Create")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun SectionDialog(
    initialTitle: String,
    initialAccent: SectionAccentKey,
    isEdit: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, SectionAccentKey) -> Unit,
    onDelete: (() -> Unit)?
) {
    var title by remember { mutableStateOf(initialTitle) }
    var accent by remember { mutableStateOf(initialAccent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Section" else "Create Section") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Section Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                AccentPickerRow(selected = accent, onSelect = { accent = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, accent) }) {
                Text(if (isEdit) "Save" else "Create")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun CreatePageDialog(
    hasActivePage: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var asSubpage by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Document Page") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Page Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (hasActivePage) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = asSubpage,
                            onCheckedChange = { asSubpage = it }
                        )
                        Text(
                            text = "Nest as subpage under current page",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title, asSubpage) }) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SyncSettingsDialog(
    initialEndpoint: String,
    initialUseHttp: Boolean,
    syncStatusLabel: String,
    onDismiss: () -> Unit,
    onSaveAndSync: (String, Boolean) -> Unit
) {
    var endpoint by remember { mutableStateOf(initialEndpoint) }
    var useHttp by remember { mutableStateOf(initialUseHttp) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Overbay.app Sync Settings") },
        text = {
            Column {
                Text(
                    text = "Status: $syncStatusLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("Sync Server Endpoint") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Connect to Remote HTTP Server",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = useHttp,
                        onCheckedChange = { useHttp = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSaveAndSync(endpoint, useHttp) }) {
                Text("Sync Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun AccentPickerRow(
    selected: SectionAccentKey,
    onSelect: (SectionAccentKey) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SectionAccentKey.entries.forEach { key ->
            FilterChip(
                selected = key == selected,
                onClick = { onSelect(key) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(key.accentColor())
                    )
                },
                label = { Text(key.label) }
            )
        }
    }
}

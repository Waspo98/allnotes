package app.overbay.allnotes.ui.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.SectionAccentKey

@Composable
fun WorkspaceDialogsHost(
    activeDialog: ActiveDialog,
    onDismiss: () -> Unit,
    onCreateNotebook: (String, String, SectionAccentKey) -> Unit,
    onCreateSection: (String, SectionAccentKey) -> Unit,
    onCreatePage: (String) -> Unit
) {
    when (activeDialog) {
        ActiveDialog.NONE -> Unit
        ActiveDialog.NEW_NOTEBOOK -> CreateNotebookDialog(onDismiss, onCreateNotebook)
        ActiveDialog.NEW_SECTION -> CreateSectionDialog(onDismiss, onCreateSection)
        ActiveDialog.NEW_PAGE -> CreatePageDialog(onDismiss, onCreatePage)
    }
}

@Composable
private fun CreateNotebookDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, SectionAccentKey) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var accent by remember { mutableStateOf(SectionAccentKey.INDIGO) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Notebook") },
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
            TextButton(onClick = { onCreate(title, subtitle, accent) }) {
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
private fun CreateSectionDialog(
    onDismiss: () -> Unit,
    onCreate: (String, SectionAccentKey) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var accent by remember { mutableStateOf(SectionAccentKey.EMERALD) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Section") },
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
            TextButton(onClick = { onCreate(title, accent) }) {
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
private fun CreatePageDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Document Page") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Page Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title) }) {
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
                label = { Text(key.label) }
            )
        }
    }
}

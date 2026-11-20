package app.overbay.allnotes.ui.workspace.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.ui.theme.accentColor

@Composable
internal fun NotebookDialog(
    initialTitle: String,
    initialSubtitle: String,
    initialAccent: SectionAccentKey,
    isEdit: Boolean,
    canReorder: Boolean,
    onReorder: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String, String, SectionAccentKey) -> Unit,
    onRequestDelete: (() -> Unit)?
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

                if (isEdit && canReorder) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onReorder(-1) }, modifier = Modifier.weight(1f)) {
                            Text("Move Left")
                        }
                        OutlinedButton(onClick = { onReorder(1) }, modifier = Modifier.weight(1f)) {
                            Text("Move Right")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, subtitle, accent) }) {
                Text(if (isEdit) "Save" else "Create")
            }
        },
        dismissButton = {
            Row {
                if (onRequestDelete != null) {
                    TextButton(onClick = onRequestDelete) {
                        Text("Delete…", color = MaterialTheme.colorScheme.error)
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
internal fun SectionDialog(
    initialTitle: String,
    initialAccent: SectionAccentKey,
    isEdit: Boolean,
    canReorder: Boolean,
    onReorder: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String, SectionAccentKey) -> Unit,
    onRequestDelete: (() -> Unit)?
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

                if (isEdit && canReorder) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onReorder(-1) }, modifier = Modifier.weight(1f)) {
                            Text("Move Left")
                        }
                        OutlinedButton(onClick = { onReorder(1) }, modifier = Modifier.weight(1f)) {
                            Text("Move Right")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, accent) }) {
                Text(if (isEdit) "Save" else "Create")
            }
        },
        dismissButton = {
            Row {
                if (onRequestDelete != null) {
                    TextButton(onClick = onRequestDelete) {
                        Text("Delete…", color = MaterialTheme.colorScheme.error)
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
internal fun ConfirmDeleteHierarchyDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.error) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirmDelete) {
                Text("Delete Permanently", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
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
internal fun MovePageDialog(
    pageTitle: String,
    currentSectionId: String,
    notebooks: List<Notebook>,
    sections: List<NoteSection>,
    onDismiss: () -> Unit,
    onMoveToSection: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move \"${pageTitle.ifBlank { "Untitled Page" }}\"") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                notebooks.forEach { nb ->
                    val nbSections = sections.filter { it.notebookId == nb.id }.sortedBy { it.sortOrder }
                    if (nbSections.isNotEmpty()) {
                        Text(
                            text = nb.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        nbSections.forEach { sec ->
                            val isCurrent = sec.id == currentSectionId
                            Surface(
                                onClick = { if (!isCurrent) onMoveToSection(sec.id) },
                                color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(sec.accent.accentColor())
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isCurrent) "${sec.title} (Current)" else sec.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
internal fun InsertLinkDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onApplyLink: (String) -> Unit
) {
    var url by remember { mutableStateOf(initialUrl.ifBlank { "https://" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert or Edit Link") },
        text = {
            Column {
                Text(
                    text = "Applies hyperlink styling to the selected words (or current word under cursor).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (url.isNotBlank()) onApplyLink(url.trim()) }
            ) {
                Text("Apply Link")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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

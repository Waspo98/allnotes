package app.overbay.allnotes.ui.workspace.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.ui.theme.LocalAllNotesMonoFont

@Composable
internal fun MarkdownExportDialog(
    pageTitle: String,
    markdown: String,
    onCopied: () -> Unit,
    onDismiss: () -> Unit
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val monoFont = LocalAllNotesMonoFont.current
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
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = monoFont),
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
    val monoFont = LocalAllNotesMonoFont.current
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
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = monoFont),
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

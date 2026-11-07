package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.workspace.WorkspaceAction

@Composable
internal fun ChecklistBlockContent(
    block: DocumentBlock.Checklist,
    isActive: Boolean,
    onAction: (WorkspaceAction) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        block.items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.isChecked,
                    onCheckedChange = { onAction(WorkspaceAction.ToggleChecklistItem(block.id, item.id)) },
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                    if (item.text.isEmpty()) {
                        Text(
                            text = "To-do item…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                    }
                    BasicTextField(
                        value = item.text,
                        onValueChange = { newText ->
                            if (newText.contains('\n')) {
                                val cleaned = newText.replace("\n", "")
                                onAction(WorkspaceAction.UpdateChecklistItemText(block.id, item.id, cleaned))
                                onAction(WorkspaceAction.InsertChecklistItemAfter(block.id, item.id))
                            } else {
                                onAction(WorkspaceAction.UpdateChecklistItemText(block.id, item.id, newText))
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (item.isChecked) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                onAction(WorkspaceAction.InsertChecklistItemAfter(block.id, item.id))
                            }
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (isActive && block.items.size > 1) {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.DeleteChecklistItem(block.id, item.id)) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            AllNotesIcons.Close,
                            contentDescription = "Delete item",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BulletListBlockContent(
    block: DocumentBlock.BulletList,
    isActive: Boolean,
    onAction: (WorkspaceAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        block.items.forEachIndexed { index, item ->
            val prefix = if (block.isNumbered) "${index + 1}." else "•"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = prefix,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp).padding(top = 1.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (item.isEmpty()) {
                        Text(
                            text = "List item…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                    }
                    BasicTextField(
                        value = item,
                        onValueChange = { newText ->
                            if (newText.contains('\n')) {
                                val cleaned = newText.replace("\n", "")
                                onAction(WorkspaceAction.UpdateBulletListItem(block.id, index, cleaned))
                                onAction(WorkspaceAction.InsertBulletListItemAfter(block.id, index))
                            } else {
                                onAction(WorkspaceAction.UpdateBulletListItem(block.id, index, newText))
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                onAction(WorkspaceAction.InsertBulletListItemAfter(block.id, index))
                            }
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (isActive && block.items.size > 1) {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.DeleteBulletListItem(block.id, index)) },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            AllNotesIcons.Close,
                            contentDescription = "Remove list item",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

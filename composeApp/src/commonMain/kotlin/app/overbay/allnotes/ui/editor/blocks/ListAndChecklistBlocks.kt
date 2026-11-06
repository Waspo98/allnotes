package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    onAction: (WorkspaceAction) -> Unit
) {
    var draftItem by remember(block.id) { mutableStateOf("") }
    val checkedCount = block.items.count { it.isChecked }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        AllNotesIcons.Checklist,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (block.title.isEmpty()) {
                            Text(
                                text = "Checklist Title…",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        BasicTextField(
                            value = block.title,
                            onValueChange = { onAction(WorkspaceAction.UpdateChecklistTitle(block.id, it)) },
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "$checkedCount/${block.items.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            block.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = item.isChecked,
                        onCheckedChange = { onAction(WorkspaceAction.ToggleChecklistItem(block.id, item.id)) }
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (item.text.isEmpty()) {
                            Text(
                                text = "Task description…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        BasicTextField(
                            value = item.text,
                            onValueChange = { onAction(WorkspaceAction.UpdateChecklistItemText(block.id, item.id, it)) },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (item.isChecked) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    IconButton(
                        onClick = { onAction(WorkspaceAction.DeleteChecklistItem(block.id, item.id)) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            AllNotesIcons.Close,
                            contentDescription = "Delete item",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    AllNotesIcons.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, end = 10.dp).size(16.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (draftItem.isEmpty()) {
                        Text(
                            text = "Add checklist item…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }
                    BasicTextField(
                        value = draftItem,
                        onValueChange = { draftItem = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (draftItem.isNotBlank()) {
                                    onAction(WorkspaceAction.AddChecklistItem(block.id, draftItem))
                                    draftItem = ""
                                }
                            }
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TextButton(
                    onClick = {
                        if (draftItem.isNotBlank()) {
                            onAction(WorkspaceAction.AddChecklistItem(block.id, draftItem))
                            draftItem = ""
                        }
                    }
                ) {
                    Text("Add")
                }
            }
        }
    }
}

@Composable
internal fun BulletListBlockContent(
    block: DocumentBlock.BulletList,
    onAction: (WorkspaceAction) -> Unit
) {
    var draftItem by remember(block.id) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        block.items.forEachIndexed { index, item ->
            val prefix = if (block.isNumbered) "${index + 1}." else "•"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = prefix,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(28.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (item.isEmpty()) {
                        Text(
                            text = "List item…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    BasicTextField(
                        value = item,
                        onValueChange = { onAction(WorkspaceAction.UpdateBulletListItem(block.id, index, it)) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                IconButton(
                    onClick = { onAction(WorkspaceAction.DeleteBulletListItem(block.id, index)) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        AllNotesIcons.Close,
                        contentDescription = "Remove list item",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                if (draftItem.isEmpty()) {
                    Text(
                        text = "Add list item…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                BasicTextField(
                    value = draftItem,
                    onValueChange = { draftItem = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (draftItem.isNotBlank()) {
                                onAction(WorkspaceAction.AddBulletListItem(block.id, draftItem))
                                draftItem = ""
                            }
                        }
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            TextButton(
                onClick = {
                    if (draftItem.isNotBlank()) {
                        onAction(WorkspaceAction.AddBulletListItem(block.id, draftItem))
                        draftItem = ""
                    }
                }
            ) {
                Text("Add")
            }
        }
    }
}

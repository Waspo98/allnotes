package app.overbay.allnotes.ui.workspace.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.AllNotesMotion
import app.overbay.allnotes.ui.theme.accentColor
import app.overbay.allnotes.ui.theme.containerColor
import app.overbay.allnotes.ui.theme.contentColor
import app.overbay.allnotes.ui.workspace.ActiveDialog
import app.overbay.allnotes.ui.workspace.WorkspaceAction

/**
 * Organizer dialog for pinning and reordering sections within the active notebook.
 */
@Composable
internal fun ManageSectionsDialog(
    notebookTitle: String,
    sections: List<NoteSection>,
    activeSectionId: String?,
    onAction: (WorkspaceAction) -> Unit,
    onDismiss: () -> Unit
) {
    val pinned = sections.filter { it.isPinned }
    val unpinned = sections.filter { !it.isPinned }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Sections in $notebookTitle")
                Text(
                    text = "Pinned sections stay at the top of the rail and strip.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = sections, key = { it.id }) { sec ->
                    val partition = if (sec.isPinned) pinned else unpinned
                    val idx = partition.indexOfFirst { it.id == sec.id }
                    val canMoveUp = idx > 0
                    val canMoveDown = idx >= 0 && idx < partition.lastIndex
                    val isActive = sec.id == activeSectionId
                    val accent = sec.accent.accentColor()

                    Surface(
                        onClick = {
                            onAction(WorkspaceAction.SelectSection(sec.id))
                            onDismiss()
                        },
                        color = if (isActive) sec.accent.containerColor()
                        else MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = if (isActive) sec.accent.contentColor()
                        else MaterialTheme.colorScheme.onSurface,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(
                            1.dp,
                            if (isActive) accent.copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = AllNotesMotion.effectsFast(),
                                fadeOutSpec = AllNotesMotion.effectsFast(),
                                placementSpec = AllNotesMotion.spatialFast()
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = sec.title.ifBlank { "Untitled Section" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isActive || sec.isPinned) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onAction(WorkspaceAction.TogglePinSection(sec.id)) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (sec.isPinned) AllNotesIcons.PinFilled else AllNotesIcons.Pin,
                                    contentDescription = if (sec.isPinned) "Unpin section" else "Pin section",
                                    tint = if (sec.isPinned) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onAction(WorkspaceAction.ReorderSection(sec.id, -1)) },
                                enabled = canMoveUp,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = AllNotesIcons.ArrowUp,
                                    contentDescription = "Move section up",
                                    tint = if (canMoveUp) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onAction(WorkspaceAction.ReorderSection(sec.id, 1)) },
                                enabled = canMoveDown,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = AllNotesIcons.ArrowDown,
                                    contentDescription = "Move section down",
                                    tint = if (canMoveDown) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onAction(WorkspaceAction.OpenEditSectionDialog(sec.id)) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = AllNotesIcons.Edit,
                                    contentDescription = "Edit section",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.NEW_SECTION)) }) {
                Text("+ New Section")
            }
        }
    )
}

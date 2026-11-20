package app.overbay.allnotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.ui.hierarchy.titleMonogram
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.accentColor
import app.overbay.allnotes.ui.theme.containerColor
import app.overbay.allnotes.ui.theme.contentColor
import app.overbay.allnotes.ui.workspace.ActiveDialog
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

/**
 * Interactive `Notebook ▾ › Section ▾` breadcrumb enabling 1-click notebook and section
 * switching (plus section pinning/reordering shortcuts) without expanding the sidebar.
 */
@Composable
internal fun EditorBreadcrumbBar(
    notebook: Notebook,
    section: NoteSection,
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var notebookMenuOpen by remember { mutableStateOf(false) }
    var sectionMenuOpen by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(modifier = Modifier.weight(1f, fill = false)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable { notebookMenuOpen = true }
                    .padding(horizontal = 6.dp, vertical = 7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(section.accent.accentColor())
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = notebook.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    imageVector = AllNotesIcons.ChevronDown,
                    contentDescription = "Switch notebook",
                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    modifier = Modifier.size(13.dp)
                )
            }

            DropdownMenu(
                expanded = notebookMenuOpen,
                onDismissRequest = { notebookMenuOpen = false }
            ) {
                state.notebooks.forEach { item ->
                    val current = item.id == notebook.id
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = item.title,
                                fontWeight = if (current) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(item.accent.containerColor()),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = titleMonogram(item.title),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = item.accent.contentColor()
                                )
                            }
                        },
                        trailingIcon = if (current) {
                            {
                                Icon(
                                    AllNotesIcons.Check,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        } else null,
                        onClick = {
                            notebookMenuOpen = false
                            onAction(WorkspaceAction.SelectNotebook(item.id))
                        }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(
                    text = { Text("New Notebook…", color = colorScheme.primary, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        Icon(AllNotesIcons.Add, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(17.dp))
                    },
                    onClick = {
                        notebookMenuOpen = false
                        onAction(WorkspaceAction.OpenDialog(ActiveDialog.NEW_NOTEBOOK))
                    }
                )
            }
        }

        Text(
            text = "›",
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        Box(modifier = Modifier.weight(1f, fill = false)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable { sectionMenuOpen = true }
                    .padding(horizontal = 6.dp, vertical = 7.dp)
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    imageVector = AllNotesIcons.ChevronDown,
                    contentDescription = "Switch or organize section",
                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    modifier = Modifier.size(13.dp)
                )
            }

            DropdownMenu(
                expanded = sectionMenuOpen,
                onDismissRequest = { sectionMenuOpen = false }
            ) {
                state.sectionsForNotebook.forEach { sec ->
                    val current = sec.id == section.id
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = sec.title,
                                fontWeight = if (current) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(sec.accent.accentColor())
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (sec.isPinned) {
                                    Icon(
                                        AllNotesIcons.PinFilled,
                                        contentDescription = "Pinned",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                if (current) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        AllNotesIcons.Check,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            sectionMenuOpen = false
                            onAction(WorkspaceAction.SelectSection(sec.id))
                        }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(
                    text = { Text(if (section.isPinned) "Unpin \"${section.title}\"" else "Pin \"${section.title}\"") },
                    leadingIcon = {
                        Icon(
                            if (section.isPinned) AllNotesIcons.Pin else AllNotesIcons.PinFilled,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                    },
                    onClick = {
                        sectionMenuOpen = false
                        onAction(WorkspaceAction.TogglePinSection(section.id))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Manage & Reorder Sections…") },
                    leadingIcon = {
                        Icon(AllNotesIcons.Menu, contentDescription = null, modifier = Modifier.size(17.dp))
                    },
                    onClick = {
                        sectionMenuOpen = false
                        onAction(WorkspaceAction.OpenDialog(ActiveDialog.MANAGE_SECTIONS))
                    }
                )
                DropdownMenuItem(
                    text = { Text("New Section…", color = colorScheme.primary, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        Icon(AllNotesIcons.Add, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(17.dp))
                    },
                    onClick = {
                        sectionMenuOpen = false
                        onAction(WorkspaceAction.OpenDialog(ActiveDialog.NEW_SECTION))
                    }
                )
            }
        }
    }
}

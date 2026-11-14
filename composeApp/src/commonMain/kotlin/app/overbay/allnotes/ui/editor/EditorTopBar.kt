package app.overbay.allnotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.accentColor
import app.overbay.allnotes.ui.workspace.ActiveDialog
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

@Composable
internal fun EditorTopBar(
    state: WorkspaceUiState,
    isCompactMobile: Boolean,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val notebook = state.activeNotebook
    val section = state.activeSection
    val page = state.activePage

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (isCompactMobile) 8.dp else 0.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isCompactMobile) {
                        onAction(WorkspaceAction.SetMobileHierarchySheetOpen(true))
                    } else {
                        onAction(WorkspaceAction.ToggleSidebar)
                    }
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isCompactMobile) AllNotesIcons.Menu else AllNotesIcons.Sidebar,
                    contentDescription = if (isCompactMobile) "Open navigation drawer" else "Toggle sidebar",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (notebook != null && section != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (isCompactMobile) {
                                onAction(WorkspaceAction.SetMobileHierarchySheetOpen(true))
                            } else {
                                onAction(WorkspaceAction.ToggleSidebar)
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(section.accent.accentColor())
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${notebook.title}  ›  ${section.title}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (!isCompactMobile && page != null && state.wordCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = "${state.wordCount} words · ${state.readingTimeMinutes} min read",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            if (page != null) {
                IconButton(
                    onClick = { onAction(WorkspaceAction.Undo) },
                    enabled = state.canUndo,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        AllNotesIcons.Undo,
                        contentDescription = "Undo (Ctrl+Z)",
                        tint = if (state.canUndo) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { onAction(WorkspaceAction.Redo) },
                    enabled = state.canRedo,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        AllNotesIcons.Redo,
                        contentDescription = "Redo (Ctrl+Y)",
                        tint = if (state.canRedo) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { onAction(WorkspaceAction.ToggleActivePagePin) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        AllNotesIcons.Pin,
                        contentDescription = if (page.isPinned) "Unpin Page" else "Pin Page",
                        tint = if (page.isPinned) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { onAction(WorkspaceAction.SetPageMenuOpen(true)) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        AllNotesIcons.MoreVert,
                        contentDescription = "Page options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = state.isPageMenuOpen,
                    onDismissRequest = { onAction(WorkspaceAction.SetPageMenuOpen(false)) }
                ) {
                    if (page != null) {
                        DropdownMenuItem(
                            text = {
                                Text(if (page.parentPageId != null) "Promote to Top-Level Page" else "Make Subpage")
                            },
                            leadingIcon = {
                                Icon(
                                    if (page.parentPageId != null) AllNotesIcons.Outdent else AllNotesIcons.Indent,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                onAction(WorkspaceAction.SetPageMenuOpen(false))
                                onAction(WorkspaceAction.TogglePageSubpageIndent(page.id))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move Page to Section…") },
                            leadingIcon = {
                                Icon(AllNotesIcons.FolderMove, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.MOVE_PAGE)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Export as Markdown") },
                            leadingIcon = {
                                Icon(AllNotesIcons.Markdown, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.MARKDOWN_EXPORT)) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Import Markdown Page…") },
                        leadingIcon = {
                            Icon(AllNotesIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.MARKDOWN_IMPORT)) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text("Workspace & Sync Settings") },
                        leadingIcon = {
                            Icon(AllNotesIcons.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.SYNC_SETTINGS)) }
                    )
                    if (page != null) {
                        DropdownMenuItem(
                            text = { Text("Delete Page", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    AllNotesIcons.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = { onAction(WorkspaceAction.RequestDeletePage(page.id)) }
                        )
                    }
                }
            }
        }
    }
}

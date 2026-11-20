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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.ui.editor.toolbar.StudioTooltip
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
    val haptics = LocalHapticFeedback.current
    val buttonSize = if (isCompactMobile) 44.dp else 40.dp
    val showTooltips = !isCompactMobile

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (isCompactMobile) 6.dp else 0.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudioTooltip(
                label = if (isCompactMobile) "Browse workspace" else "Toggle sidebar",
                enabled = showTooltips
            ) {
                IconButton(
                    onClick = {
                        if (isCompactMobile) {
                            onAction(WorkspaceAction.SetMobileHierarchySheetOpen(true))
                        } else {
                            onAction(WorkspaceAction.ToggleSidebar)
                        }
                    },
                    modifier = Modifier.size(if (isCompactMobile) 48.dp else 40.dp)
                ) {
                    Icon(
                        imageVector = if (isCompactMobile) AllNotesIcons.Menu else AllNotesIcons.Sidebar,
                        contentDescription = if (isCompactMobile) "Open navigation drawer" else "Toggle sidebar",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (notebook != null && section != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable {
                            if (isCompactMobile) {
                                onAction(WorkspaceAction.SetMobileHierarchySheetOpen(true))
                            } else {
                                onAction(WorkspaceAction.ToggleSidebar)
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
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
            if (page != null) {
                StudioTooltip(label = "Undo  ·  Ctrl+Z", enabled = showTooltips) {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.Undo) },
                        enabled = state.canUndo,
                        modifier = Modifier.size(buttonSize)
                    ) {
                        Icon(
                            AllNotesIcons.Undo,
                            contentDescription = "Undo (Ctrl+Z)",
                            tint = if (state.canUndo) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                StudioTooltip(label = "Redo  ·  Ctrl+Y", enabled = showTooltips) {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.Redo) },
                        enabled = state.canRedo,
                        modifier = Modifier.size(buttonSize)
                    ) {
                        Icon(
                            AllNotesIcons.Redo,
                            contentDescription = "Redo (Ctrl+Y)",
                            tint = if (state.canRedo) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                StudioTooltip(
                    label = if (page.isPinned) "Unpin page" else "Pin page to top",
                    enabled = showTooltips
                ) {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAction(WorkspaceAction.ToggleActivePagePin)
                        },
                        modifier = Modifier.size(buttonSize)
                    ) {
                        Icon(
                            if (page.isPinned) AllNotesIcons.PinFilled else AllNotesIcons.Pin,
                            contentDescription = if (page.isPinned) "Unpin Page" else "Pin Page",
                            tint = if (page.isPinned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Box {
                StudioTooltip(label = "Page & workspace options", enabled = showTooltips) {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.SetPageMenuOpen(true)) },
                        modifier = Modifier.size(buttonSize)
                    ) {
                        Icon(
                            AllNotesIcons.MoreVert,
                            contentDescription = "Page options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = state.isPageMenuOpen,
                    onDismissRequest = { onAction(WorkspaceAction.SetPageMenuOpen(false)) }
                ) {
                    if (page != null) {
                        DropdownMenuItem(
                            text = { Text("New Subpage") },
                            leadingIcon = {
                                Icon(AllNotesIcons.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                onAction(WorkspaceAction.SetPageMenuOpen(false))
                                onAction(WorkspaceAction.CreatePageInstant(asSubpageOfActive = true))
                            }
                        )
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
                        text = { Text("Settings & Sync") },
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

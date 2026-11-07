package app.overbay.allnotes.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.ui.editor.blocks.DocumentBlockView
import app.overbay.allnotes.ui.editor.toolbar.DesktopFormattingRibbon
import app.overbay.allnotes.ui.editor.toolbar.MobileFloatingToolbar
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.accentColor
import app.overbay.allnotes.ui.theme.allNotesSpring
import app.overbay.allnotes.ui.workspace.ActiveDialog
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

@Composable
fun DocumentEditorPane(
    state: WorkspaceUiState,
    isCompactMobile: Boolean,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val notebook = state.activeNotebook
    val section = state.activeSection
    val page = state.activePage
    var isOverflowMenuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = if (isCompactMobile) 680.dp else 900.dp)
                    .padding(
                        horizontal = if (isCompactMobile) 0.dp else 24.dp,
                        vertical = if (isCompactMobile) 0.dp else 12.dp
                    )
            ) {
                // Clean, Quiet Top Bar
                Row(
                    modifier = Modifier
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
                        if (page != null) {
                            IconButton(
                                onClick = { onAction(WorkspaceAction.ToggleActivePagePin) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    AllNotesIcons.Pin,
                                    contentDescription = if (page.isPinned) "Unpin Page" else "Pin Page",
                                    tint = if (page.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { isOverflowMenuOpen = true },
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
                                expanded = isOverflowMenuOpen,
                                onDismissRequest = { isOverflowMenuOpen = false }
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
                                            isOverflowMenuOpen = false
                                            onAction(WorkspaceAction.TogglePageSubpageIndent(page.id))
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Sync Settings") },
                                    leadingIcon = {
                                        Icon(AllNotesIcons.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        isOverflowMenuOpen = false
                                        onAction(WorkspaceAction.OpenDialog(ActiveDialog.SYNC_SETTINGS))
                                    }
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
                                        onClick = {
                                            isOverflowMenuOpen = false
                                            onAction(WorkspaceAction.DeleteActivePage)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isCompactMobile && page != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    DesktopFormattingRibbon(
                        state = state,
                        onAction = onAction
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (page == null) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No Page Selected",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Select a page from the navigation drawer or create a new one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            FilledTonalButton(onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.NEW_PAGE)) }) {
                                Icon(AllNotesIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New Page")
                            }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = if (isCompactMobile) {
                            MaterialTheme.colorScheme.background
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                        shape = if (isCompactMobile) RectangleShape else RoundedCornerShape(20.dp),
                        border = if (isCompactMobile) {
                            null
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = if (isCompactMobile) 20.dp else 44.dp,
                                end = if (isCompactMobile) 20.dp else 44.dp,
                                top = if (isCompactMobile) 8.dp else 32.dp,
                                bottom = if (isCompactMobile) 104.dp else 64.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item(key = "page-header-${page.id}") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        if (page.title.isEmpty()) {
                                            Text(
                                                text = "Untitled Page",
                                                style = MaterialTheme.typography.headlineMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                )
                                            )
                                        }
                                        BasicTextField(
                                            value = page.title,
                                            onValueChange = { onAction(WorkspaceAction.UpdateActivePageTitle(it)) },
                                            textStyle = MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                }
                            }

                            items(page.blocks, key = { it.id }) { block ->
                                DocumentBlockView(
                                    block = block,
                                    isActive = block.id == state.activeBlockId,
                                    onAction = onAction,
                                    modifier = Modifier.animateItem(placementSpec = allNotesSpring())
                                )
                            }
                        }
                    }
                }
            }

            if (isCompactMobile && page != null) {
                MobileFloatingToolbar(
                    state = state,
                    onAction = onAction,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp, start = 12.dp, end = 12.dp)
                )
            }
        }
    }
}

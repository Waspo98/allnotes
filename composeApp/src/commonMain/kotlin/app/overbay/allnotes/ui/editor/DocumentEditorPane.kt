package app.overbay.allnotes.ui.editor

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.ui.editor.blocks.DocumentBlockView
import app.overbay.allnotes.ui.editor.toolbar.DesktopFormattingRibbon
import app.overbay.allnotes.ui.editor.toolbar.MobileFloatingToolbar
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.allNotesSpring
import app.overbay.allnotes.ui.theme.containerColor
import app.overbay.allnotes.ui.theme.contentColor
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
                    .widthIn(max = if (isCompactMobile) 640.dp else 940.dp)
                    .padding(horizontal = if (isCompactMobile) 12.dp else 24.dp, vertical = 12.dp)
            ) {
                // Top Breadcrumb & Workspace Mode Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isCompactMobile) {
                            FilledTonalButton(
                                onClick = { onAction(WorkspaceAction.ToggleSidebar) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(AllNotesIcons.Sidebar, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (state.isSidebarOpen) "Hide Sidebar" else "Show Sidebar",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { onAction(WorkspaceAction.SetMobileHierarchySheetOpen(true)) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(AllNotesIcons.Sidebar, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Hierarchy", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        if (notebook != null && section != null) {
                            Surface(
                                color = section.accent.containerColor(),
                                contentColor = section.accent.contentColor(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(
                                    text = "${notebook.title}  ›  ${section.title}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    if (isCompactMobile && page != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { onAction(WorkspaceAction.ToggleActivePagePin) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    AllNotesIcons.Pin,
                                    contentDescription = if (page.isPinned) "Unpin Page" else "Pin Page",
                                    tint = if (page.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onAction(WorkspaceAction.TogglePageSubpageIndent(page.id)) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    if (page.parentPageId != null) AllNotesIcons.Outdent else AllNotesIcons.Indent,
                                    contentDescription = "Subpage indent",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else if (!isCompactMobile) {
                        Text(
                            text = "Word Document Studio · Overbay.app",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!isCompactMobile && page != null) {
                    DesktopFormattingRibbon(
                        state = state,
                        onAction = onAction
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (page == null) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No Document Page Selected",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Select a page from the hierarchy or create a new page in this section.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            FilledTonalButton(onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.NEW_PAGE)) }) {
                                Icon(AllNotesIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create New Page")
                            }
                        }
                    }
                } else {
                    // Word-Grade Bounded Document Sheet
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(if (isCompactMobile) 20.dp else 26.dp),
                        tonalElevation = 1.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = if (isCompactMobile) 16.dp else 44.dp,
                                end = if (isCompactMobile) 16.dp else 44.dp,
                                top = if (isCompactMobile) 20.dp else 36.dp,
                                bottom = if (isCompactMobile) 104.dp else 64.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            item(key = "page-header-${page.id}") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        if (page.title.isEmpty()) {
                                            Text(
                                                text = "Untitled Page",
                                                style = MaterialTheme.typography.headlineLarge.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                )
                                            )
                                        }
                                        BasicTextField(
                                            value = page.title,
                                            onValueChange = { onAction(WorkspaceAction.UpdateActivePageTitle(it)) },
                                            textStyle = MaterialTheme.typography.headlineLarge.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "${page.blocks.size} blocks",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (page.isPinned) {
                                            Text(
                                                text = "• Pinned",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (page.parentPageId != null) {
                                            Text(
                                                text = "• Nested Subpage",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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

                            item(key = "page-footer-insert-${page.id}") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AssistChip(
                                        onClick = { onAction(WorkspaceAction.InsertParagraph(null)) },
                                        leadingIcon = { Icon(AllNotesIcons.Add, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                        label = { Text("Add Paragraph") }
                                    )
                                    AssistChip(
                                        onClick = { onAction(WorkspaceAction.InsertChecklist(null)) },
                                        leadingIcon = { Icon(AllNotesIcons.Checklist, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                        label = { Text("Add Checklist") }
                                    )
                                }
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
                        .padding(bottom = 14.dp, start = 10.dp, end = 10.dp)
                )
            }
        }
    }
}

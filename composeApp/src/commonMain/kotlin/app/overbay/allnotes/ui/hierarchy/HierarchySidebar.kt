package app.overbay.allnotes.ui.hierarchy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.accentColor
import app.overbay.allnotes.ui.theme.allNotesSpring
import app.overbay.allnotes.ui.workspace.ActiveDialog
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

@Composable
fun HierarchySidebar(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val parentsWithChildren = state.parentPageIdsWithChildren
    val globalHits = state.globalSearchResults
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.searchFocusRequestToken) {
        if (state.searchFocusRequestToken > 0L) {
            runCatching { searchFocusRequester.requestFocus() }
            onAction(WorkspaceAction.ConsumeSearchFocusRequest(state.searchFocusRequestToken))
        }
    }

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusable(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AllNotes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onAction(WorkspaceAction.TriggerSync) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            AllNotesIcons.Sync,
                            contentDescription = "Sync status",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = state.syncStatus.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.SYNC_SETTINGS)) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            AllNotesIcons.Settings,
                            contentDescription = "Workspace & Sync Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        AllNotesIcons.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (state.searchQuery.isEmpty()) {
                            Text(
                                text = "Search all notebooks…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                maxLines = 1
                            )
                        }
                        BasicTextField(
                            value = state.searchQuery,
                            onValueChange = { onAction(WorkspaceAction.SetSearchQuery(it)) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester)
                        )
                    }
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onAction(WorkspaceAction.SetSearchQuery("")) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                AllNotesIcons.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NOTEBOOKS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.activeNotebook != null) {
                        IconButton(
                            onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.EDIT_NOTEBOOK)) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                AllNotesIcons.Edit,
                                contentDescription = "Edit active notebook",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    TextButton(
                        onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.NEW_NOTEBOOK)) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("+ Notebook")
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.notebooks.forEach { notebook ->
                    NotebookPill(
                        notebook = notebook,
                        isSelected = notebook.id == state.activeNotebook?.id,
                        onClick = { onAction(WorkspaceAction.SelectNotebook(notebook.id)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SECTIONS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.activeSection != null) {
                        IconButton(
                            onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.EDIT_SECTION)) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                AllNotesIcons.Edit,
                                contentDescription = "Edit active section",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    TextButton(
                        onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.NEW_SECTION)) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("+ Section")
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.sectionsForNotebook.forEach { section ->
                    SectionTabChip(
                        section = section,
                        isSelected = section.id == state.activeSection?.id,
                        onClick = { onAction(WorkspaceAction.SelectSection(section.id)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PAGES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.NEW_PAGE)) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("+ Page")
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (state.pagesForSection.isEmpty()) {
                    item(key = "empty-pages-placeholder") {
                        EmptyPagesPlaceholder(
                            hasSearchQuery = state.searchQuery.isNotBlank(),
                            onClearSearch = { onAction(WorkspaceAction.SetSearchQuery("")) },
                            onNewPage = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.NEW_PAGE)) }
                        )
                    }
                } else {
                    items(state.pagesForSection, key = { it.id }) { page ->
                        PageHierarchyItem(
                            page = page,
                            isSelected = page.id == state.activePage?.id,
                            onClick = { onAction(WorkspaceAction.SelectPage(page.id)) },
                            hasChildren = state.searchQuery.isBlank() && page.id in parentsWithChildren,
                            isCollapsed = page.id in state.collapsedParentPageIds,
                            onToggleCollapse = { onAction(WorkspaceAction.ToggleSubpageCollapse(page.id)) },
                            searchQuery = state.searchQuery,
                            onTogglePin = { onAction(WorkspaceAction.TogglePinPage(page.id)) },
                            onRequestDelete = { onAction(WorkspaceAction.RequestDeletePage(page.id)) },
                            modifier = Modifier.animateItem(placementSpec = allNotesSpring())
                        )
                    }
                }

                if (globalHits.isNotEmpty()) {
                    item(key = "global-search-header") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "OTHER SECTIONS & NOTEBOOKS (${globalHits.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(globalHits, key = { "global-${it.page.id}" }) { hit ->
                        GlobalSearchResultCard(
                            hit = hit,
                            searchQuery = state.searchQuery,
                            onClick = { onAction(WorkspaceAction.SelectPage(hit.page.id)) }
                        )
                    }
                }
            }
        }
    }
}

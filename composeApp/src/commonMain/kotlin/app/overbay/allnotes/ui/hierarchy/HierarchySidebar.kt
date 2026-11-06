package app.overbay.allnotes.ui.hierarchy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.overbay.allnotes.ui.theme.AllNotesIcons
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
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            // Top Brand & Sync Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AllNotes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onAction(WorkspaceAction.OpenDialog(ActiveDialog.SYNC_SETTINGS)) }
                            .padding(vertical = 2.dp)
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
                }
                AssistChip(
                    onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.NEW_NOTEBOOK)) },
                    leadingIcon = { Icon(AllNotesIcons.Add, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    label = { Text("Notebook") }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Instant Local Search Field
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { onAction(WorkspaceAction.SetSearchQuery(it)) },
                placeholder = { Text("Search pages & blocks…") },
                leadingIcon = {
                    Icon(AllNotesIcons.Search, contentDescription = "Search", modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onAction(WorkspaceAction.SetSearchQuery("")) }) {
                            Icon(AllNotesIcons.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Notebooks Header & Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NOTEBOOKS (${state.notebooks.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.activeNotebook != null) {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.EDIT_NOTEBOOK)) },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            AllNotesIcons.Edit,
                            contentDescription = "Edit active notebook",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

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

            state.activeNotebook?.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sections Header & Color-Coded Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SECTIONS (${state.sectionsForNotebook.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.activeSection != null) {
                        IconButton(
                            onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.EDIT_SECTION)) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                AllNotesIcons.Edit,
                                contentDescription = "Edit active section",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
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

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(6.dp))

            // Pages & Subpages Tree Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PAGES (${state.pagesForSection.size})",
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
                items(state.pagesForSection, key = { it.id }) { page ->
                    PageHierarchyItem(
                        page = page,
                        isSelected = page.id == state.activePage?.id,
                        onClick = { onAction(WorkspaceAction.SelectPage(page.id)) },
                        modifier = Modifier.animateItem(placementSpec = allNotesSpring())
                    )
                }
            }
        }
    }
}

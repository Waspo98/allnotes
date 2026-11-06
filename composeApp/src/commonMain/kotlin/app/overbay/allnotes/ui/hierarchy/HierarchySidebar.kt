package app.overbay.allnotes.ui.hierarchy

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.ui.theme.accentColor
import app.overbay.allnotes.ui.theme.allNotesSpring
import app.overbay.allnotes.ui.theme.containerColor
import app.overbay.allnotes.ui.theme.contentColor
import app.overbay.allnotes.ui.workspace.ActiveDialog
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

@Composable
fun HierarchySidebar(
    state: WorkspaceUiState,
    onSelectNotebook: (String) -> Unit,
    onSelectSection: (String) -> Unit,
    onSelectPage: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenDialog: (ActiveDialog) -> Unit,
    onTriggerSync: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AllNotes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = state.syncStatus.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onTriggerSync() }
                    )
                }
                AssistChip(
                    onClick = { onOpenDialog(ActiveDialog.NEW_NOTEBOOK) },
                    label = { Text("+ Notebook") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search pages & blocks…") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "NOTEBOOKS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

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
                        onClick = { onSelectNotebook(notebook.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SECTIONS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { onOpenDialog(ActiveDialog.NEW_SECTION) }) {
                    Text("+ Section")
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
                        onClick = { onSelectSection(section.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PAGES (${state.pagesForSection.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { onOpenDialog(ActiveDialog.NEW_PAGE) }) {
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
                        onClick = { onSelectPage(page.id) },
                        modifier = Modifier.animateItem(placementSpec = allNotesSpring())
                    )
                }
            }
        }
    }
}

@Composable
private fun NotebookPill(
    notebook: Notebook,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) notebook.accent.containerColor() else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = allNotesSpring()
    )
    val fg by animateColorAsState(
        targetValue = if (isSelected) notebook.accent.contentColor() else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = allNotesSpring()
    )
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = notebook.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = bg,
            labelColor = fg,
            selectedContainerColor = bg,
            selectedLabelColor = fg
        )
    )
}

@Composable
private fun SectionTabChip(
    section: NoteSection,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) section.accent.containerColor()
        else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = allNotesSpring()
    )
    val fg by animateColorAsState(
        targetValue = if (isSelected) section.accent.contentColor()
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = allNotesSpring()
    )

    Surface(
        onClick = onClick,
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(section.accent.accentColor())
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = section.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PageHierarchyItem(
    page: NotePage,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = allNotesSpring()
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = allNotesSpring()
    )

    Surface(
        onClick = onClick,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = page.title.ifBlank { "Untitled Page" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${page.blocks.size} blocks",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.75f)
                )
            }
            if (page.isPinned) {
                Text(
                    text = "PINNED",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}

package app.overbay.allnotes.ui.hierarchy

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.AllNotesMotion
import app.overbay.allnotes.ui.theme.accentColor
import app.overbay.allnotes.ui.theme.containerColor
import app.overbay.allnotes.ui.theme.contentColor
import app.overbay.allnotes.ui.workspace.ActiveDialog
import app.overbay.allnotes.ui.workspace.GlobalSearchHit
import app.overbay.allnotes.ui.workspace.WorkspaceAction

internal fun titleMonogram(title: String, fallback: String = "N"): String {
    val words = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> fallback
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "${words[0].first()}${words[1].first()}".uppercase()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.clickableWithContextAction(
    onClick: () -> Unit,
    onContextMenu: () -> Unit
): Modifier {
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnContext by rememberUpdatedState(onContextMenu)
    return this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        event.changes.forEach { it.consume() }
                        latestOnContext()
                    }
                }
            }
        }
        .combinedClickable(
            onClick = { latestOnClick() },
            onLongClick = { latestOnContext() }
        )
}

@Composable
internal fun SectionContextMenu(
    section: NoteSection,
    sectionsInNotebook: List<NoteSection>,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (WorkspaceAction) -> Unit
) {
    val partition = remember(sectionsInNotebook, section.isPinned) {
        sectionsInNotebook.filter { it.isPinned == section.isPinned }
    }
    val idx = partition.indexOfFirst { it.id == section.id }
    val canMoveEarlier = idx > 0
    val canMoveLater = idx >= 0 && idx < partition.lastIndex

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (section.isPinned) "Unpin Section" else "Pin Section to Top") },
            leadingIcon = {
                Icon(
                    imageVector = if (section.isPinned) AllNotesIcons.Pin else AllNotesIcons.PinFilled,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
            },
            onClick = {
                onDismiss()
                onAction(WorkspaceAction.TogglePinSection(section.id))
            }
        )
        DropdownMenuItem(
            text = { Text("Move Earlier") },
            leadingIcon = { Icon(AllNotesIcons.ArrowUp, contentDescription = null, modifier = Modifier.size(17.dp)) },
            enabled = canMoveEarlier,
            onClick = {
                onDismiss()
                onAction(WorkspaceAction.ReorderSection(section.id, -1))
            }
        )
        DropdownMenuItem(
            text = { Text("Move Later") },
            leadingIcon = { Icon(AllNotesIcons.ArrowDown, contentDescription = null, modifier = Modifier.size(17.dp)) },
            enabled = canMoveLater,
            onClick = {
                onDismiss()
                onAction(WorkspaceAction.ReorderSection(section.id, 1))
            }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        DropdownMenuItem(
            text = { Text("Edit Section…") },
            leadingIcon = { Icon(AllNotesIcons.Edit, contentDescription = null, modifier = Modifier.size(17.dp)) },
            onClick = {
                onDismiss()
                onAction(WorkspaceAction.OpenEditSectionDialog(section.id))
            }
        )
        DropdownMenuItem(
            text = { Text("Manage & Reorder…") },
            leadingIcon = { Icon(AllNotesIcons.Menu, contentDescription = null, modifier = Modifier.size(17.dp)) },
            onClick = {
                onDismiss()
                onAction(WorkspaceAction.OpenDialog(ActiveDialog.MANAGE_SECTIONS))
            }
        )
    }
}

/**
 * Tier 2 Hierarchy Anchor: Full-pill section tab with color-coded indicator and subtle 1dp hairline.
 */
@Composable
internal fun SectionTabChip(
    section: NoteSection,
    sectionsInNotebook: List<NoteSection>,
    isSelected: Boolean,
    onClick: () -> Unit,
    onAction: (WorkspaceAction) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val bgState = animateColorAsState(
        targetValue = if (isSelected) section.accent.containerColor()
        else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = AllNotesMotion.effectsFast(),
        label = "SectionChipBg"
    )
    val fg = if (isSelected) section.accent.contentColor()
    else MaterialTheme.colorScheme.onSurfaceVariant
    val accent = section.accent.accentColor()

    Box {
        CompositionLocalProvider(LocalContentColor provides fg) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .then(
                        if (isSelected) {
                            Modifier.border(1.dp, accent.copy(alpha = 0.45f), CircleShape)
                        } else Modifier
                    )
                    .drawBehind {
                        val radius = size.height / 2f
                        drawRoundRect(
                            color = bgState.value,
                            cornerRadius = CornerRadius(radius, radius)
                        )
                    }
                    .clickableWithContextAction(
                        onClick = onClick,
                        onContextMenu = { menuOpen = true }
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
                if (section.isPinned) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = AllNotesIcons.PinFilled,
                        contentDescription = "Pinned section",
                        tint = accent,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
        SectionContextMenu(
            section = section,
            sectionsInNotebook = sectionsInNotebook,
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onAction = onAction
        )
    }
}

@Composable
internal fun GlobalSearchResultCard(
    hit: GlobalSearchHit,
    searchQuery: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val titleAnnotated = remember(hit.page.title, searchQuery, colorScheme) {
        RichSpanEngine.buildSearchHighlightedString(
            text = hit.page.title.ifBlank { "Untitled Page" },
            colorScheme = colorScheme,
            searchQuery = searchQuery
        )
    }
    val snippetAnnotated = remember(hit.matchSnippet, searchQuery, colorScheme) {
        hit.matchSnippet?.takeIf { it.isNotBlank() }?.let { snippet ->
            RichSpanEngine.buildSearchHighlightedString(
                text = snippet,
                colorScheme = colorScheme,
                searchQuery = searchQuery
            )
        }
    }
    Surface(
        onClick = onClick,
        color = colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = titleAnnotated,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (snippetAnnotated != null) {
                Text(
                    text = snippetAnnotated,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(hit.sectionAccent.accentColor())
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${hit.notebookTitle} › ${hit.sectionTitle}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun EmptyPagesPlaceholder(
    hasSearchQuery: Boolean,
    onClearSearch: () -> Unit,
    onNewPage: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (hasSearchQuery) "No matching pages in this section" else "No pages in this section yet",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            FilledTonalButton(
                onClick = if (hasSearchQuery) onClearSearch else onNewPage
            ) {
                Text(if (hasSearchQuery) "Clear Search" else "New Page")
            }
        }
    }
}

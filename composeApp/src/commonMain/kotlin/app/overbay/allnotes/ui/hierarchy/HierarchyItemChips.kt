package app.overbay.allnotes.ui.hierarchy

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.accentColor
import app.overbay.allnotes.ui.theme.allNotesSpring
import app.overbay.allnotes.ui.theme.containerColor
import app.overbay.allnotes.ui.theme.contentColor
import app.overbay.allnotes.ui.workspace.GlobalSearchHit

@Composable
internal fun NotebookPill(
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
                    .background(notebook.accent.accentColor())
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = notebook.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
internal fun SectionTabChip(
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
internal fun PageHierarchyItem(
    page: NotePage,
    isSelected: Boolean,
    onClick: () -> Unit,
    hasChildren: Boolean = false,
    isCollapsed: Boolean = false,
    onToggleCollapse: (() -> Unit)? = null,
    searchQuery: String = "",
    onTogglePin: (() -> Unit)? = null,
    onRequestDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isSubpage = page.parentPageId != null
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

    val snippet = page.blocks.firstNotNullOfOrNull { block ->
        when (block) {
            is DocumentBlock.Paragraph -> block.text.takeIf { it.isNotBlank() }
            is DocumentBlock.Heading -> block.text.takeIf { it.isNotBlank() }
            is DocumentBlock.Callout -> block.text.takeIf { it.isNotBlank() }
            is DocumentBlock.Checklist -> block.title.takeIf { it.isNotBlank() }
                ?: block.items.firstOrNull { it.text.isNotBlank() }?.text
            is DocumentBlock.BulletList -> block.items.firstOrNull { it.isNotBlank() }
            is DocumentBlock.Attachment -> block.caption.ifBlank { block.fileName }.takeIf { it.isNotBlank() }
            is DocumentBlock.Divider -> null
            else -> null
        }
    } ?: "${page.blocks.size} blocks"

    val titleAnnotated = RichSpanEngine.buildSearchHighlightedString(
        text = page.title.ifBlank { "Untitled Page" },
        colorScheme = MaterialTheme.colorScheme,
        searchQuery = searchQuery
    )
    val snippetAnnotated = RichSpanEngine.buildSearchHighlightedString(
        text = snippet,
        colorScheme = MaterialTheme.colorScheme,
        searchQuery = searchQuery
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = if (isSubpage) 18.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSubpage) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else if (hasChildren && onToggleCollapse != null) {
            IconButton(
                onClick = onToggleCollapse,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isCollapsed) AllNotesIcons.ChevronRight else AllNotesIcons.ChevronDown,
                    contentDescription = if (isCollapsed) "Expand subpages" else "Collapse subpages",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
        }

        Surface(
            onClick = onClick,
            color = containerColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleAnnotated,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = snippetAnnotated,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isSelected && (onTogglePin != null || onRequestDelete != null)) {
                    Spacer(modifier = Modifier.width(4.dp))
                    if (onTogglePin != null) {
                        IconButton(
                            onClick = onTogglePin,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = AllNotesIcons.Pin,
                                contentDescription = if (page.isPinned) "Unpin Page" else "Pin Page",
                                tint = if (page.isPinned) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    if (onRequestDelete != null) {
                        IconButton(
                            onClick = onRequestDelete,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = AllNotesIcons.Delete,
                                contentDescription = "Delete Page",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                } else if (page.isPinned) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = AllNotesIcons.Pin,
                        contentDescription = "Pinned",
                        tint = contentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun GlobalSearchResultCard(
    hit: GlobalSearchHit,
    searchQuery: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(
                text = RichSpanEngine.buildSearchHighlightedString(
                    text = hit.page.title.ifBlank { "Untitled Page" },
                    colorScheme = MaterialTheme.colorScheme,
                    searchQuery = searchQuery
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val matchSnippet = hit.matchSnippet
            if (!matchSnippet.isNullOrBlank()) {
                Text(
                    text = RichSpanEngine.buildSearchHighlightedString(
                        text = matchSnippet,
                        colorScheme = MaterialTheme.colorScheme,
                        searchQuery = searchQuery
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                Text(if (hasSearchQuery) "Clear Search" else "+ New Page")
            }
        }
    }
}

package app.overbay.allnotes.ui.hierarchy

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.AllNotesMotion
import app.overbay.allnotes.ui.theme.accentColor
import app.overbay.allnotes.ui.theme.containerColor
import app.overbay.allnotes.ui.theme.contentColor
import app.overbay.allnotes.ui.workspace.GlobalSearchHit

@Composable
internal fun NotebookPill(
    notebook: Notebook,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgState = animateColorAsState(
        targetValue = if (isSelected) notebook.accent.containerColor() else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = AllNotesMotion.effectsFast(),
        label = "NotebookPillBg"
    )
    val fg = if (isSelected) notebook.accent.contentColor() else MaterialTheme.colorScheme.onSurfaceVariant
    val pillShape = RoundedCornerShape(12.dp)
    CompositionLocalProvider(LocalContentColor provides fg) {
        Row(
            modifier = Modifier
                .clip(pillShape)
                .drawBehind {
                    val radius = 12.dp.toPx()
                    drawRoundRect(
                        color = bgState.value,
                        cornerRadius = CornerRadius(radius, radius)
                    )
                }
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                color = fg,
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
    val bgState = animateColorAsState(
        targetValue = if (isSelected) section.accent.containerColor()
        else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = AllNotesMotion.effectsFast(),
        label = "SectionChipBg"
    )
    val fg = if (isSelected) section.accent.contentColor()
    else MaterialTheme.colorScheme.onSurfaceVariant
    val chipShape = RoundedCornerShape(12.dp)

    CompositionLocalProvider(LocalContentColor provides fg) {
        Row(
            modifier = Modifier
                .clip(chipShape)
                .drawBehind {
                    val radius = 12.dp.toPx()
                    drawRoundRect(
                        color = bgState.value,
                        cornerRadius = CornerRadius(radius, radius)
                    )
                }
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                color = fg,
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
    val colorScheme = MaterialTheme.colorScheme
    val isSubpage = page.parentPageId != null
    val containerColorState = animateColorAsState(
        targetValue = if (isSelected) colorScheme.primaryContainer
        else colorScheme.surfaceContainer,
        animationSpec = AllNotesMotion.effectsFast(),
        label = "PageRowBg"
    )
    val contentColor = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface

    val snippet = remember(page.id, page.updatedAtEpochMs, page.blocks) {
        page.blocks.firstNotNullOfOrNull { block ->
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
        } ?: "Empty page"
    }

    val titleAnnotated = remember(page.title, searchQuery, colorScheme) {
        RichSpanEngine.buildSearchHighlightedString(
            text = page.title.ifBlank { "Untitled Page" },
            colorScheme = colorScheme,
            searchQuery = searchQuery
        )
    }
    val snippetAnnotated = remember(snippet, searchQuery, colorScheme) {
        RichSpanEngine.buildSearchHighlightedString(
            text = snippet,
            colorScheme = colorScheme,
            searchQuery = searchQuery
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = if (isSubpage) 18.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSubpage) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colorScheme.outlineVariant)
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
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
        }

        val itemShape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(itemShape)
                .drawBehind {
                    val radius = 14.dp.toPx()
                    drawRoundRect(
                        color = containerColorState.value,
                        cornerRadius = CornerRadius(radius, radius)
                    )
                }
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleAnnotated,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
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
                            tint = if (page.isPinned) colorScheme.primary else contentColor.copy(alpha = 0.7f),
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
                            tint = colorScheme.error.copy(alpha = 0.85f),
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
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
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

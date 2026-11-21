package app.overbay.allnotes.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.ui.editor.toolbar.StudioTooltip
import app.overbay.allnotes.ui.hierarchy.clickableWithContextAction
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.AllNotesMotion
import app.overbay.allnotes.ui.theme.accentColor
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

/**
 * Floating bottom page switcher pill bar for Desktop, Web, and Tablet viewports.
 * Displays all pages (and indented subpages) in the active section without opening the sidebar.
 */
@Composable
internal fun DesktopBottomPageBar(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val section = state.activeSection ?: return
    val colorScheme = MaterialTheme.colorScheme
    val sectionAccent = section.accent.accentColor()

    Surface(
        modifier = modifier,
        color = colorScheme.surfaceContainerHigh,
        contentColor = colorScheme.onSurface,
        shape = CircleShape,
        shadowElevation = 5.dp,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 52.dp)
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(sectionAccent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            PagePillStripContent(
                pages = state.pagesForSection,
                activePageId = state.activePage?.id,
                sectionAccent = sectionAccent,
                compact = false,
                onAction = onAction,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

/**
 * Shared horizontally scrollable row of page pills with active-page auto-centering and
 * an anchored `+` button to create a new page in the current section.
 */
@Composable
internal fun PagePillStripContent(
    pages: List<NotePage>,
    activePageId: String?,
    sectionAccent: Color,
    compact: Boolean,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(activePageId, pages.size) {
        if (activePageId != null) {
            val idx = pages.indexOfFirst { it.id == activePageId }
            if (idx >= 0) {
                val layoutInfo = listState.layoutInfo
                val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
                val fullyVisible = visibleItem != null &&
                    visibleItem.offset >= 0 &&
                    (visibleItem.offset + visibleItem.size) <= layoutInfo.viewportEndOffset
                if (!fullyVisible) {
                    listState.animateScrollToItem(idx)
                }
            }
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pages.isEmpty()) {
            Text(
                text = "No pages",
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        } else {
            LazyRow(
                state = listState,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .animateContentSize(animationSpec = AllNotesMotion.spatialFast())
                    .clip(CircleShape),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(0.dp)
            ) {
                items(items = pages, key = { it.id }, contentType = { "bottom-page-pill" }) { page ->
                    PagePillChip(
                        page = page,
                        isSelected = page.id == activePageId,
                        sectionAccent = sectionAccent,
                        compact = compact,
                        onAction = onAction
                    )
                }
            }
        }

        VerticalDivider(
            modifier = Modifier
                .height(22.dp)
                .padding(horizontal = 6.dp),
            color = colorScheme.outlineVariant.copy(alpha = 0.45f)
        )

        StudioTooltip(
            label = "New Page  ·  Ctrl+N",
            enabled = !compact,
            position = TooltipAnchorPosition.Above
        ) {
            IconButton(
                onClick = { onAction(WorkspaceAction.CreatePageInstant(false)) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = AllNotesIcons.Add,
                    contentDescription = "New Page in section",
                    tint = colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PagePillChip(
    page: NotePage,
    isSelected: Boolean,
    sectionAccent: Color,
    compact: Boolean,
    onAction: (WorkspaceAction) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var menuOpen by remember(page.id) { mutableStateOf(false) }

    val bgState = animateColorAsState(
        targetValue = if (isSelected) colorScheme.primaryContainer
        else colorScheme.surfaceContainer,
        animationSpec = AllNotesMotion.effectsFast(),
        label = "BottomPagePillBg"
    )
    val fg = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant
    val isSubpage = page.parentPageId != null

    Box {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(CircleShape)
                .then(
                    if (isSelected) {
                        Modifier.border(1.dp, sectionAccent.copy(alpha = 0.5f), CircleShape)
                    } else Modifier
                )
                .drawBehind {
                    val r = size.height / 2f
                    drawRoundRect(color = bgState.value, cornerRadius = CornerRadius(r, r))
                }
                .clickableWithContextAction(
                    onClick = { onAction(WorkspaceAction.SelectPage(page.id)) },
                    onContextMenu = { menuOpen = true }
                )
                .padding(
                    horizontal = if (compact) 15.dp else 16.dp,
                    vertical = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (page.isPinned) {
                Icon(
                    imageVector = AllNotesIcons.PinFilled,
                    contentDescription = "Pinned",
                    tint = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            if (isSubpage) {
                Text(
                    text = "↳",
                    style = MaterialTheme.typography.labelMedium,
                    color = fg.copy(alpha = 0.72f)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = page.title.ifBlank { "Untitled Page" },
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = if (compact) 15.sp else 14.5.sp,
                    lineHeight = 20.sp
                ),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = if (compact) 172.dp else 200.dp)
            )
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (page.isPinned) "Unpin Page" else "Pin Page to Front") },
                leadingIcon = {
                    Icon(
                        if (page.isPinned) AllNotesIcons.Pin else AllNotesIcons.PinFilled,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                },
                onClick = {
                    menuOpen = false
                    onAction(WorkspaceAction.TogglePinPage(page.id))
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("Delete Page", color = colorScheme.error) },
                leadingIcon = {
                    Icon(
                        AllNotesIcons.Delete,
                        contentDescription = null,
                        tint = colorScheme.error,
                        modifier = Modifier.size(17.dp)
                    )
                },
                onClick = {
                    menuOpen = false
                    onAction(WorkspaceAction.RequestDeletePage(page.id))
                }
            )
        }
    }
}

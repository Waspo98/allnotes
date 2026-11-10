package app.overbay.allnotes.ui.editor.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.RichSpanKind
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.workspace.ActiveDialog
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MobileFloatingToolbar(
    state: WorkspaceUiState,
    onRequestMediaPicker: () -> Unit,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeBlock = state.activeBlock

    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (activeBlock) {
                is DocumentBlock.Paragraph -> {
                    val s = state.selectionStart
                    val e = state.selectionEnd
                    ToolbarToggleIcon(
                        icon = AllNotesIcons.FormatBold,
                        contentDescription = "Bold",
                        selected = RichSpanEngine.hasActiveStyle(activeBlock, s, e, RichSpanKind.BOLD),
                        onClick = { onAction(WorkspaceAction.ToggleSelectionSpan(RichSpanKind.BOLD)) }
                    )
                    ToolbarToggleIcon(
                        icon = AllNotesIcons.FormatItalic,
                        contentDescription = "Italic",
                        selected = RichSpanEngine.hasActiveStyle(activeBlock, s, e, RichSpanKind.ITALIC),
                        onClick = { onAction(WorkspaceAction.ToggleSelectionSpan(RichSpanKind.ITALIC)) }
                    )
                    ToolbarToggleIcon(
                        icon = AllNotesIcons.FormatUnderlined,
                        contentDescription = "Underline",
                        selected = RichSpanEngine.hasActiveStyle(activeBlock, s, e, RichSpanKind.UNDERLINE),
                        onClick = { onAction(WorkspaceAction.ToggleSelectionSpan(RichSpanKind.UNDERLINE)) }
                    )
                    ToolbarToggleIcon(
                        icon = AllNotesIcons.FormatStrikethrough,
                        contentDescription = "Strikethrough",
                        selected = RichSpanEngine.hasActiveStyle(activeBlock, s, e, RichSpanKind.STRIKETHROUGH),
                        onClick = { onAction(WorkspaceAction.ToggleSelectionSpan(RichSpanKind.STRIKETHROUGH)) }
                    )
                    ToolbarToggleIcon(
                        icon = AllNotesIcons.Highlight,
                        contentDescription = "Highlight",
                        selected = RichSpanEngine.hasActiveStyle(activeBlock, s, e, RichSpanKind.HIGHLIGHT),
                        onClick = { onAction(WorkspaceAction.ToggleSelectionSpan(RichSpanKind.HIGHLIGHT)) }
                    )
                    ToolbarToggleIcon(
                        icon = AllNotesIcons.Code,
                        contentDescription = "Inline Code",
                        selected = RichSpanEngine.hasActiveStyle(activeBlock, s, e, RichSpanKind.INLINE_CODE),
                        onClick = { onAction(WorkspaceAction.ToggleSelectionSpan(RichSpanKind.INLINE_CODE)) }
                    )
                    ToolbarToggleIcon(
                        icon = AllNotesIcons.Palette,
                        contentDescription = "Accent Color",
                        selected = RichSpanEngine.hasActiveStyle(activeBlock, s, e, RichSpanKind.COLOR_ACCENT),
                        onClick = { onAction(WorkspaceAction.ToggleSelectionSpan(RichSpanKind.COLOR_ACCENT)) }
                    )
                    ToolbarToggleIcon(
                        icon = AllNotesIcons.Link,
                        contentDescription = "Insert Link",
                        selected = RichSpanEngine.hasActiveStyle(activeBlock, s, e, RichSpanKind.LINK),
                        onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.INSERT_LINK)) }
                    )
                }
                is DocumentBlock.Heading -> {
                    (1..3).forEach { lvl ->
                        val selected = activeBlock.level == lvl
                        Surface(
                            onClick = { onAction(WorkspaceAction.UpdateHeadingLevel(activeBlock.id, lvl)) },
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "H$lvl",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                is DocumentBlock.BulletList -> {
                    ToolbarToggleIcon(
                        icon = AllNotesIcons.BulletList,
                        contentDescription = "Bulleted list",
                        selected = !activeBlock.isNumbered,
                        onClick = { if (activeBlock.isNumbered) onAction(WorkspaceAction.ToggleBulletListKind(activeBlock.id)) }
                    )
                    ToolbarToggleIcon(
                        icon = AllNotesIcons.NumberedList,
                        contentDescription = "Numbered list",
                        selected = activeBlock.isNumbered,
                        onClick = { if (!activeBlock.isNumbered) onAction(WorkspaceAction.ToggleBulletListKind(activeBlock.id)) }
                    )
                }
                is DocumentBlock.Callout -> {
                    CalloutTone.entries.forEach { tone ->
                        val selected = activeBlock.tone == tone
                        Surface(
                            onClick = { onAction(WorkspaceAction.UpdateCalloutTone(activeBlock.id, tone)) },
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = tone.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                else -> {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.InsertHeading(2, state.activeBlockId)) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(AllNotesIcons.Heading, contentDescription = "Insert Heading", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { onAction(WorkspaceAction.InsertChecklist(state.activeBlockId)) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(AllNotesIcons.Checklist, contentDescription = "Insert Checklist", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { onAction(WorkspaceAction.InsertTable(state.activeBlockId)) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(AllNotesIcons.Table, contentDescription = "Insert Table", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onRequestMediaPicker,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(AllNotesIcons.Image, contentDescription = "Attach Image or File", modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (activeBlock != null) {
                VerticalDivider(modifier = Modifier.height(22.dp).padding(horizontal = 4.dp))
                IconButton(
                    onClick = { onAction(WorkspaceAction.MoveBlock(activeBlock.id, -1)) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(AllNotesIcons.ArrowUp, contentDescription = "Move block up", modifier = Modifier.size(17.dp))
                }
                IconButton(
                    onClick = { onAction(WorkspaceAction.MoveBlock(activeBlock.id, 1)) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(AllNotesIcons.ArrowDown, contentDescription = "Move block down", modifier = Modifier.size(17.dp))
                }
                IconButton(
                    onClick = { onAction(WorkspaceAction.DeleteBlock(activeBlock.id)) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        AllNotesIcons.Delete,
                        contentDescription = "Delete block",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun ToolbarToggleIcon(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

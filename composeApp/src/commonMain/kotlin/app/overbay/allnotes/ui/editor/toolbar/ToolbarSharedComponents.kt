package app.overbay.allnotes.ui.editor.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.RichSpanKind
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.workspace.ActiveDialog
import app.overbay.allnotes.ui.workspace.SlashCommandKind
import app.overbay.allnotes.ui.workspace.WorkspaceAction

private data class SpanButtonSpec(
    val icon: ImageVector,
    val label: String,
    val shortcut: String?,
    val kind: RichSpanKind
)

private val InlineSpanSpecs = listOf(
    SpanButtonSpec(AllNotesIcons.FormatBold, "Bold", "Ctrl+B", RichSpanKind.BOLD),
    SpanButtonSpec(AllNotesIcons.FormatItalic, "Italic", "Ctrl+I", RichSpanKind.ITALIC),
    SpanButtonSpec(AllNotesIcons.FormatUnderlined, "Underline", "Ctrl+U", RichSpanKind.UNDERLINE),
    SpanButtonSpec(AllNotesIcons.FormatStrikethrough, "Strikethrough", "Ctrl+Shift+S", RichSpanKind.STRIKETHROUGH),
    SpanButtonSpec(AllNotesIcons.Highlight, "Highlight", null, RichSpanKind.HIGHLIGHT),
    SpanButtonSpec(AllNotesIcons.Code, "Inline Code", null, RichSpanKind.INLINE_CODE),
    SpanButtonSpec(AllNotesIcons.Palette, "Accent Text Color", null, RichSpanKind.COLOR_ACCENT)
)

private val ConvertibleBlockTargets = listOf(
    SlashCommandKind.PARAGRAPH to AllNotesIcons.Paragraph,
    SlashCommandKind.HEADING_1 to AllNotesIcons.Heading,
    SlashCommandKind.HEADING_2 to AllNotesIcons.Heading,
    SlashCommandKind.HEADING_3 to AllNotesIcons.Heading,
    SlashCommandKind.CHECKLIST to AllNotesIcons.Checklist,
    SlashCommandKind.BULLET_LIST to AllNotesIcons.BulletList,
    SlashCommandKind.NUMBERED_LIST to AllNotesIcons.NumberedList,
    SlashCommandKind.CALLOUT to AllNotesIcons.Callout,
    SlashCommandKind.CODE to AllNotesIcons.Code
)

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

@Composable
internal fun ParagraphInlineFormatButtons(
    activeParagraph: DocumentBlock.Paragraph?,
    selectionStart: Int,
    selectionEnd: Int,
    showShortcuts: Boolean,
    onAction: (WorkspaceAction) -> Unit
) {
    InlineSpanSpecs.forEach { spec ->
        val description = if (showShortcuts && spec.shortcut != null) {
            "${spec.label} (${spec.shortcut})"
        } else {
            spec.label
        }
        ToolbarToggleIcon(
            icon = spec.icon,
            contentDescription = description,
            selected = activeParagraph != null && RichSpanEngine.hasActiveStyle(
                activeParagraph,
                selectionStart,
                selectionEnd,
                spec.kind
            ),
            onClick = { onAction(WorkspaceAction.ToggleSelectionSpan(spec.kind)) }
        )
    }
    ToolbarToggleIcon(
        icon = AllNotesIcons.Link,
        contentDescription = if (showShortcuts) "Insert Link (Ctrl+K)" else "Insert Link",
        selected = activeParagraph != null && RichSpanEngine.hasActiveStyle(
            activeParagraph,
            selectionStart,
            selectionEnd,
            RichSpanKind.LINK
        ),
        onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.INSERT_LINK)) }
    )

    val hasClearableFormatting = activeParagraph != null && activeParagraph.effectiveSpans().isNotEmpty()
    if (hasClearableFormatting) {
        Surface(
            onClick = { onAction(WorkspaceAction.ClearActiveParagraphFormatting) },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "Tx",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.LineThrough
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
internal fun BlockTypeConverterPill(
    activeBlock: DocumentBlock?,
    onAction: (WorkspaceAction) -> Unit
) {
    val currentLabel = when (activeBlock) {
        is DocumentBlock.Paragraph -> "Body"
        is DocumentBlock.Heading -> "H${activeBlock.level}"
        is DocumentBlock.Checklist -> "Todo"
        is DocumentBlock.BulletList -> if (activeBlock.isNumbered) "1. List" else "Bullets"
        is DocumentBlock.Callout -> "Callout"
        is DocumentBlock.CodeSnippet -> "Code"
        else -> return
    }
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            onClick = { menuExpanded = true },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = AllNotesIcons.ChevronDown,
                    contentDescription = "Convert block type",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            ConvertibleBlockTargets.forEach { (kind, icon) ->
                DropdownMenuItem(
                    text = { Text(kind.title) },
                    leadingIcon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onAction(WorkspaceAction.ConvertActiveBlock(kind))
                    }
                )
            }
        }
    }
    Spacer(modifier = Modifier.width(2.dp))
}

package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.allNotesSpring
import app.overbay.allnotes.ui.workspace.WorkspaceAction

@Composable
fun DocumentBlockView(
    block: DocumentBlock,
    isActive: Boolean,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val indicatorColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = allNotesSpring()
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.hasFocus && !isActive) {
                    onAction(WorkspaceAction.SetActiveBlock(block.id))
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isActive) {
                    onAction(WorkspaceAction.SetActiveBlock(block.id))
                }
            }
    ) {
        AnimatedVisibility(
            visible = isActive,
            enter = expandVertically(animationSpec = allNotesSpring()) + fadeIn(animationSpec = allNotesSpring()),
            exit = shrinkVertically(animationSpec = allNotesSpring()) + fadeOut(animationSpec = allNotesSpring())
        ) {
            ContextualBlockActionBar(
                block = block,
                onAction = onAction,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(indicatorColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                when (block) {
                    is DocumentBlock.Heading -> HeadingBlockContent(block, onAction)
                    is DocumentBlock.Paragraph -> ParagraphBlockContent(block, onAction)
                    is DocumentBlock.Checklist -> ChecklistBlockContent(block, onAction)
                    is DocumentBlock.BulletList -> BulletListBlockContent(block, onAction)
                    is DocumentBlock.Table -> ResponsiveTableBlockContent(block, isActive, onAction)
                    is DocumentBlock.Callout -> CalloutBlockContent(block, onAction)
                    is DocumentBlock.CodeSnippet -> CodeSnippetBlockContent(block, onAction)
                }
            }
        }
    }
}

@Composable
private fun ContextualBlockActionBar(
    block: DocumentBlock,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when (block) {
                is DocumentBlock.Heading -> {
                    (1..3).forEach { lvl ->
                        FilterChip(
                            selected = block.level == lvl,
                            onClick = { onAction(WorkspaceAction.UpdateHeadingLevel(block.id, lvl)) },
                            label = { Text("H$lvl", fontWeight = FontWeight.Bold) }
                        )
                    }
                }
                is DocumentBlock.Paragraph -> {
                    FilterChip(
                        selected = block.isBold,
                        onClick = { onAction(WorkspaceAction.ToggleParagraphStyle(block.id, "bold")) },
                        label = { Text("B", fontWeight = FontWeight.Bold) }
                    )
                    FilterChip(
                        selected = block.isItalic,
                        onClick = { onAction(WorkspaceAction.ToggleParagraphStyle(block.id, "italic")) },
                        label = { Text("I", fontStyle = FontStyle.Italic) }
                    )
                    FilterChip(
                        selected = block.isUnderlined,
                        onClick = { onAction(WorkspaceAction.ToggleParagraphStyle(block.id, "underline")) },
                        label = { Text("U", textDecoration = TextDecoration.Underline) }
                    )
                    FilterChip(
                        selected = block.isStrikethrough,
                        onClick = { onAction(WorkspaceAction.ToggleParagraphStyle(block.id, "strikethrough")) },
                        label = { Text("S", textDecoration = TextDecoration.LineThrough) }
                    )
                    FilterChip(
                        selected = block.isHighlighted,
                        onClick = { onAction(WorkspaceAction.ToggleParagraphStyle(block.id, "highlight")) },
                        label = { Text("Highlight") }
                    )
                }
                is DocumentBlock.BulletList -> {
                    FilterChip(
                        selected = !block.isNumbered,
                        onClick = { if (block.isNumbered) onAction(WorkspaceAction.ToggleBulletListKind(block.id)) },
                        label = { Text("• Bulleted") }
                    )
                    FilterChip(
                        selected = block.isNumbered,
                        onClick = { if (!block.isNumbered) onAction(WorkspaceAction.ToggleBulletListKind(block.id)) },
                        label = { Text("1. Numbered") }
                    )
                }
                is DocumentBlock.Callout -> {
                    CalloutTone.entries.forEach { tone ->
                        FilterChip(
                            selected = block.tone == tone,
                            onClick = { onAction(WorkspaceAction.UpdateCalloutTone(block.id, tone)) },
                            label = { Text(tone.label) }
                        )
                    }
                }
                is DocumentBlock.CodeSnippet -> {
                    listOf("kotlin", "python", "bash", "sql", "json", "yaml").forEach { lang ->
                        FilterChip(
                            selected = block.language.equals(lang, ignoreCase = true),
                            onClick = { onAction(WorkspaceAction.UpdateCodeLanguage(block.id, lang)) },
                            label = { Text(lang) }
                        )
                    }
                }
                is DocumentBlock.Table -> {
                    FilterChip(
                        selected = false,
                        onClick = { onAction(WorkspaceAction.AddTableRow(block.id)) },
                        label = { Text("+ Row") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { onAction(WorkspaceAction.AddTableColumn(block.id, "Column ${block.headers.size + 1}")) },
                        label = { Text("+ Column") }
                    )
                }
                is DocumentBlock.Checklist -> {
                    Text(
                        text = "Checklist Block",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = { onAction(WorkspaceAction.MoveBlock(block.id, -1)) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(AllNotesIcons.ArrowUp, contentDescription = "Move up", modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = { onAction(WorkspaceAction.MoveBlock(block.id, 1)) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(AllNotesIcons.ArrowDown, contentDescription = "Move down", modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = { onAction(WorkspaceAction.DeleteBlock(block.id)) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    AllNotesIcons.Delete,
                    contentDescription = "Remove block",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

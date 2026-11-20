package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.editor.SlashCommandPopup
import app.overbay.allnotes.ui.theme.AllNotesMotion
import app.overbay.allnotes.ui.workspace.EditorFocusRequest
import app.overbay.allnotes.ui.workspace.EditorInputController
import app.overbay.allnotes.ui.workspace.SlashCommandKind
import app.overbay.allnotes.ui.workspace.WorkspaceAction

@Composable
fun DocumentBlockView(
    block: DocumentBlock,
    isActive: Boolean,
    isCompactMobile: Boolean,
    searchQuery: String,
    focusRequest: EditorFocusRequest?,
    onRequestMediaPicker: () -> Unit,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val slashMatches = if (isActive && block is DocumentBlock.Paragraph) {
        EditorInputController.matchingSlashCommands(block.text)
    } else {
        emptyList()
    }
    var selectedSlashIndex by remember(block.id, slashMatches.size) { mutableIntStateOf(0) }

    val executeCommand: (SlashCommandKind) -> Unit = { cmd ->
        if (cmd == SlashCommandKind.ATTACHMENT) {
            onAction(WorkspaceAction.UpdateBlockText(block.id, ""))
            onRequestMediaPicker()
        } else {
            onAction(WorkspaceAction.ExecuteSlashCommand(block.id, cmd))
        }
    }

    val handleSlashKey: (KeyEvent) -> Boolean = { event ->
        if (slashMatches.isEmpty()) {
            false
        } else {
            when (event.key) {
                Key.DirectionDown -> {
                    selectedSlashIndex = (selectedSlashIndex + 1) % slashMatches.size
                    true
                }
                Key.DirectionUp -> {
                    selectedSlashIndex = (selectedSlashIndex - 1 + slashMatches.size) % slashMatches.size
                    true
                }
                Key.Enter, Key.Tab -> {
                    val chosen = slashMatches.getOrNull(selectedSlashIndex.coerceIn(0, slashMatches.lastIndex))
                    if (chosen != null) executeCommand(chosen)
                    true
                }
                Key.Escape -> {
                    onAction(WorkspaceAction.UpdateBlockText(block.id, ""))
                    true
                }
                else -> false
            }
        }
    }

    val isPlainTextBlock = block is DocumentBlock.Paragraph ||
        block is DocumentBlock.Heading ||
        block is DocumentBlock.Checklist ||
        block is DocumentBlock.BulletList
    val focusCushionColor = animateColorAsState(
        targetValue = if (isActive && isPlainTextBlock) {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.42f)
        } else {
            Color.Transparent
        },
        animationSpec = AllNotesMotion.effectsFast(),
        label = "ActiveBlockFocusCushion"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val c = focusCushionColor.value
                if (c.alpha > 0.005f) {
                    val r = 10.dp.toPx()
                    drawRoundRect(color = c, cornerRadius = CornerRadius(r, r))
                }
            }
            .then(if (isPlainTextBlock) Modifier.padding(horizontal = 6.dp, vertical = 2.dp) else Modifier)
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
        when (block) {
            is DocumentBlock.Heading -> HeadingBlockContent(block, searchQuery, focusRequest, onAction)
            is DocumentBlock.Paragraph -> ParagraphBlockContent(
                block = block,
                isActive = isActive,
                searchQuery = searchQuery,
                focusRequest = focusRequest,
                onAction = onAction,
                onSlashKeyEvent = if (slashMatches.isNotEmpty()) handleSlashKey else null
            )
            is DocumentBlock.Checklist -> ChecklistBlockContent(block, isActive, searchQuery, focusRequest, onAction)
            is DocumentBlock.BulletList -> BulletListBlockContent(block, isActive, searchQuery, focusRequest, onAction)
            is DocumentBlock.Table -> ResponsiveTableBlockContent(block, isActive, isCompactMobile, searchQuery, onAction)
            is DocumentBlock.Callout -> CalloutBlockContent(block, searchQuery, focusRequest, onAction)
            is DocumentBlock.CodeSnippet -> CodeSnippetBlockContent(block, isActive, searchQuery, focusRequest, onAction)
            is DocumentBlock.Divider -> DividerBlockContent(isActive)
            is DocumentBlock.Attachment -> AttachmentBlockContent(block, isActive, onAction)
        }

        SlashCommandPopup(
            commands = slashMatches,
            selectedIndex = selectedSlashIndex.coerceIn(0, (slashMatches.size - 1).coerceAtLeast(0)),
            onSelectCommand = executeCommand
        )
    }
}

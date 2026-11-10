package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.editor.SlashCommandPopup
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
        when (block) {
            is DocumentBlock.Heading -> HeadingBlockContent(block, searchQuery, focusRequest, onAction)
            is DocumentBlock.Paragraph -> ParagraphBlockContent(block, isActive, searchQuery, focusRequest, onAction)
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
            onSelectCommand = { cmd ->
                if (cmd == SlashCommandKind.ATTACHMENT) {
                    onAction(WorkspaceAction.UpdateBlockText(block.id, ""))
                    onRequestMediaPicker()
                } else {
                    onAction(WorkspaceAction.ExecuteSlashCommand(block.id, cmd))
                }
            }
        )
    }
}

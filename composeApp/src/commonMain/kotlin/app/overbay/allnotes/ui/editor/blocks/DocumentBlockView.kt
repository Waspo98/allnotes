package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.workspace.WorkspaceAction

@Composable
fun DocumentBlockView(
    block: DocumentBlock,
    isActive: Boolean,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
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
            is DocumentBlock.Heading -> HeadingBlockContent(block, onAction)
            is DocumentBlock.Paragraph -> ParagraphBlockContent(block, onAction)
            is DocumentBlock.Checklist -> ChecklistBlockContent(block, isActive, onAction)
            is DocumentBlock.BulletList -> BulletListBlockContent(block, isActive, onAction)
            is DocumentBlock.Table -> ResponsiveTableBlockContent(block, isActive, onAction)
            is DocumentBlock.Callout -> CalloutBlockContent(block, onAction)
            is DocumentBlock.CodeSnippet -> CodeSnippetBlockContent(block, isActive, onAction)
        }
    }
}

package app.overbay.allnotes.ui.editor.toolbar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

@Composable
fun DesktopFormattingRibbon(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    state.activePage ?: return
    val activeBlock = state.activeBlock
    val activeParagraph = activeBlock as? DocumentBlock.Paragraph
    val activeHeading = activeBlock as? DocumentBlock.Heading

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarToggleIcon(
                icon = AllNotesIcons.FormatBold,
                contentDescription = "Bold (Ctrl+B)",
                selected = activeParagraph?.isBold == true,
                onClick = { onAction(WorkspaceAction.ToggleActiveParagraphStyle("bold")) }
            )
            ToolbarToggleIcon(
                icon = AllNotesIcons.FormatItalic,
                contentDescription = "Italic (Ctrl+I)",
                selected = activeParagraph?.isItalic == true,
                onClick = { onAction(WorkspaceAction.ToggleActiveParagraphStyle("italic")) }
            )
            ToolbarToggleIcon(
                icon = AllNotesIcons.FormatUnderlined,
                contentDescription = "Underline (Ctrl+U)",
                selected = activeParagraph?.isUnderlined == true,
                onClick = { onAction(WorkspaceAction.ToggleActiveParagraphStyle("underline")) }
            )
            ToolbarToggleIcon(
                icon = AllNotesIcons.FormatStrikethrough,
                contentDescription = "Strikethrough (Ctrl+Shift+S)",
                selected = activeParagraph?.isStrikethrough == true,
                onClick = { onAction(WorkspaceAction.ToggleActiveParagraphStyle("strikethrough")) }
            )
            ToolbarToggleIcon(
                icon = AllNotesIcons.Highlight,
                contentDescription = "Highlight",
                selected = activeParagraph?.isHighlighted == true,
                onClick = { onAction(WorkspaceAction.ToggleActiveParagraphStyle("highlight")) }
            )

            if (activeHeading != null) {
                VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))
                (1..3).forEach { lvl ->
                    val selected = activeHeading.level == lvl
                    Surface(
                        onClick = { onAction(WorkspaceAction.UpdateHeadingLevel(activeHeading.id, lvl)) },
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "H$lvl",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))

            IconButton(
                onClick = { onAction(WorkspaceAction.InsertHeading(2, state.activeBlockId)) },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(AllNotesIcons.Heading, contentDescription = "Insert Heading", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { onAction(WorkspaceAction.InsertParagraph(state.activeBlockId)) },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(AllNotesIcons.Paragraph, contentDescription = "Insert Paragraph", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { onAction(WorkspaceAction.InsertChecklist(state.activeBlockId)) },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(AllNotesIcons.Checklist, contentDescription = "Insert Checklist", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { onAction(WorkspaceAction.InsertBulletList(isNumbered = false, afterBlockId = state.activeBlockId)) },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(AllNotesIcons.BulletList, contentDescription = "Insert Bullet List", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { onAction(WorkspaceAction.InsertBulletList(isNumbered = true, afterBlockId = state.activeBlockId)) },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(AllNotesIcons.NumberedList, contentDescription = "Insert Numbered List", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { onAction(WorkspaceAction.InsertTable(state.activeBlockId)) },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(AllNotesIcons.Table, contentDescription = "Insert Table", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { onAction(WorkspaceAction.InsertCallout(state.activeBlockId)) },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(AllNotesIcons.Callout, contentDescription = "Insert Callout", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { onAction(WorkspaceAction.InsertCode(state.activeBlockId)) },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(AllNotesIcons.Code, contentDescription = "Insert Code Block", modifier = Modifier.size(18.dp))
            }

            if (activeBlock != null) {
                Spacer(modifier = Modifier.weight(1f))
                VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))
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

package app.overbay.allnotes.ui.editor.toolbar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
    val page = state.activePage ?: return
    val activeBlock = state.activeBlock
    val activeParagraph = activeBlock as? DocumentBlock.Paragraph
    val activeHeading = activeBlock as? DocumentBlock.Heading

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            // Row 1: Typography & Active Block Formatting + Page Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FORMAT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))

                FilterChip(
                    selected = activeParagraph?.isBold == true,
                    onClick = { onAction(WorkspaceAction.ToggleActiveParagraphStyle("bold")) },
                    label = { Text("B", fontWeight = FontWeight.ExtraBold) }
                )
                FilterChip(
                    selected = activeParagraph?.isItalic == true,
                    onClick = { onAction(WorkspaceAction.ToggleActiveParagraphStyle("italic")) },
                    label = { Text("I", fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold) }
                )
                FilterChip(
                    selected = activeParagraph?.isUnderlined == true,
                    onClick = { onAction(WorkspaceAction.ToggleActiveParagraphStyle("underline")) },
                    label = { Text("U", textDecoration = TextDecoration.Underline) }
                )
                FilterChip(
                    selected = activeParagraph?.isStrikethrough == true,
                    onClick = { onAction(WorkspaceAction.ToggleActiveParagraphStyle("strikethrough")) },
                    label = { Text("S", textDecoration = TextDecoration.LineThrough) }
                )
                FilterChip(
                    selected = activeParagraph?.isHighlighted == true,
                    onClick = { onAction(WorkspaceAction.ToggleActiveParagraphStyle("highlight")) },
                    label = { Text("Highlight") }
                )

                if (activeHeading != null) {
                    VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
                    (1..3).forEach { lvl ->
                        FilterChip(
                            selected = activeHeading.level == lvl,
                            onClick = { onAction(WorkspaceAction.UpdateHeadingLevel(activeHeading.id, lvl)) },
                            label = { Text("H$lvl", fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                if (activeBlock != null) {
                    VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
                    IconButton(
                        onClick = { onAction(WorkspaceAction.MoveBlock(activeBlock.id, -1)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(AllNotesIcons.ArrowUp, contentDescription = "Move block up", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { onAction(WorkspaceAction.MoveBlock(activeBlock.id, 1)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(AllNotesIcons.ArrowDown, contentDescription = "Move block down", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { onAction(WorkspaceAction.DeleteBlock(activeBlock.id)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            AllNotesIcons.Delete,
                            contentDescription = "Delete active block",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                FilterChip(
                    selected = page.isPinned,
                    onClick = { onAction(WorkspaceAction.ToggleActivePagePin) },
                    leadingIcon = {
                        Icon(AllNotesIcons.Pin, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text(if (page.isPinned) "Pinned" else "Pin Page") }
                )
                AssistChip(
                    onClick = { onAction(WorkspaceAction.TogglePageSubpageIndent(page.id)) },
                    leadingIcon = {
                        Icon(
                            if (page.parentPageId != null) AllNotesIcons.Outdent else AllNotesIcons.Indent,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(if (page.parentPageId != null) "Promote Page" else "Make Subpage") }
                )
                AssistChip(
                    onClick = { onAction(WorkspaceAction.DeleteActivePage) },
                    leadingIcon = {
                        Icon(
                            AllNotesIcons.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Delete Page", color = MaterialTheme.colorScheme.error) }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Row 2: Rich Block Insertion
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INSERT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(4.dp))

                AssistChip(
                    onClick = { onAction(WorkspaceAction.InsertHeading(2, state.activeBlockId)) },
                    leadingIcon = { Icon(AllNotesIcons.Heading, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Heading") }
                )
                AssistChip(
                    onClick = { onAction(WorkspaceAction.InsertParagraph(state.activeBlockId)) },
                    leadingIcon = { Icon(AllNotesIcons.Paragraph, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Paragraph") }
                )
                AssistChip(
                    onClick = { onAction(WorkspaceAction.InsertChecklist(state.activeBlockId)) },
                    leadingIcon = { Icon(AllNotesIcons.Checklist, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Checklist") }
                )
                AssistChip(
                    onClick = { onAction(WorkspaceAction.InsertBulletList(isNumbered = false, afterBlockId = state.activeBlockId)) },
                    leadingIcon = { Icon(AllNotesIcons.BulletList, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Bullet List") }
                )
                AssistChip(
                    onClick = { onAction(WorkspaceAction.InsertBulletList(isNumbered = true, afterBlockId = state.activeBlockId)) },
                    leadingIcon = { Icon(AllNotesIcons.BulletList, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("1. Numbered List") }
                )
                AssistChip(
                    onClick = { onAction(WorkspaceAction.InsertTable(state.activeBlockId)) },
                    leadingIcon = { Icon(AllNotesIcons.Table, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Table") }
                )
                AssistChip(
                    onClick = { onAction(WorkspaceAction.InsertCallout(state.activeBlockId)) },
                    leadingIcon = { Icon(AllNotesIcons.Callout, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Callout") }
                )
                AssistChip(
                    onClick = { onAction(WorkspaceAction.InsertCode(state.activeBlockId)) },
                    leadingIcon = { Icon(AllNotesIcons.Code, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Code") }
                )
            }
        }
    }
}

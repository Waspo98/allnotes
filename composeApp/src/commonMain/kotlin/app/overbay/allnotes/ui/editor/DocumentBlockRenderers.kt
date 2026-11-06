package app.overbay.allnotes.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.theme.allNotesSpring

@Composable
fun DocumentBlockView(
    block: DocumentBlock,
    onUpdateText: (String) -> Unit,
    onToggleParagraphStyle: (String) -> Unit,
    onToggleChecklistItem: (String) -> Unit,
    onAddChecklistItem: (String) -> Unit,
    onDeleteBlock: () -> Unit,
    modifier: Modifier = Modifier,
    onUpdateHeadingLevel: (Int) -> Unit = {},
    onAddBulletListItem: (String) -> Unit = {},
    onUpdateTableCell: (rowIndex: Int, colIndex: Int, text: String) -> Unit = { _, _, _ -> },
    onAddTableRow: () -> Unit = {}
) {
    when (block) {
        is DocumentBlock.Heading -> HeadingBlockView(block, onUpdateText, onUpdateHeadingLevel, onDeleteBlock, modifier)
        is DocumentBlock.Paragraph -> ParagraphBlockView(block, onUpdateText, onToggleParagraphStyle, onDeleteBlock, modifier)
        is DocumentBlock.Checklist -> ChecklistBlockView(block, onToggleChecklistItem, onAddChecklistItem, onDeleteBlock, modifier)
        is DocumentBlock.BulletList -> BulletListBlockView(block, onAddBulletListItem, onDeleteBlock, modifier)
        is DocumentBlock.Table -> ResponsiveTableBlockView(block, onUpdateTableCell, onAddTableRow, onDeleteBlock, modifier)
        is DocumentBlock.Callout -> CalloutBlockView(block, onUpdateText, onDeleteBlock, modifier)
        is DocumentBlock.CodeSnippet -> CodeSnippetBlockView(block, onUpdateText, onDeleteBlock, modifier)
    }
}

@Composable
private fun HeadingBlockView(
    block: DocumentBlock.Heading,
    onUpdateText: (String) -> Unit,
    onUpdateLevel: (Int) -> Unit = {},
    onDeleteBlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                (1..3).forEach { lvl ->
                    FilterChip(
                        selected = block.level == lvl,
                        onClick = { onUpdateLevel(lvl) },
                        label = { Text("H$lvl") }
                    )
                }
            }
            TextButton(onClick = onDeleteBlock) {
                Text("Remove", style = MaterialTheme.typography.labelSmall)
            }
        }
        val headingStyle = when (block.level) {
            1 -> MaterialTheme.typography.headlineMedium
            2 -> MaterialTheme.typography.headlineSmall
            else -> MaterialTheme.typography.titleLarge
        }
        OutlinedTextField(
            value = block.text,
            onValueChange = onUpdateText,
            textStyle = headingStyle.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun ParagraphBlockView(
    block: DocumentBlock.Paragraph,
    onUpdateText: (String) -> Unit,
    onToggleStyle: (String) -> Unit,
    onDeleteBlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetBg = if (block.isHighlighted) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val bg by animateColorAsState(targetValue = targetBg, animationSpec = allNotesSpring())

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = bg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(selected = block.isBold, onClick = { onToggleStyle("bold") }, label = { Text("B", fontWeight = FontWeight.Bold) })
                    FilterChip(selected = block.isItalic, onClick = { onToggleStyle("italic") }, label = { Text("I", fontStyle = FontStyle.Italic) })
                    FilterChip(selected = block.isUnderlined, onClick = { onToggleStyle("underline") }, label = { Text("U", textDecoration = TextDecoration.Underline) })
                    FilterChip(selected = block.isHighlighted, onClick = { onToggleStyle("highlight") }, label = { Text("Highlight") })
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDeleteBlock) {
                    Text("Remove", style = MaterialTheme.typography.labelSmall)
                }
            }
            OutlinedTextField(
                value = block.text,
                onValueChange = onUpdateText,
                placeholder = { Text("Type paragraph content…") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (block.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (block.isItalic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (block.isUnderlined) TextDecoration.Underline else TextDecoration.None
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

@Composable
private fun ChecklistBlockView(
    block: DocumentBlock.Checklist,
    onToggleItem: (String) -> Unit,
    onAddItem: (String) -> Unit,
    onDeleteBlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draftItem by remember(block.id) { mutableStateOf("") }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = block.title.ifBlank { "Checklist" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onDeleteBlock) {
                    Text("Remove", style = MaterialTheme.typography.labelSmall)
                }
            }
            block.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = item.isChecked,
                        onCheckedChange = { onToggleItem(item.id) }
                    )
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (item.isChecked) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draftItem,
                    onValueChange = { draftItem = it },
                    placeholder = { Text("Add checklist item…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        if (draftItem.isNotBlank()) {
                            onAddItem(draftItem)
                            draftItem = ""
                        }
                    }
                ) {
                    Text("Add")
                }
            }
        }
    }
}

@Composable
private fun BulletListBlockView(
    block: DocumentBlock.BulletList,
    onAddItem: (String) -> Unit = {},
    onDeleteBlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draftItem by remember(block.id) { mutableStateOf("") }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (block.isNumbered) "Numbered List" else "Bulleted List",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onDeleteBlock) {
                    Text("Remove", style = MaterialTheme.typography.labelSmall)
                }
            }
            block.items.forEachIndexed { index, item ->
                val bullet = if (block.isNumbered) "${index + 1}." else "•"
                Text(
                    text = "$bullet  $item",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draftItem,
                    onValueChange = { draftItem = it },
                    placeholder = { Text("Add list item…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        if (draftItem.isNotBlank()) {
                            onAddItem(draftItem)
                            draftItem = ""
                        }
                    }
                ) {
                    Text("Add")
                }
            }
        }
    }
}


@Composable
private fun CalloutBlockView(
    block: DocumentBlock.Callout,
    onUpdateText: (String) -> Unit,
    onDeleteBlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (container, content) = when (block.tone) {
        CalloutTone.INFO -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        CalloutTone.TIP -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        CalloutTone.IMPORTANT -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${block.tone.label.uppercase()} · ${block.title}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onDeleteBlock) {
                    Text("Remove", style = MaterialTheme.typography.labelSmall, color = content)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = block.text,
                onValueChange = onUpdateText,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}


package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.workspace.WorkspaceAction

@Composable
internal fun ResponsiveTableBlockContent(
    block: DocumentBlock.Table,
    isActive: Boolean,
    isCompactMobile: Boolean,
    searchQuery: String,
    onAction: (WorkspaceAction) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val searchVisual = remember(searchQuery, colorScheme) {
        VisualTransformation { raw ->
            TransformedText(
                RichSpanEngine.buildSearchHighlightedString(raw.text, colorScheme, searchQuery),
                OffsetMapping.Identity
            )
        }
    }

    val useMobileStackedCards = isCompactMobile && block.headers.size >= 4

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (useMobileStackedCards) {
                MobileStackedTableView(block, isActive, searchVisual, onAction)
            } else {
                StandardGridTableView(block, isActive, searchVisual, onAction)
            }

            if (isActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { onAction(WorkspaceAction.AddTableRow(block.id)) }) {
                        Text("+ Row", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        onClick = {
                            onAction(
                                WorkspaceAction.AddTableColumn(
                                    blockId = block.id,
                                    headerTitle = "Col ${block.headers.size + 1}"
                                )
                            )
                        }
                    ) {
                        Text("+ Column", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileStackedTableView(
    block: DocumentBlock.Table,
    isActive: Boolean,
    searchVisual: VisualTransformation,
    onAction: (WorkspaceAction) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isActive) {
            Text(
                text = "COLUMNS (${block.headers.size})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary
            )
            block.headers.forEachIndexed { colIndex, header ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = header,
                        onValueChange = { onAction(WorkspaceAction.UpdateTableHeader(block.id, colIndex, it)) },
                        textStyle = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        ),
                        singleLine = true,
                        visualTransformation = searchVisual,
                        cursorBrush = SolidColor(colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    )
                    if (block.headers.size > 1) {
                        IconButton(
                            onClick = { onAction(WorkspaceAction.DeleteTableColumn(block.id, colIndex)) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(AllNotesIcons.Close, contentDescription = "Delete column", modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
            HorizontalDivider(color = colorScheme.outlineVariant)
        }

        block.rows.forEachIndexed { rIdx, row ->
            Surface(
                color = colorScheme.surfaceContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isActive && block.rows.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Row ${rIdx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = { onAction(WorkspaceAction.DeleteTableRow(block.id, rIdx)) },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(AllNotesIcons.Close, contentDescription = "Delete row", modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                    block.headers.forEachIndexed { cIdx, header ->
                        val cellValue = row.getOrElse(cIdx) { "" }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = header.ifBlank { "Col ${cIdx + 1}" },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(104.dp).padding(end = 8.dp)
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                if (cellValue.isEmpty()) {
                                    Text(
                                        text = "—",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                                BasicTextField(
                                    value = cellValue,
                                    onValueChange = { onAction(WorkspaceAction.UpdateTableCell(block.id, rIdx, cIdx, it)) },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurface),
                                    visualTransformation = searchVisual,
                                    cursorBrush = SolidColor(colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StandardGridTableView(
    block: DocumentBlock.Table,
    isActive: Boolean,
    searchVisual: VisualTransformation,
    onAction: (WorkspaceAction) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        block.headers.forEachIndexed { colIndex, header ->
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (header.isEmpty()) {
                        Text(
                            text = "Column ${colIndex + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    BasicTextField(
                        value = header,
                        onValueChange = { onAction(WorkspaceAction.UpdateTableHeader(block.id, colIndex, it)) },
                        textStyle = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        ),
                        singleLine = true,
                        visualTransformation = searchVisual,
                        cursorBrush = SolidColor(colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (isActive && block.headers.size > 1) {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.DeleteTableColumn(block.id, colIndex)) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(AllNotesIcons.Close, contentDescription = "Delete column", tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
        if (isActive && block.rows.size > 1) {
            Spacer(modifier = Modifier.width(24.dp))
        }
    }

    HorizontalDivider(color = colorScheme.outlineVariant)

    block.rows.forEachIndexed { rIdx, row ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            row.forEachIndexed { cIdx, cell ->
                Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    if (cell.isEmpty()) {
                        Text(
                            text = "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    BasicTextField(
                        value = cell,
                        onValueChange = { onAction(WorkspaceAction.UpdateTableCell(block.id, rIdx, cIdx, it)) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurface),
                        visualTransformation = searchVisual,
                        cursorBrush = SolidColor(colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (isActive && block.rows.size > 1) {
                IconButton(
                    onClick = { onAction(WorkspaceAction.DeleteTableRow(block.id, rIdx)) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(AllNotesIcons.Close, contentDescription = "Delete row", tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                }
            }
        }
        if (rIdx < block.rows.lastIndex || isActive) {
            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.35f))
        }
    }
}

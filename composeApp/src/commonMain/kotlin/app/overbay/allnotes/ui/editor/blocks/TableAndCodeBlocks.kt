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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.workspace.WorkspaceAction

@Composable
internal fun ResponsiveTableBlockContent(
    block: DocumentBlock.Table,
    isActive: Boolean,
    onAction: (WorkspaceAction) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Table Header Row (Editable column headers)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                block.headers.forEachIndexed { colIndex, header ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (header.isEmpty()) {
                                Text(
                                    text = "Column ${colIndex + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            BasicTextField(
                                value = header,
                                onValueChange = { updated ->
                                    onAction(WorkspaceAction.UpdateTableHeader(block.id, colIndex, updated))
                                },
                                textStyle = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (isActive && block.headers.size > 1) {
                            IconButton(
                                onClick = { onAction(WorkspaceAction.DeleteTableColumn(block.id, colIndex)) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    AllNotesIcons.Close,
                                    contentDescription = "Delete column",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
                if (isActive && block.rows.size > 1) {
                    Spacer(modifier = Modifier.width(24.dp))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Table Body Rows
            block.rows.forEachIndexed { rIdx, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEachIndexed { cIdx, cell ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        ) {
                            if (cell.isEmpty()) {
                                Text(
                                    text = "—",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                            BasicTextField(
                                value = cell,
                                onValueChange = { updated ->
                                    onAction(WorkspaceAction.UpdateTableCell(block.id, rIdx, cIdx, updated))
                                },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (isActive && block.rows.size > 1) {
                        IconButton(
                            onClick = { onAction(WorkspaceAction.DeleteTableRow(block.id, rIdx)) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                AllNotesIcons.Close,
                                contentDescription = "Delete row",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }

            // Table Footer Actions
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

@Composable
internal fun CodeSnippetBlockContent(
    block: DocumentBlock.CodeSnippet,
    onAction: (WorkspaceAction) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        AllNotesIcons.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = block.language.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "${block.code.lines().size} lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                if (block.code.isEmpty()) {
                    Text(
                        text = "// Write or paste code…",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                BasicTextField(
                    value = block.code,
                    onValueChange = { onAction(WorkspaceAction.UpdateBlockText(block.id, it)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

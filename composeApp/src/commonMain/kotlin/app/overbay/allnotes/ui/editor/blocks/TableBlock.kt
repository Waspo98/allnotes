package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
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
    val searchVisual = rememberSearchHighlightTransformation(searchQuery)
    val useMobileStackedCards = isCompactMobile && block.headers.size >= 3

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f))
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
                val headerKey = Triple(block.id, -1, colIndex)
                val holder = rememberSyncedFieldHolder(headerKey, header)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SyncedCursorTextField(
                        focusKey = headerKey,
                        holder = holder,
                        textStyle = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        ),
                        visualTransformation = searchVisual,
                        singleLine = true,
                        onTextChange = { onAction(WorkspaceAction.UpdateTableHeader(block.id, colIndex, it)) },
                        modifier = Modifier.weight(1f)
                    )
                    if (block.headers.size > 1) {
                        IconButton(
                            onClick = { onAction(WorkspaceAction.DeleteTableColumn(block.id, colIndex)) },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(AllNotesIcons.Close, contentDescription = "Delete column", modifier = Modifier.size(15.dp))
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
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(AllNotesIcons.Close, contentDescription = "Delete row", modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    block.headers.forEachIndexed { cIdx, header ->
                        val cellValue = row.getOrElse(cIdx) { "" }
                        val cellKey = Triple(block.id, rIdx, cIdx)
                        val cellHolder = rememberSyncedFieldHolder(cellKey, cellValue)
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
                                if (cellHolder.fieldValue.text.isEmpty()) {
                                    Text(
                                        text = "—",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                                SyncedCursorTextField(
                                    focusKey = cellKey,
                                    holder = cellHolder,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurface),
                                    visualTransformation = searchVisual,
                                    onTextChange = { onAction(WorkspaceAction.UpdateTableCell(block.id, rIdx, cIdx, it)) },
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
    val colCount = block.headers.size.coerceAtLeast(1)
    val totalGridRows = block.rows.size + 1
    val gridRequesters = remember(block.id, totalGridRows, colCount) {
        List(totalGridRows) { List(colCount) { FocusRequester() } }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        block.headers.forEachIndexed { colIndex, header ->
            val headerKey = Triple(block.id, -1, colIndex)
            val holder = rememberSyncedFieldHolder(headerKey, header)
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (holder.fieldValue.text.isEmpty()) {
                        Text(
                            text = "Column ${colIndex + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    SyncedCursorTextField(
                        focusKey = headerKey,
                        holder = holder,
                        textStyle = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        ),
                        visualTransformation = searchVisual,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        onTextChange = { onAction(WorkspaceAction.UpdateTableHeader(block.id, colIndex, it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(gridRequesters[0][colIndex])
                            .onPreviewKeyEvent {
                                handleTableGridKey(it, 0, colIndex, totalGridRows, colCount, gridRequesters)
                            }
                    )
                }
                if (isActive && block.headers.size > 1) {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.DeleteTableColumn(block.id, colIndex)) },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(AllNotesIcons.Close, contentDescription = "Delete column", tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
        if (isActive && block.rows.size > 1) {
            Spacer(modifier = Modifier.width(38.dp))
        }
    }

    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.7f))

    block.rows.forEachIndexed { rIdx, row ->
        val rowBg = if (rIdx % 2 == 1) colorScheme.surfaceContainerLow.copy(alpha = 0.55f) else colorScheme.surfaceContainerLowest
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBg)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            block.headers.indices.forEach { cIdx ->
                val cell = row.getOrElse(cIdx) { "" }
                val cellKey = Triple(block.id, rIdx, cIdx)
                val holder = rememberSyncedFieldHolder(cellKey, cell)
                Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    if (holder.fieldValue.text.isEmpty()) {
                        Text(
                            text = "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    SyncedCursorTextField(
                        focusKey = cellKey,
                        holder = holder,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurface),
                        visualTransformation = searchVisual,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        onTextChange = { onAction(WorkspaceAction.UpdateTableCell(block.id, rIdx, cIdx, it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(gridRequesters[rIdx + 1][cIdx])
                            .onPreviewKeyEvent {
                                handleTableGridKey(it, rIdx + 1, cIdx, totalGridRows, colCount, gridRequesters)
                            }
                    )
                }
            }
            if (isActive && block.rows.size > 1) {
                IconButton(
                    onClick = { onAction(WorkspaceAction.DeleteTableRow(block.id, rIdx)) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(AllNotesIcons.Close, contentDescription = "Delete row", tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                }
            }
        }
        if (rIdx < block.rows.lastIndex || isActive) {
            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.35f))
        }
    }
}

private fun handleTableGridKey(
    event: KeyEvent,
    gridRow: Int,
    col: Int,
    totalGridRows: Int,
    colCount: Int,
    requesters: List<List<FocusRequester>>
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val (nextRow, nextCol) = when {
        event.key == Key.Tab && event.isShiftPressed -> when {
            col > 0 -> gridRow to (col - 1)
            gridRow > 0 -> (gridRow - 1) to (colCount - 1)
            else -> return false
        }
        event.key == Key.Tab -> when {
            col + 1 < colCount -> gridRow to (col + 1)
            gridRow + 1 < totalGridRows -> (gridRow + 1) to 0
            else -> return false
        }
        event.key == Key.Enter && !event.isShiftPressed -> {
            if (gridRow + 1 < totalGridRows) (gridRow + 1) to col else return false
        }
        else -> return false
    }
    requesters.getOrNull(nextRow)?.getOrNull(nextCol)?.let { req ->
        runCatching { req.requestFocus() }
        return true
    }
    return false
}

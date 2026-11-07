package app.overbay.allnotes.ui.workspace

import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.ChecklistItem
import app.overbay.allnotes.model.DocumentBlock

/**
 * Pure, stateless document block transformation engine.
 * Keeps block mutation rules isolated, testable, and fast.
 */
object BlockMutationEngine {

    fun blockContainsQuery(block: DocumentBlock, query: String): Boolean = when (block) {
        is DocumentBlock.Heading -> block.text.lowercase().contains(query)
        is DocumentBlock.Paragraph -> block.text.lowercase().contains(query)
        is DocumentBlock.Checklist -> block.title.lowercase().contains(query) ||
            block.items.any { it.text.lowercase().contains(query) }
        is DocumentBlock.BulletList -> block.items.any { it.lowercase().contains(query) }
        is DocumentBlock.Table -> block.headers.any { it.lowercase().contains(query) } ||
            block.rows.any { row -> row.any { it.lowercase().contains(query) } }
        is DocumentBlock.Callout -> block.title.lowercase().contains(query) ||
            block.text.lowercase().contains(query)
        is DocumentBlock.CodeSnippet -> block.code.lowercase().contains(query) ||
            block.language.lowercase().contains(query)
    }

    fun insertBlock(
        blocks: List<DocumentBlock>,
        newBlock: DocumentBlock,
        afterBlockId: String? = null
    ): List<DocumentBlock> {
        if (afterBlockId == null) return blocks + newBlock
        val idx = blocks.indexOfFirst { it.id == afterBlockId }
        if (idx < 0) return blocks + newBlock
        val mutable = blocks.toMutableList()
        mutable.add(idx + 1, newBlock)
        return mutable
    }

    fun moveBlock(blocks: List<DocumentBlock>, blockId: String, delta: Int): List<DocumentBlock> {
        val currentIdx = blocks.indexOfFirst { it.id == blockId }
        if (currentIdx < 0) return blocks
        val targetIdx = (currentIdx + delta).coerceIn(0, blocks.lastIndex)
        if (targetIdx == currentIdx) return blocks
        val mutable = blocks.toMutableList()
        val item = mutable.removeAt(currentIdx)
        mutable.add(targetIdx, item)
        return mutable
    }

    fun updateBlockText(blocks: List<DocumentBlock>, blockId: String, newText: String): List<DocumentBlock> =
        blocks.map { block ->
            if (block.id != blockId) return@map block
            when (block) {
                is DocumentBlock.Heading -> block.copy(text = newText)
                is DocumentBlock.Paragraph -> block.copy(text = newText)
                is DocumentBlock.Callout -> block.copy(text = newText)
                is DocumentBlock.CodeSnippet -> block.copy(code = newText)
                else -> block
            }
        }

    fun toggleParagraphStyle(blocks: List<DocumentBlock>, blockId: String, style: String): List<DocumentBlock> =
        blocks.map { block ->
            if (block.id == blockId && block is DocumentBlock.Paragraph) {
                when (style.lowercase()) {
                    "bold" -> block.copy(isBold = !block.isBold)
                    "italic" -> block.copy(isItalic = !block.isItalic)
                    "underline" -> block.copy(isUnderlined = !block.isUnderlined)
                    "strikethrough", "strike" -> block.copy(isStrikethrough = !block.isStrikethrough)
                    "highlight" -> block.copy(isHighlighted = !block.isHighlighted)
                    else -> block
                }
            } else {
                block
            }
        }

    fun updateHeadingLevel(blocks: List<DocumentBlock>, blockId: String, level: Int): List<DocumentBlock> {
        val clamped = level.coerceIn(1, 3)
        return blocks.map { block ->
            if (block.id == blockId && block is DocumentBlock.Heading) {
                block.copy(level = clamped)
            } else {
                block
            }
        }
    }

    fun updateChecklistTitle(blocks: List<DocumentBlock>, blockId: String, title: String): List<DocumentBlock> =
        blocks.map { block ->
            if (block.id == blockId && block is DocumentBlock.Checklist) {
                block.copy(title = title)
            } else {
                block
            }
        }

    fun updateChecklistItemText(
        blocks: List<DocumentBlock>,
        blockId: String,
        itemId: String,
        text: String
    ): List<DocumentBlock> = blocks.map { block ->
        if (block.id == blockId && block is DocumentBlock.Checklist) {
            block.copy(
                items = block.items.map { item ->
                    if (item.id == itemId) item.copy(text = text) else item
                }
            )
        } else {
            block
        }
    }

    fun deleteChecklistItem(blocks: List<DocumentBlock>, blockId: String, itemId: String): List<DocumentBlock> =
        blocks.map { block ->
            if (block.id == blockId && block is DocumentBlock.Checklist) {
                block.copy(items = block.items.filterNot { it.id == itemId })
            } else {
                block
            }
        }

    fun insertChecklistItemAfter(
        blocks: List<DocumentBlock>,
        blockId: String,
        newItem: ChecklistItem,
        afterItemId: String? = null
    ): List<DocumentBlock> = blocks.map { block ->
        if (block.id == blockId && block is DocumentBlock.Checklist) {
            val idx = if (afterItemId != null) block.items.indexOfFirst { it.id == afterItemId } else -1
            val updated = if (idx >= 0) {
                block.items.toMutableList().apply { add(idx + 1, newItem) }
            } else {
                block.items + newItem
            }
            block.copy(items = updated)
        } else {
            block
        }
    }

    fun toggleBulletListKind(blocks: List<DocumentBlock>, blockId: String): List<DocumentBlock> =
        blocks.map { block ->
            if (block.id == blockId && block is DocumentBlock.BulletList) {
                block.copy(isNumbered = !block.isNumbered)
            } else {
                block
            }
        }

    fun addBulletListItem(blocks: List<DocumentBlock>, blockId: String, text: String): List<DocumentBlock> =
        blocks.map { block ->
            if (block.id == blockId && block is DocumentBlock.BulletList) {
                block.copy(items = block.items + text)
            } else {
                block
            }
        }

    fun insertBulletListItemAfter(
        blocks: List<DocumentBlock>,
        blockId: String,
        afterItemIndex: Int? = null
    ): List<DocumentBlock> = blocks.map { block ->
        if (block.id == blockId && block is DocumentBlock.BulletList) {
            val updated = if (afterItemIndex != null && afterItemIndex in block.items.indices) {
                block.items.toMutableList().apply { add(afterItemIndex + 1, "") }
            } else {
                block.items + ""
            }
            block.copy(items = updated)
        } else {
            block
        }
    }

    fun updateBulletListItem(
        blocks: List<DocumentBlock>,
        blockId: String,
        itemIndex: Int,
        text: String
    ): List<DocumentBlock> = blocks.map { block ->
        if (block.id == blockId && block is DocumentBlock.BulletList && itemIndex in block.items.indices) {
            block.copy(
                items = block.items.mapIndexed { idx, existing ->
                    if (idx == itemIndex) text else existing
                }
            )
        } else {
            block
        }
    }

    fun deleteBulletListItem(
        blocks: List<DocumentBlock>,
        blockId: String,
        itemIndex: Int
    ): List<DocumentBlock> = blocks.map { block ->
        if (block.id == blockId && block is DocumentBlock.BulletList && itemIndex in block.items.indices) {
            block.copy(items = block.items.filterIndexed { idx, _ -> idx != itemIndex })
        } else {
            block
        }
    }

    fun updateTableHeader(
        blocks: List<DocumentBlock>,
        blockId: String,
        colIndex: Int,
        title: String
    ): List<DocumentBlock> = blocks.map { block ->
        if (block.id == blockId && block is DocumentBlock.Table && colIndex in block.headers.indices) {
            block.copy(
                headers = block.headers.mapIndexed { idx, existing ->
                    if (idx == colIndex) title else existing
                }
            )
        } else {
            block
        }
    }

    fun updateTableCell(
        blocks: List<DocumentBlock>,
        blockId: String,
        rowIndex: Int,
        colIndex: Int,
        text: String
    ): List<DocumentBlock> = blocks.map { block ->
        if (block.id == blockId && block is DocumentBlock.Table) {
            val updatedRows = block.rows.mapIndexed { rIdx, row ->
                if (rIdx == rowIndex) {
                    row.mapIndexed { cIdx, cell ->
                        if (cIdx == colIndex) text else cell
                    }
                } else {
                    row
                }
            }
            block.copy(rows = updatedRows)
        } else {
            block
        }
    }

    fun addTableRow(
        blocks: List<DocumentBlock>,
        blockId: String,
        rowData: List<String> = emptyList()
    ): List<DocumentBlock> = blocks.map { block ->
        if (block.id == blockId && block is DocumentBlock.Table) {
            val newRow = if (rowData.isNotEmpty()) {
                rowData
            } else {
                List(block.headers.size) { "" }
            }
            block.copy(rows = block.rows + listOf(newRow))
        } else {
            block
        }
    }

    fun deleteTableRow(
        blocks: List<DocumentBlock>,
        blockId: String,
        rowIndex: Int
    ): List<DocumentBlock> = blocks.map { block ->
        if (block.id == blockId && block is DocumentBlock.Table && rowIndex in block.rows.indices && block.rows.size > 1) {
            block.copy(rows = block.rows.filterIndexed { idx, _ -> idx != rowIndex })
        } else {
            block
        }
    }

    fun addTableColumn(
        blocks: List<DocumentBlock>,
        blockId: String,
        headerTitle: String,
        defaultCellText: String = ""
    ): List<DocumentBlock> = blocks.map { block ->
        if (block.id == blockId && block is DocumentBlock.Table) {
            val newHeaders = block.headers + headerTitle
            val newRows = block.rows.map { row -> row + defaultCellText }
            block.copy(headers = newHeaders, rows = newRows)
        } else {
            block
        }
    }

    fun deleteTableColumn(
        blocks: List<DocumentBlock>,
        blockId: String,
        colIndex: Int
    ): List<DocumentBlock> = blocks.map { block ->
        if (block.id == blockId && block is DocumentBlock.Table && colIndex in block.headers.indices && block.headers.size > 1) {
            block.copy(
                headers = block.headers.filterIndexed { idx, _ -> idx != colIndex },
                rows = block.rows.map { row -> row.filterIndexed { idx, _ -> idx != colIndex } }
            )
        } else {
            block
        }
    }

    fun updateCalloutTitle(blocks: List<DocumentBlock>, blockId: String, title: String): List<DocumentBlock> =
        blocks.map { block ->
            if (block.id == blockId && block is DocumentBlock.Callout) {
                block.copy(title = title)
            } else {
                block
            }
        }

    fun updateCalloutTone(blocks: List<DocumentBlock>, blockId: String, tone: CalloutTone): List<DocumentBlock> =
        blocks.map { block ->
            if (block.id == blockId && block is DocumentBlock.Callout) {
                block.copy(tone = tone)
            } else {
                block
            }
        }

    fun updateCodeLanguage(blocks: List<DocumentBlock>, blockId: String, language: String): List<DocumentBlock> =
        blocks.map { block ->
            if (block.id == blockId && block is DocumentBlock.CodeSnippet) {
                block.copy(language = language.trim().ifEmpty { "text" })
            } else {
                block
            }
        }
}

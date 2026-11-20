package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine

/**
 * Holds synchronized [TextFieldValue] state for a block field while guarding IME composition
 * and cursor positions against delayed repository echoes.
 */
internal class SyncedFieldHolder(initialText: String) {
    var fieldValue: TextFieldValue by mutableStateOf(
        TextFieldValue(text = initialText, selection = TextRange(initialText.length))
    )
    var lastExternalText: String = initialText
    private var currentLocalText: String = initialText
    private var currentSelection: TextRange = TextRange(initialText.length)
    private val pendingLocalTexts = ArrayDeque<String>(8)

    fun syncExternal(externalText: String) {
        if (externalText == lastExternalText) return
        lastExternalText = externalText

        if (externalText == currentLocalText) {
            consumeMatchingEcho(externalText)
            return
        }

        if (consumeMatchingEcho(externalText)) {
            return
        }

        pendingLocalTexts.clear()
        val clampedStart = currentSelection.start.coerceIn(0, externalText.length)
        val clampedEnd = currentSelection.end.coerceIn(0, externalText.length)
        val nextSelection = TextRange(clampedStart, clampedEnd)
        currentLocalText = externalText
        currentSelection = nextSelection
        fieldValue = TextFieldValue(
            text = externalText,
            selection = nextSelection
        )
    }

    fun applyLocalChange(updated: TextFieldValue, emittedText: String = updated.text) {
        val textChanged = emittedText != currentLocalText
        currentLocalText = emittedText
        currentSelection = updated.selection
        fieldValue = updated
        if (textChanged && pendingLocalTexts.lastOrNull() != emittedText) {
            if (pendingLocalTexts.size >= 16) {
                pendingLocalTexts.removeFirst()
            }
            pendingLocalTexts.addLast(emittedText)
        }
    }

    fun moveCursorTo(offset: Int) {
        val pos = offset.coerceIn(0, currentLocalText.length)
        val nextSelection = TextRange(pos)
        currentSelection = nextSelection
        fieldValue = fieldValue.copy(selection = nextSelection)
    }

    private fun consumeMatchingEcho(text: String): Boolean {
        val idx = pendingLocalTexts.indexOf(text)
        if (idx < 0) return false
        repeat(idx + 1) {
            if (pendingLocalTexts.isNotEmpty()) pendingLocalTexts.removeFirst()
        }
        return true
    }
}

@Composable
internal fun rememberSyncedFieldHolder(
    key: Any,
    externalText: String
): SyncedFieldHolder {
    val holder = remember(key) { SyncedFieldHolder(externalText) }
    SideEffect {
        holder.syncExternal(externalText)
    }
    return holder
}

@Composable
internal fun rememberSearchHighlightTransformation(searchQuery: String): VisualTransformation {
    val colorScheme = MaterialTheme.colorScheme
    return remember(searchQuery, colorScheme) {
        VisualTransformation { raw ->
            TransformedText(
                RichSpanEngine.buildSearchHighlightedString(raw.text, colorScheme, searchQuery),
                OffsetMapping.Identity
            )
        }
    }
}

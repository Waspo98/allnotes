package app.overbay.allnotes.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.data.currentEpochMillis
import app.overbay.allnotes.ui.editor.blocks.SyncedFieldHolder
import app.overbay.allnotes.ui.editor.blocks.rememberCursorIntoViewHandler

@Composable
internal fun DocumentPageHeader(
    titleHolder: SyncedFieldHolder,
    updatedAtEpochMs: Long,
    wordCount: Int,
    readingTimeMinutes: Int,
    accentColor: Color,
    isCompactMobile: Boolean,
    titleFocusToken: Long,
    onTitleFocusConsumed: (Long) -> Unit,
    onTitleChange: (String) -> Unit,
    onTitleEnter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val titleFocusRequester = remember { FocusRequester() }
    var isTitleFocused by remember { mutableStateOf(false) }
    val (titleIntoViewModifier, onTitleLayout) = rememberCursorIntoViewHandler(isTitleFocused, titleHolder.fieldValue.selection)

    if (titleFocusToken > 0L) {
        LaunchedEffect(titleFocusToken) {
            runCatching { titleFocusRequester.requestFocus() }
            onTitleFocusConsumed(titleFocusToken)
        }
    }

    val relativeTime = remember(updatedAtEpochMs) {
        formatRelativeTimestamp(updatedAtEpochMs, currentEpochMillis())
    }

    val metaText = remember(relativeTime, wordCount, readingTimeMinutes) {
        buildList {
            if (relativeTime.isNotEmpty()) add("Edited $relativeTime")
            if (wordCount > 0) {
                add("$wordCount ${if (wordCount == 1) "word" else "words"}")
                add("$readingTimeMinutes min read")
            }
        }.joinToString("  ·  ")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (isCompactMobile) 2.dp else 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (titleHolder.fieldValue.text.isEmpty()) {
                Text(
                    text = "Untitled Page",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                )
            }
            BasicTextField(
                value = titleHolder.fieldValue,
                onValueChange = { updated ->
                    if (updated.text.contains('\n')) {
                        val cleaned = updated.text.replace("\n", "")
                        val changed = cleaned != titleHolder.fieldValue.text
                        titleHolder.applyLocalChange(updated.copy(text = cleaned), cleaned)
                        if (changed) onTitleChange(cleaned)
                        onTitleEnter()
                    } else {
                        val changed = updated.text != titleHolder.fieldValue.text
                        titleHolder.applyLocalChange(updated)
                        if (changed) onTitleChange(updated.text)
                    }
                },
                textStyle = MaterialTheme.typography.headlineLarge.copy(
                    color = colorScheme.onSurface
                ),
                onTextLayout = onTitleLayout,
                cursorBrush = SolidColor(accentColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocusRequester)
                    .then(titleIntoViewModifier)
                    .onFocusChanged { isTitleFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                            onTitleEnter()
                            true
                        } else {
                            false
                        }
                    }
            )
        }

        if (metaText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(if (isCompactMobile) 4.dp else 8.dp))
            Text(
                text = metaText,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )
        }

        Spacer(modifier = Modifier.height(if (isCompactMobile) 10.dp else 16.dp))
        HorizontalDivider(
            thickness = 1.dp,
            color = colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    }
}

internal fun formatRelativeTimestamp(epochMs: Long, nowMs: Long): String {
    if (epochMs <= 0L) return ""
    val diffSec = ((nowMs - epochMs) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "just now"
        diffSec < 3600L -> "${diffSec / 60L}m ago"
        diffSec < 86_400L -> "${diffSec / 3600L}h ago"
        diffSec < 172_800L -> "yesterday"
        diffSec < 604_800L -> "${diffSec / 86_400L}d ago"
        else -> "${diffSec / 604_800L}w ago"
    }
}

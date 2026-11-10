package app.overbay.allnotes.ui.editor.blocks

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.workspace.EditorFocusRequest
import app.overbay.allnotes.ui.workspace.WorkspaceAction

@Composable
internal fun HeadingBlockContent(
    block: DocumentBlock.Heading,
    searchQuery: String,
    focusRequest: EditorFocusRequest?,
    onAction: (WorkspaceAction) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val focusRequester = remember(block.id) { FocusRequester() }
    var fieldValue by remember(block.id) {
        mutableStateOf(TextFieldValue(text = block.text, selection = TextRange(block.text.length)))
    }
    if (fieldValue.text != block.text) {
        val pos = fieldValue.selection.start.coerceIn(0, block.text.length)
        fieldValue = TextFieldValue(text = block.text, selection = TextRange(pos))
    }

    LaunchedEffect(focusRequest?.requestToken) {
        if (focusRequest != null && focusRequest.blockId == block.id && focusRequest.subItemIndex < 0) {
            val pos = focusRequest.cursorOffset.coerceIn(0, block.text.length)
            fieldValue = fieldValue.copy(selection = TextRange(pos))
            runCatching { focusRequester.requestFocus() }
            onAction(WorkspaceAction.ConsumeFocusRequest(focusRequest.requestToken))
        }
    }

    val headingStyle = when (block.level) {
        1 -> MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            color = colorScheme.onSurface
        )
        2 -> MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface
        )
        else -> MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.primary
        )
    }

    val searchVisual = remember(searchQuery, colorScheme) {
        VisualTransformation { raw ->
            TransformedText(
                RichSpanEngine.buildSearchHighlightedString(raw.text, colorScheme, searchQuery),
                OffsetMapping.Identity
            )
        }
    }

    Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
        if (fieldValue.text.isEmpty()) {
            Text(
                text = "Heading ${block.level}…",
                style = headingStyle.copy(color = colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
            )
        }
        BasicTextField(
            value = fieldValue,
            onValueChange = { updated ->
                if (updated.text.length == fieldValue.text.length + 1 && updated.composition == null) {
                    val idx = updated.selection.start - 1
                    if (idx in updated.text.indices && updated.text[idx] == '\n') {
                        onAction(WorkspaceAction.SplitBlockAtCursor(block.id, idx))
                        return@BasicTextField
                    }
                }
                val textChanged = updated.text != fieldValue.text
                fieldValue = updated
                if (textChanged) {
                    onAction(WorkspaceAction.UpdateBlockText(block.id, updated.text.replace("\n", " ")))
                }
            },
            textStyle = headingStyle,
            visualTransformation = searchVisual,
            cursorBrush = SolidColor(colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.Enter && !event.isShiftPressed -> {
                            onAction(WorkspaceAction.SplitBlockAtCursor(block.id, fieldValue.selection.min))
                            true
                        }
                        event.key == Key.Backspace && fieldValue.selection.collapsed && fieldValue.selection.start == 0 -> {
                            onAction(WorkspaceAction.BackspaceAtBlockStart(block.id))
                            true
                        }
                        else -> false
                    }
                }
        )
    }
}

@Composable
internal fun ParagraphBlockContent(
    block: DocumentBlock.Paragraph,
    isActive: Boolean,
    searchQuery: String,
    focusRequest: EditorFocusRequest?,
    onAction: (WorkspaceAction) -> Unit
) {
    RichParagraphField(
        block = block,
        isActive = isActive,
        searchQuery = searchQuery,
        focusRequest = focusRequest,
        onAction = onAction
    )
}

@Composable
internal fun CalloutBlockContent(
    block: DocumentBlock.Callout,
    searchQuery: String,
    focusRequest: EditorFocusRequest?,
    onAction: (WorkspaceAction) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val bodyFocusRequester = remember(block.id) { FocusRequester() }
    var titleField by remember(block.id) {
        mutableStateOf(TextFieldValue(text = block.title, selection = TextRange(block.title.length)))
    }
    var bodyField by remember(block.id) {
        mutableStateOf(TextFieldValue(text = block.text, selection = TextRange(block.text.length)))
    }
    if (titleField.text != block.title) {
        titleField = TextFieldValue(block.title, TextRange(titleField.selection.start.coerceIn(0, block.title.length)))
    }
    if (bodyField.text != block.text) {
        bodyField = TextFieldValue(block.text, TextRange(bodyField.selection.start.coerceIn(0, block.text.length)))
    }

    LaunchedEffect(focusRequest?.requestToken) {
        if (focusRequest != null && focusRequest.blockId == block.id && focusRequest.subItemIndex < 0) {
            val pos = focusRequest.cursorOffset.coerceIn(0, block.text.length)
            bodyField = bodyField.copy(selection = TextRange(pos))
            runCatching { bodyFocusRequester.requestFocus() }
            onAction(WorkspaceAction.ConsumeFocusRequest(focusRequest.requestToken))
        }
    }

    val (container, content, badge) = when (block.tone) {
        CalloutTone.INFO -> Triple(
            colorScheme.secondaryContainer,
            colorScheme.onSecondaryContainer,
            colorScheme.secondary
        )
        CalloutTone.TIP -> Triple(
            colorScheme.tertiaryContainer,
            colorScheme.onTertiaryContainer,
            colorScheme.tertiary
        )
        CalloutTone.IMPORTANT -> Triple(
            colorScheme.primaryContainer,
            colorScheme.onPrimaryContainer,
            colorScheme.primary
        )
    }

    val searchVisual = remember(searchQuery, colorScheme) {
        VisualTransformation { raw ->
            TransformedText(
                RichSpanEngine.buildSearchHighlightedString(raw.text, colorScheme, searchQuery),
                OffsetMapping.Identity
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container.copy(alpha = 0.65f),
        contentColor = content,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = AllNotesIcons.Callout,
                contentDescription = null,
                tint = badge,
                modifier = Modifier.padding(top = 2.dp).size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (titleField.text.isEmpty()) {
                        Text(
                            text = "Callout title…",
                            style = MaterialTheme.typography.titleSmall,
                            color = content.copy(alpha = 0.5f)
                        )
                    }
                    BasicTextField(
                        value = titleField,
                        onValueChange = { updated ->
                            val changed = updated.text != titleField.text
                            titleField = updated
                            if (changed) {
                                onAction(WorkspaceAction.UpdateCalloutTitle(block.id, updated.text))
                            }
                        },
                        textStyle = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = content
                        ),
                        singleLine = true,
                        visualTransformation = searchVisual,
                        cursorBrush = SolidColor(badge),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (bodyField.text.isEmpty()) {
                        Text(
                            text = "Add callout details…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = content.copy(alpha = 0.6f)
                        )
                    }
                    BasicTextField(
                        value = bodyField,
                        onValueChange = { updated ->
                            val changed = updated.text != bodyField.text
                            bodyField = updated
                            if (changed) {
                                onAction(WorkspaceAction.UpdateBlockText(block.id, updated.text))
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = content),
                        visualTransformation = searchVisual,
                        cursorBrush = SolidColor(badge),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(bodyFocusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                    event.key == Key.Backspace &&
                                    bodyField.text.isEmpty() &&
                                    titleField.text.isEmpty()
                                ) {
                                    onAction(WorkspaceAction.BackspaceAtBlockStart(block.id))
                                    true
                                } else {
                                    false
                                }
                            }
                    )
                }
            }
        }
    }
}

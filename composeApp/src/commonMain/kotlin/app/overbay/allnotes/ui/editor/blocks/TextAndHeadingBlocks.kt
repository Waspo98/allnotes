package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.DocumentBlock
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
    val fieldHolder = rememberSyncedFieldHolder(block.id, block.text)
    val fieldValue = fieldHolder.fieldValue
    var isFocused by remember(block.id) { mutableStateOf(false) }
    val layoutRef = remember(block.id) { LayoutResultRef() }
    val (cursorIntoViewModifier, bringCursorTextLayout) = rememberCursorIntoViewHandler(isFocused, fieldValue.selection)

    if (focusRequest != null && focusRequest.blockId == block.id && focusRequest.subItemIndex < 0) {
        LaunchedEffect(focusRequest.requestToken) {
            val pos = focusRequest.cursorOffset.coerceIn(0, block.text.length)
            fieldHolder.moveCursorTo(pos)
            runCatching { focusRequester.requestFocus() }
            onAction(WorkspaceAction.ConsumeFocusRequest(focusRequest.requestToken))
        }
    }

    val headingStyle = when (block.level) {
        1 -> MaterialTheme.typography.headlineMedium.copy(
            color = colorScheme.onSurface
        )
        2 -> MaterialTheme.typography.headlineSmall.copy(
            color = colorScheme.onSurface
        )
        else -> MaterialTheme.typography.titleLarge.copy(
            color = colorScheme.primary
        )
    }

    val topSpacing = when (block.level) {
        1 -> 20.dp
        2 -> 14.dp
        else -> 10.dp
    }

    val searchVisual = rememberSearchHighlightTransformation(searchQuery)

    Box(modifier = Modifier.fillMaxWidth().padding(top = topSpacing, bottom = 2.dp)) {
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
                val cleaned = updated.text.replace("\n", " ")
                val normalized = if (cleaned == updated.text) updated else updated.copy(text = cleaned)
                val textChanged = cleaned != fieldValue.text
                fieldHolder.applyLocalChange(normalized, cleaned)
                if (textChanged) {
                    onAction(WorkspaceAction.UpdateBlockText(block.id, cleaned))
                }
            },
            textStyle = headingStyle,
            visualTransformation = searchVisual,
            onTextLayout = { result ->
                layoutRef.value = result
                bringCursorTextLayout(result)
            },
            cursorBrush = SolidColor(colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .then(cursorIntoViewModifier)
                .onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (handleVerticalBoundaryKeyEvent(event, fieldHolder.fieldValue, layoutRef.value, block.id, onAction)) {
                        return@onPreviewKeyEvent true
                    }
                    when {
                        event.key == Key.Enter && !event.isShiftPressed -> {
                            onAction(WorkspaceAction.SplitBlockAtCursor(block.id, fieldHolder.fieldValue.selection.min))
                            true
                        }
                        event.key == Key.Backspace &&
                            fieldHolder.fieldValue.selection.collapsed &&
                            fieldHolder.fieldValue.selection.start == 0 -> {
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
    onAction: (WorkspaceAction) -> Unit,
    onSlashKeyEvent: ((KeyEvent) -> Boolean)? = null
) {
    RichParagraphField(
        block = block,
        isActive = isActive,
        searchQuery = searchQuery,
        focusRequest = focusRequest,
        onAction = onAction,
        onSlashKeyEvent = onSlashKeyEvent
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
    val titleHolder = rememberSyncedFieldHolder(block.id to "title", block.title)
    val bodyHolder = rememberSyncedFieldHolder(block.id to "body", block.text)
    var titleFocused by remember(block.id) { mutableStateOf(false) }
    var bodyFocused by remember(block.id) { mutableStateOf(false) }
    val (titleIntoViewModifier, onTitleLayout) = rememberCursorIntoViewHandler(titleFocused, titleHolder.fieldValue.selection)
    val (bodyIntoViewModifier, onBodyLayout) = rememberCursorIntoViewHandler(bodyFocused, bodyHolder.fieldValue.selection)

    if (focusRequest != null && focusRequest.blockId == block.id && focusRequest.subItemIndex < 0) {
        LaunchedEffect(focusRequest.requestToken) {
            val pos = focusRequest.cursorOffset.coerceIn(0, block.text.length)
            bodyHolder.moveCursorTo(pos)
            runCatching { bodyFocusRequester.requestFocus() }
            onAction(WorkspaceAction.ConsumeFocusRequest(focusRequest.requestToken))
        }
    }

    val (container, content, badge, toneIcon) = when (block.tone) {
        CalloutTone.INFO -> CalloutVisuals(
            colorScheme.secondaryContainer,
            colorScheme.onSecondaryContainer,
            colorScheme.secondary,
            AllNotesIcons.Callout
        )
        CalloutTone.TIP -> CalloutVisuals(
            colorScheme.tertiaryContainer,
            colorScheme.onTertiaryContainer,
            colorScheme.tertiary,
            AllNotesIcons.Lightbulb
        )
        CalloutTone.IMPORTANT -> CalloutVisuals(
            colorScheme.primaryContainer,
            colorScheme.onPrimaryContainer,
            colorScheme.primary,
            AllNotesIcons.Spark
        )
    }

    val searchVisual = rememberSearchHighlightTransformation(searchQuery)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container.copy(alpha = 0.56f),
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, badge.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = toneIcon,
                contentDescription = block.tone.label,
                tint = badge,
                modifier = Modifier.padding(top = 2.dp).size(19.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (titleHolder.fieldValue.text.isEmpty()) {
                        Text(
                            text = "Callout title…",
                            style = MaterialTheme.typography.titleSmall,
                            color = content.copy(alpha = 0.5f)
                        )
                    }
                    BasicTextField(
                        value = titleHolder.fieldValue,
                        onValueChange = { updated ->
                            val changed = updated.text != titleHolder.fieldValue.text
                            titleHolder.applyLocalChange(updated)
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
                        onTextLayout = onTitleLayout,
                        cursorBrush = SolidColor(badge),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(titleIntoViewModifier)
                            .onFocusChanged { titleFocused = it.isFocused }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (bodyHolder.fieldValue.text.isEmpty()) {
                        Text(
                            text = "Add callout details…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = content.copy(alpha = 0.6f)
                        )
                    }
                    BasicTextField(
                        value = bodyHolder.fieldValue,
                        onValueChange = { updated ->
                            val changed = updated.text != bodyHolder.fieldValue.text
                            bodyHolder.applyLocalChange(updated)
                            if (changed) {
                                onAction(WorkspaceAction.UpdateBlockText(block.id, updated.text))
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = content),
                        visualTransformation = searchVisual,
                        onTextLayout = onBodyLayout,
                        cursorBrush = SolidColor(badge),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(bodyFocusRequester)
                            .then(bodyIntoViewModifier)
                            .onFocusChanged { bodyFocused = it.isFocused }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                    event.key == Key.Backspace &&
                                    bodyHolder.fieldValue.text.isEmpty() &&
                                    titleHolder.fieldValue.text.isEmpty()
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

private data class CalloutVisuals(
    val container: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color,
    val badge: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

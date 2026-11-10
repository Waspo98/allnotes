package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.workspace.EditorFocusRequest
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import kotlinx.coroutines.delay

@Composable
internal fun CodeSnippetBlockContent(
    block: DocumentBlock.CodeSnippet,
    isActive: Boolean,
    searchQuery: String,
    focusRequest: EditorFocusRequest?,
    onAction: (WorkspaceAction) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val focusRequester = remember(block.id) { FocusRequester() }
    var copiedRecently by remember(block.id) { mutableStateOf(false) }

    LaunchedEffect(copiedRecently) {
        if (copiedRecently) {
            delay(1500)
            copiedRecently = false
        }
    }

    LaunchedEffect(focusRequest?.requestToken) {
        if (focusRequest != null && focusRequest.blockId == block.id && focusRequest.subItemIndex < 0) {
            runCatching { focusRequester.requestFocus() }
            onAction(WorkspaceAction.ConsumeFocusRequest(focusRequest.requestToken))
        }
    }

    val searchVisual = remember(searchQuery, colorScheme) {
        VisualTransformation { raw ->
            TransformedText(
                RichSpanEngine.buildSearchHighlightedString(raw.text, colorScheme, searchQuery),
                OffsetMapping.Identity
            )
        }
    }

    val languages = listOf(
        "kotlin", "python", "typescript", "javascript", "rust", "go",
        "bash", "powershell", "sql", "json", "yaml", "dockerfile", "markdown"
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f))
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
                        tint = colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = block.language.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
                        TextButton(
                            onClick = {
                                val currentIdx = languages.indexOfFirst { it.equals(block.language, ignoreCase = true) }
                                val nextLang = languages[(currentIdx + 1) % languages.size]
                                onAction(WorkspaceAction.UpdateCodeLanguage(block.id, nextLang))
                            }
                        ) {
                            Text("Language", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(block.code))
                            copiedRecently = true
                        }
                    ) {
                        Icon(
                            imageVector = if (copiedRecently) AllNotesIcons.Check else AllNotesIcons.Copy,
                            contentDescription = "Copy code",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (copiedRecently) "Copied" else "Copy", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                if (block.code.isEmpty()) {
                    Text(
                        text = "// Write or paste code…",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                BasicTextField(
                    value = block.code,
                    onValueChange = { onAction(WorkspaceAction.UpdateBlockText(block.id, it)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.onSurface
                    ),
                    visualTransformation = searchVisual,
                    cursorBrush = SolidColor(colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
        }
    }
}

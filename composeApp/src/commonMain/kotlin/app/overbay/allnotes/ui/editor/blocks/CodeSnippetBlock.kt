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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
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
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val focusRequester = remember(block.id) { FocusRequester() }
    val codeHolder = rememberSyncedFieldHolder(block.id, block.code)
    var copiedRecently by remember(block.id) { mutableStateOf(false) }
    var languageMenuOpen by remember(block.id) { mutableStateOf(false) }

    LaunchedEffect(copiedRecently) {
        if (copiedRecently) {
            delay(1500)
            copiedRecently = false
        }
    }

    LaunchedEffect(focusRequest?.requestToken) {
        if (focusRequest != null && focusRequest.blockId == block.id && focusRequest.subItemIndex < 0) {
            codeHolder.moveCursorTo(focusRequest.cursorOffset)
            runCatching { focusRequester.requestFocus() }
            onAction(WorkspaceAction.ConsumeFocusRequest(focusRequest.requestToken))
        }
    }

    val searchVisual = rememberSearchHighlightTransformation(searchQuery)

    val languages = remember {
        listOf(
            "kotlin", "python", "typescript", "javascript", "rust", "go",
            "bash", "powershell", "sql", "json", "yaml", "dockerfile", "markdown", "text"
        )
    }
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
                Box {
                    Surface(
                        onClick = { if (isActive) languageMenuOpen = true },
                        color = colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
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
                            if (isActive) {
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    AllNotesIcons.ChevronDown,
                                    contentDescription = "Select code language",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = languageMenuOpen,
                        onDismissRequest = { languageMenuOpen = false }
                    ) {
                        languages.forEach { lang ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = lang.uppercase(),
                                        fontWeight = if (lang.equals(block.language, ignoreCase = true)) {
                                            FontWeight.Bold
                                        } else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    languageMenuOpen = false
                                    onAction(WorkspaceAction.UpdateCodeLanguage(block.id, lang))
                                }
                            )
                        }
                    }
                }

                TextButton(
                    onClick = {
                        @Suppress("DEPRECATION")
                        clipboard.setText(AnnotatedString(codeHolder.fieldValue.text))
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
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                if (codeHolder.fieldValue.text.isEmpty()) {
                    Text(
                        text = "// Write or paste code…",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                BasicTextField(
                    value = codeHolder.fieldValue,
                    onValueChange = { updated ->
                        val changed = updated.text != codeHolder.fieldValue.text
                        codeHolder.applyLocalChange(updated)
                        if (changed) onAction(WorkspaceAction.UpdateBlockText(block.id, updated.text))
                    },
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

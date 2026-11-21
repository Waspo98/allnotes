package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.AllNotesMotion
import app.overbay.allnotes.ui.theme.LocalAllNotesMonoFont
import app.overbay.allnotes.ui.workspace.EditorFocusRequest
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import kotlinx.coroutines.delay

private val CodeKeywords = setOf(
    "fun", "val", "var", "class", "object", "interface", "return", "if", "else", "when",
    "for", "while", "import", "package", "private", "internal", "public", "suspend", "data",
    "def", "async", "await", "const", "let", "function", "export", "from", "true", "false",
    "null", "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "CREATE", "TABLE", "fn", "pub"
)

private val CodeTokenRegex = Regex(
    "(//[^\\n]*|#[^\\n]*|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\b[A-Za-z_][A-Za-z0-9_]*\\b)"
)

@Composable
internal fun CodeSnippetBlockContent(
    block: DocumentBlock.CodeSnippet,
    isActive: Boolean,
    searchQuery: String,
    focusRequest: EditorFocusRequest?,
    onAction: (WorkspaceAction) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val monoFont = LocalAllNotesMonoFont.current
    val haptics = LocalHapticFeedback.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val focusRequester = remember(block.id) { FocusRequester() }
    val codeHolder = rememberSyncedFieldHolder(block.id, block.code)
    var isFocused by remember(block.id) { mutableStateOf(false) }
    val layoutRef = remember(block.id) { LayoutResultRef() }
    val (cursorIntoViewModifier, bringCursorTextLayout) = rememberCursorIntoViewHandler(isFocused, codeHolder.fieldValue.selection)
    var copiedRecently by remember(block.id) { mutableStateOf(false) }
    var languageMenuOpen by remember(block.id) { mutableStateOf(false) }

    LaunchedEffect(copiedRecently) {
        if (copiedRecently) {
            delay(1500)
            copiedRecently = false
        }
    }

    if (focusRequest != null && focusRequest.blockId == block.id && focusRequest.subItemIndex < 0) {
        LaunchedEffect(focusRequest.requestToken) {
            codeHolder.moveCursorTo(focusRequest.cursorOffset)
            runCatching { focusRequester.requestFocus() }
            onAction(WorkspaceAction.ConsumeFocusRequest(focusRequest.requestToken))
        }
    }

    val syntaxVisual = remember(searchQuery, colorScheme) {
        VisualTransformation { raw ->
            TransformedText(
                buildSyntaxHighlightedCode(raw.text, colorScheme, searchQuery),
                OffsetMapping.Identity
            )
        }
    }

    val languages = remember {
        listOf(
            "kotlin", "python", "typescript", "javascript", "rust", "go",
            "bash", "powershell", "sql", "json", "yaml", "dockerfile", "markdown", "text"
        )
    }

    val copyBg by animateColorAsState(
        targetValue = if (copiedRecently) colorScheme.tertiaryContainer else colorScheme.surfaceContainer,
        animationSpec = AllNotesMotion.effectsFast(),
        label = "CodeCopyBg"
    )
    val copyFg = if (copiedRecently) colorScheme.onTertiaryContainer else colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f))
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
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                AllNotesIcons.Code,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = block.language.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary
                            )
                            if (isActive) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    AllNotesIcons.ChevronDown,
                                    contentDescription = "Select code language",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
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

                Surface(
                    onClick = {
                        @Suppress("DEPRECATION")
                        clipboard.setText(AnnotatedString(codeHolder.fieldValue.text))
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        copiedRecently = true
                    },
                    color = copyBg,
                    contentColor = copyFg,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (copiedRecently) AllNotesIcons.Check else AllNotesIcons.Copy,
                            contentDescription = "Copy code",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (copiedRecently) "Copied" else "Copy",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                if (codeHolder.fieldValue.text.isEmpty() && isActive) {
                    Text(
                        text = "// Write or paste code…",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = monoFont),
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
                        fontFamily = monoFont,
                        color = colorScheme.onSurface
                    ),
                    visualTransformation = syntaxVisual,
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
                            handleVerticalBoundaryKeyEvent(
                                event = event,
                                fieldValue = codeHolder.fieldValue,
                                layout = layoutRef.value,
                                blockId = block.id,
                                onAction = onAction
                            )
                        }
                )
            }
        }
    }
}

private fun buildSyntaxHighlightedCode(
    text: String,
    colorScheme: ColorScheme,
    searchQuery: String
): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    if (searchQuery.isNotBlank()) {
        return RichSpanEngine.buildSearchHighlightedString(text, colorScheme, searchQuery)
    }
    return buildAnnotatedString {
        append(text)
        val commentStyle = SpanStyle(
            color = colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            fontStyle = FontStyle.Italic
        )
        val stringStyle = SpanStyle(color = colorScheme.tertiary)
        val keywordStyle = SpanStyle(color = colorScheme.primary, fontWeight = FontWeight.SemiBold)

        for (match in CodeTokenRegex.findAll(text)) {
            val token = match.value
            val style = when {
                token.startsWith("//") || token.startsWith("#") -> commentStyle
                token.startsWith("\"") || token.startsWith("'") -> stringStyle
                token in CodeKeywords -> keywordStyle
                else -> null
            }
            if (style != null) {
                addStyle(style, match.range.first, match.range.last + 1)
            }
        }
    }
}

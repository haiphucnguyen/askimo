/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.view.components.CodeHighlighter
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.parser.Parser
import org.commonmark.node.Text as MarkdownText

/**
 * Simple Markdown renderer for Compose.
 */
@Composable
fun markdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    viewportTopY: Float? = null,
) {
    val parser = Parser.builder()
        .extensions(listOf(TablesExtension.create(), AutolinkExtension.create()))
        .build()
    val document = parser.parse(markdown)

    Column(modifier = modifier) {
        renderNode(document, viewportTopY)
    }
}

@Composable
private fun renderNode(node: Node, viewportTopY: Float? = null) {
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is Paragraph -> {
                // Check if paragraph contains only a video link
                val videoUrl = extractVideoUrl(child)
                if (videoUrl != null) {
                    renderVideo(videoUrl)
                } else {
                    renderParagraph(child)
                }
            }
            is Heading -> renderHeading(child)
            is BulletList -> renderBulletList(child)
            is OrderedList -> renderOrderedList(child)
            is FencedCodeBlock -> renderCodeBlock(child, viewportTopY)
            is BlockQuote -> renderBlockQuote(child, viewportTopY)
            is TableBlock -> renderTable(child)
            is Image -> {
                // Check if it's actually a video
                val destination = child.destination
                if (isVideoUrl(destination)) {
                    renderVideo(destination)
                } else {
                    renderImage(child)
                }
            }
            else -> renderNode(child, viewportTopY)
        }
        child = child.next
    }
}

@Composable
private fun renderParagraph(paragraph: Paragraph) {
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val linkColor = MaterialTheme.colorScheme.primary

    // Extract raw text to check for LaTeX
    val rawText = extractTextContent(paragraph)

    // Check if this paragraph contains LaTeX formulas with \[ \] or [ ]
    val latexMatches = mutableListOf<Triple<Int, Int, String>>() // (start, end, content)

    // Find \[ ... \] or standalone [ ... ] patterns
    var i = 0
    while (i < rawText.length) {
        val hasBackslash = i < rawText.length - 1 && rawText[i] == '\\' && rawText[i + 1] == '['
        val justBracket = rawText[i] == '[' && (i == 0 || rawText[i - 1] != '\\')

        if (hasBackslash || justBracket) {
            val start = i
            val contentStart = if (hasBackslash) i + 2 else i + 1
            var j = contentStart

            while (j < rawText.length) {
                val hasEndBackslash = j < rawText.length - 1 && rawText[j] == '\\' && rawText[j + 1] == ']'
                val justEndBracket = rawText[j] == ']' && (j == 0 || rawText[j - 1] != '\\')

                if (hasEndBackslash || justEndBracket) {
                    val content = rawText.substring(contentStart, j)
                    // More comprehensive math content detection
                    val isMathContent = content.contains(Regex("[\\\\^_{}=+\\-*/]|\\b(sin|cos|tan|log|ln|exp|theta|pi|alpha|beta|gamma|delta|sum|int|frac|boxed|begin|end|aligned)\\b"))

                    if (content.isNotBlank() && isMathContent) {
                        val endIndex = if (hasEndBackslash) j + 2 else j + 1
                        // Fix markdown-mangled LaTeX content
                        val fixedContent = fixMarkdownMangledLatex(content)
                        latexMatches.add(Triple(start, endIndex, fixedContent))
                        i = endIndex
                        break
                    }
                }
                j++
            }
        }
        i++
    }

    // Find inline math \( ... \) - these should not span multiple lines
    i = 0
    while (i < rawText.length - 1) {
        if (rawText[i] == '\\' && rawText[i + 1] == '(') {
            val start = i
            val contentStart = i + 2
            var j = contentStart
            var foundEnd = false

            while (j < rawText.length - 1) {
                // Stop if we hit a newline (inline math shouldn't span lines)
                if (rawText[j] == '\n') {
                    break
                }

                if (rawText[j] == '\\' && rawText[j + 1] == ')') {
                    val content = rawText.substring(contentStart, j)
                    // Check if it's math content
                    val isMathContent = content.contains(Regex("[\\\\^_{}=+\\-*/]|\\b(sin|cos|tan|log|ln|exp|theta|pi|alpha|beta|gamma|delta|sum|int|frac)\\b"))

                    if (content.isNotBlank() && isMathContent) {
                        // Check for overlap with existing matches
                        val overlaps = latexMatches.any { (existingStart, existingEnd, _) ->
                            start in existingStart until existingEnd || existingStart in start until (j + 2)
                        }

                        if (!overlaps) {
                            // Fix markdown-mangled LaTeX content
                            val fixedContent = fixMarkdownMangledLatex(content)
                            latexMatches.add(Triple(start, j + 2, fixedContent))
                            foundEnd = true
                        }
                        i = j + 2
                        break
                    }
                }
                j++
            }

            if (!foundEnd) {
                i++
            }
        } else {
            i++
        }
    }

    // Sort matches by start position
    latexMatches.sortBy { it.first }

    // If we found LaTeX, render with mixed content using Row
    if (latexMatches.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            var lastIndex = 0
            latexMatches.forEach { (start, end, latexContent) ->
                // Render text before LaTeX (if any)
                if (start > lastIndex) {
                    val textBefore = rawText.substring(lastIndex, start)
                    if (textBefore.isNotBlank()) {
                        Text(
                            text = textBefore,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                // Render LaTeX formula as image
                latexFormula(
                    latex = latexContent.trim(),
                    fontSize = 32f,
                )

                lastIndex = end
            }

            // Render remaining text (if any)
            if (lastIndex < rawText.length) {
                val textAfter = rawText.substring(lastIndex)
                if (textAfter.isNotBlank()) {
                    Text(
                        text = textAfter,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    } else {
        // No LaTeX found, check for dollar notation $ ... $
        if (rawText.contains("$")) {
            // Try to detect $ ... $ patterns
            val dollarRegex = """\$([^\$\n]+?)\$""".toRegex()
            val dollarMatches = dollarRegex.findAll(rawText).toList()

            if (dollarMatches.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    var lastIdx = 0
                    dollarMatches.forEach { match ->
                        // Render text before
                        if (match.range.first > lastIdx) {
                            val textBefore = rawText.substring(lastIdx, match.range.first)
                            if (textBefore.isNotBlank()) {
                                Text(
                                    text = textBefore,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        // Render LaTeX formula
                        latexFormula(
                            latex = match.groupValues[1].trim(),
                            fontSize = 28f,
                        )

                        lastIdx = match.range.last + 1
                    }

                    // Render remaining text
                    if (lastIdx < rawText.length) {
                        val textAfter = rawText.substring(lastIdx)
                        if (textAfter.isNotBlank()) {
                            Text(
                                text = textAfter,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                return
            }
        }

        // No LaTeX at all, render normally with full markdown support
        val annotatedText = buildInlineContent(paragraph, inlineCodeBg, linkColor)
        Text(
            text = annotatedText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )
    }
}

/**
 * Fix LaTeX content that has been mangled by markdown escape processing.
 *
 * Markdown treats backslash as escape character, so:
 * - `\\` (LaTeX line break) becomes `\` + next char (e.g., `\a_n`)
 * - `\,` (LaTeX thin space) becomes `,`
 *
 * This function attempts to reconstruct the original LaTeX.
 */
private fun fixMarkdownMangledLatex(latex: String): String {
    var fixed = latex

    // Fix: \a_n → a_n  (after line break, letter should not have backslash)
    // Pattern: backslash followed by lowercase letter followed by underscore or caret
    // BUT preserve alignment markers like &= in aligned environments
    fixed = fixed.replace(Regex("""\\([a-z])([_^])""")) { matchResult ->
        val letter = matchResult.groupValues[1]
        val symbol = matchResult.groupValues[2]

        // Don't fix if this is after an alignment marker &
        val startIndex = matchResult.range.first
        if (startIndex > 0 && fixed.getOrNull(startIndex - 1) == '&') {
            matchResult.value // Keep as-is
        } else {
            "$letter$symbol"
        }
    }

    // Fix: \f(x) → f(x)  (function names shouldn't have leading backslash unless they're LaTeX commands)
    fixed = fixed.replace(Regex("""\\([a-z])\("""), "$1(")

    // Fix common markdown escape artifacts after line breaks
    // Pattern: } or \right followed by space and \X where X is a letter
    // BUT don't touch & alignment markers
    fixed = fixed.replace(Regex("""([}]|\\right)\s+\\([a-zA-Z])"""), "$1 $2")

    return fixed
}

@Composable
private fun renderHeading(heading: Heading) {
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val linkColor = MaterialTheme.colorScheme.primary

    val style = when (heading.level) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.headlineSmall
        3 -> MaterialTheme.typography.titleLarge
        4 -> MaterialTheme.typography.titleMedium
        5 -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.bodyLarge
    }

    Text(
        text = buildInlineContent(heading, inlineCodeBg, linkColor),
        style = style,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun renderBulletList(list: BulletList) {
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

    Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
        var item = list.firstChild
        while (item != null) {
            if (item is ListItem) {
                renderListItem(item, "• ", inlineCodeBg)
            }
            item = item.next
        }
    }
}

@Composable
private fun renderOrderedList(list: OrderedList) {
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

    Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
        var item = list.firstChild

        var index = list.markerStartNumber
        while (item != null) {
            if (item is ListItem) {
                renderListItem(item, "$index. ", inlineCodeBg)
                index++
            }
            item = item.next
        }
    }
}

@Composable
private fun renderListItem(
    item: ListItem,
    marker: String,
    inlineCodeBg: Color,
) {
    val linkColor = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        // First, collect inline content and nested blocks
        val inlineContent = mutableListOf<Node>()
        val nestedBlocks = mutableListOf<Node>()

        var child = item.firstChild
        while (child != null) {
            when (child) {
                is BulletList, is OrderedList -> nestedBlocks.add(child)
                else -> inlineContent.add(child)
            }
            child = child.next
        }

        // Render inline content with marker
        if (inlineContent.isNotEmpty()) {
            val annotatedText = buildAnnotatedString {
                append(marker)
                inlineContent.forEach { node ->
                    append(buildInlineContentForNode(node, inlineCodeBg, linkColor))
                }
            }

            Text(
                text = annotatedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Render nested lists
        nestedBlocks.forEach { block ->
            when (block) {
                is BulletList -> renderBulletList(block)
                is OrderedList -> renderOrderedList(block)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun renderCodeBlock(codeBlock: FencedCodeBlock, viewportTopY: Float? = null) {
    val backgroundColor = MaterialTheme.colorScheme.surface
    val isDark = backgroundColor.luminance() < 0.5
    val clipboardManager = LocalClipboardManager.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var isHovered by remember { mutableStateOf(false) }
    var codeBlockBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var codeBlockPositionInRoot by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }

    val language = codeBlock.info?.trim()?.takeIf { it.isNotBlank() }
    val theme = if (isDark) CodeHighlighter.darkTheme() else CodeHighlighter.lightTheme()
    val highlightedCode = CodeHighlighter.highlight(
        code = codeBlock.literal,
        language = language,
        theme = theme,
    )

    // Calculate button offset - simple logic
    val copyButtonTopOffset = if (viewportTopY != null && codeBlockPositionInRoot != null) {
        val posInRoot = codeBlockPositionInRoot!!
        // If position in root is less than viewport top, the top is scrolled out
        if (posInRoot.y < viewportTopY) {
            // Position button in visible area
            with(density) {
                val offsetPx = viewportTopY - posInRoot.y + 10f
                offsetPx.toDp()
            }
        } else {
            4.dp
        }
    } else {
        4.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .onGloballyPositioned { coordinates ->
                if (coordinates.isAttached) {
                    codeBlockBounds = coordinates.boundsInWindow()
                    codeBlockPositionInRoot = coordinates.positionInRoot()
                }
            },
    ) {
        Text(
            text = highlightedCode,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(12.dp)
                .horizontalScroll(rememberScrollState()),
        )

        // Simple: button inside code block, just adjust offset
        if (isHovered) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = copyButtonTopOffset, end = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                themedTooltip(text = stringResource("code.copy")) {
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(codeBlock.literal)) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource("code.copy.description"),
                            modifier = Modifier.size(16.dp).pointerHoverIcon(PointerIcon.Hand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun renderBlockQuote(blockQuote: BlockQuote, viewportTopY: Float? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(8.dp),
    ) {
        renderNode(blockQuote, viewportTopY)
    }
}

@Composable
private fun renderImage(image: Image) {
    val context = LocalPlatformContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(image.destination)
                .crossfade(true)
                .build(),
            contentDescription = image.title ?: extractTextContent(image),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(8.dp),
        )

        // Show caption if title or alt text exists
        val caption = image.title ?: extractTextContent(image)
        if (caption.isNotBlank()) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun renderVideo(videoUrl: String) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { uriHandler.openUri(videoUrl) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play video",
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            shape = CircleShape,
                        )
                        .padding(16.dp),
                    tint = Color.White,
                )
                Text(
                    text = "Click to play video",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Text(
            text = videoUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}

@Composable
private fun renderTable(table: TableBlock) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, borderColor),
    ) {
        var child = table.firstChild
        while (child != null) {
            when (child) {
                is TableHead -> {
                    // Render table header
                    var headerRow = child.firstChild
                    while (headerRow != null) {
                        if (headerRow is TableRow) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    ),
                            ) {
                                var cell = headerRow.firstChild
                                while (cell != null) {
                                    if (cell is TableCell) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .border(1.dp, borderColor)
                                                .padding(8.dp),
                                            contentAlignment = Alignment.TopStart,
                                        ) {
                                            Text(
                                                text = extractCellText(cell),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                    cell = cell.next
                                }
                            }
                        }
                        headerRow = headerRow.next
                    }
                }
                is TableBody -> {
                    // Render table body
                    var bodyRow = child.firstChild
                    while (bodyRow != null) {
                        if (bodyRow is TableRow) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                            ) {
                                var cell = bodyRow.firstChild
                                while (cell != null) {
                                    if (cell is TableCell) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .border(1.dp, borderColor)
                                                .padding(8.dp),
                                            contentAlignment = Alignment.TopStart,
                                        ) {
                                            Text(
                                                text = extractCellText(cell),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                    cell = cell.next
                                }
                            }
                        }
                        bodyRow = bodyRow.next
                    }
                }
            }
            child = child.next
        }
    }
}

/**
 * Extract plain text content from a table cell node.
 */
private fun extractCellText(node: Node): String {
    val builder = StringBuilder()
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is MarkdownText -> builder.append(child.literal)
            is Paragraph -> builder.append(extractCellText(child))
            is StrongEmphasis -> builder.append(extractCellText(child))
            is Emphasis -> builder.append(extractCellText(child))
            is Code -> builder.append(child.literal)
            else -> builder.append(extractCellText(child))
        }
        child = child.next
    }
    return builder.toString()
}

private fun buildInlineContent(
    node: Node,
    inlineCodeBg: Color,
    linkColor: Color,
): AnnotatedString = buildAnnotatedString {
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is MarkdownText -> {
                val text = child.literal
                var lastIndex = 0

                // Detect inline math expressions wrapped in \[ \] or [ ] or $ $ or $$ $$
                val mathRanges = mutableListOf<Triple<IntRange, String, Boolean>>() // Triple(range, content, isDisplayMath)

                // Find display math \[ ... \] or [ ... ] (markdown parser might strip the backslash)
                var i = 0
                while (i < text.length) {
                    // Check for \[ or just [
                    val hasBackslash = i < text.length - 1 && text[i] == '\\' && text[i + 1] == '['
                    val justBracket = text[i] == '[' && (i == 0 || text[i - 1] != '\\')

                    if (hasBackslash || justBracket) {
                        // Found start of display math
                        val start = i
                        val contentStart = if (hasBackslash) i + 2 else i + 1
                        var j = contentStart
                        var foundEnd = false

                        while (j < text.length) {
                            // Check for \] or just ]
                            val hasEndBackslash = j < text.length - 1 && text[j] == '\\' && text[j + 1] == ']'
                            val justEndBracket = text[j] == ']' && (j == 0 || text[j - 1] != '\\')

                            if (hasEndBackslash || justEndBracket) {
                                // Found end of display math
                                val content = text.substring(contentStart, j)
                                // Only treat as math if it contains typical math content
                                if (content.contains(Regex("[a-zA-Z\\\\^_{}=+\\-*/()]"))) {
                                    val endIndex = if (hasEndBackslash) j + 1 else j
                                    mathRanges.add(Triple(IntRange(start, endIndex), content, true))
                                    i = endIndex + 1 // Skip past the math expression
                                    foundEnd = true
                                    break
                                }
                            }
                            j++
                        }

                        // If we didn't find the end, just move past the opening bracket
                        if (!foundEnd) {
                            i++
                        }
                    } else {
                        i++
                    }
                }

                // Find inline math \( ... \)
                i = 0
                while (i < text.length - 1) {
                    if (text[i] == '\\' && text[i + 1] == '(') {
                        val start = i
                        val contentStart = i + 2
                        var j = contentStart
                        var foundEnd = false

                        while (j < text.length - 1) {
                            if (text[j] == '\\' && text[j + 1] == ')') {
                                val content = text.substring(contentStart, j)
                                // Only treat as math if it contains typical math content
                                if (content.contains(Regex("[a-zA-Z\\\\^_{}=+\\-*/()]"))) {
                                    // Check for overlap
                                    val overlaps = mathRanges.any { (range, _, _) ->
                                        start in range || (j + 1) in range ||
                                            range.first in start..(j + 1) || range.last in start..(j + 1)
                                    }

                                    if (!overlaps) {
                                        mathRanges.add(Triple(IntRange(start, j + 1), content, false))
                                    }
                                    i = j + 2
                                    foundEnd = true
                                    break
                                }
                            }
                            j++
                        }

                        if (!foundEnd) {
                            i++
                        }
                    } else {
                        i++
                    }
                }

                // Find dollar notation math $$ ... $$ and $ ... $
                val dollarRegex = """\$\$(.+?)\$\$|\$([^\$\n]+?)\$""".toRegex()
                dollarRegex.findAll(text).forEach { match ->
                    // Check if this range doesn't overlap with already found ranges
                    val overlaps = mathRanges.any { (range, _, _) ->
                        match.range.first in range || match.range.last in range ||
                            range.first in match.range || range.last in match.range
                    }
                    if (!overlaps) {
                        val content = match.groupValues[1].ifEmpty { match.groupValues[2] }
                        val isDisplayMath = match.value.startsWith("$$")
                        mathRanges.add(Triple(match.range, content, isDisplayMath))
                    }
                }

                // Sort by position
                mathRanges.sortBy { it.first.first }

                // Reset lastIndex for building the final string
                lastIndex = 0

                // Build the annotated string with math expressions
                mathRanges.forEach { (range, content, _) ->
                    // Append text before the math expression
                    if (range.first > lastIndex) {
                        append(text.substring(lastIndex, range.first))
                    }

                    // Render the math expression
                    // For now, we'll use a placeholder and render the image separately
                    // because Compose Text doesn't support inline images directly
                    if (content.isNotEmpty()) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                background = inlineCodeBg.copy(alpha = 0.2f),
                            ),
                        ) {
                            append(" ")
                            append(parseLatexMath(content.trim()))
                            append(" ")
                        }
                    }

                    lastIndex = range.last + 1
                }

                // Append remaining text after the last match
                if (lastIndex < text.length) {
                    append(text.substring(lastIndex))
                }
            }
            is StrongEmphasis -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(buildInlineContent(child, inlineCodeBg, linkColor))
                }
            }
            is Emphasis -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(buildInlineContent(child, inlineCodeBg, linkColor))
                }
            }
            is Code -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = inlineCodeBg,
                    ),
                ) {
                    append(" ${child.literal} ")
                }
            }
            is FencedCodeBlock -> {
                // Treat fenced code blocks as inline code when they appear in inline contexts
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = inlineCodeBg,
                    ),
                ) {
                    append(" ${child.literal} ")
                }
            }
            is Link -> {
                // Add link annotation for clickable links using the new LinkAnnotation API
                // Build styled content for link children (e.g., inline code with backticks)
                val linkContent = buildInlineContent(child, inlineCodeBg, linkColor)
                val displayContent = linkContent.ifEmpty {
                    AnnotatedString(child.destination)
                }

                withLink(
                    LinkAnnotation.Url(
                        url = child.destination,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                    ),
                ) {
                    append(displayContent)
                }
            }
            is Image -> {
                // For inline images, show [image: alt text] placeholder
                append("[image: ${extractTextContent(child)}]")
            }
            is HardLineBreak, is SoftLineBreak -> append("\n")
            is Paragraph -> append(buildInlineContent(child, inlineCodeBg, linkColor))
            else -> append(buildInlineContent(child, inlineCodeBg, linkColor))
        }
        child = child.next
    }
}

/**
 * Build inline content for a single node (used by list items).
 */
private fun buildInlineContentForNode(
    node: Node,
    inlineCodeBg: Color,
    linkColor: Color,
): AnnotatedString = buildAnnotatedString {
    append(buildInlineContent(node, inlineCodeBg, linkColor))
}

/**
 * Parse LaTeX math expressions and convert to styled AnnotatedString.
 * Handles superscripts, subscripts, and Greek letter symbols.
 */
private fun parseLatexMath(latex: String): AnnotatedString = buildAnnotatedString {
    var text = latex

    // Handle superscripts (e.g., e^{i\theta} or x^2)
    val superscriptRegex = """\^(\{[^}]+\}|\S)""".toRegex()
    text = superscriptRegex.replace(text) { matchResult ->
        val content = matchResult.groupValues[1].removeSurrounding("{", "}")
        "^($content)"
    }

    // Handle subscripts (e.g., x_{n})
    val subscriptRegex = """_(\{[^}]+\}|\S)""".toRegex()
    text = subscriptRegex.replace(text) { matchResult ->
        val content = matchResult.groupValues[1].removeSurrounding("{", "}")
        "_($content)"
    }

    // Replace Greek letters and special mathematical symbols
    val symbols = mapOf(
        "\\theta" to "θ",
        "\\pi" to "π",
        "\\alpha" to "α",
        "\\beta" to "β",
        "\\gamma" to "γ",
        "\\delta" to "δ",
        "\\epsilon" to "ε",
        "\\zeta" to "ζ",
        "\\eta" to "η",
        "\\lambda" to "λ",
        "\\mu" to "μ",
        "\\nu" to "ν",
        "\\xi" to "ξ",
        "\\rho" to "ρ",
        "\\sigma" to "σ",
        "\\tau" to "τ",
        "\\phi" to "φ",
        "\\chi" to "χ",
        "\\psi" to "ψ",
        "\\omega" to "ω",
        "\\Theta" to "Θ",
        "\\Pi" to "Π",
        "\\Sigma" to "Σ",
        "\\Phi" to "Φ",
        "\\Psi" to "Ψ",
        "\\Omega" to "Ω",
        "\\Delta" to "Δ",
        "\\Gamma" to "Γ",
        "\\Lambda" to "Λ",
        "\\infty" to "∞",
        "\\sum" to "∑",
        "\\prod" to "∏",
        "\\int" to "∫",
        "\\sqrt" to "√",
        "\\cdot" to "⋅",
        "\\times" to "×",
        "\\div" to "÷",
        "\\pm" to "±",
        "\\mp" to "∓",
        "\\neq" to "≠",
        "\\leq" to "≤",
        "\\geq" to "≥",
        "\\approx" to "≈",
        "\\equiv" to "≡",
        "\\in" to "∈",
        "\\notin" to "∉",
        "\\subset" to "⊂",
        "\\supset" to "⊃",
        "\\cup" to "∪",
        "\\cap" to "∩",
        "\\forall" to "∀",
        "\\exists" to "∃",
        "\\nabla" to "∇",
        "\\partial" to "∂",
        "\\propto" to "∝",
        "\\rightarrow" to "→",
        "\\leftarrow" to "←",
        "\\leftrightarrow" to "↔",
        "\\Rightarrow" to "⇒",
        "\\Leftarrow" to "⇐",
        "\\Leftrightarrow" to "⇔",
        "\\cos" to "cos",
        "\\sin" to "sin",
        "\\tan" to "tan",
        "\\log" to "log",
        "\\ln" to "ln",
        "\\exp" to "exp",
    )

    symbols.forEach { (latex, unicode) ->
        text = text.replace(latex, unicode)
    }

    // Parse and apply styles for superscripts and subscripts
    var i = 0
    while (i < text.length) {
        when {
            // Superscript
            text[i] == '^' && i + 1 < text.length && text[i + 1] == '(' -> {
                val end = text.indexOf(')', i + 2)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontSize = 11.sp,
                            baselineShift = BaselineShift(0.5f),
                        ),
                    ) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            // Subscript
            text[i] == '_' && i + 1 < text.length && text[i + 1] == '(' -> {
                val end = text.indexOf(')', i + 2)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontSize = 11.sp,
                            baselineShift = BaselineShift(-0.3f),
                        ),
                    ) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}

/**
 * Extract text content from a node by collecting all MarkdownText children.
 * Used for extracting link text, image alt text, etc.
 */
private fun extractTextContent(node: Node): String {
    val builder = StringBuilder()
    var child = node.firstChild
    while (child != null) {
        if (child is MarkdownText) {
            builder.append(child.literal)
        }
        child = child.next
    }
    return builder.toString()
}

/**
 * Check if a URL is a video URL based on file extension.
 */
private fun isVideoUrl(url: String): Boolean {
    val videoExtensions = listOf(".mp4", ".webm", ".mov", ".avi", ".mkv", ".m4v", ".flv", ".wmv")
    return videoExtensions.any { url.lowercase().endsWith(it) }
}

/**
 * Extract video URL from paragraph if it's the only content.
 * Returns the video URL if found, null otherwise.
 */
private fun extractVideoUrl(paragraph: Paragraph): String? {
    var child = paragraph.firstChild
    var linkFound: String? = null
    var hasOtherContent = false

    while (child != null) {
        when (child) {
            is Link -> {
                val destination = child.destination
                if (isVideoUrl(destination)) {
                    linkFound = destination
                } else {
                    hasOtherContent = true
                }
            }
            is MarkdownText -> {
                if (child.literal.trim().isNotEmpty()) {
                    hasOtherContent = true
                }
            }
            else -> hasOtherContent = true
        }
        child = child.next
    }

    return if (!hasOtherContent && linkFound != null) linkFound else null
}

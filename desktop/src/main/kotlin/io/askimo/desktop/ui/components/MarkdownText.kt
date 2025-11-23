/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.ui.components

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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
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
 *
 * This component renders markdown text using commonmark parser and Compose UI components.
 * It supports basic markdown features like headings, lists, code blocks, emphasis, etc.
 */
@Composable
fun markdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val parser = Parser.builder()
        .extensions(listOf(TablesExtension.create()))
        .build()
    val document = parser.parse(markdown)

    Column(modifier = modifier) {
        renderNode(document)
    }
}

@Composable
private fun renderNode(node: Node) {
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
            is FencedCodeBlock -> renderCodeBlock(child)
            is BlockQuote -> renderBlockQuote(child)
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
            else -> renderNode(child)
        }
        child = child.next
    }
}

@Composable
private fun renderParagraph(paragraph: Paragraph) {
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val linkColor = MaterialTheme.colorScheme.primary
    val annotatedText = buildInlineContent(paragraph, inlineCodeBg, linkColor)

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
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
private fun renderCodeBlock(codeBlock: FencedCodeBlock) {
    // Use surface color for code blocks to ensure proper contrast with primaryContainer message background
    // This provides better visual separation and respects the theme colors
    val backgroundColor = MaterialTheme.colorScheme.surface
    val isDark = backgroundColor.luminance() < 0.5

    val clipboardManager = LocalClipboardManager.current
    var isHovered by remember { mutableStateOf(false) }

    // Get language from the info string (e.g., "kotlin", "java", "python")
    val language = codeBlock.info?.trim()?.takeIf { it.isNotBlank() }

    // Choose theme based on background luminance
    val theme = if (isDark) {
        CodeHighlighter.darkTheme()
    } else {
        CodeHighlighter.lightTheme()
    }

    // Apply syntax highlighting
    val highlightedCode = CodeHighlighter.highlight(
        code = codeBlock.literal,
        language = language,
        theme = theme,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false },
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

        // Copy button (shown on hover)
        if (isHovered) {
            themedTooltip(
                text = "Copy code",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            ) {
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(codeBlock.literal))
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun renderBlockQuote(blockQuote: BlockQuote) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(8.dp),
    ) {
        renderNode(blockQuote)
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
            is MarkdownText -> append(child.literal)
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
                val linkText = extractTextContent(child)
                val displayText = linkText.ifEmpty { child.destination }

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
                    append(displayText)
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

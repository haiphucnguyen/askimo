/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
            is Paragraph -> renderParagraph(child)
            is Heading -> renderHeading(child)
            is BulletList -> renderBulletList(child)
            is OrderedList -> renderOrderedList(child)
            is FencedCodeBlock -> renderCodeBlock(child)
            is BlockQuote -> renderBlockQuote(child)
            is TableBlock -> renderTable(child)
            else -> renderNode(child)
        }
        child = child.next
    }
}

@Composable
private fun renderParagraph(paragraph: Paragraph) {
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

    Text(
        text = buildInlineContent(paragraph, inlineCodeBg),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun renderHeading(heading: Heading) {
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

    val style = when (heading.level) {
        1 -> MaterialTheme.typography.headlineLarge
        2 -> MaterialTheme.typography.headlineMedium
        3 -> MaterialTheme.typography.headlineSmall
        4 -> MaterialTheme.typography.titleLarge
        5 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }

    Text(
        text = buildInlineContent(heading, inlineCodeBg),
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
                Text(
                    text = buildAnnotatedString {
                        append("• ")
                        append(buildInlineContent(item, inlineCodeBg))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
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
                Text(
                    text = buildAnnotatedString {
                        append("$index. ")
                        append(buildInlineContent(item, inlineCodeBg))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                index++
            }
            item = item.next
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
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(codeBlock.literal))
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp),
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
private fun renderTable(table: TableBlock) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val minCellWidth = 150.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier.border(1.dp, borderColor),
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
                                    modifier = Modifier.background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    ),
                                ) {
                                    var cell = headerRow.firstChild
                                    while (cell != null) {
                                        if (cell is TableCell) {
                                            Box(
                                                modifier = Modifier
                                                    .widthIn(min = minCellWidth)
                                                    .border(1.dp, borderColor)
                                                    .padding(8.dp),
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
                                Row {
                                    var cell = bodyRow.firstChild
                                    while (cell != null) {
                                        if (cell is TableCell) {
                                            Box(
                                                modifier = Modifier
                                                    .widthIn(min = minCellWidth)
                                                    .border(1.dp, borderColor)
                                                    .padding(8.dp),
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
    inlineCodeBg: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString {
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is MarkdownText -> append(child.literal)
            is StrongEmphasis -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(buildInlineContent(child, inlineCodeBg))
                }
            }
            is Emphasis -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(buildInlineContent(child, inlineCodeBg))
                }
            }
            is Code -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = inlineCodeBg,
                    ),
                ) {
                    append(child.literal)
                }
            }
            is HardLineBreak, is SoftLineBreak -> append("\n")
            is Paragraph -> append(buildInlineContent(child, inlineCodeBg))
            else -> append(buildInlineContent(child, inlineCodeBg))
        }
        child = child.next
    }
}

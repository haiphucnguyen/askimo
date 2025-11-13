/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
    val parser = Parser.builder().build()
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
            else -> renderNode(child)
        }
        child = child.next
    }
}

@Composable
private fun renderParagraph(paragraph: Paragraph) {
    Text(
        text = buildInlineContent(paragraph),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun renderHeading(heading: Heading) {
    val style = when (heading.level) {
        1 -> MaterialTheme.typography.headlineLarge
        2 -> MaterialTheme.typography.headlineMedium
        3 -> MaterialTheme.typography.headlineSmall
        4 -> MaterialTheme.typography.titleLarge
        5 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }

    Text(
        text = buildInlineContent(heading),
        style = style,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun renderBulletList(list: BulletList) {
    Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
        var item = list.firstChild
        while (item != null) {
            if (item is ListItem) {
                Text(
                    text = buildAnnotatedString {
                        append("• ")
                        append(buildInlineContent(item))
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            item = item.next
        }
    }
}

@Composable
private fun renderOrderedList(list: OrderedList) {
    Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
        var item = list.firstChild

        @Suppress("DEPRECATION")
        var index = list.startNumber
        while (item != null) {
            if (item is ListItem) {
                Text(
                    text = buildAnnotatedString {
                        append("$index. ")
                        append(buildInlineContent(item))
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                index++
            }
            item = item.next
        }
    }
}

@Composable
private fun renderCodeBlock(codeBlock: FencedCodeBlock) {
    Text(
        text = codeBlock.literal,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
            .horizontalScroll(rememberScrollState()),
    )
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

private fun buildInlineContent(node: Node): androidx.compose.ui.text.AnnotatedString = buildAnnotatedString {
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is MarkdownText -> append(child.literal)
            is StrongEmphasis -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(buildInlineContent(child))
                }
            }
            is Emphasis -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(buildInlineContent(child))
                }
            }
            is Code -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = androidx.compose.ui.graphics.Color.LightGray.copy(alpha = 0.3f),
                    ),
                ) {
                    append(child.literal)
                }
            }
            is HardLineBreak, is SoftLineBreak -> append("\n")
            is Paragraph -> append(buildInlineContent(child))
            else -> append(buildInlineContent(child))
        }
        child = child.next
    }
}

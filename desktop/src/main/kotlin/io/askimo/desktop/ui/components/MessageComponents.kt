/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.askimo.desktop.model.ChatMessage

@Composable
fun messageList(messages: List<ChatMessage>) {
    val scrollState = rememberScrollState()

    LaunchedEffect(messages.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        messages.forEach { message ->
            messageBubble(message)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun messageBubble(message: ChatMessage) {
    val clipboardManager = LocalClipboardManager.current
    var isHovered by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Dynamic width calculation based on available space
        // For narrow screens: use 90% of width (min 200dp)
        // For medium screens: use 75% of width (up to 800dp)
        // For wide screens: use 65% of width (max 1000dp for readability)
        val maxBubbleWidth = when {
            maxWidth < 600.dp -> (maxWidth * 0.9f).coerceAtLeast(200.dp)
            maxWidth < 1200.dp -> (maxWidth * 0.75f).coerceAtMost(800.dp)
            else -> (maxWidth * 0.65f).coerceAtMost(1000.dp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        ) {
            Box(
                modifier = Modifier
                    .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                    .onPointerEvent(PointerEventType.Exit) { isHovered = false },
            ) {
                Card(
                    modifier = Modifier.widthIn(max = maxBubbleWidth),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.isUser) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                    ),
                ) {
                    if (message.isUser) {
                        // User messages: plain text with selection enabled
                        SelectionContainer {
                            Text(
                                text = message.content,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        // AI messages: markdown rendering with selection enabled
                        SelectionContainer {
                            markdownText(
                                markdown = message.content,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }

                // Copy button for AI messages (shown on hover)
                if (!message.isUser && isHovered) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(message.content))
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy message",
                            modifier = Modifier.size(16.dp).pointerHoverIcon(PointerIcon.Hand),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}

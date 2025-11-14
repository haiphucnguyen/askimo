/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.ui.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.askimo.desktop.model.ChatMessage
import io.askimo.desktop.ui.components.messageList

@Composable
fun chatView(
    messages: List<ChatMessage>,
    inputText: TextFieldValue,
    onInputTextChange: (TextFieldValue) -> Unit,
    onSendMessage: (String) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // Messages area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            if (messages.isEmpty()) {
                Text(
                    "Welcome to Askimo Desktop!\nStart a conversation by typing a message below.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                messageList(messages = messages)
            }
        }

        HorizontalDivider()

        // Input area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChange,
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { keyEvent ->
                        // Handle Enter key to send message (without Shift)
                        // Shift+Enter adds a new line
                        if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown) {
                            if (keyEvent.isShiftPressed) {
                                // Shift+Enter: add new line manually and move cursor
                                val newText = inputText.text + "\n"
                                val newCursorPosition = newText.length
                                onInputTextChange(
                                    TextFieldValue(
                                        text = newText,
                                        selection = TextRange(newCursorPosition),
                                    ),
                                )
                                true // consume the event
                            } else {
                                // Enter without Shift: send message
                                if (inputText.text.isNotBlank() && !isLoading) {
                                    onSendMessage(inputText.text)
                                }
                                true // consume the event
                            }
                        } else {
                            false // don't consume other key events
                        }
                    },
                placeholder = { Text("Type your message... (Enter to send, Shift+Enter for new line)") },
                maxLines = 5,
                isError = errorMessage != null,
                supportingText = if (errorMessage != null) {
                    { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
                } else {
                    null
                },
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(12.dp)
                        .width(24.dp),
                )
            } else {
                IconButton(
                    onClick = {
                        if (inputText.text.isNotBlank()) {
                            onSendMessage(inputText.text)
                        }
                    },
                    enabled = inputText.text.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

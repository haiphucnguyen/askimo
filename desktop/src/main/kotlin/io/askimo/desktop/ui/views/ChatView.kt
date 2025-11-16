/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.askimo.desktop.model.ChatMessage
import io.askimo.desktop.model.FileAttachment
import io.askimo.desktop.ui.components.messageList
import io.askimo.desktop.ui.theme.ComponentColors

// Helper function for file size formatting
private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}

// File attachment item composable
@Composable
private fun fileAttachmentItem(
    attachment: FileAttachment,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = ComponentColors.surfaceVariantCardColors(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column {
                    Text(
                        text = attachment.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatFileSize(attachment.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(24.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove attachment",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun chatView(
    messages: List<ChatMessage>,
    inputText: TextFieldValue,
    onInputTextChange: (TextFieldValue) -> Unit,
    onSendMessage: (String, List<FileAttachment>) -> Unit,
    onStopResponse: () -> Unit = {},
    isLoading: Boolean = false,
    isThinking: Boolean = false,
    thinkingElapsedSeconds: Int = 0,
    spinnerFrame: Char = '⠋',
    errorMessage: String? = null,
    attachments: List<FileAttachment> = emptyList(),
    onAttachmentsChange: (List<FileAttachment>) -> Unit = {},
    provider: String? = null,
    model: String? = null,
    onNavigateToSettings: () -> Unit = {},
    hasMoreMessages: Boolean = false,
    isLoadingPrevious: Boolean = false,
    onLoadPrevious: () -> Unit = {},
    isSearchMode: Boolean = false,
    searchQuery: String = "",
    searchResults: List<ChatMessage> = emptyList(),
    isSearching: Boolean = false,
    onSearch: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onJumpToMessage: (String, java.time.LocalDateTime) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // Configuration info header
        if (provider != null && model != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = ComponentColors.primaryCardColors(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Provider: $provider",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        VerticalDivider(
                            modifier = Modifier.size(width = 1.dp, height = 20.dp),
                        )
                        Text(
                            text = "Model: $model",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    TextButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        colors = ComponentColors.primaryTextButtonColors(),
                    ) {
                        Text(
                            text = "Change",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearch,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search in conversation...") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = onClearSearch,
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear search",
                            )
                        }
                    }
                },
                singleLine = true,
            )
        }

        // Search mode indicator
        if (isSearchMode) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = ComponentColors.bannerCardColors(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isSearching) {
                            "Searching..."
                        } else {
                            "Found ${searchResults.size} result(s)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        // Messages area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            when {
                isSearchMode && searchResults.isEmpty() && !isSearching -> {
                    Text(
                        "No messages found matching \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                isSearchMode -> {
                    messageList(
                        messages = searchResults,
                        isThinking = false,
                        thinkingElapsedSeconds = 0,
                        spinnerFrame = spinnerFrame,
                        hasMoreMessages = false,
                        isLoadingPrevious = false,
                        onLoadPrevious = {},
                        searchQuery = searchQuery,
                        onMessageClick = onJumpToMessage,
                    )
                }
                messages.isEmpty() -> {
                    Text(
                        "Welcome to Askimo!\nStart a conversation by typing a message below.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    messageList(
                        messages = messages,
                        isThinking = isThinking,
                        thinkingElapsedSeconds = thinkingElapsedSeconds,
                        spinnerFrame = spinnerFrame,
                        hasMoreMessages = hasMoreMessages,
                        isLoadingPrevious = isLoadingPrevious,
                        onLoadPrevious = onLoadPrevious,
                    )
                }
            }
        }

        HorizontalDivider()

        // Input area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // File attachments display
            if (attachments.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    attachments.forEach { attachment ->
                        fileAttachmentItem(
                            attachment = attachment,
                            onRemove = {
                                onAttachmentsChange(attachments - attachment)
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Attach file button
                IconButton(
                    onClick = {
                        // Open file picker
                        val fileChooser =
                            java.awt.FileDialog(null as java.awt.Frame?, "Select File", java.awt.FileDialog.LOAD)
                        fileChooser.isVisible = true
                        val selectedFile = fileChooser.file
                        val selectedDir = fileChooser.directory
                        if (selectedFile != null && selectedDir != null) {
                            val file = java.io.File(selectedDir, selectedFile)
                            try {
                                val content = file.readText()
                                val attachment = FileAttachment(
                                    fileName = file.name,
                                    content = content,
                                    mimeType = file.extension,
                                    size = file.length(),
                                )
                                onAttachmentsChange(attachments + attachment)
                            } catch (e: Exception) {
                                // Handle error - file too large or unreadable
                                println("Error reading file: ${e.message}")
                            }
                        }
                    },
                    colors = ComponentColors.primaryIconButtonColors(),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Attach file",
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

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
                                    // Shift+Enter: insert new line at cursor position
                                    val cursorPosition = inputText.selection.start
                                    val textBeforeCursor = inputText.text.substring(0, cursorPosition)
                                    val textAfterCursor = inputText.text.substring(cursorPosition)
                                    val newText = textBeforeCursor + "\n" + textAfterCursor
                                    val newCursorPosition = cursorPosition + 1
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
                                        onSendMessage(inputText.text, attachments)
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

                // Show stop button when loading, send button otherwise
                if (isLoading) {
                    IconButton(
                        onClick = onStopResponse,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (inputText.text.isNotBlank()) {
                                onSendMessage(inputText.text, attachments)
                            }
                        },
                        enabled = inputText.text.isNotBlank(),
                        colors = ComponentColors.primaryIconButtonColors(),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                        )
                    }
                }
            }
        }
    }
}

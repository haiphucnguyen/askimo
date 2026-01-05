/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.askimo.core.chat.dto.ChatMessageDTO
import io.askimo.core.chat.dto.FileAttachmentDTO
import io.askimo.core.logging.logger
import io.askimo.core.util.TimeUtil
import io.askimo.core.util.formatFileSize
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.keymap.KeyMapManager
import io.askimo.desktop.keymap.KeyMapManager.AppShortcut
import io.askimo.desktop.theme.ComponentColors
import io.askimo.desktop.util.FileDialogUtils
import io.askimo.desktop.util.Platform
import java.awt.FileDialog
import java.awt.Frame
import java.time.LocalDateTime
import java.util.UUID

private object ChatInputFieldObject
private val log = logger<ChatInputFieldObject>()

/**
 * Reusable chat input field component with attachment support.
 *
 * @param inputText Current input text value
 * @param onInputTextChange Callback when input text changes
 * @param attachments List of file attachments
 * @param onAttachmentsChange Callback when attachments list changes
 * @param onSendMessage Callback when send button is clicked
 * @param isLoading Whether the chat is currently loading
 * @param isThinking Whether the AI is thinking
 * @param onStopResponse Callback to stop the current response
 * @param errorMessage Optional error message to display
 * @param editingMessage Optional message being edited
 * @param onCancelEdit Callback to cancel edit mode
 * @param sessionId Optional session ID for file attachments
 * @param placeholder Optional placeholder text
 * @param modifier Optional modifier for the component
 */
@Composable
fun chatInputField(
    inputText: TextFieldValue,
    onInputTextChange: (TextFieldValue) -> Unit,
    attachments: List<FileAttachmentDTO>,
    onAttachmentsChange: (List<FileAttachmentDTO>) -> Unit,
    onSendMessage: () -> Unit,
    isLoading: Boolean = false,
    isThinking: Boolean = false,
    onStopResponse: () -> Unit = {},
    errorMessage: String? = null,
    editingMessage: ChatMessageDTO? = null,
    onCancelEdit: () -> Unit = {},
    sessionId: String? = null,
    placeholder: String = stringResource("chat.input.placeholder"),
    modifier: Modifier = Modifier,
) {
    val inputFocusRequester = remember { FocusRequester() }

    // Focus input field when entering edit mode
    LaunchedEffect(editingMessage) {
        if (editingMessage != null) {
            inputFocusRequester.requestFocus()
        }
    }

    // File attachment handler
    val selectFileTitle = stringResource("chat.select.file")
    val openFileDialog = {
        val fileChooser = FileDialog(null as Frame?, selectFileTitle, FileDialog.LOAD)
        fileChooser.isMultipleMode = true
        fileChooser.setFilenameFilter(FileDialogUtils.createSupportedFileFilter())
        fileChooser.isVisible = true
        val selectedFiles = fileChooser.files
        if (selectedFiles != null && selectedFiles.isNotEmpty()) {
            try {
                val newAttachments = selectedFiles.map { file ->
                    FileAttachmentDTO(
                        id = UUID.randomUUID().toString(),
                        messageId = "",
                        sessionId = sessionId ?: "",
                        fileName = file.name,
                        mimeType = file.extension,
                        size = file.length(),
                        createdAt = LocalDateTime.now(),
                        content = null,
                        filePath = file.absolutePath,
                    )
                }
                onAttachmentsChange(attachments + newAttachments)
            } catch (e: Exception) {
                log.error("Error adding file attachments: ${e.message}", e)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { keyEvent ->
                val shortcut = KeyMapManager.handleKeyEvent(keyEvent)
                when (shortcut) {
                    AppShortcut.ATTACH_FILE -> {
                        if (!isLoading) {
                            openFileDialog()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            },
    ) {
        // Edit mode banner
        if (editingMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = ComponentColors.bannerCardColors(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = editingMessage.timestamp?.let { timestamp ->
                                val formattedTime = TimeUtil.formatDisplay(timestamp)
                                stringResource("message.editing.banner.from", formattedTime)
                            } ?: stringResource("message.editing.banner"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    IconButton(
                        onClick = onCancelEdit,
                        modifier = Modifier
                            .size(32.dp)
                            .pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource("message.cancel.edit"),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

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
            themedTooltip(
                text = stringResource("chat.attach.file", Platform.modifierKey),
            ) {
                IconButton(
                    onClick = openFileDialog,
                    colors = ComponentColors.primaryIconButtonColors(),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Attach file",
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(inputFocusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        val shortcut = KeyMapManager.handleKeyEvent(keyEvent)
                        when (shortcut) {
                            AppShortcut.NEW_LINE -> {
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
                                true
                            }
                            AppShortcut.SEND_MESSAGE -> {
                                if (inputText.text.isNotBlank() && !isLoading && !isThinking) {
                                    onSendMessage()
                                }
                                true
                            }
                            else -> false
                        }
                    },
                placeholder = { Text(placeholder) },
                maxLines = 5,
                isError = errorMessage != null,
                supportingText = if (errorMessage != null) {
                    { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
                } else {
                    null
                },
                colors = ComponentColors.outlinedTextFieldColors(),
            )

            Spacer(modifier = Modifier.width(8.dp))

            if (isLoading || isThinking) {
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
                themedTooltip(
                    text = if (editingMessage != null) {
                        stringResource("message.update.regenerate")
                    } else {
                        stringResource("message.send")
                    },
                ) {
                    IconButton(
                        onClick = onSendMessage,
                        enabled = inputText.text.isNotBlank(),
                        colors = ComponentColors.primaryIconButtonColors(),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            if (editingMessage != null) Icons.Default.Edit else Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (editingMessage != null) {
                                stringResource("message.update.regenerate")
                            } else {
                                stringResource("message.send")
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun fileAttachmentItem(
    attachment: FileAttachmentDTO,
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
                    contentDescription = stringResource("chat.attachment.remove"),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.askimo.core.chat.dto.ChatMessageDTO
import io.askimo.core.chat.dto.FileAttachmentDTO
import io.askimo.core.logging.currentFileLogger
import io.askimo.core.util.TimeUtil
import io.askimo.core.util.formatFileSize
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.keymap.KeyMapManager
import io.askimo.desktop.common.keymap.KeyMapManager.AppShortcut
import io.askimo.desktop.common.theme.ComponentColors
import io.askimo.desktop.common.ui.themedTooltip
import io.askimo.desktop.common.ui.util.FileDialogUtils
import io.askimo.desktop.util.Platform
import java.awt.Cursor
import java.awt.FileDialog
import java.awt.Frame
import java.time.LocalDateTime
import java.util.UUID

private val log = currentFileLogger()

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

    // State for image creation mode
    var isImageCreationMode by remember { mutableStateOf(false) }

    // State for resizable text field (min 60dp, will calculate max based on available space)
    val defaultTextFieldHeight = 60.dp
    val badgeHeight = 44.dp // Height reserved for the badge + gap (badge ~36dp + 8dp gap)
    var textFieldHeight by remember { mutableStateOf(defaultTextFieldHeight) }
    var manuallyResized by remember { mutableStateOf(false) }

    // Calculate desired height based on text content
    val lineCount = remember(inputText.text) {
        if (inputText.text.isEmpty()) 1 else inputText.text.count { it == '\n' } + 1
    }

    // Approximate height per line (can be adjusted based on your text style)
    val lineHeight = 24.dp
    val padding = 36.dp // Top and bottom padding for the text field
    val calculatedHeight = (lineHeight * lineCount) + padding

    // Total height includes badge space when in image creation mode
    val totalFieldHeight = if (isImageCreationMode) textFieldHeight + badgeHeight else textFieldHeight

    // Reset height to default when message is sent (detected by empty input text)
    LaunchedEffect(inputText.text) {
        if (inputText.text.isEmpty()) {
            textFieldHeight = defaultTextFieldHeight
            manuallyResized = false
        }
    }

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
                            tint = MaterialTheme.colorScheme.onSurface,
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
            verticalAlignment = Alignment.Top,
        ) {
            // Action dropdown menu (attachments, image creation, etc.)
            var actionMenuExpanded by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier.height(textFieldHeight),
                contentAlignment = Alignment.Center,
            ) {
                themedTooltip(
                    text = stringResource("chat.attach.file", Platform.modifierKey),
                ) {
                    IconButton(
                        onClick = { actionMenuExpanded = true },
                        colors = ComponentColors.primaryIconButtonColors(),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Actions menu",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                ComponentColors.themedDropdownMenu(
                    expanded = actionMenuExpanded,
                    onDismissRequest = { actionMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.AttachFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource("chat.attach.file.menu"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        },
                        onClick = {
                            actionMenuExpanded = false
                            openFileDialog()
                        },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    )

                    HorizontalDivider()

                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource("chat.create.image.menu"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        },
                        onClick = {
                            actionMenuExpanded = false
                            isImageCreationMode = true
                        },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Wrap in BoxWithConstraints to get max available height
            BoxWithConstraints(
                modifier = Modifier.weight(1f),
            ) {
                val maxAvailableHeight = maxHeight
                val minTextFieldHeight = defaultTextFieldHeight
                val maxTextFieldHeight = (maxAvailableHeight * 0.5f).coerceAtLeast(minTextFieldHeight)

                // Auto-calculate height if not manually resized
                LaunchedEffect(calculatedHeight, manuallyResized) {
                    if (!manuallyResized) {
                        textFieldHeight = calculatedHeight.coerceIn(minTextFieldHeight, maxTextFieldHeight)
                    }
                }

                Column {
                    // Resize handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .pointerHoverIcon(
                                PointerIcon(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)),
                            )
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { change, dragAmount ->
                                    change.consume()
                                    val newHeight = textFieldHeight - dragAmount.toDp()
                                    textFieldHeight = newHeight.coerceIn(minTextFieldHeight, maxTextFieldHeight)
                                    manuallyResized = true
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Visual indicator
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(2.dp),
                                ),
                        )
                    }

                    // Text field with image mode badge overlay
                    Box(
                        modifier = Modifier.height(totalFieldHeight),
                    ) {
                        // Text field with constrained height - using Box padding to reserve space for badge
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = if (isImageCreationMode) badgeHeight else 0.dp),
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = onInputTextChange,
                                modifier = Modifier
                                    .fillMaxWidth()
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
                                maxLines = Int.MAX_VALUE,
                                isError = errorMessage != null,
                                supportingText = if (errorMessage != null) {
                                    { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
                                } else {
                                    null
                                },
                                colors = ComponentColors.outlinedTextFieldColors(),
                            )
                        }

                        // Image creation mode badge at bottom
                        if (isImageCreationMode) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(bottom = 6.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                tonalElevation = 2.dp,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        text = stringResource("chat.create.image.mode"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource("chat.create.image.mode.cancel"),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { isImageCreationMode = false }
                                            .pointerHoverIcon(PointerIcon.Hand),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier.height(textFieldHeight),
                contentAlignment = Alignment.Center,
            ) {
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
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
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
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Column {
                    Text(
                        text = attachment.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
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
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

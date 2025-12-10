/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.chat

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.askimo.core.chat.dto.ChatMessageDTO
import io.askimo.core.chat.dto.FileAttachmentDTO
import io.askimo.core.util.formatFileSize
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.theme.ComponentColors
import io.askimo.desktop.util.highlightSearchText
import io.askimo.desktop.view.components.markdownText
import io.askimo.desktop.view.components.themedTooltip
import java.time.LocalDateTime

@Composable
fun messageList(
    messages: List<ChatMessageDTO>,
    isThinking: Boolean = false,
    thinkingElapsedSeconds: Int = 0,
    spinnerFrame: Char = '⠋',
    hasMoreMessages: Boolean = false,
    isLoadingPrevious: Boolean = false,
    onLoadPrevious: () -> Unit = {},
    searchQuery: String = "",
    currentSearchResultIndex: Int = 0,
    onMessageClick: ((String, LocalDateTime) -> Unit)? = null,
    onEditMessage: ((ChatMessageDTO) -> Unit)? = null,
    onDownloadAttachment: ((FileAttachmentDTO) -> Unit)? = null,
    userAvatarPath: String? = null,
    aiAvatarPath: String? = null,
) {
    val scrollState = rememberScrollState()

    // Key shouldAutoScroll to the first message ID (if exists) or message count
    // This ensures it resets to true when switching to a different session
    val sessionKey = messages.firstOrNull()?.id ?: messages.size
    var shouldAutoScroll by remember(sessionKey) { mutableStateOf(true) }

    // Auto-scroll to bottom when messages change (including streaming updates) or when thinking
    LaunchedEffect(messages, isThinking) {
        if (shouldAutoScroll) {
            // Use scrollTo instead of animateScrollTo for instant scroll during streaming
            // This ensures the view stays at the bottom even during rapid updates
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    // Additional auto-scroll effect that triggers on every scroll state change
    // This ensures we stay at the bottom during streaming even if maxValue changes
    LaunchedEffect(scrollState.maxValue) {
        if (shouldAutoScroll) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    // Auto-scroll to active search result when it changes
    LaunchedEffect(currentSearchResultIndex, searchQuery) {
        if (searchQuery.isNotBlank() && messages.isNotEmpty()) {
            // Estimate the scroll position for the active result
            // Each message bubble is approximately 150dp (this is an estimate)
            val estimatedItemHeight = 150f
            val targetPosition = (currentSearchResultIndex * estimatedItemHeight * scrollState.maxValue / (messages.size * estimatedItemHeight)).toInt()

            // Scroll to the estimated position
            scrollState.animateScrollTo(targetPosition)
        }
    }

    // Detect when user scrolls to top to load previous messages
    LaunchedEffect(scrollState.value) {
        // Check if scrolled to top (within 100px threshold)
        if (scrollState.value < 100 && hasMoreMessages && !isLoadingPrevious) {
            onLoadPrevious()
        }

        // Update shouldAutoScroll based on scroll position
        // If user is near the bottom, enable auto-scroll
        val isNearBottom = scrollState.value >= scrollState.maxValue - 200
        shouldAutoScroll = isNearBottom
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 12.dp) // Add padding for scrollbar
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Show loading indicator when loading previous messages
            if (isLoadingPrevious) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource("message.loading.previous"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            // Group messages into active and outdated branches
            val messageGroups = groupMessagesWithOutdatedBranches(messages)

            var messageIndex = 0
            messageGroups.forEach { group ->
                when (group) {
                    is MessageGroup.ActiveMessage -> {
                        val isActiveResult = searchQuery.isNotBlank() && messageIndex == currentSearchResultIndex
                        messageBubble(
                            message = group.message,
                            searchQuery = searchQuery,
                            isActiveSearchResult = isActiveResult,
                            onMessageClick = onMessageClick,
                            onEditMessage = onEditMessage,
                            onDownloadAttachment = onDownloadAttachment,
                            userAvatarPath = userAvatarPath,
                            aiAvatarPath = aiAvatarPath,
                        )
                        messageIndex++
                    }
                    is MessageGroup.OutdatedBranch -> {
                        outdatedBranchComponent(
                            messages = group.messages,
                        )
                        messageIndex += group.messages.size
                    }
                }
            }

            // Show "Thinking..." indicator when AI is processing but hasn't returned first token
            if (isThinking) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Text(
                        text = "$spinnerFrame ${stringResource("message.thinking", thinkingElapsedSeconds)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd),
            adapter = rememberScrollbarAdapter(scrollState),
            style = ScrollbarStyle(
                minimalHeight = 16.dp,
                thickness = 8.dp,
                shape = MaterialTheme.shapes.small,
                hoverDurationMillis = 300,
                unhoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                hoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            ),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun messageBubble(
    message: ChatMessageDTO,
    searchQuery: String = "",
    isActiveSearchResult: Boolean = false,
    onMessageClick: ((String, LocalDateTime) -> Unit)? = null,
    onEditMessage: ((ChatMessageDTO) -> Unit)? = null,
    onDownloadAttachment: ((FileAttachmentDTO) -> Unit)? = null,
    userAvatarPath: String? = null,
    aiAvatarPath: String? = null,
) {
    val clipboardManager = LocalClipboardManager.current
    var isHovered by remember { mutableStateOf(false) }
    val isClickable = onMessageClick != null && message.id != null && message.timestamp != null

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxBubbleWidth = when {
            maxWidth < 600.dp -> (maxWidth * 0.9f).coerceAtLeast(200.dp)
            maxWidth < 1200.dp -> (maxWidth * 0.75f).coerceAtMost(800.dp)
            else -> (maxWidth * 0.65f).coerceAtMost(1000.dp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            if (!message.isUser) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (aiAvatarPath != null) {
                        io.askimo.desktop.view.components.asyncImage(
                            imagePath = aiAvatarPath,
                            contentDescription = "AI",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "AI",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                    .onPointerEvent(PointerEventType.Exit) { isHovered = false },
            ) {
                Card(
                    modifier = Modifier
                        .widthIn(max = maxBubbleWidth)
                        .then(
                            if (isClickable) {
                                Modifier
                                    .clickable {
                                        onMessageClick?.invoke(message.id!!, message.timestamp!!)
                                    }
                                    .pointerHoverIcon(PointerIcon.Hand)
                            } else {
                                Modifier
                            },
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Column {
                        // Show file attachments if any
                        if (message.attachments.isNotEmpty()) {
                            Column(
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                message.attachments.forEach { attachment ->
                                    fileAttachmentChip(
                                        attachment = attachment,
                                        onDownload = onDownloadAttachment,
                                    )
                                }
                            }
                        }

                        // Show message content
                        if (message.isUser) {
                            // User messages: plain text with selection enabled and optional highlighting
                            SelectionContainer {
                                if (searchQuery.isNotBlank()) {
                                    Text(
                                        text = highlightSearchText(
                                            text = message.content,
                                            query = searchQuery,
                                            highlightColor = Color(0xFFFFEB3B).copy(alpha = 0.5f), // Yellow for normal matches
                                            isActiveResult = isActiveSearchResult,
                                            activeHighlightColor = Color(0xFFFF9800).copy(alpha = 0.8f), // Bright orange for active match
                                        ),
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                } else {
                                    Text(
                                        text = message.content,
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        } else {
                            SelectionContainer {
                                if (searchQuery.isNotBlank()) {
                                    Text(
                                        text = highlightSearchText(
                                            text = message.content,
                                            query = searchQuery,
                                            highlightColor = Color(0xFFFFEB3B).copy(alpha = 0.5f), // Yellow for normal matches
                                            isActiveResult = isActiveSearchResult,
                                            activeHighlightColor = Color(0xFFFF9800).copy(alpha = 0.8f), // Bright orange for active match
                                        ),
                                        modifier = Modifier.padding(start = 12.dp, end = 48.dp, top = 12.dp, bottom = 12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                } else {
                                    markdownText(
                                        markdown = message.content,
                                        modifier = Modifier.padding(
                                            start = 12.dp,
                                            end = 48.dp,
                                            top = 12.dp,
                                            bottom = 12.dp,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                if (message.isUser && isHovered) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Copy button
                            themedTooltip(
                                text = stringResource("message.copy"),
                            ) {
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(message.content))
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = stringResource("message.copy.description"),
                                        modifier = Modifier.size(16.dp).pointerHoverIcon(PointerIcon.Hand),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }

                            themedTooltip(
                                text = stringResource("message.edit"),
                            ) {
                                IconButton(
                                    onClick = {
                                        onEditMessage?.invoke(message)
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource("message.edit.description"),
                                        modifier = Modifier.size(16.dp).pointerHoverIcon(PointerIcon.Hand),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }

                // Copy and Edit button bar for AI messages (shown on hover)
                if (!message.isUser && isHovered) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Copy button
                            themedTooltip(
                                text = stringResource("message.copy"),
                            ) {
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(message.content))
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = stringResource("message.copy.description"),
                                        modifier = Modifier.size(16.dp).pointerHoverIcon(PointerIcon.Hand),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }

                            // Edit button for AI messages
                            themedTooltip(
                                text = stringResource("message.ai.edit"),
                            ) {
                                IconButton(
                                    onClick = {
                                        onEditMessage?.invoke(message)
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource("message.ai.edit.description"),
                                        modifier = Modifier.size(16.dp).pointerHoverIcon(PointerIcon.Hand),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Show user icon on the right for user messages
            if (message.isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (userAvatarPath != null) {
                        io.askimo.desktop.view.components.asyncImage(
                            imagePath = userAvatarPath,
                            contentDescription = "User",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "User",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // Show edited indicator if message has been edited
        if (message.isEdited && !message.isUser) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
            ) {
                Text(
                    text = stringResource("message.edited.indicator"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun fileAttachmentChip(
    attachment: FileAttachmentDTO,
    onDownload: ((FileAttachmentDTO) -> Unit)? = null,
) {
    themedTooltip(
        text = if (onDownload != null) stringResource("attachment.download") else "",
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onDownload != null) {
                        Modifier
                            .clickable { onDownload(attachment) }
                            .pointerHoverIcon(PointerIcon.Hand)
                    } else {
                        Modifier
                    },
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
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
                if (onDownload != null) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = stringResource("attachment.download.description"),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun aiMessageEditDialog(
    message: ChatMessageDTO,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var editedContent by remember { mutableStateOf(message.content) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(700.dp)
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Title
                Text(
                    text = stringResource("message.ai.edit"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Scrollable content field with consistent styling
                OutlinedTextField(
                    value = editedContent,
                    onValueChange = { editedContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = ComponentColors.outlinedTextFieldColors(),
                    label = { Text(stringResource("message.ai.edit.content.label")) },
                )

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ComponentColors.primaryTextButtonColors(),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text(stringResource("action.cancel"))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            onSave(editedContent)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text(stringResource("action.save"))
                    }
                }
            }
        }
    }
}

/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.askimo.core.chat.domain.ChatDirective
import io.askimo.core.chat.dto.ChatMessageDTO
import io.askimo.core.chat.dto.FileAttachmentDTO
import io.askimo.core.chat.service.ChatDirectiveService
import io.askimo.core.db.DatabaseManager
import io.askimo.core.logging.logger
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.keymap.KeyMapManager
import io.askimo.desktop.keymap.KeyMapManager.AppShortcut
import io.askimo.desktop.theme.ComponentColors
import io.askimo.desktop.view.components.chatInputField
import io.askimo.desktop.view.components.manageDirectivesDialog
import io.askimo.desktop.view.components.newDirectiveDialog
import io.askimo.desktop.view.components.sessionActionsMenu
import io.askimo.desktop.view.components.sessionMemoryDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.LocalDateTime

private object ChatViewObject
private val log = logger<ChatViewObject>()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun chatView(
    messages: List<ChatMessageDTO>,
    onSendMessage: (String, List<FileAttachmentDTO>, ChatMessageDTO?) -> Unit,
    onStopResponse: () -> Unit = {},
    isLoading: Boolean = false,
    isThinking: Boolean = false,
    thinkingElapsedSeconds: Int = 0,
    spinnerFrame: Char = '⠋',
    errorMessage: String? = null,
    provider: String? = null,
    model: String? = null,
    hasMoreMessages: Boolean = false,
    isLoadingPrevious: Boolean = false,
    onLoadPrevious: () -> Unit = {},
    isSearchMode: Boolean = false,
    searchQuery: String = "",
    searchResults: List<ChatMessageDTO> = emptyList(),
    currentSearchResultIndex: Int = 0,
    isSearching: Boolean = false,
    onSearch: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onNextSearchResult: () -> Unit = {},
    onPreviousSearchResult: () -> Unit = {},
    onJumpToMessage: (String, LocalDateTime) -> Unit = { _, _ -> },
    selectedDirective: String? = null,
    onDirectiveSelected: (String?) -> Unit = {},
    onUpdateAIMessage: (String, String) -> Unit = { _, _ -> },
    initialInputText: TextFieldValue = TextFieldValue(""),
    initialAttachments: List<FileAttachmentDTO> = emptyList(),
    initialEditingMessage: ChatMessageDTO? = null,
    onStateChange: (TextFieldValue, List<FileAttachmentDTO>, ChatMessageDTO?) -> Unit = { _, _, _ -> },
    sessionId: String? = null,
    sessionTitle: String? = null,
    onRenameSession: (String) -> Unit = {},
    onExportSession: (String) -> Unit = {},
    onDeleteSession: (String) -> Unit = {},
    onRetryMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Internal state management for ChatView
    var inputText by remember(initialInputText) { mutableStateOf(initialInputText) }
    var attachments by remember(initialAttachments) { mutableStateOf(initialAttachments) }
    var editingMessage by remember(initialEditingMessage) { mutableStateOf(initialEditingMessage) }
    var editingAIMessage by remember { mutableStateOf<ChatMessageDTO?>(null) }

    // Notify parent of state changes
    LaunchedEffect(inputText, attachments, editingMessage) {
        onStateChange(inputText, attachments, editingMessage)
    }

    val directiveService = remember {
        GlobalContext.get().get<ChatDirectiveService>()
    }

    // Load all directives
    var availableDirectives by remember { mutableStateOf<List<ChatDirective>>(emptyList()) }
    var showNewDirectiveDialog by remember { mutableStateOf(false) }

    // Session memory dialog state
    var showSessionMemoryDialog by remember { mutableStateOf(false) }
    var sessionMemory by remember { mutableStateOf<io.askimo.core.chat.domain.SessionMemory?>(null) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        availableDirectives = directiveService.listAllDirectives()
    }

    // Focus requester for search field
    val searchFocusRequester = remember { FocusRequester() }

    // Focus requester for input field
    val inputFocusRequester = remember { FocusRequester() }

    // Focus search field when search mode is activated
    LaunchedEffect(isSearchMode) {
        if (isSearchMode) {
            searchFocusRequester.requestFocus()
        }
    }

    // Focus input field and position cursor at start when entering edit mode
    LaunchedEffect(editingMessage) {
        if (editingMessage != null) {
            inputFocusRequester.requestFocus()
        }
    }

    // Load avatar paths
    val userAvatarPath = remember { io.askimo.desktop.preferences.ThemePreferences.getUserAvatarPath() }
    val aiAvatarPath = remember { io.askimo.desktop.preferences.ThemePreferences.getAIAvatarPath() }

    // Show new directive dialog
    if (showNewDirectiveDialog) {
        newDirectiveDialog(
            onDismiss = { showNewDirectiveDialog = false },
            onConfirm = { name, content, applyToCurrent ->
                // Create the new directive
                val newDirective = directiveService.createDirective(name, content)

                // Reload directives
                availableDirectives = directiveService.listAllDirectives()

                // Apply to current session if requested
                if (applyToCurrent) {
                    onDirectiveSelected(newDirective.id)
                }

                showNewDirectiveDialog = false
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                val shortcut = KeyMapManager.handleKeyEvent(keyEvent)

                when (shortcut) {
                    AppShortcut.CLOSE_SEARCH -> {
                        if (isSearchMode) {
                            onClearSearch()
                            true
                        } else {
                            false
                        }
                    }
                    AppShortcut.NEXT_SEARCH_RESULT -> {
                        if (isSearchMode && searchResults.isNotEmpty()) {
                            onNextSearchResult()
                            true
                        } else {
                            false
                        }
                    }
                    AppShortcut.PREVIOUS_SEARCH_RESULT -> {
                        if (isSearchMode && searchResults.isNotEmpty()) {
                            onPreviousSearchResult()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            },
    ) {
        // Session header with title and directive selector
        if (provider != null && model != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = ComponentColors.sidebarSurfaceColor(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left side: Session title
                    Text(
                        text = sessionTitle ?: "New Chat",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    // Right side: Directive selector and session actions
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource("chat.directive"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        var showManageDirectivesDialog by remember { mutableStateOf(false) }
                        var directiveDropdownExpanded by remember { mutableStateOf(false) }

                        // Get the selected directive details
                        val selectedDirectiveObj = remember(selectedDirective, availableDirectives) {
                            selectedDirective?.let { id ->
                                availableDirectives.find { it.id == id }
                            }
                        }

                        Box {
                            TooltipArea(
                                tooltip = {
                                    if (selectedDirectiveObj != null) {
                                        Surface(
                                            modifier = Modifier.width(400.dp),
                                            shadowElevation = 4.dp,
                                            shape = MaterialTheme.shapes.small,
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                            ) {
                                                Text(
                                                    text = selectedDirectiveObj.name,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                                Text(
                                                    text = selectedDirectiveObj.content,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(top = 8.dp),
                                                )
                                            }
                                        }
                                    }
                                },
                            ) {
                                TextButton(
                                    onClick = { directiveDropdownExpanded = true },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                    colors = ComponentColors.primaryTextButtonColors(),
                                ) {
                                    Text(
                                        text = selectedDirectiveObj?.name?.take(30)?.let {
                                            if (selectedDirectiveObj.name.length > 30) "$it..." else it
                                        } ?: stringResource("chat.directive.none"),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select directive",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            ComponentColors.themedDropdownMenu(
                                expanded = directiveDropdownExpanded,
                                onDismissRequest = { directiveDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.3f),
                            ) {
                                // "None" option to clear directive
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource("chat.directive.none"),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    },
                                    onClick = {
                                        onDirectiveSelected(null)
                                        directiveDropdownExpanded = false
                                    },
                                    leadingIcon = if (selectedDirective == null) {
                                        {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                )

                                // Show available directives
                                if (availableDirectives.isNotEmpty()) {
                                    HorizontalDivider()

                                    availableDirectives.forEach { directive ->
                                        TooltipArea(
                                            tooltip = {
                                                Surface(
                                                    modifier = Modifier.width(400.dp),
                                                    shadowElevation = 4.dp,
                                                    shape = MaterialTheme.shapes.small,
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(12.dp),
                                                    ) {
                                                        Text(
                                                            text = directive.name,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.primary,
                                                        )
                                                        Text(
                                                            text = directive.content,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            modifier = Modifier.padding(top = 8.dp),
                                                        )
                                                    }
                                                }
                                            },
                                        ) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = directive.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                    )
                                                },
                                                onClick = {
                                                    onDirectiveSelected(directive.id)
                                                    directiveDropdownExpanded = false
                                                },
                                                leadingIcon = if (selectedDirective == directive.id) {
                                                    {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = "Selected",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                        )
                                                    }
                                                } else {
                                                    null
                                                },
                                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                            )
                                        }
                                    }
                                }

                                // Action items section
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )

                                // New Directive action
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = "New directive",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Text(
                                                text = stringResource("chat.directive.new"),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    },
                                    onClick = {
                                        showNewDirectiveDialog = true
                                        directiveDropdownExpanded = false
                                    },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                )

                                // Manage Directives action
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Manage directives",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Text(
                                                text = stringResource("chat.directive.manage"),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    },
                                    onClick = {
                                        showManageDirectivesDialog = true
                                        directiveDropdownExpanded = false
                                    },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                )
                            }

                            // Show manage directives dialog
                            if (showManageDirectivesDialog) {
                                manageDirectivesDialog(
                                    directives = availableDirectives,
                                    onDismiss = { showManageDirectivesDialog = false },
                                    onUpdate = { id, newName, newContent ->
                                        directiveService.updateDirective(id, newName, newContent)
                                        availableDirectives = directiveService.listAllDirectives()
                                    },
                                    onDelete = { id ->
                                        directiveService.deleteDirective(id)
                                        if (selectedDirective == id) {
                                            onDirectiveSelected(null)
                                        }
                                        availableDirectives = directiveService.listAllDirectives()
                                    },
                                )
                            }
                        }

                        if (sessionId != null) {
                            sessionActionsMenu(
                                sessionId = sessionId,
                                onRenameSession = onRenameSession,
                                onExportSession = onExportSession,
                                onDeleteSession = onDeleteSession,
                                onShowSessionSummary = { sid ->
                                    // Query session memory from repository
                                    coroutineScope.launch {
                                        val memory = withContext(Dispatchers.IO) {
                                            val repository = DatabaseManager.getInstance().getSessionMemoryRepository()
                                            repository.getBySessionId(sid)
                                        }
                                        sessionMemory = memory
                                        showSessionMemoryDialog = true
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // Search bar - fixed at top, always visible when search mode is active
        if (isSearchMode) {
            HorizontalDivider()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = ComponentColors.bannerCardColors(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Search icon
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )

                    // Search input field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearch,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester),
                        placeholder = { Text(stringResource("chat.search.placeholder")) },
                        singleLine = true,
                        colors = ComponentColors.outlinedTextFieldColors(),
                    )

                    // Result count
                    if (!isSearching && searchQuery.isNotEmpty()) {
                        Text(
                            text = if (searchResults.isEmpty()) {
                                stringResource("chat.search.no.results")
                            } else {
                                "${currentSearchResultIndex + 1}/${searchResults.size}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }

                    // Navigation buttons (Previous)
                    IconButton(
                        onClick = onPreviousSearchResult,
                        enabled = searchResults.isNotEmpty(),
                        modifier = Modifier
                            .size(36.dp)
                            .pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Previous result (${AppShortcut.PREVIOUS_SEARCH_RESULT.getDisplayString()})",
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // Navigation buttons (Next)
                    IconButton(
                        onClick = onNextSearchResult,
                        enabled = searchResults.isNotEmpty(),
                        modifier = Modifier
                            .size(36.dp)
                            .pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Next result (${AppShortcut.NEXT_SEARCH_RESULT.getDisplayString()})",
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // Close button
                    IconButton(
                        onClick = onClearSearch,
                        modifier = Modifier
                            .size(36.dp)
                            .pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close search",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // Download attachment handler
        val saveDialogTitle = stringResource("attachment.save.file")
        val downloadAttachment: (FileAttachmentDTO) -> Unit = { attachment ->
            val fileChooser = FileDialog(null as Frame?, saveDialogTitle, FileDialog.SAVE)
            fileChooser.file = attachment.fileName
            fileChooser.isVisible = true
            val selectedFile = fileChooser.file
            val selectedDir = fileChooser.directory
            if (selectedFile != null && selectedDir != null) {
                val targetFile = File(selectedDir, selectedFile)
                // Copy the attachment file to the selected location
                attachment.filePath?.let { filePath ->
                    val sourceFile = File(filePath)
                    if (sourceFile.exists()) {
                        try {
                            sourceFile.copyTo(targetFile, overwrite = true)
                            log.info("Downloaded attachment: ${attachment.fileName} to ${targetFile.absolutePath}")
                        } catch (e: Exception) {
                            log.error("Error copying attachment file: ${e.message}", e)
                        }
                    } else {
                        log.error("Source file not found: $filePath")
                    }
                } ?: log.error("Attachment file path is null: ${attachment.fileName}")
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
                        stringResource("chat.search.not.found", searchQuery),
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
                        spinnerFrame = spinnerFrame.toString(),
                        hasMoreMessages = false,
                        isLoadingPrevious = false,
                        onLoadPrevious = {},
                        searchQuery = searchQuery,
                        currentSearchResultIndex = currentSearchResultIndex,
                        onMessageClick = onJumpToMessage,
                        onEditMessage = { message ->
                            if (message.isUser) {
                                // User message - set editing mode
                                editingMessage = message
                                inputText = TextFieldValue(
                                    text = message.content,
                                    selection = TextRange(0),
                                )
                                attachments = message.attachments
                            } else {
                                // AI message - show edit dialog
                                editingAIMessage = message
                            }
                        },
                        onDownloadAttachment = downloadAttachment,
                        userAvatarPath = userAvatarPath,
                        aiAvatarPath = aiAvatarPath,
                        onRetryMessage = onRetryMessage,
                    )
                }
                messages.isEmpty() -> {
                    Text(
                        stringResource("chat.welcome"),
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
                        spinnerFrame = spinnerFrame.toString(),
                        hasMoreMessages = hasMoreMessages,
                        isLoadingPrevious = isLoadingPrevious,
                        onLoadPrevious = onLoadPrevious,
                        onEditMessage = { message ->
                            if (message.isUser) {
                                // User message - set editing mode
                                editingMessage = message
                                inputText = TextFieldValue(
                                    text = message.content,
                                    selection = TextRange(0),
                                )
                                attachments = message.attachments
                            } else {
                                // AI message - show edit dialog
                                editingAIMessage = message
                            }
                        },
                        onDownloadAttachment = downloadAttachment,
                        userAvatarPath = userAvatarPath,
                        aiAvatarPath = aiAvatarPath,
                        onRetryMessage = onRetryMessage,
                    )
                }
            }
        }

        // Input area
        chatInputField(
            inputText = inputText,
            onInputTextChange = { inputText = it },
            attachments = attachments,
            onAttachmentsChange = { attachments = it },
            onSendMessage = {
                if (inputText.text.isNotBlank() && !isLoading && !isThinking) {
                    onSendMessage(inputText.text, attachments, editingMessage)
                    inputText = TextFieldValue("")
                    attachments = emptyList()
                    editingMessage = null
                }
            },
            isLoading = isLoading,
            isThinking = isThinking,
            onStopResponse = onStopResponse,
            errorMessage = errorMessage,
            editingMessage = editingMessage,
            onCancelEdit = {
                editingMessage = null
                inputText = TextFieldValue("")
                attachments = emptyList()
            },
            sessionId = sessionId,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }

    editingAIMessage?.let { message ->
        aiMessageEditDialog(
            message = message,
            onDismiss = { editingAIMessage = null },
            onSave = { newContent ->
                message.id?.let { messageId ->
                    onUpdateAIMessage(messageId, newContent)
                }
            },
        )
    }

    // Session memory dialog
    if (showSessionMemoryDialog) {
        sessionMemoryDialog(
            sessionMemory = sessionMemory,
            onDismiss = {
                showSessionMemoryDialog = false
                sessionMemory = null
            },
        )
    }
}

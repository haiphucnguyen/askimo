/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.askimo.core.chat.repository.PaginationDirection
import io.askimo.core.chat.service.ChatSessionService
import io.askimo.core.context.AppContext
import io.askimo.core.context.MessageRole
import io.askimo.desktop.model.ChatMessage
import io.askimo.desktop.model.FileAttachment
import io.askimo.desktop.util.ErrorHandler
import io.askimo.desktop.util.constructMessageWithAttachments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * ViewModel for managing chat state and interactions.
 *
 * This class handles the business logic for the chat view, including:
 * - Managing the list of messages
 * - Sending messages to the AI
 * - Handling loading and error states
 * - Resuming previous chat sessions
 */
class ChatViewModel(
    private val sessionManager: SessionManager,
    private val scope: CoroutineScope,
    private val chatSessionService: ChatSessionService,
    private val appContext: AppContext,
) {
    var messages by mutableStateOf(listOf<ChatMessage>())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var currentResponse by mutableStateOf("")
        private set

    var isThinking by mutableStateOf(false)
        private set

    var thinkingElapsedSeconds by mutableStateOf(0)
        private set

    var thinkingFrameIndex by mutableStateOf(0)
        private set

    var isLoadingPrevious by mutableStateOf(false)
        private set

    var hasMoreMessages by mutableStateOf(false)
        private set

    var isSearching by mutableStateOf(false)
        private set

    var searchQuery by mutableStateOf("")
        private set

    var searchResults by mutableStateOf<List<ChatMessage>>(emptyList())
        private set

    var currentSearchResultIndex by mutableStateOf(0)
        private set

    var isSearchMode by mutableStateOf(false)
        private set

    var selectedDirective by mutableStateOf<String?>(null)
        private set

    private val sessionService = ChatSessionService()

    private var onMessageComplete: (() -> Unit)? = null
    private var currentJob: Job? = null
    private var thinkingJob: Job? = null
    private var animationJob: Job? = null

    // Track active subscription jobs per threadId (not chatId) to ensure proper cleanup
    // Key = threadId, Value = subscription Job
    private val activeSubscriptions = mutableMapOf<String, Job>()

    // Pagination state
    private var currentCursor: LocalDateTime? = null
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId

    // Spinner frames matching CLI implementation
    private val spinnerFrames = charArrayOf('⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏')

    companion object {
        private const val MESSAGE_PAGE_SIZE = 100
        private const val MESSAGE_BUFFER_THRESHOLD = MESSAGE_PAGE_SIZE * 2
    }

    /**
     * Set a callback to be invoked when a message exchange is complete.
     * This is useful for refreshing the sessions list after the first message.
     */
    fun setOnMessageCompleteCallback(callback: (() -> Unit)?) {
        onMessageComplete = callback
    }

    /**
     * Get the current spinner frame character for the thinking indicator.
     */
    fun getSpinnerFrame(): Char = spinnerFrames[thinkingFrameIndex % spinnerFrames.size]

    /**
     * Subscribe to a SPECIFIC thread by sessionId.
     * This ensures we only get chunks from THIS specific question, not old ones.
     */
    private fun subscribeToThread(sessionId: String) {
        // Cancel any existing subscription for this sessionId to prevent duplicates
        activeSubscriptions[sessionId]?.cancel()
        activeSubscriptions.remove(sessionId)

        val activeThread = sessionManager.getActiveThread(sessionId)

        if (activeThread != null) {
            // Check if chunks have been received yet
            val hasChunks = activeThread.chunks.value.isNotEmpty()

            if (!hasChunks) {
                // Still in "thinking" phase - show thinking indicator
                isThinking = true
                startThinkingTimer()
            } else {
                // Chunks already available - display them immediately
                val streamingContent = activeThread.chunks.value.joinToString("")
                val lastMessage = messages.lastOrNull()
                if (lastMessage != null && !lastMessage.isUser) {
                    messages = messages.dropLast(1) + ChatMessage(
                        content = streamingContent,
                        isUser = false,
                    )
                } else {
                    messages = messages + ChatMessage(
                        content = streamingContent,
                        isUser = false,
                    )
                }
            }

            // Create a single job for this SPECIFIC threadId's subscription
            val subscriptionJob = scope.launch {
                var firstTokenReceived = hasChunks

                try {
                    activeThread.chunks.collect { chunks ->
                        // STRICT CHECK: Only update UI if CURRENTLY viewing THIS EXACT session
                        if (_currentSessionId.value == sessionId && chunks.isNotEmpty()) {
                            // First token received - stop thinking indicator
                            if (!firstTokenReceived) {
                                firstTokenReceived = true
                                isThinking = false
                                stopThinkingTimer()
                            }

                            val streamingContent = chunks.joinToString("")
                            currentResponse = streamingContent

                            // Update the AI message
                            val lastMessage = messages.lastOrNull()
                            if (lastMessage != null && !lastMessage.isUser) {
                                // Update existing AI message
                                messages = messages.dropLast(1) + ChatMessage(
                                    content = streamingContent,
                                    isUser = false,
                                )
                            } else {
                                // Add new AI message
                                messages = messages + ChatMessage(
                                    content = streamingContent,
                                    isUser = false,
                                )
                            }

                            isLoading = false

                            // Notify that message exchange is complete (for refreshing sidebar)
                            onMessageComplete?.invoke()
                        }
                    }
                } finally {
                    // Clean up this subscription when done
                    activeSubscriptions.remove(sessionId)
                }
            }

            // Track this subscription by sessionId
            activeSubscriptions[sessionId] = subscriptionJob

            // Monitor completion in a separate job
            scope.launch {
                try {
                    activeThread.isComplete.collect { isComplete ->
                        // Only update if still on this session
                        if (_currentSessionId.value == sessionId && isComplete) {
                            isLoading = false
                            isThinking = false
                            stopThinkingTimer()

                            // Cancel and clean up subscription when thread completes
                            activeSubscriptions[sessionId]?.cancel()
                            activeSubscriptions.remove(sessionId)
                        }
                    }
                } catch (e: CancellationException) {
                    // Expected when subscription is cancelled
                }
            }
        }
    }

    /**
     * Send a message or edit an existing message.
     * This method handles both normal message sending and edit mode.
     *
     * @param message The user's message
     * @param attachments Optional list of file attachments
     * @param editingMessage The message being edited (null for normal send)
     * @return The session ID after sending (or null if no session)
     */
    fun sendOrEditMessage(
        message: String,
        attachments: List<FileAttachment> = emptyList(),
        editingMessage: ChatMessage? = null,
    ): String? {
        if (message.isBlank() || isLoading) return _currentSessionId.value

        // Get current session ID before any operations
        val currentSessionId = _currentSessionId.value

        if (editingMessage != null && editingMessage.id != null) {
            // Edit mode:
            // Store the original message ID BEFORE any operations
            val originalMessageId = editingMessage.id

            // 1. Mark ORIGINAL message and ALL subsequent messages as outdated
            //    This must happen BEFORE creating the new message
            editMessage(originalMessageId, message, attachments)

            // 2. Send the NEW message with updated content
            //    This creates a NEW active message (not marked as outdated)
            //    The new message will have editParentId linking to originalMessageId
            sendMessage(message, attachments)
        } else {
            // Normal mode: just send the message
            sendMessage(message, attachments)
        }

        return currentSessionId
    }

    /**
     * Send a message to the AI.
     *
     * @param message The user's message
     * @param attachments Optional list of file attachments
     */
    fun sendMessage(message: String, attachments: List<FileAttachment> = emptyList()) {
        if (message.isBlank() || isLoading) return

        // Get or create session ID
        val sessionId = _currentSessionId.value ?: run {
            val existingSession = appContext.currentChatSession
            if (existingSession != null) {
                existingSession.id
            } else {
                // Create new session in database BEFORE sending message
                // This ensures the session exists when AI response tries to save messages
                try {
                    val newSession = appContext.startNewChatSession(directiveId = selectedDirective)
                    println("Created new session: ${newSession.id}")
                    newSession.id
                } catch (e: Exception) {
                    println("Failed to create session: ${e.message}")
                    e.printStackTrace()
                    // Fallback to generated ID if session creation fails
                    UUID.randomUUID().toString()
                }
            }
        }

        // Update current session ID immediately
        if (_currentSessionId.value == null) {
            _currentSessionId.value = sessionId
        }

        // Cancel any previous job to prevent old subscriptions from interfering
        currentJob?.cancel()
        currentJob = null

        // Clear any previous error
        errorMessage = null
        isLoading = true
        currentResponse = ""
        isThinking = true
        thinkingElapsedSeconds = 0

        // Add user message to the list (for display purposes)
        messages = messages + ChatMessage(
            content = message,
            isUser = true,
            attachments = attachments,
        )

        // Trim messages if they exceed the buffer threshold
        if (messages.size > MESSAGE_BUFFER_THRESHOLD) {
            // Keep only the most recent MESSAGE_PAGE_SIZE messages
            messages = messages.takeLast(MESSAGE_PAGE_SIZE)
            // Reset pagination state since we're trimming
            currentCursor = null
            hasMoreMessages = false
        }

        // Start thinking timer
        startThinkingTimer()

        // Construct the full message with attachments for AI
        val fullMessage = constructMessageWithAttachments(message, attachments)

        currentJob = scope.launch {
            try {
                val threadId = sessionManager.sendMessage(sessionId, fullMessage)

                if (threadId == null) {
                    errorMessage = "Please wait for the current response to complete before asking another question."
                    isLoading = false
                    isThinking = false
                    stopThinkingTimer()
                    return@launch
                }

                if (_currentSessionId.value == null) {
                    val currentSession = appContext.currentChatSession
                    _currentSessionId.value = currentSession?.id ?: sessionId
                }

                subscribeToThread(sessionId)
            } catch (e: Exception) {
                if (_currentSessionId.value == sessionId) {
                    errorMessage = ErrorHandler.getUserFriendlyError(e, "sending message")
                    isLoading = false
                    isThinking = false
                    stopThinkingTimer()
                }
            }
        }
    }

    /**
     * Cancel the current AI response.
     * Stops the stream and discards all buffered chunks (does not save to database).
     */
    fun cancelResponse() {
        currentJob?.cancel()
        currentJob = null
        isLoading = false
        isThinking = false
        stopThinkingTimer()

        // Stop the streaming service and cancel ALL subscriptions for current chat
        val chatId = _currentSessionId.value
        if (chatId != null) {
            // Cancel ALL subscriptions (in case there are multiple)
            activeSubscriptions.values.forEach { it.cancel() }
            activeSubscriptions.clear()

            // Stop the streaming thread
            sessionManager.stopStream(chatId)
        }
    }

    private fun startThinkingTimer() {
        thinkingElapsedSeconds = 0
        thinkingFrameIndex = 0

        // Timer for elapsed seconds
        thinkingJob = scope.launch {
            while (isThinking) {
                kotlinx.coroutines.delay(1000)
                thinkingElapsedSeconds++
            }
        }

        // Animation for spinner frames (200ms interval like CLI)
        animationJob = scope.launch {
            while (isThinking) {
                kotlinx.coroutines.delay(200)
                thinkingFrameIndex++
            }
        }
    }

    private fun stopThinkingTimer() {
        thinkingJob?.cancel()
        thinkingJob = null
        animationJob?.cancel()
        animationJob = null
    }

    /**
     * Resume a chat session by ID and load the most recent messages.
     * If the session is actively streaming, continue displaying the stream.
     *
     * @param sessionId The ID of the session to resume
     * @return true if successful, false otherwise
     */
    fun resumeSession(sessionId: String): Boolean {
        scope.launch {
            try {
                // IMPORTANT: Cancel ALL old subscriptions before switching
                // Clear all subscriptions to prevent any old threads from updating UI
                activeSubscriptions.values.forEach { it.cancel() }
                activeSubscriptions.clear()

                isLoading = true
                errorMessage = null

                // Clear search state when switching sessions
                clearSearch()

                val result = withContext(Dispatchers.IO) {
                    sessionService.resumeSessionPaginated(
                        appContext,
                        sessionId,
                        MESSAGE_PAGE_SIZE,
                    )
                }

                if (result.success) {
                    // Convert session messages to chat messages
                    messages = result.messages.map { sessionMessage ->
                        ChatMessage(
                            content = sessionMessage.content,
                            isUser = sessionMessage.role == MessageRole.USER,
                            id = sessionMessage.id,
                            timestamp = sessionMessage.createdAt,
                        )
                    }
                    // Store pagination state
                    currentCursor = result.cursor
                    hasMoreMessages = result.hasMore

                    // Update current session ID AFTER cancelling old subscriptions
                    _currentSessionId.value = sessionId

                    // Load directive from the resumed session
                    selectedDirective = appContext.currentChatSession?.directiveId

                    // Reset thinking state
                    isThinking = false
                    stopThinkingTimer()

                    // Subscribe to active thread if this session is streaming
                    val activeThread = sessionManager.getActiveThread(sessionId)
                    if (activeThread != null) {
                        // This session has an active thread - subscribe to it
                        subscribeToThread(sessionId)
                    }
                } else {
                    errorMessage = result.errorMessage
                }

                isLoading = false
            } catch (e: Exception) {
                errorMessage = ErrorHandler.getUserFriendlyError(e, "resuming session", "Failed to load session. Please try again.")
                isLoading = false
            }
        }
        return true
    }

    /**
     * Load previous messages when scrolling to the top.
     * Only loads if there are more messages available.
     */
    fun loadPreviousMessages() {
        if (isLoadingPrevious || !hasMoreMessages || currentCursor == null || _currentSessionId.value == null) {
            return
        }

        scope.launch {
            try {
                isLoadingPrevious = true

                val (previousMessages, nextCursor) = withContext(Dispatchers.IO) {
                    sessionService.loadPreviousMessages(
                        _currentSessionId.value!!,
                        currentCursor!!,
                        MESSAGE_PAGE_SIZE,
                    )
                }

                // Convert and prepend messages
                val chatMessages = previousMessages.map { sessionMessage ->
                    ChatMessage(
                        content = sessionMessage.content,
                        isUser = sessionMessage.role == MessageRole.USER,
                        id = sessionMessage.id,
                        timestamp = sessionMessage.createdAt,
                    )
                }

                messages = chatMessages + messages

                // Update pagination state
                currentCursor = nextCursor
                hasMoreMessages = nextCursor != null

                isLoadingPrevious = false
            } catch (e: Exception) {
                errorMessage = ErrorHandler.getUserFriendlyError(e, "loading previous messages", "Failed to load previous messages. Please try again.")
                isLoadingPrevious = false
            }
        }
    }

    /**
     * Search for messages in the current session.
     *
     * @param query The search query
     */
    fun searchMessages(query: String) {
        if (_currentSessionId.value == null) {
            return
        }

        searchQuery = query

        if (query.isBlank()) {
            // Clear search results but keep search mode active
            searchResults = emptyList()
            currentSearchResultIndex = 0
            // DON'T set isSearchMode = false here!
            // User must close search with the X button
            return
        }

        scope.launch {
            try {
                isSearching = true
                isSearchMode = true

                val results = withContext(Dispatchers.IO) {
                    sessionService.searchMessages(_currentSessionId.value!!, query, 100)
                }

                // Convert to chat messages
                searchResults = results.map { sessionMessage ->
                    ChatMessage(
                        content = sessionMessage.content,
                        isUser = sessionMessage.role == MessageRole.USER,
                        id = sessionMessage.id,
                        timestamp = sessionMessage.createdAt,
                    )
                }

                // Reset to first result
                currentSearchResultIndex = 0

                // Auto-jump to first result if available
                if (searchResults.isNotEmpty()) {
                    val firstResult = searchResults[0]
                    if (firstResult.id != null && firstResult.timestamp != null) {
                        jumpToMessage(firstResult.id, firstResult.timestamp)
                    }
                }

                isSearching = false
            } catch (e: Exception) {
                errorMessage = ErrorHandler.getUserFriendlyError(e, "searching messages", "Search failed. Please try again.")
                isSearching = false
            }
        }
    }

    /**
     * Enable search mode without performing a search.
     */
    fun enableSearchMode() {
        isSearchMode = true
    }

    /**
     * Clear the search and return to normal view.
     */
    fun clearSearch() {
        searchQuery = ""
        searchResults = emptyList()
        currentSearchResultIndex = 0
        isSearchMode = false
    }

    /**
     * Navigate to the next search result.
     */
    fun nextSearchResult() {
        if (searchResults.isEmpty()) return
        currentSearchResultIndex = (currentSearchResultIndex + 1) % searchResults.size
        // Jump to the message
        val result = searchResults[currentSearchResultIndex]
        if (result.id != null && result.timestamp != null) {
            jumpToMessage(result.id, result.timestamp)
        }
    }

    /**
     * Navigate to the previous search result.
     */
    fun previousSearchResult() {
        if (searchResults.isEmpty()) return
        currentSearchResultIndex = if (currentSearchResultIndex == 0) {
            searchResults.size - 1
        } else {
            currentSearchResultIndex - 1
        }
        // Jump to the message
        val result = searchResults[currentSearchResultIndex]
        if (result.id != null && result.timestamp != null) {
            jumpToMessage(result.id, result.timestamp)
        }
    }

    /**
     * Jump to a specific message in the conversation by loading context around it.
     * This exits search mode and loads messages around the target message.
     *
     * @param messageId The ID of the message to jump to
     * @param messageTimestamp The timestamp of the message
     */
    fun jumpToMessage(messageId: String, messageTimestamp: LocalDateTime) {
        if (_currentSessionId.value == null) return

        scope.launch {
            try {
                isLoading = true

                // Don't clear search mode - we might be jumping to a search result
                // clearSearch()  // REMOVED - was causing search box to disappear

                // Load messages around the target message
                // We'll load MESSAGE_PAGE_SIZE/2 messages before and after
                val halfPageSize = MESSAGE_PAGE_SIZE / 2

                val (beforeMessages, _) = withContext(Dispatchers.IO) {
                    sessionService.loadPreviousMessages(
                        _currentSessionId.value!!,
                        messageTimestamp,
                        halfPageSize,
                    )
                }

                val afterMessages = withContext(Dispatchers.IO) {
                    // Load messages after the target
                    val (after, _) = chatSessionService.getMessagesPaginated(
                        sessionId = _currentSessionId.value!!,
                        limit = halfPageSize,
                        cursor = messageTimestamp,
                        direction = PaginationDirection.FORWARD,
                    )
                    after
                }

                // Get the target message itself
                val allSessionMessages = withContext(Dispatchers.IO) {
                    chatSessionService.getMessages(_currentSessionId.value!!)
                }
                val targetMessage = allSessionMessages.find { it.id == messageId }

                // Combine messages
                val contextMessages = if (targetMessage != null) {
                    beforeMessages + listOf(targetMessage) + afterMessages
                } else {
                    beforeMessages + afterMessages
                }

                // Convert to chat messages
                messages = contextMessages.map { sessionMessage ->
                    ChatMessage(
                        content = sessionMessage.content,
                        isUser = sessionMessage.role == MessageRole.USER,
                        id = sessionMessage.id,
                        timestamp = sessionMessage.createdAt,
                    )
                }

                // Update pagination state
                currentCursor = if (beforeMessages.isNotEmpty()) {
                    beforeMessages.first().createdAt
                } else {
                    null
                }
                hasMoreMessages = currentCursor != null

                isLoading = false
            } catch (e: Exception) {
                errorMessage = ErrorHandler.getUserFriendlyError(e, "jumping to message", "Failed to navigate to message. Please try again.")
                isLoading = false
            }
        }
    }

    /**
     * Mark the original message and all subsequent messages as outdated.
     * This should be called BEFORE creating the new edited message.
     * Does NOT reload messages - caller should reload after creating new message.
     *
     * @param originalMessageId The ID of the original message being edited
     * @return true if successful, false otherwise
     */
    suspend fun markOriginalAndSubsequentAsOutdated(originalMessageId: String): Boolean {
        val sessionId = _currentSessionId.value ?: return false

        return try {
            withContext(Dispatchers.IO) {
                // 1. Mark the ORIGINAL message (being edited) as outdated
                chatSessionService.markMessageAsOutdated(originalMessageId)

                // 2. Mark ALL subsequent messages as outdated
                val subsequentCount = chatSessionService.markMessagesAsOutdatedAfter(sessionId, originalMessageId)
                println("Marked original message '$originalMessageId' and $subsequentCount subsequent messages as outdated")

                true
            }
        } catch (e: Exception) {
            errorMessage = ErrorHandler.getUserFriendlyError(
                e,
                "editing message",
                "Failed to mark messages as outdated.",
            )
            false
        }
    }

    /**
     * Edit a user message by marking the original and subsequent messages as outdated.
     * Note: This does NOT create the new message - caller must call sendMessage() separately.
     *
     * @param messageId The ID of the original message to edit
     * @param newContent The new content (not used, kept for compatibility)
     * @param attachments Optional attachments (not used, kept for compatibility)
     */
    fun editMessage(messageId: String, newContent: String, attachments: List<FileAttachment> = emptyList()) {
        scope.launch {
            try {
                markOriginalAndSubsequentAsOutdated(messageId)
            } catch (e: Exception) {
                errorMessage = ErrorHandler.getUserFriendlyError(
                    e,
                    "editing message",
                    "Failed to edit message. Please try again.",
                )
            }
        }
    }

    /**
     * Clear all messages and start a new chat.
     * Note: A new session will be created automatically when the first message is sent.
     */
    fun clearChat() {
        messages = listOf()
        errorMessage = null
        currentResponse = ""

        // Reset pagination state
        currentCursor = null
        hasMoreMessages = false
        _currentSessionId.value = null

        // Clear search state
        clearSearch()

        // Reset directive to null for new chat session
        selectedDirective = null

        // Start a new session without a directive
        appContext.currentChatSession = null

        // Clear conversation memory
        val provider = appContext.getActiveProvider()
        val modelName = appContext.params.getModel(provider)
        appContext.removeMemory(provider, modelName)
    }

    /**
     * Get the current session ID.
     */
    fun getCurrentSessionId(): String? = _currentSessionId.value

    /**
     * Set the directive for the current or next chat session.
     * @param directiveId The directive ID to set (null to clear directive)
     */
    fun setDirective(directiveId: String?) {
        selectedDirective = directiveId

        // If there's an active session, update it immediately
        if (appContext.currentChatSession != null) {
            appContext.setCurrentSessionDirective(directiveId)
        }
        // Otherwise, the directive will be applied when a new session is started
    }

    /**
     * Clean up all resources when this ViewModel is removed from cache.
     * This is called by SessionManager when the ViewModel needs to be evicted.
     */
    fun cleanup() {
        // Cancel any ongoing operations
        currentJob?.cancel()
        currentJob = null

        // Cancel all subscriptions
        activeSubscriptions.values.forEach { it.cancel() }
        activeSubscriptions.clear()

        // Stop timers
        stopThinkingTimer()

        // Clear state to free memory
        messages = emptyList()
        currentResponse = ""
        isLoading = false
        isThinking = false
        errorMessage = null
        searchResults = emptyList()

        println("Cleaned up ChatViewModel for session: ${_currentSessionId.value}")
    }
}

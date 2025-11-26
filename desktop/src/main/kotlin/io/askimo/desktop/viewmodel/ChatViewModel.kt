/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.askimo.core.session.ChatSessionRepository
import io.askimo.core.session.ChatSessionService
import io.askimo.core.session.MessageRole
import io.askimo.core.session.SessionConfigInfo
import io.askimo.core.session.getConfigInfo
import io.askimo.desktop.model.ChatMessage
import io.askimo.desktop.model.FileAttachment
import io.askimo.desktop.service.ChatService
import io.askimo.desktop.util.ErrorHandler
import io.askimo.desktop.util.constructMessageWithAttachments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
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
    private val chatService: ChatService = ChatService(),
    private val scope: CoroutineScope,
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
    private var currentCursor: java.time.LocalDateTime? = null
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
     * Subscribe to a SPECIFIC thread by threadId.
     * This ensures we only get chunks from THIS specific question, not old ones.
     */
    private fun subscribeToThread(threadId: String, chatId: String) {
        // Cancel any existing subscription for this threadId to prevent duplicates
        activeSubscriptions[threadId]?.cancel()
        activeSubscriptions.remove(threadId)

        val streamingService = chatService.getStreamingService()
        val activeThread = streamingService.getActiveThread(threadId)

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
                        // STRICT CHECK: Only update UI if CURRENTLY viewing THIS EXACT chatId
                        if (_currentSessionId.value == chatId && chunks.isNotEmpty()) {
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
                    // Clean up this subscription when done (by threadId)
                    activeSubscriptions.remove(threadId)
                }
            }

            // Track this subscription by threadId (not chatId!)
            activeSubscriptions[threadId] = subscriptionJob

            // Monitor completion in a separate job
            scope.launch {
                try {
                    activeThread.isComplete.collect { isComplete ->
                        // Only update if still on this chat
                        if (_currentSessionId.value == chatId && isComplete) {
                            isLoading = false
                            isThinking = false
                            stopThinkingTimer()

                            // Cancel and clean up subscription when thread completes (by threadId)
                            activeSubscriptions[threadId]?.cancel()
                            activeSubscriptions.remove(threadId)
                        }
                    }
                } catch (e: CancellationException) {
                    // Expected when subscription is cancelled
                }
            }
        }
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
            // New chat - will get session ID after first message
            chatService.getSession().currentChatSession?.id ?: "temp-" + System.currentTimeMillis()
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
                // Start streaming in background - creates new thread for this Q&A
                val threadId = chatService.sendMessage(fullMessage, sessionId)

                if (threadId == null) {
                    // Either max concurrent streams reached OR chat already has active question
                    errorMessage = "Please wait for the current response to complete before asking another question."
                    isLoading = false
                    isThinking = false
                    stopThinkingTimer()
                    return@launch
                }

                // Update session ID if this was a new chat
                if (_currentSessionId.value == null) {
                    val currentSession = chatService.getSession().currentChatSession
                    _currentSessionId.value = currentSession?.id ?: sessionId
                }

                // Subscribe to this SPECIFIC thread (not just chatId)
                subscribeToThread(threadId, sessionId)

            } catch (e: CancellationException) {
                // Handle cancellation gracefully - only update if still on same session
                if (_currentSessionId.value == sessionId) {
                    isLoading = false
                    isThinking = false
                    stopThinkingTimer()
                    errorMessage = ErrorHandler.getCancellationMessage()
                }
            } catch (e: Exception) {
                // Only update if still on same session
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
            chatService.getStreamingService().stopStream(chatId)
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
                        chatService.getSession(),
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
                    val session = chatService.getSession()
                    selectedDirective = session.currentChatSession?.directiveId

                    // Reset thinking state
                    isThinking = false
                    stopThinkingTimer()

                    // Subscribe to active thread if this chat is streaming
                    val streamingService = chatService.getStreamingService()
                    val activeThread = streamingService.getActiveThreadForChat(sessionId)
                    if (activeThread != null) {
                        // This chat has an active thread - subscribe to it
                        subscribeToThread(activeThread.threadId, sessionId)
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
     * Exit search mode and return to normal message view.
     */
    fun exitSearchMode() {
        clearSearch()
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
                    val repo = ChatSessionRepository()
                    val (after, _) = repo.getMessagesPaginated(
                        sessionId = _currentSessionId.value!!,
                        limit = halfPageSize,
                        cursor = messageTimestamp,
                        direction = "forward",
                    )
                    after
                }

                // Get the target message itself
                val allSessionMessages = withContext(Dispatchers.IO) {
                    val repo = ChatSessionRepository()
                    repo.getMessages(_currentSessionId.value!!)
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
        val session = chatService.getSession()
        session.currentChatSession = null

        chatService.clearMemory()
    }

    /**
     * Get the current session ID.
     */
    fun getCurrentSessionId(): String? = _currentSessionId.value

    /**
     * Clear the error message.
     */
    fun clearError() {
        errorMessage = null
    }

    /**
     * Get the current session configuration info (provider and model).
     *
     * @return SessionConfigInfo containing provider, model, and settings description
     */
    fun getSessionConfigInfo(): SessionConfigInfo = chatService.getSession().getConfigInfo()

    /**
     * Set the directive for the current or next chat session.
     * @param directiveId The directive ID to set (null to clear directive)
     */
    fun setDirective(directiveId: String?) {
        selectedDirective = directiveId

        // If there's an active session, update it immediately
        val session = chatService.getSession()
        if (session.currentChatSession != null) {
            session.setCurrentSessionDirective(directiveId)
        }
        // Otherwise, the directive will be applied when a new session is started
    }

    /**
     * Get the underlying ChatService for advanced operations like setting language directive.
     * @return The ChatService instance
     */
    fun getChatService(): ChatService = chatService
}

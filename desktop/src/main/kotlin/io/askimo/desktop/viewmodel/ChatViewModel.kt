/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.askimo.core.session.ChatSessionService
import io.askimo.core.session.MessageRole
import io.askimo.core.session.SessionConfigInfo
import io.askimo.core.session.getConfigInfo
import io.askimo.desktop.model.ChatMessage
import io.askimo.desktop.model.FileAttachment
import io.askimo.desktop.service.ChatService
import io.askimo.desktop.util.constructMessageWithAttachments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    var isSearchMode by mutableStateOf(false)
        private set

    private val sessionService = ChatSessionService()

    private var onMessageComplete: (() -> Unit)? = null
    private var currentJob: kotlinx.coroutines.Job? = null
    private var thinkingJob: kotlinx.coroutines.Job? = null
    private var animationJob: kotlinx.coroutines.Job? = null

    // Pagination state
    private var currentCursor: java.time.LocalDateTime? = null
    private var currentSessionId: String? = null

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
     * Send a message to the AI.
     *
     * @param message The user's message
     * @param attachments Optional list of file attachments
     */
    fun sendMessage(message: String, attachments: List<FileAttachment> = emptyList()) {
        if (message.isBlank() || isLoading) return

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
            var firstTokenReceived = false
            try {
                chatService.sendMessage(fullMessage)
                    .catch { exception ->
                        errorMessage = "Error: ${exception.message}"
                        isLoading = false
                        isThinking = false
                        stopThinkingTimer()
                    }
                    .collect { chatMessage ->
                        if (!chatMessage.isUser) {
                            // First token received - stop thinking indicator
                            if (!firstTokenReceived) {
                                firstTokenReceived = true
                                isThinking = false
                                stopThinkingTimer()
                            }

                            // This is the AI response
                            currentResponse = chatMessage.content

                            // Add or update the AI message
                            val lastMessage = messages.lastOrNull()
                            if (lastMessage != null && !lastMessage.isUser) {
                                // Update existing AI message
                                messages = messages.dropLast(1) + chatMessage
                            } else {
                                // Add new AI message
                                messages = messages + chatMessage
                            }

                            isLoading = false

                            // Notify that message exchange is complete (for refreshing sidebar)
                            onMessageComplete?.invoke()
                        }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Handle cancellation gracefully
                isLoading = false
                isThinking = false
                stopThinkingTimer()
                errorMessage = "Response cancelled"
            } catch (e: Exception) {
                errorMessage = "Error: ${e.message}"
                isLoading = false
                isThinking = false
                stopThinkingTimer()
            }
        }
    }

    /**
     * Cancel the current AI response.
     */
    fun cancelResponse() {
        currentJob?.cancel()
        currentJob = null
        isLoading = false
        isThinking = false
        stopThinkingTimer()
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
     *
     * @param sessionId The ID of the session to resume
     * @return true if successful, false otherwise
     */
    fun resumeSession(sessionId: String): Boolean {
        scope.launch {
            try {
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
                    currentSessionId = sessionId
                } else {
                    errorMessage = result.errorMessage
                }

                isLoading = false
            } catch (e: Exception) {
                errorMessage = "Error resuming session: ${e.message}"
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
        if (isLoadingPrevious || !hasMoreMessages || currentCursor == null || currentSessionId == null) {
            return
        }

        scope.launch {
            try {
                isLoadingPrevious = true

                val (previousMessages, nextCursor) = withContext(Dispatchers.IO) {
                    sessionService.loadPreviousMessages(
                        currentSessionId!!,
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
                errorMessage = "Error loading previous messages: ${e.message}"
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
        if (currentSessionId == null) {
            return
        }

        searchQuery = query

        if (query.isBlank()) {
            // Clear search results and exit search mode
            searchResults = emptyList()
            isSearchMode = false
            return
        }

        scope.launch {
            try {
                isSearching = true
                isSearchMode = true

                val results = withContext(Dispatchers.IO) {
                    sessionService.searchMessages(currentSessionId!!, query, 100)
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

                isSearching = false
            } catch (e: Exception) {
                errorMessage = "Error searching messages: ${e.message}"
                isSearching = false
            }
        }
    }

    /**
     * Clear the search and return to normal view.
     */
    fun clearSearch() {
        searchQuery = ""
        searchResults = emptyList()
        isSearchMode = false
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
    fun jumpToMessage(messageId: String, messageTimestamp: java.time.LocalDateTime) {
        if (currentSessionId == null) return

        scope.launch {
            try {
                isLoading = true

                // Clear search mode
                clearSearch()

                // Load messages around the target message
                // We'll load MESSAGE_PAGE_SIZE/2 messages before and after
                val halfPageSize = MESSAGE_PAGE_SIZE / 2

                val (beforeMessages, _) = withContext(Dispatchers.IO) {
                    sessionService.loadPreviousMessages(
                        currentSessionId!!,
                        messageTimestamp,
                        halfPageSize,
                    )
                }

                val afterMessages = withContext(Dispatchers.IO) {
                    // Load messages after the target
                    val repo = io.askimo.core.session.ChatSessionRepository()
                    val (after, _) = repo.getMessagesPaginated(
                        sessionId = currentSessionId!!,
                        limit = halfPageSize,
                        cursor = messageTimestamp,
                        direction = "forward",
                    )
                    after
                }

                // Get the target message itself
                val allSessionMessages = withContext(Dispatchers.IO) {
                    val repo = io.askimo.core.session.ChatSessionRepository()
                    repo.getMessages(currentSessionId!!)
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
                errorMessage = "Error jumping to message: ${e.message}"
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
        currentSessionId = null

        // Clear search state
        clearSearch()

        // Clear the current session and memory
        chatService.getSession().currentChatSession = null
        chatService.clearMemory()
    }

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
}

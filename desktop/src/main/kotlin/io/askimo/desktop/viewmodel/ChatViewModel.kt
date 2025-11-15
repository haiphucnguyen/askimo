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

    private val sessionService = ChatSessionService()

    private var onMessageComplete: (() -> Unit)? = null
    private var currentJob: kotlinx.coroutines.Job? = null
    private var thinkingJob: kotlinx.coroutines.Job? = null
    private var animationJob: kotlinx.coroutines.Job? = null

    // Spinner frames matching CLI implementation
    private val spinnerFrames = charArrayOf('⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏')

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
     * Resume a chat session by ID and load all messages.
     *
     * @param sessionId The ID of the session to resume
     * @return true if successful, false otherwise
     */
    fun resumeSession(sessionId: String): Boolean {
        scope.launch {
            try {
                isLoading = true
                errorMessage = null

                val result = withContext(Dispatchers.IO) {
                    sessionService.resumeSession(chatService.getSession(), sessionId)
                }

                if (result.success) {
                    // Convert session messages to chat messages
                    messages = result.messages.map { sessionMessage ->
                        ChatMessage(
                            content = sessionMessage.content,
                            isUser = sessionMessage.role == MessageRole.USER,
                        )
                    }
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
     * Clear all messages and start a new chat.
     * Note: A new session will be created automatically when the first message is sent.
     */
    fun clearChat() {
        messages = listOf()
        errorMessage = null
        currentResponse = ""

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

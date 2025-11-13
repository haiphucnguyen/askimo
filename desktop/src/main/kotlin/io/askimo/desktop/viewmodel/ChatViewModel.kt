/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.askimo.core.session.MessageRole
import io.askimo.core.session.SessionResumeService
import io.askimo.desktop.model.ChatMessage
import io.askimo.desktop.service.ChatService
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

    private val resumeService = SessionResumeService(chatService.getSession())

    /**
     * Send a message to the AI.
     *
     * @param message The user's message
     */
    fun sendMessage(message: String) {
        if (message.isBlank() || isLoading) return

        // Clear any previous error
        errorMessage = null
        isLoading = true
        currentResponse = ""

        // Add user message to the list
        messages = messages + ChatMessage(content = message, isUser = true)

        scope.launch {
            try {
                chatService.sendMessage(message)
                    .catch { exception ->
                        errorMessage = "Error: ${exception.message}"
                        isLoading = false
                    }
                    .collect { chatMessage ->
                        if (!chatMessage.isUser) {
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
                        }
                    }
            } catch (e: Exception) {
                errorMessage = "Error: ${e.message}"
                isLoading = false
            }
        }
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
                    resumeService.resumeSession(sessionId)
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
     */
    fun clearChat() {
        messages = listOf()
        errorMessage = null
        currentResponse = ""
        chatService.clearMemory()
    }

    /**
     * Clear the error message.
     */
    fun clearError() {
        errorMessage = null
    }
}

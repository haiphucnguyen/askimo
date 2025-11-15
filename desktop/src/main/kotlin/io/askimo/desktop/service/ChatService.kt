/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.service

import io.askimo.core.providers.sendStreamingMessageWithCallback
import io.askimo.core.session.Session
import io.askimo.core.session.SessionFactory
import io.askimo.desktop.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Service for managing chat interactions in the desktop application.
 *
 * This service provides a bridge between the UI and the core chat functionality,
 * handling session management and message streaming without JLine dependencies.
 */
class ChatService {
    private val session: Session = SessionFactory.createSession()

    init {
        // Add shutdown hook to gracefully close database connections
        Runtime.getRuntime().addShutdownHook(
            Thread {
                session.chatSessionRepository.close()
            },
        )
    }

    /**
     * Send a message and get a streaming response.
     *
     * @param userMessage The message from the user
     * @return Flow of chat messages representing the streaming response
     */
    fun sendMessage(userMessage: String): Flow<ChatMessage> = callbackFlow {
        // Emit the user message first
        send(ChatMessage(content = userMessage, isUser = true))

        // Build response with streaming
        val responseBuilder = StringBuilder()

        withContext(Dispatchers.IO) {
            // Prepare context and get the prompt to use
            val promptWithContext = session.prepareContextAndGetPrompt(userMessage)

            // Stream the response with token-by-token emission
            val fullResponse = session.getChatService().sendStreamingMessageWithCallback(promptWithContext) { token ->
                responseBuilder.append(token)
                // Send each token as it arrives for real-time streaming effect
                trySend(ChatMessage(content = responseBuilder.toString(), isUser = false))
            }

            // Save the AI response to session
            session.saveAiResponse(fullResponse)

            // Update last response
            session.lastResponse = fullResponse
        }

        // Close the channel when done
        close()
    }

    /**
     * Clear the conversation memory.
     */
    fun clearMemory() {
        val provider = session.getActiveProvider()
        val modelName = session.params.getModel(provider)
        session.removeMemory(provider, modelName)
    }

    /**
     * Get the current session for advanced operations.
     */
    fun getSession(): Session = session
}

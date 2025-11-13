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
import kotlinx.coroutines.flow.flow
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
    suspend fun sendMessage(userMessage: String): Flow<ChatMessage> = flow {
        // Emit the user message first
        emit(ChatMessage(content = userMessage, isUser = true))

        // Build response with streaming
        val responseBuilder = StringBuilder()

        withContext(Dispatchers.IO) {
            // Prepare context and get the prompt to use
            val promptWithContext = session.prepareContextAndGetPrompt(userMessage)

            // Stream the response
            val fullResponse = session.getChatService().sendStreamingMessageWithCallback(promptWithContext) { token ->
                responseBuilder.append(token)
                // Emit partial responses for streaming effect
            }

            // Save the AI response to session
            session.saveAiResponse(fullResponse)

            // Update last response
            session.lastResponse = fullResponse
        }

        // Emit the complete AI response
        emit(ChatMessage(content = responseBuilder.toString(), isUser = false))
    }

    /**
     * Send a message and get the complete response (non-streaming).
     *
     * @param userMessage The message from the user
     * @return The complete AI response
     */
    suspend fun sendMessageSync(userMessage: String): String = withContext(Dispatchers.IO) {
        // Prepare context and get the prompt to use
        val promptWithContext = session.prepareContextAndGetPrompt(userMessage)

        // Get the response
        val fullResponse = session.getChatService().sendStreamingMessageWithCallback(promptWithContext) { _ ->
            // No-op for non-streaming
        }

        // Save the AI response to session
        session.saveAiResponse(fullResponse)

        // Update last response
        session.lastResponse = fullResponse

        fullResponse
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

/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.model.chat.request.ChatRequest
import io.askimo.core.chat.repository.ChatDirectiveRepository
import io.askimo.core.context.AppContextFactory
import io.askimo.core.db.DatabaseManager

/**
 * Utility functions for transforming chat requests before they are sent to the AI model.
 */
object ChatRequestTransformers {

    private val directiveRepository: ChatDirectiveRepository = DatabaseManager.getInstance().getChatDirectiveRepository()

    /**
     * Adds custom system messages and removes duplicates from the chat request.
     *
     * This transformer does two things:
     * 1. Prepends custom system messages from AppContext.systemDirective and session directives
     * 2. Removes duplicate system messages from ChatMemory
     *
     * LangChain4j's systemMessageProvider adds system messages on every request,
     * but ChatMemory also stores those system messages from previous turns.
     * This causes duplicates like: [SystemMessage, UserMessage, AiMessage, SystemMessage (duplicate!), UserMessage]
     *
     * @param sessionId The session ID from the create method
     * @param chatRequest The original chat request
     * @param memoryId The memory ID (can be null)
     * @return A new chat request with custom system messages added and duplicates removed
     */
    @JvmStatic
    fun addCustomSystemMessagesAndRemoveDuplicates(
        sessionId: String?,
        chatRequest: ChatRequest,
        memoryId: Any?,
    ): ChatRequest {
        val existingMessages = chatRequest.messages()

        // Collect custom system messages to add
        val customMessages = mutableListOf<SystemMessage>()

        // Add app-level system directive if present
        val appSystemDirective = AppContextFactory.createAppContext().systemDirective
        if (appSystemDirective != null) {
            customMessages.add(SystemMessage.from(appSystemDirective))
        }

        // Add session-specific directive if present
        if (sessionId != null) {
            val directive = directiveRepository.findDirectiveBySessionId(sessionId)
            if (directive != null && directive.content.isNotBlank()) {
                customMessages.add(SystemMessage.from(directive.content))
            }
        }

        // Collect all system message texts we want to keep (including new custom ones)
        val desiredSystemMessageTexts = customMessages.map { it.text() }.toSet()

        // Collect existing leading system messages that are not duplicates
        val leadingSystemMessages = existingMessages
            .takeWhile { it is SystemMessage }
            .filterIsInstance<SystemMessage>()
            .filter { it.text() !in desiredSystemMessageTexts } // Exclude duplicates of our custom messages
            .toList()

        // Combine all system messages we want at the beginning
        val allLeadingSystemMessages = customMessages + leadingSystemMessages
        val allLeadingSystemMessageTexts = allLeadingSystemMessages.map { it.text() }.toSet()

        // Build the cleaned message list
        val cleanedMessages = mutableListOf<dev.langchain4j.data.message.ChatMessage>()
        var foundNonSystemMessage = false

        // First, add all leading system messages
        cleanedMessages.addAll(allLeadingSystemMessages)

        // Then process the rest of the messages
        for (message in existingMessages) {
            if (message is SystemMessage) {
                if (!foundNonSystemMessage) {
                    // Already added these at the beginning, skip
                    continue
                } else {
                    // This is a system message after user/AI messages (from memory)
                    // Only keep it if it has unique content not in the leading system messages
                    if (message.text() !in allLeadingSystemMessageTexts) {
                        cleanedMessages.add(message)
                    }
                    // Otherwise skip it (it's a duplicate)
                }
            } else {
                foundNonSystemMessage = true
                cleanedMessages.add(message)
            }
        }

        // Only rebuild if we made changes
        return if (customMessages.isNotEmpty() || cleanedMessages.size != existingMessages.size) {
            chatRequest.toBuilder().messages(cleanedMessages).build()
        } else {
            chatRequest
        }
    }
}

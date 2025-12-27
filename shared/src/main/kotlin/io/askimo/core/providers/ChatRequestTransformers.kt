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

        val existingSystemMessageTexts = existingMessages
            .filterIsInstance<SystemMessage>()
            .map { it.text() }
            .toSet()

        val nonSystemMessages = existingMessages.filterNot { it is SystemMessage }

        val newSystemMessages = mutableListOf<SystemMessage>()

        val appSystemDirective = AppContextFactory.createAppContext().systemDirective
        if (appSystemDirective != null && appSystemDirective !in existingSystemMessageTexts) {
            newSystemMessages.add(SystemMessage.from(appSystemDirective))
        }

        if (sessionId != null) {
            val directive = directiveRepository.findDirectiveBySessionId(sessionId)
            if (directive != null &&
                directive.content.isNotBlank() &&
                directive.content !in existingSystemMessageTexts
            ) {
                newSystemMessages.add(SystemMessage.from(directive.content))
            }
        }

        return if (newSystemMessages.isNotEmpty()) {
            val rebuiltMessages = newSystemMessages + nonSystemMessages
            chatRequest.toBuilder().messages(rebuiltMessages).build()
        } else {
            if (nonSystemMessages.size != existingMessages.size) {
                chatRequest.toBuilder().messages(nonSystemMessages).build()
            } else {
                chatRequest
            }
        }
    }
}

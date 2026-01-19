/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.service

import io.askimo.core.chat.domain.ChatDirective
import io.askimo.core.chat.repository.ChatDirectiveRepository

/**
 * Service for managing chat directives and building system prompts for chat sessions.
 */
class ChatDirectiveService(
    private val repository: ChatDirectiveRepository,
) {
    /**
     * Build a system prompt by combining selected directives.
     * @param directiveIds List of directive IDs to include
     * @return Combined system prompt text
     */
    fun buildSystemPrompt(
        directiveIds: List<String> = emptyList(),
    ): String {
        val selected = if (directiveIds.isNotEmpty()) {
            repository.getByIds(directiveIds)
        } else {
            emptyList()
        }

        if (selected.isEmpty()) {
            return "You are a helpful AI assistant."
        }

        return buildString {
            appendLine("You are a helpful AI assistant. Follow these session directives:")
            appendLine()
            selected.forEach { directive ->
                appendLine("## ${directive.name}")
                appendLine(directive.content.trim())
                appendLine()
            }
            appendLine("---")
            appendLine("Apply the above directives consistently throughout this conversation.")
        }.trim()
    }

    /**
     * Create a new directive.
     */
    fun createDirective(
        name: String,
        content: String,
    ): ChatDirective {
        val directive = ChatDirective(
            name = name,
            content = content,
        )
        return repository.save(directive)
    }

    /**
     * Update an existing directive.
     * @return true if updated successfully
     */
    fun updateDirective(
        id: String,
        name: String,
        content: String,
    ): Boolean {
        val existing = repository.get(id) ?: return false
        val directive = existing.copy(
            name = name,
            content = content,
        )
        return repository.update(directive)
    }

    /**
     * Delete a directive.
     * @return true if deleted successfully
     */
    fun deleteDirective(id: String): Boolean = repository.delete(id)

    /**
     * List all directives.
     */
    fun listAllDirectives(): List<ChatDirective> = repository.list()
}

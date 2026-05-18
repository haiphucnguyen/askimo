/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Askimo
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

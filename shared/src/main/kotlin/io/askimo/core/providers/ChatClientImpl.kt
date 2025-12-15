/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import dev.langchain4j.service.TokenStream
import io.askimo.core.chat.domain.SessionMemory
import io.askimo.core.chat.repository.SessionMemoryRepository
import io.askimo.core.memory.TokenAwareSummarizingMemory
import org.slf4j.LoggerFactory

class ChatClientImpl(
    private val delegate: ChatClient,
    private val chatMemory: TokenAwareSummarizingMemory,
    private val sessionId: String,
    @Suppress("UNUSED_PARAMETER") // Kept for backward compatibility
    private val sessionMemoryRepository: SessionMemoryRepository? = null,
) : ChatClient {
    private val log = LoggerFactory.getLogger(ChatClientImpl::class.java)

    override fun sendMessageStreaming(prompt: String): TokenStream = delegate.sendMessageStreaming(prompt)

    override fun sendMessage(prompt: String): String = delegate.sendMessage(prompt)

    /**
     * Save the current memory state for this session.
     * @deprecated TokenAwareSummarizingMemory now handles its own persistence automatically.
     * This method is kept for backward compatibility but does nothing.
     */
    @Deprecated(
        message = "TokenAwareSummarizingMemory now handles its own persistence automatically",
        level = DeprecationLevel.WARNING,
    )
    fun saveMemory() {
        // No-op: TokenAwareSummarizingMemory handles persistence automatically
        log.debug("saveMemory() called but no action needed - memory persists automatically for session: $sessionId")
    }

    override fun clearMemory() {
        chatMemory.clear()
        log.debug("Cleared memory for session: $sessionId")
    }

    /**
     * Restore memory state from saved session memory.
     * @deprecated TokenAwareSummarizingMemory now loads from database automatically in its init block.
     * This method is kept for backward compatibility but does nothing.
     */
    @Deprecated(
        message = "TokenAwareSummarizingMemory now loads from database automatically",
        level = DeprecationLevel.WARNING,
    )
    fun restoreMemoryState(@Suppress("UNUSED_PARAMETER") savedMemory: SessionMemory) {
        // No-op: TokenAwareSummarizingMemory loads from database in init block
        log.debug("restoreMemoryState() called but no action needed - memory loaded automatically for session: $sessionId")
    }
}

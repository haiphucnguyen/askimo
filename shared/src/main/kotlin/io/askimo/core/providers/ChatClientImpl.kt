/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.service.TokenStream
import io.askimo.core.chat.domain.SessionMemory
import io.askimo.core.chat.repository.SessionMemoryRepository
import io.askimo.core.db.DatabaseManager
import io.askimo.core.memory.ConversationSummary
import io.askimo.core.memory.TokenAwareSummarizingMemory
import io.askimo.core.memory.TokenAwareSummarizingMemory.MemoryState
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class ChatClientImpl(
    private val delegate: ChatClient,
    private val chatMemory: TokenAwareSummarizingMemory,
    private val sessionMemoryRepository: SessionMemoryRepository = DatabaseManager.getInstance().getSessionMemoryRepository(),
) : ChatClient {
    private var currentSessionId: String? = null
    private val log = LoggerFactory.getLogger(ChatClientImpl::class.java)
    private val chatSessionRepository = DatabaseManager.getInstance().getChatSessionRepository()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun sendMessageStreaming(prompt: String): TokenStream = delegate.sendMessageStreaming(prompt)

    override fun sendMessage(prompt: String): String = delegate.sendMessage(prompt)

    override suspend fun switchSession(sessionId: String) {
        log.info("Switching session from ${currentSessionId ?: "none"} to $sessionId")

        // Save current session if exists
        currentSessionId?.let { oldSessionId ->
            try {
                saveCurrentSession()
                log.debug("Saved memory for session: $oldSessionId")
            } catch (e: Exception) {
                log.error("Failed to save memory for session: $oldSessionId", e)
            }
        }

        val session = chatSessionRepository.getSession(sessionId)
        val projectId = session?.projectId

        if (projectId != null) {
            log.debug("Session $sessionId belongs to project: $projectId")
            // TODO: Handle project-specific session (RAG enabled)
        } else {
            try {
                val savedMemory = sessionMemoryRepository.getBySessionId(sessionId)
                if (savedMemory != null) {
                    restoreMemoryState(savedMemory)
                    log.debug("Restored memory for session: $sessionId")
                } else {
                    chatMemory.clear()
                    log.debug("No existing memory found for session: $sessionId, starting fresh")
                }
            } catch (e: Exception) {
                log.error("Failed to load memory for session: $sessionId", e)
                chatMemory.clear()
            }
        }

        currentSessionId = sessionId
    }

    override suspend fun saveCurrentSession() {
        val sessionId = currentSessionId ?: run {
            log.warn("Cannot save memory: no current session")
            return
        }

        try {
            val state = chatMemory.exportState()
            val sessionMemory = SessionMemory(
                sessionId = sessionId,
                memorySummary = state.summary?.let { json.encodeToString(it) },
                memoryMessages = serializeMessages(state.messages),
            )
            sessionMemoryRepository.saveMemory(sessionMemory)
            log.debug("Successfully saved memory for session: $sessionId")
        } catch (e: Exception) {
            log.error("Failed to save memory for session: $sessionId", e)
            throw e
        }
    }

    override fun getCurrentSessionId(): String? = currentSessionId

    override fun clearMemory() {
        chatMemory.clear()
        log.debug("Cleared memory for session: ${currentSessionId ?: "none"}")
    }

    private fun restoreMemoryState(savedMemory: SessionMemory) {
        try {
            val summary = savedMemory.memorySummary?.let {
                json.decodeFromString<ConversationSummary>(it)
            }
            val messages = deserializeMessages(savedMemory.memoryMessages)
            chatMemory.importState(MemoryState(messages, summary))
        } catch (e: Exception) {
            log.error("Failed to deserialize memory state", e)
            chatMemory.clear()
        }
    }

    private fun serializeMessages(messages: List<ChatMessage>): String {
        val serializable = messages.map { message ->
            when (message) {
                is UserMessage -> mapOf("type" to "user", "content" to message.singleText())
                is AiMessage -> mapOf("type" to "ai", "content" to message.text())
                is SystemMessage -> mapOf("type" to "system", "content" to message.text())
                else -> mapOf("type" to "unknown", "content" to "")
            }
        }
        return json.encodeToString(serializable)
    }

    private fun deserializeMessages(messagesJson: String): List<ChatMessage> {
        return try {
            val serializable = json.decodeFromString<List<Map<String, String>>>(messagesJson)
            serializable.mapNotNull { map ->
                val content = map["content"] ?: return@mapNotNull null
                when (map["type"]) {
                    "user" -> UserMessage.from(content)
                    "ai" -> AiMessage.from(content)
                    "system" -> SystemMessage.from(content)
                    else -> null
                }
            }
        } catch (e: Exception) {
            log.error("Failed to deserialize messages", e)
            emptyList()
        }
    }
}

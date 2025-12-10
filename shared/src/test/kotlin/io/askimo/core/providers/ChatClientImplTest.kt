/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.service.TokenStream
import io.askimo.core.chat.domain.SessionMemory
import io.askimo.core.chat.repository.SessionMemoryRepository
import io.askimo.core.memory.ConversationSummary
import io.askimo.core.memory.TokenAwareSummarizingMemory
import io.askimo.core.memory.TokenAwareSummarizingMemory.MemoryState
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatClientImplTest {
    private lateinit var delegate: ChatClient
    private lateinit var chatMemory: TokenAwareSummarizingMemory
    private lateinit var sessionMemoryRepository: SessionMemoryRepository
    private lateinit var chatClient: ChatClientImpl

    @BeforeEach
    fun setup() {
        delegate = mock()
        chatMemory = mock()
        sessionMemoryRepository = mock()
        chatClient = ChatClientImpl(delegate, chatMemory, sessionMemoryRepository)
    }

    @AfterEach
    fun tearDown() {
        reset(delegate, chatMemory, sessionMemoryRepository)
    }

    @Test
    fun `sendMessageStreaming delegates to underlying client`() {
        // Given
        val prompt = "Hello, world!"
        val tokenStream: TokenStream = mock()
        whenever(delegate.sendMessageStreaming(prompt)) doReturn tokenStream

        // When
        val result = chatClient.sendMessageStreaming(prompt)

        // Then
        assertEquals(tokenStream, result)
        verify(delegate).sendMessageStreaming(prompt)
    }

    @Test
    fun `sendMessage delegates to underlying client`() {
        // Given
        val prompt = "What is 2+2?"
        val response = "The answer is 4."
        whenever(delegate.sendMessage(prompt)) doReturn response

        // When
        val result = chatClient.sendMessage(prompt)

        // Then
        assertEquals(response, result)
        verify(delegate).sendMessage(prompt)
    }

    @Test
    fun `getCurrentSessionId returns null initially`() {
        // When
        val sessionId = chatClient.getCurrentSessionId()

        // Then
        assertNull(sessionId)
    }

    @Test
    fun `switchSession with no current session loads new session`() = runBlocking {
        // Given
        val sessionId = "session-123"
        val savedMessages = """[{"type":"user","content":"Hello"},{"type":"ai","content":"Hi!"}]"""
        val savedMemory = SessionMemory(
            sessionId = sessionId,
            memorySummary = null,
            memoryMessages = savedMessages,
        )
        whenever(sessionMemoryRepository.loadMemory(sessionId)) doReturn savedMemory

        // When
        chatClient.switchSession(sessionId)

        // Then
        assertEquals(sessionId, chatClient.getCurrentSessionId())
        verify(chatMemory).importState(any())
        verify(sessionMemoryRepository).loadMemory(sessionId)
    }

    @Test
    fun `switchSession saves old session before loading new one`() = runBlocking {
        // Given
        val oldSessionId = "session-old"
        val newSessionId = "session-new"
        val messages = listOf(
            UserMessage.from("Hello"),
            AiMessage.from("Hi there!"),
        )
        val state = MemoryState(messages, null)

        whenever(chatMemory.exportState()) doReturn state
        whenever(sessionMemoryRepository.loadMemory(any())) doReturn null

        // Switch to old session first
        chatClient.switchSession(oldSessionId)

        // When - switch to new session
        chatClient.switchSession(newSessionId)

        // Then
        assertEquals(newSessionId, chatClient.getCurrentSessionId())
        verify(sessionMemoryRepository).saveMemory(argThat { memory -> memory.sessionId == oldSessionId })
        verify(sessionMemoryRepository).loadMemory(newSessionId)
    }

    @Test
    fun `switchSession clears memory when no saved memory exists`() = runBlocking {
        // Given
        val sessionId = "new-session"
        whenever(sessionMemoryRepository.loadMemory(sessionId)) doReturn null

        // When
        chatClient.switchSession(sessionId)

        // Then
        assertEquals(sessionId, chatClient.getCurrentSessionId())
        verify(chatMemory).clear()
    }

    @Test
    fun `switchSession handles load error gracefully by clearing memory`() = runBlocking {
        // Given
        val sessionId = "problematic-session"
        whenever(sessionMemoryRepository.loadMemory(sessionId)) doThrow RuntimeException("DB error")

        // When
        chatClient.switchSession(sessionId)

        // Then
        assertEquals(sessionId, chatClient.getCurrentSessionId())
        verify(chatMemory).clear()
    }

    @Test
    fun `switchSession handles save error gracefully and continues`() = runBlocking {
        // Given
        val oldSessionId = "session-1"
        val newSessionId = "session-2"

        whenever(chatMemory.exportState()) doThrow RuntimeException("Export failed")
        whenever(sessionMemoryRepository.loadMemory(any())) doReturn null

        // Switch to old session first
        chatClient.switchSession(oldSessionId)

        // When - switch should still work despite save error
        chatClient.switchSession(newSessionId)

        // Then
        assertEquals(newSessionId, chatClient.getCurrentSessionId())
        verify(sessionMemoryRepository).loadMemory(newSessionId)
    }

    @Test
    fun `saveCurrentSession persists memory state`() = runBlocking {
        // Given
        val sessionId = "session-to-save"
        val messages = listOf(
            UserMessage.from("User message"),
            AiMessage.from("AI response"),
        )
        val state = MemoryState(messages, null)

        whenever(chatMemory.exportState()) doReturn state
        whenever(sessionMemoryRepository.loadMemory(sessionId)) doReturn null

        chatClient.switchSession(sessionId)

        // When
        chatClient.saveCurrentSession()

        // Then
        verify(sessionMemoryRepository).saveMemory(
            argThat { memory ->
                memory.sessionId == sessionId &&
                    memory.memoryMessages.contains("\"type\":\"user\"") &&
                    memory.memoryMessages.contains("\"type\":\"ai\"")
            },
        )
    }

    @Test
    fun `saveCurrentSession with summary persists both messages and summary`() = runBlocking {
        // Given
        val sessionId = "session-with-summary"
        val summary = ConversationSummary(
            keyFacts = mapOf("weather" to "sunny"),
            mainTopics = listOf("weather", "forecast"),
            recentContext = "Conversation about weather",
        )
        val messages = listOf(UserMessage.from("How's the weather?"))
        val state = MemoryState(messages, summary)

        whenever(chatMemory.exportState()) doReturn state
        whenever(sessionMemoryRepository.loadMemory(sessionId)) doReturn null

        chatClient.switchSession(sessionId)

        // When
        chatClient.saveCurrentSession()

        // Then
        verify(sessionMemoryRepository).saveMemory(
            argThat { memory ->
                memory.sessionId == sessionId &&
                    memory.memorySummary != null &&
                    memory.memorySummary!!.contains("recentContext")
            },
        )
    }

    @Test
    fun `saveCurrentSession does nothing when no current session`() = runBlocking {
        // When
        chatClient.saveCurrentSession()

        // Then
        verify(sessionMemoryRepository, never()).saveMemory(any())
    }

    @Test
    fun `clearMemory clears the chat memory`() {
        // When
        chatClient.clearMemory()

        // Then
        verify(chatMemory).clear()
    }

    @Test
    fun `message serialization handles all message types`() = runBlocking {
        // Given
        val sessionId = "multi-type-session"
        val messages = listOf(
            UserMessage.from("User says hello"),
            AiMessage.from("AI responds"),
            SystemMessage.from("System message"),
        )
        val state = MemoryState(messages, null)

        whenever(chatMemory.exportState()) doReturn state
        whenever(sessionMemoryRepository.loadMemory(sessionId)) doReturn null

        chatClient.switchSession(sessionId)

        // When
        chatClient.saveCurrentSession()

        // Then
        verify(sessionMemoryRepository).saveMemory(
            argThat { memory ->
                memory.memoryMessages.contains("\"type\":\"user\"") &&
                    memory.memoryMessages.contains("\"type\":\"ai\"") &&
                    memory.memoryMessages.contains("\"type\":\"system\"") &&
                    memory.memoryMessages.contains("User says hello") &&
                    memory.memoryMessages.contains("AI responds") &&
                    memory.memoryMessages.contains("System message")
            },
        )
    }

    @Test
    fun `restoreMemoryState correctly deserializes user messages`() = runBlocking {
        // Given
        val sessionId = "restore-test"
        val savedMessages = """[{"type":"user","content":"Test message"}]"""
        val savedMemory = SessionMemory(
            sessionId = sessionId,
            memorySummary = null,
            memoryMessages = savedMessages,
        )
        whenever(sessionMemoryRepository.loadMemory(sessionId)) doReturn savedMemory

        // When
        chatClient.switchSession(sessionId)

        // Then
        verify(chatMemory).importState(
            argThat { state ->
                state.messages.size == 1 &&
                    state.messages[0] is UserMessage &&
                    (state.messages[0] as UserMessage).singleText() == "Test message"
            },
        )
    }

    @Test
    fun `restoreMemoryState correctly deserializes ai messages`() = runBlocking {
        // Given
        val sessionId = "restore-ai-test"
        val savedMessages = """[{"type":"ai","content":"AI response"}]"""
        val savedMemory = SessionMemory(
            sessionId = sessionId,
            memorySummary = null,
            memoryMessages = savedMessages,
        )
        whenever(sessionMemoryRepository.loadMemory(sessionId)) doReturn savedMemory

        // When
        chatClient.switchSession(sessionId)

        // Then
        verify(chatMemory).importState(
            argThat { state ->
                state.messages.size == 1 &&
                    state.messages[0] is AiMessage &&
                    (state.messages[0] as AiMessage).text() == "AI response"
            },
        )
    }

    @Test
    fun `restoreMemoryState correctly deserializes system messages`() = runBlocking {
        // Given
        val sessionId = "restore-system-test"
        val savedMessages = """[{"type":"system","content":"System instruction"}]"""
        val savedMemory = SessionMemory(
            sessionId = sessionId,
            memorySummary = null,
            memoryMessages = savedMessages,
        )
        whenever(sessionMemoryRepository.loadMemory(sessionId)) doReturn savedMemory

        // When
        chatClient.switchSession(sessionId)

        // Then
        verify(chatMemory).importState(
            argThat { state ->
                state.messages.size == 1 &&
                    state.messages[0] is SystemMessage &&
                    (state.messages[0] as SystemMessage).text() == "System instruction"
            },
        )
    }

    @Test
    fun `restoreMemoryState handles malformed JSON gracefully`() = runBlocking {
        // Given
        val sessionId = "malformed-json"
        val savedMemory = SessionMemory(
            sessionId = sessionId,
            memorySummary = null,
            memoryMessages = "this is not valid JSON",
        )
        whenever(sessionMemoryRepository.loadMemory(sessionId)) doReturn savedMemory

        // When
        chatClient.switchSession(sessionId)

        // Then - deserializeMessages catches the exception and returns empty list
        verify(chatMemory).importState(
            argThat { state ->
                state.messages.isEmpty() && state.summary == null
            },
        )
    }

    @Test
    fun `restoreMemoryState ignores unknown message types`() = runBlocking {
        // Given
        val sessionId = "unknown-type-test"
        val savedMessages = """[
            {"type":"user","content":"Valid message"},
            {"type":"unknown","content":"Should be ignored"},
            {"type":"ai","content":"Another valid"}
        ]"""
        val savedMemory = SessionMemory(
            sessionId = sessionId,
            memorySummary = null,
            memoryMessages = savedMessages,
        )
        whenever(sessionMemoryRepository.loadMemory(sessionId)) doReturn savedMemory

        // When
        chatClient.switchSession(sessionId)

        // Then
        verify(chatMemory).importState(
            argThat { state ->
                state.messages.size == 2 &&
                    state.messages[0] is UserMessage &&
                    state.messages[1] is AiMessage
            },
        )
    }

    @Test
    fun `restoreMemoryState restores summary when present`() = runBlocking {
        // Given
        val sessionId = "summary-test"
        val summaryJson = """{"keyFacts":{"topic":"test"},"mainTopics":["Point 1","Point 2"],"recentContext":"Brief summary"}"""
        val savedMessages = """[{"type":"user","content":"Test"}]"""
        val savedMemory = SessionMemory(
            sessionId = sessionId,
            memorySummary = summaryJson,
            memoryMessages = savedMessages,
        )
        whenever(sessionMemoryRepository.loadMemory(sessionId)) doReturn savedMemory

        // When
        chatClient.switchSession(sessionId)

        // Then
        verify(chatMemory).importState(
            argThat { state ->
                state.summary != null &&
                    state.summary!!.recentContext == "Brief summary" &&
                    state.summary!!.mainTopics.size == 2
            },
        )
    }

    @Test
    fun `multiple session switches maintain correct state`() = runBlocking {
        // Given
        val session1 = "session-1"
        val session2 = "session-2"
        val session3 = "session-3"

        val state1 = MemoryState(listOf(UserMessage.from("Message 1")), null)
        val state2 = MemoryState(listOf(UserMessage.from("Message 2")), null)

        whenever(chatMemory.exportState())
            .doReturn(state1)
            .doReturn(state2)
        whenever(sessionMemoryRepository.loadMemory(any())) doReturn null

        // When
        chatClient.switchSession(session1)
        assertEquals(session1, chatClient.getCurrentSessionId())

        chatClient.switchSession(session2)
        assertEquals(session2, chatClient.getCurrentSessionId())

        chatClient.switchSession(session3)
        assertEquals(session3, chatClient.getCurrentSessionId())

        // Then
        verify(sessionMemoryRepository).saveMemory(argThat { memory -> memory.sessionId == session1 })
        verify(sessionMemoryRepository).saveMemory(argThat { memory -> memory.sessionId == session2 })
        verify(sessionMemoryRepository, never()).saveMemory(argThat { memory -> memory.sessionId == session3 })
    }
}

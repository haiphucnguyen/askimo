/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.repository

import io.askimo.core.chat.domain.ChatMessage
import io.askimo.core.chat.domain.ChatSession
import io.askimo.core.chat.domain.ConversationSummary
import io.askimo.core.context.MessageRole
import io.askimo.core.db.DatabaseManager
import io.askimo.core.util.AskimoHome
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDateTime

class ConversationSummaryRepositoryIT {

    private lateinit var testSession: ChatSession

    @BeforeEach
    fun setUp() {
        // Create a fresh test session for each test
        testSession = sessionRepository.createSession(
            ChatSession(id = "", title = "Test Session"),
        )
    }

    @AfterEach
    fun tearDown() {
        // Clean up test data after each test
        if (::testSession.isInitialized) {
            sessionRepository.deleteSession(testSession.id)
        }
    }

    companion object {
        private lateinit var testBaseScope: AskimoHome.TestBaseScope
        private lateinit var databaseManager: DatabaseManager
        private lateinit var summaryRepository: ConversationSummaryRepository
        private lateinit var sessionRepository: ChatSessionRepository
        private lateinit var messageRepository: ChatMessageRepository
        private lateinit var folderRepository: ChatFolderRepository

        @JvmStatic
        @BeforeAll
        fun setUpClass(@TempDir tempDir: Path) {
            testBaseScope = AskimoHome.withTestBase(tempDir)

            databaseManager = DatabaseManager.getTestInstance(this)

            sessionRepository = databaseManager.getChatSessionRepository()
            messageRepository = databaseManager.getChatMessageRepository()
            summaryRepository = databaseManager.getConversationSummaryRepository()
            folderRepository = databaseManager.getChatFolderRepository()
        }

        @JvmStatic
        @AfterAll
        fun tearDownClass() {
            if (::databaseManager.isInitialized) {
                databaseManager.close()
            }
            if (::testBaseScope.isInitialized) {
                testBaseScope.close()
            }
        }
    }

    @Test
    fun `should save and retrieve conversation summary`() {
        val message = messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "Test message",
            ),
        )

        val summary = ConversationSummary(
            sessionId = testSession.id,
            keyFacts = mapOf("topic" to "testing"),
            mainTopics = listOf("unit tests", "integration tests"),
            recentContext = "Testing conversation summaries",
            lastSummarizedMessageId = message.id,
        )

        summaryRepository.saveSummary(summary)

        val retrieved = summaryRepository.getConversationSummary(testSession.id)
        assertNotNull(retrieved)
        assertEquals(testSession.id, retrieved!!.sessionId)
        assertEquals(mapOf("topic" to "testing"), retrieved.keyFacts)
        assertEquals(listOf("unit tests", "integration tests"), retrieved.mainTopics)
        assertEquals("Testing conversation summaries", retrieved.recentContext)
        assertEquals(message.id, retrieved.lastSummarizedMessageId)
    }

    @Test
    fun `should update conversation summary when saving again`() {
        val message1 = messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "First message",
            ),
        )
        val message2 = messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "Second message",
            ),
        )

        val summary1 = ConversationSummary(
            sessionId = testSession.id,
            keyFacts = mapOf("version" to "1"),
            mainTopics = listOf("topic 1"),
            recentContext = "Context 1",
            lastSummarizedMessageId = message1.id,
        )
        summaryRepository.saveSummary(summary1)

        val summary2 = ConversationSummary(
            sessionId = testSession.id,
            keyFacts = mapOf("version" to "2"),
            mainTopics = listOf("topic 1", "topic 2"),
            recentContext = "Context 2",
            lastSummarizedMessageId = message2.id,
        )
        summaryRepository.saveSummary(summary2)

        val retrieved = summaryRepository.getConversationSummary(testSession.id)
        assertEquals(mapOf("version" to "2"), retrieved!!.keyFacts)
        assertEquals(listOf("topic 1", "topic 2"), retrieved.mainTopics)
        assertEquals("Context 2", retrieved.recentContext)
        assertEquals(message2.id, retrieved.lastSummarizedMessageId)
    }

    @Test
    fun `should return null for non-existent summary`() {
        val summary = summaryRepository.getConversationSummary("non-existent-session-id")
        assertNull(summary)
    }

    @Test
    fun `should handle conversation summary with complex data`() {
        val message = messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "Message",
            ),
        )

        val summary = ConversationSummary(
            sessionId = testSession.id,
            keyFacts = mapOf(
                "user_name" to "John",
                "project" to "Askimo",
                "language" to "Kotlin",
            ),
            mainTopics = listOf(
                "repository pattern",
                "testing",
                "database",
                "conversation summaries",
            ),
            recentContext = "Discussing implementation of conversation summary feature",
            lastSummarizedMessageId = message.id,
        )

        summaryRepository.saveSummary(summary)

        val retrieved = summaryRepository.getConversationSummary(testSession.id)
        assertEquals(3, retrieved!!.keyFacts.size)
        assertEquals("John", retrieved.keyFacts["user_name"])
        assertEquals("Askimo", retrieved.keyFacts["project"])
        assertEquals("Kotlin", retrieved.keyFacts["language"])
        assertEquals(4, retrieved.mainTopics.size)
    }

    @Test
    fun `should delete summary by session`() {
        val message = messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "Test",
            ),
        )

        val summary = ConversationSummary(
            sessionId = testSession.id,
            keyFacts = mapOf("test" to "data"),
            mainTopics = listOf("testing"),
            recentContext = "Test context",
            lastSummarizedMessageId = message.id,
        )
        summaryRepository.saveSummary(summary)

        val deleted = summaryRepository.deleteSummaryBySession(testSession.id)

        assertEquals(1, deleted)
        assertNull(summaryRepository.getConversationSummary(testSession.id))
    }

    @Test
    fun `should return 0 when deleting non-existent summary`() {
        val deleted = summaryRepository.deleteSummaryBySession("non-existent-session-id")
        assertEquals(0, deleted)
    }

    @Test
    fun `should maintain timestamp when saving summary`() {
        val message = messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "Message",
            ),
        )

        val createdAt = LocalDateTime.now().withNano(0)
        val summary = ConversationSummary(
            sessionId = testSession.id,
            keyFacts = mapOf("test" to "value"),
            mainTopics = listOf("topic"),
            recentContext = "Context",
            lastSummarizedMessageId = message.id,
            createdAt = createdAt,
        )

        summaryRepository.saveSummary(summary)

        val retrieved = summaryRepository.getConversationSummary(testSession.id)
        assertEquals(createdAt, retrieved!!.createdAt.withNano(0))
    }

    @Test
    fun `should handle empty key facts and topics`() {
        val message = messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "Message",
            ),
        )

        val summary = ConversationSummary(
            sessionId = testSession.id,
            keyFacts = emptyMap(),
            mainTopics = emptyList(),
            recentContext = "Empty summary",
            lastSummarizedMessageId = message.id,
        )

        summaryRepository.saveSummary(summary)

        val retrieved = summaryRepository.getConversationSummary(testSession.id)
        assertNotNull(retrieved)
        assertEquals(0, retrieved!!.keyFacts.size)
        assertEquals(0, retrieved.mainTopics.size)
    }

    @Test
    fun `should handle special characters in summary data`() {
        val message = messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "Message",
            ),
        )

        val summary = ConversationSummary(
            sessionId = testSession.id,
            keyFacts = mapOf(
                "quote" to "He said \"hello\"",
                "newline" to "Line1\nLine2",
                "special" to "!@#$%^&*()",
            ),
            mainTopics = listOf("topic's", "topic\"with\"quotes"),
            recentContext = "Context with special chars: <>&",
            lastSummarizedMessageId = message.id,
        )

        summaryRepository.saveSummary(summary)

        val retrieved = summaryRepository.getConversationSummary(testSession.id)
        assertNotNull(retrieved)
        assertEquals("He said \"hello\"", retrieved!!.keyFacts["quote"])
        assertEquals("Line1\nLine2", retrieved.keyFacts["newline"])
        assertEquals("!@#$%^&*()", retrieved.keyFacts["special"])
    }
}

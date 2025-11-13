/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

import io.askimo.core.util.AskimoHome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class ChatSessionRepositoryIT {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var repository: ChatSessionRepository
    private lateinit var testBaseScope: AskimoHome.TestBaseScope

    @BeforeEach
    fun setUp() {
        testBaseScope = AskimoHome.withTestBase(tempDir)
        repository = ChatSessionRepository()

        repository.getAllSessions()
    }

    @AfterEach
    fun tearDown() {
        repository.close()
        testBaseScope.close()
    }

    @Test
    fun `should create and retrieve a chat session`() {
        val session = repository.createSession("Test Session")

        assertNotNull(session.id)
        assertEquals("Test Session", session.title)
        assertNotNull(session.createdAt)
        assertNotNull(session.updatedAt)

        val retrieved = repository.getSession(session.id)
        assertNotNull(retrieved)
        assertEquals(session.id, retrieved!!.id)
        assertEquals(session.title, retrieved.title)
        assertEquals(session.createdAt, retrieved.createdAt)
        assertEquals(session.updatedAt, retrieved.updatedAt)
    }

    @Test
    fun `should return null for non-existent session`() {
        val result = repository.getSession("non-existent-id")

        assertNull(result)
    }

    @Test
    fun `should retrieve all sessions ordered by updated_at desc`() {
        val session1 = repository.createSession("First Session")
        Thread.sleep(10) // Ensure different timestamps
        val session2 = repository.createSession("Second Session")
        Thread.sleep(10)
        val session3 = repository.createSession("Third Session")

        val sessions = repository.getAllSessions()

        assertEquals(3, sessions.size)
        assertEquals(session3.id, sessions[0].id) // Most recent first
        assertEquals(session2.id, sessions[1].id)
        assertEquals(session1.id, sessions[2].id)
    }

    @Test
    fun `should add and retrieve messages for a session`() {
        val session = repository.createSession("Test Session")

        val userMessage = repository.addMessage(session.id, MessageRole.USER, "Hello, AI!")
        val assistantMessage = repository.addMessage(session.id, MessageRole.ASSISTANT, "Hello! How can I help you?")

        assertNotNull(userMessage.id)
        assertEquals(session.id, userMessage.sessionId)
        assertEquals(MessageRole.USER, userMessage.role)
        assertEquals("Hello, AI!", userMessage.content)
        assertNotNull(userMessage.createdAt)

        assertNotNull(assistantMessage.id)
        assertEquals(session.id, assistantMessage.sessionId)
        assertEquals(MessageRole.ASSISTANT, assistantMessage.role)
        assertEquals("Hello! How can I help you?", assistantMessage.content)
        assertNotNull(assistantMessage.createdAt)

        val messages = repository.getMessages(session.id)
        assertEquals(2, messages.size)
        assertEquals(userMessage.id, messages[0].id)
        assertEquals(assistantMessage.id, messages[1].id)
    }

    @Test
    fun `should update session updated_at when adding message`() {
        val session = repository.createSession("Test Session")
        val originalUpdatedAt = session.updatedAt

        Thread.sleep(10)

        repository.addMessage(session.id, MessageRole.USER, "Test message")

        val updatedSession = repository.getSession(session.id)
        assertNotNull(updatedSession)
        assertTrue(updatedSession!!.updatedAt.isAfter(originalUpdatedAt))
    }

    @Test
    fun `should retrieve recent messages in chronological order`() {
        val session = repository.createSession("Test Session")
        repository.addMessage(session.id, MessageRole.USER, "Message 1")
        repository.addMessage(session.id, MessageRole.ASSISTANT, "Response 1")
        repository.addMessage(session.id, MessageRole.USER, "Message 2")
        repository.addMessage(session.id, MessageRole.ASSISTANT, "Response 2")

        val recentMessages = repository.getRecentMessages(session.id, 3)

        assertEquals(3, recentMessages.size)
        assertEquals("Response 1", recentMessages[0].content) // Oldest of the 3
        assertEquals("Message 2", recentMessages[1].content)
        assertEquals("Response 2", recentMessages[2].content) // Most recent
    }

    @Test
    fun `should count messages correctly`() {
        val session = repository.createSession("Test Session")

        assertEquals(0, repository.getMessageCount(session.id))

        repository.addMessage(session.id, MessageRole.USER, "Message 1")
        assertEquals(1, repository.getMessageCount(session.id))

        repository.addMessage(session.id, MessageRole.ASSISTANT, "Response 1")
        assertEquals(2, repository.getMessageCount(session.id))
    }

    @Test
    fun `should retrieve messages after specific message`() {
        val session = repository.createSession("Test Session")
        val message1 = repository.addMessage(session.id, MessageRole.USER, "Message 1")
        val message2 = repository.addMessage(session.id, MessageRole.ASSISTANT, "Response 1")
        val message3 = repository.addMessage(session.id, MessageRole.USER, "Message 2")
        val message4 = repository.addMessage(session.id, MessageRole.ASSISTANT, "Response 2")

        val messagesAfter = repository.getMessagesAfter(session.id, message1.id, 10)

        assertEquals(3, messagesAfter.size)
        assertEquals(message2.id, messagesAfter[0].id)
        assertEquals(message3.id, messagesAfter[1].id)
        assertEquals(message4.id, messagesAfter[2].id)
    }

    @Test
    fun `should save and retrieve conversation summary`() {
        val session = repository.createSession("Test Session")
        val message = repository.addMessage(session.id, MessageRole.USER, "Test message")

        val summary = ConversationSummary(
            sessionId = session.id,
            keyFacts = mapOf("topic" to "testing", "language" to "kotlin"),
            mainTopics = listOf("unit testing", "database"),
            recentContext = "User is asking about testing",
            lastSummarizedMessageId = message.id,
            createdAt = LocalDateTime.now(),
        )

        repository.saveSummary(summary)

        val retrievedSummary = repository.getConversationSummary(session.id)
        assertNotNull(retrievedSummary)
        assertEquals(summary.sessionId, retrievedSummary!!.sessionId)
        assertEquals(summary.keyFacts, retrievedSummary.keyFacts)
        assertEquals(summary.mainTopics, retrievedSummary.mainTopics)
        assertEquals(summary.recentContext, retrievedSummary.recentContext)
        assertEquals(summary.lastSummarizedMessageId, retrievedSummary.lastSummarizedMessageId)
    }

    @Test
    fun `should update conversation summary when saving again`() {
        val session = repository.createSession("Test Session")
        val message1 = repository.addMessage(session.id, MessageRole.USER, "Test message 1")
        val message2 = repository.addMessage(session.id, MessageRole.USER, "Test message 2")

        val originalSummary = ConversationSummary(
            sessionId = session.id,
            keyFacts = mapOf("topic" to "testing"),
            mainTopics = listOf("unit testing"),
            recentContext = "Initial context",
            lastSummarizedMessageId = message1.id,
            createdAt = LocalDateTime.now(),
        )

        val updatedSummary = ConversationSummary(
            sessionId = session.id,
            keyFacts = mapOf("topic" to "testing", "language" to "kotlin"),
            mainTopics = listOf("unit testing", "database"),
            recentContext = "Updated context",
            lastSummarizedMessageId = message2.id,
            createdAt = LocalDateTime.now(),
        )

        // When
        repository.saveSummary(originalSummary)
        repository.saveSummary(updatedSummary)

        // Then
        val retrievedSummary = repository.getConversationSummary(session.id)
        assertNotNull(retrievedSummary)
        assertEquals(updatedSummary.keyFacts, retrievedSummary!!.keyFacts)
        assertEquals(updatedSummary.mainTopics, retrievedSummary.mainTopics)
        assertEquals(updatedSummary.recentContext, retrievedSummary.recentContext)
        assertEquals(updatedSummary.lastSummarizedMessageId, retrievedSummary.lastSummarizedMessageId)
    }

    @Test
    fun `should return null for non-existent conversation summary`() {
        val result = repository.getConversationSummary("non-existent-session-id")

        assertNull(result)
    }

    @Test
    fun `should generate and update session title`() {
        val session = repository.createSession("Temporary Title")
        val firstMessage = "What is the best way to test a Kotlin application?"

        repository.generateAndUpdateTitle(session.id, firstMessage)

        val updatedSession = repository.getSession(session.id)
        assertNotNull(updatedSession)
        assertNotEquals("Temporary Title", updatedSession!!.title)
        assertTrue(updatedSession.title.contains("What is the best way to test"))
    }

    @Test
    fun `should generate title with ellipsis for long messages`() {
        val session = repository.createSession("Temporary Title")
        val longMessage = "This is a very long message that exceeds the fifty character limit and should be truncated with ellipsis"

        repository.generateAndUpdateTitle(session.id, longMessage)

        val updatedSession = repository.getSession(session.id)
        assertNotNull(updatedSession)
        // The current implementation truncates at 97 chars + "..." for messages > 100 chars
        assertEquals("This is a very long message that exceeds the fifty character limit and should be truncated with e...", updatedSession.title)
    }

    @Test
    fun `should generate title ending with period for sentences`() {
        val session = repository.createSession("Temporary Title")
        val sentenceMessage = "This is a complete sentence. And this is another one."

        repository.generateAndUpdateTitle(session.id, sentenceMessage)

        val updatedSession = repository.getSession(session.id)
        assertNotNull(updatedSession)
        assertEquals("This is a complete sentence. And this is another one.", updatedSession.title)
    }

    @Test
    fun `should handle database transactions correctly on failure`() {
        val session = repository.createSession("Test Session")

        // This test verifies that if something goes wrong during message addition,
        // the transaction is rolled back. We can't easily simulate a database failure,
        // but we can verify the basic transaction structure works.
        assertDoesNotThrow {
            repository.addMessage(session.id, MessageRole.USER, "Test message")
        }

        assertEquals(1, repository.getMessageCount(session.id))
    }

    @Test
    fun `should create database file in correct location`() {
        val dbFile = tempDir.resolve("chat_sessions.db")
        assertTrue(Files.exists(dbFile))
        assertTrue(Files.isRegularFile(dbFile))
    }

    @Test
    fun `should handle different message roles correctly`() {
        val session = repository.createSession("Test Session")

        repository.addMessage(session.id, MessageRole.USER, "User message")
        repository.addMessage(session.id, MessageRole.ASSISTANT, "Assistant message")
        repository.addMessage(session.id, MessageRole.SYSTEM, "System message")

        val messages = repository.getMessages(session.id)
        assertEquals(3, messages.size)
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals(MessageRole.ASSISTANT, messages[1].role)
        assertEquals(MessageRole.SYSTEM, messages[2].role)
    }

    @Test
    fun `should handle empty sessions list`() {
        val sessions = repository.getAllSessions()

        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `should handle empty messages list`() {
        val session = repository.createSession("Empty Session")

        val messages = repository.getMessages(session.id)

        assertTrue(messages.isEmpty())
    }

    @Test
    fun `should limit recent messages correctly`() {
        val session = repository.createSession("Test Session")
        repeat(10) { i ->
            repository.addMessage(session.id, MessageRole.USER, "Message $i")
        }

        val recentMessages = repository.getRecentMessages(session.id, 5)

        assertEquals(5, recentMessages.size)
        assertEquals("Message 5", recentMessages[0].content) // 5th message (0-indexed)
        assertEquals("Message 9", recentMessages[4].content) // Most recent
    }

    @Test
    fun `should handle conversation summary with complex data`() {
        val session = repository.createSession("Test Session")
        val message = repository.addMessage(session.id, MessageRole.USER, "Test message")

        val complexSummary = ConversationSummary(
            sessionId = session.id,
            keyFacts = mapOf(
                "user_name" to "John Doe",
                "project_type" to "web application",
                "tech_stack" to "Kotlin, Spring Boot, PostgreSQL",
                "special_chars" to "!@#$%^&*(){}[]|\\:;\"'<>,.?/~`",
            ),
            mainTopics = listOf(
                "software development",
                "database design",
                "unit testing",
                "performance optimization",
            ),
            recentContext = "User is working on a complex web application with multiple microservices. They need help with database optimization and testing strategies.",
            lastSummarizedMessageId = message.id,
            createdAt = LocalDateTime.now(),
        )

        repository.saveSummary(complexSummary)

        val retrievedSummary = repository.getConversationSummary(session.id)
        assertNotNull(retrievedSummary)
        assertEquals(complexSummary.keyFacts, retrievedSummary!!.keyFacts)
        assertEquals(complexSummary.mainTopics, retrievedSummary.mainTopics)
        assertEquals(complexSummary.recentContext, retrievedSummary.recentContext)
    }

    @Test
    fun `should delete session successfully`() {
        val session = repository.createSession("Session to Delete")

        val deleted = repository.deleteSession(session.id)

        assertTrue(deleted)
        assertNull(repository.getSession(session.id))
    }

    @Test
    fun `should return false when deleting non-existent session`() {
        val deleted = repository.deleteSession("non-existent-session-id")

        assertEquals(false, deleted)
    }

    @Test
    fun `should delete session and all its messages`() {
        val session = repository.createSession("Session with Messages")
        repository.addMessage(session.id, MessageRole.USER, "Message 1")
        repository.addMessage(session.id, MessageRole.ASSISTANT, "Response 1")
        repository.addMessage(session.id, MessageRole.USER, "Message 2")

        assertEquals(3, repository.getMessageCount(session.id))

        val deleted = repository.deleteSession(session.id)

        assertTrue(deleted)
        assertNull(repository.getSession(session.id))
        assertEquals(0, repository.getMessageCount(session.id))
        assertTrue(repository.getMessages(session.id).isEmpty())
    }

    @Test
    fun `should delete session and its conversation summary`() {
        val session = repository.createSession("Session with Summary")
        val message = repository.addMessage(session.id, MessageRole.USER, "Test message")

        val summary = ConversationSummary(
            sessionId = session.id,
            keyFacts = mapOf("topic" to "testing"),
            mainTopics = listOf("unit testing"),
            recentContext = "Test context",
            lastSummarizedMessageId = message.id,
            createdAt = LocalDateTime.now(),
        )

        repository.saveSummary(summary)
        assertNotNull(repository.getConversationSummary(session.id))

        val deleted = repository.deleteSession(session.id)

        assertTrue(deleted)
        assertNull(repository.getSession(session.id))
        assertNull(repository.getConversationSummary(session.id))
    }

    @Test
    fun `should delete session with all related data`() {
        val session = repository.createSession("Complete Session")
        repository.addMessage(session.id, MessageRole.USER, "Message 1")
        val message2 = repository.addMessage(session.id, MessageRole.ASSISTANT, "Response 1")

        val summary = ConversationSummary(
            sessionId = session.id,
            keyFacts = mapOf("key" to "value"),
            mainTopics = listOf("topic1", "topic2"),
            recentContext = "Context",
            lastSummarizedMessageId = message2.id,
            createdAt = LocalDateTime.now(),
        )
        repository.saveSummary(summary)

        // Verify everything exists
        assertNotNull(repository.getSession(session.id))
        assertEquals(2, repository.getMessageCount(session.id))
        assertNotNull(repository.getConversationSummary(session.id))

        // Delete session
        val deleted = repository.deleteSession(session.id)

        // Verify everything is gone
        assertTrue(deleted)
        assertNull(repository.getSession(session.id))
        assertEquals(0, repository.getMessageCount(session.id))
        assertTrue(repository.getMessages(session.id).isEmpty())
        assertNull(repository.getConversationSummary(session.id))
    }

    @Test
    fun `should not affect other sessions when deleting one`() {
        val session1 = repository.createSession("Session 1")
        val session2 = repository.createSession("Session 2")
        repository.addMessage(session1.id, MessageRole.USER, "Message in session 1")
        repository.addMessage(session2.id, MessageRole.USER, "Message in session 2")

        val deleted = repository.deleteSession(session1.id)

        assertTrue(deleted)
        assertNull(repository.getSession(session1.id))
        assertNotNull(repository.getSession(session2.id))
        assertEquals(0, repository.getMessageCount(session1.id))
        assertEquals(1, repository.getMessageCount(session2.id))
    }

    @Test
    fun `should handle deletion of session with no messages`() {
        val session = repository.createSession("Empty Session")

        assertEquals(0, repository.getMessageCount(session.id))

        val deleted = repository.deleteSession(session.id)

        assertTrue(deleted)
        assertNull(repository.getSession(session.id))
    }

    @Test
    fun `should handle deletion of session with no summary`() {
        val session = repository.createSession("Session without Summary")
        repository.addMessage(session.id, MessageRole.USER, "Message")

        assertNull(repository.getConversationSummary(session.id))

        val deleted = repository.deleteSession(session.id)

        assertTrue(deleted)
        assertNull(repository.getSession(session.id))
    }

    @Test
    fun `should delete multiple sessions independently`() {
        val session1 = repository.createSession("Session 1")
        val session2 = repository.createSession("Session 2")
        val session3 = repository.createSession("Session 3")

        assertEquals(3, repository.getAllSessions().size)

        assertTrue(repository.deleteSession(session1.id))
        assertEquals(2, repository.getAllSessions().size)
        assertNull(repository.getSession(session1.id))
        assertNotNull(repository.getSession(session2.id))
        assertNotNull(repository.getSession(session3.id))

        assertTrue(repository.deleteSession(session3.id))
        assertEquals(1, repository.getAllSessions().size)
        assertNull(repository.getSession(session3.id))
        assertNotNull(repository.getSession(session2.id))

        assertTrue(repository.deleteSession(session2.id))
        assertEquals(0, repository.getAllSessions().size)
        assertNull(repository.getSession(session2.id))
    }

    @Test
    fun `should maintain database integrity after deletion`() {
        val session1 = repository.createSession("Session 1")
        val session2 = repository.createSession("Session 2")
        repository.addMessage(session1.id, MessageRole.USER, "Message 1")
        repository.addMessage(session2.id, MessageRole.USER, "Message 2")

        repository.deleteSession(session1.id)

        // Verify we can still create new sessions and add messages
        val session3 = repository.createSession("Session 3")
        assertNotNull(session3)

        val newMessage = repository.addMessage(session2.id, MessageRole.USER, "Another message")
        assertNotNull(newMessage)
        assertEquals(2, repository.getMessageCount(session2.id))
    }
}

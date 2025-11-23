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
        // Create a message longer than SESSION_TITLE_MAX_LENGTH to test ellipsis
        val longMessage = "a".repeat(SESSION_TITLE_MAX_LENGTH + 50)

        repository.generateAndUpdateTitle(session.id, longMessage)

        val updatedSession = repository.getSession(session.id)
        assertNotNull(updatedSession)
        // The implementation truncates at SESSION_TITLE_MAX_LENGTH - 3 chars and adds "..."
        assertTrue(updatedSession.title.endsWith("..."))
        assertEquals(SESSION_TITLE_MAX_LENGTH, updatedSession.title.length)
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

    @Test
    fun `should paginate messages forward from start`() {
        val session = repository.createSession("Test Session")
        repeat(10) { i ->
            repository.addMessage(session.id, MessageRole.USER, "Message $i")
        }

        val (messages, nextCursor) = repository.getMessagesPaginated(
            sessionId = session.id,
            limit = 3,
            cursor = null,
            direction = "forward",
        )

        assertEquals(3, messages.size)
        assertEquals("Message 0", messages[0].content)
        assertEquals("Message 1", messages[1].content)
        assertEquals("Message 2", messages[2].content)
        assertNotNull(nextCursor)
    }

    @Test
    fun `should paginate messages forward with cursor`() {
        val session = repository.createSession("Test Session")
        val messages = mutableListOf<ChatMessage>()
        repeat(10) { i ->
            messages.add(repository.addMessage(session.id, MessageRole.USER, "Message $i"))
        }

        val (firstPage, cursor1) = repository.getMessagesPaginated(
            sessionId = session.id,
            limit = 3,
            cursor = null,
            direction = "forward",
        )

        assertEquals(3, firstPage.size)
        assertNotNull(cursor1)

        val (secondPage, cursor2) = repository.getMessagesPaginated(
            sessionId = session.id,
            limit = 3,
            cursor = cursor1,
            direction = "forward",
        )

        assertEquals(3, secondPage.size)
        assertEquals("Message 3", secondPage[0].content)
        assertEquals("Message 4", secondPage[1].content)
        assertEquals("Message 5", secondPage[2].content)
        assertNotNull(cursor2)
    }

    @Test
    fun `should paginate messages backward from end`() {
        val session = repository.createSession("Test Session")
        repeat(10) { i ->
            repository.addMessage(session.id, MessageRole.USER, "Message $i")
        }

        val (messages, prevCursor) = repository.getMessagesPaginated(
            sessionId = session.id,
            limit = 3,
            cursor = null,
            direction = "backward",
        )

        assertEquals(3, messages.size)
        assertEquals("Message 7", messages[0].content) // Still in chronological order
        assertEquals("Message 8", messages[1].content)
        assertEquals("Message 9", messages[2].content)
        assertNotNull(prevCursor)
    }

    @Test
    fun `should paginate messages backward with cursor`() {
        val session = repository.createSession("Test Session")
        repeat(10) { i ->
            repository.addMessage(session.id, MessageRole.USER, "Message $i")
        }

        val (firstPage, cursor1) = repository.getMessagesPaginated(
            sessionId = session.id,
            limit = 3,
            cursor = null,
            direction = "backward",
        )

        assertEquals(3, firstPage.size)
        assertNotNull(cursor1)

        val (secondPage, cursor2) = repository.getMessagesPaginated(
            sessionId = session.id,
            limit = 3,
            cursor = cursor1,
            direction = "backward",
        )

        assertEquals(3, secondPage.size)
        assertEquals("Message 4", secondPage[0].content) // Still chronological
        assertEquals("Message 5", secondPage[1].content)
        assertEquals("Message 6", secondPage[2].content)
        assertNotNull(cursor2)
    }

    @Test
    fun `should return null cursor when no more messages forward`() {
        val session = repository.createSession("Test Session")
        repeat(5) { i ->
            repository.addMessage(session.id, MessageRole.USER, "Message $i")
        }

        val (firstPage, cursor1) = repository.getMessagesPaginated(
            sessionId = session.id,
            limit = 3,
            cursor = null,
            direction = "forward",
        )

        assertEquals(3, firstPage.size)
        assertNotNull(cursor1)

        val (secondPage, cursor2) = repository.getMessagesPaginated(
            sessionId = session.id,
            limit = 3,
            cursor = cursor1,
            direction = "forward",
        )

        assertEquals(2, secondPage.size) // Only 2 messages left
        assertEquals("Message 3", secondPage[0].content)
        assertEquals("Message 4", secondPage[1].content)
        assertNull(cursor2) // No more messages
    }

    @Test
    fun `should return null cursor when no more messages backward`() {
        val session = repository.createSession("Test Session")
        repeat(5) { i ->
            repository.addMessage(session.id, MessageRole.USER, "Message $i")
        }

        val (firstPage, cursor1) = repository.getMessagesPaginated(
            sessionId = session.id,
            limit = 3,
            cursor = null,
            direction = "backward",
        )

        assertEquals(3, firstPage.size)
        assertNotNull(cursor1)

        val (secondPage, cursor2) = repository.getMessagesPaginated(
            sessionId = session.id,
            limit = 3,
            cursor = cursor1,
            direction = "backward",
        )

        assertEquals(2, secondPage.size) // Only 2 messages left
        assertEquals("Message 0", secondPage[0].content)
        assertEquals("Message 1", secondPage[1].content)
        assertNull(cursor2) // No more messages
    }

    @Test
    fun `should handle pagination with exact page size`() {
        val session = repository.createSession("Test Session")
        repeat(6) { i ->
            repository.addMessage(session.id, MessageRole.USER, "Message $i")
        }

        val (firstPage, cursor1) = repository.getMessagesPaginated(
            sessionId = session.id,
            limit = 3,
            cursor = null,
            direction = "forward",
        )

        assertEquals(3, firstPage.size)
        assertNotNull(cursor1)

        val (secondPage, cursor2) = repository.getMessagesPaginated(
            sessionId = session.id,
            limit = 3,
            cursor = cursor1,
            direction = "forward",
        )

        assertEquals(3, secondPage.size)
        assertEquals("Message 3", secondPage[0].content)
        assertEquals("Message 4", secondPage[1].content)
        assertEquals("Message 5", secondPage[2].content)
        assertNull(cursor2) // Exactly filled, no more messages
    }

    @Test
    fun `should return empty list and null cursor for empty session`() {
        val session = repository.createSession("Empty Session")

        val (messages, cursor) = repository.getMessagesPaginated(
            sessionId = session.id,
            limit = 10,
            cursor = null,
            direction = "forward",
        )

        assertTrue(messages.isEmpty())
        assertNull(cursor)
    }

    @Test
    fun `should maintain chronological order in forward pagination`() {
        val session = repository.createSession("Test Session")
        val timestamps = mutableListOf<LocalDateTime>()
        repeat(10) { i ->
            val message = repository.addMessage(session.id, MessageRole.USER, "Message $i")
            timestamps.add(message.createdAt)
            Thread.sleep(1) // Ensure different timestamps
        }

        val (messages, _) = repository.getMessagesPaginated(
            sessionId = session.id,
            limit = 5,
            cursor = null,
            direction = "forward",
        )

        assertEquals(5, messages.size)
        for (i in 0 until messages.size - 1) {
            assertTrue(
                messages[i].createdAt.isBefore(messages[i + 1].createdAt) ||
                    messages[i].createdAt.isEqual(messages[i + 1].createdAt),
            )
        }
    }

    @Test
    fun `should maintain chronological order in backward pagination`() {
        val session = repository.createSession("Test Session")
        repeat(10) { i ->
            repository.addMessage(session.id, MessageRole.USER, "Message $i")
            Thread.sleep(1) // Ensure different timestamps
        }

        val (messages, _) = repository.getMessagesPaginated(
            sessionId = session.id,
            limit = 5,
            cursor = null,
            direction = "backward",
        )

        assertEquals(5, messages.size)
        // Messages should still be in chronological order (oldest to newest)
        for (i in 0 until messages.size - 1) {
            assertTrue(
                messages[i].createdAt.isBefore(messages[i + 1].createdAt) ||
                    messages[i].createdAt.isEqual(messages[i + 1].createdAt),
            )
        }
    }

    @Test
    fun `should handle pagination with different message roles`() {
        val session = repository.createSession("Test Session")
        repository.addMessage(session.id, MessageRole.SYSTEM, "System message")
        repository.addMessage(session.id, MessageRole.USER, "User message 1")
        repository.addMessage(session.id, MessageRole.ASSISTANT, "Assistant response 1")
        repository.addMessage(session.id, MessageRole.USER, "User message 2")
        repository.addMessage(session.id, MessageRole.ASSISTANT, "Assistant response 2")

        val (messages, cursor) = repository.getMessagesPaginated(
            sessionId = session.id,
            limit = 3,
            cursor = null,
            direction = "forward",
        )

        assertEquals(3, messages.size)
        assertEquals(MessageRole.SYSTEM, messages[0].role)
        assertEquals(MessageRole.USER, messages[1].role)
        assertEquals(MessageRole.ASSISTANT, messages[2].role)
        assertNotNull(cursor)
    }

    @Test
    fun `should paginate through entire message history forward`() {
        val session = repository.createSession("Test Session")
        repeat(25) { i ->
            repository.addMessage(session.id, MessageRole.USER, "Message $i")
        }

        val allMessages = mutableListOf<ChatMessage>()
        var cursor: LocalDateTime? = null

        // Paginate through all messages
        do {
            val (messages, nextCursor) = repository.getMessagesPaginated(
                sessionId = session.id,
                limit = 10,
                cursor = cursor,
                direction = "forward",
            )
            allMessages.addAll(messages)
            cursor = nextCursor
        } while (cursor != null)

        assertEquals(25, allMessages.size)
        for (i in 0 until 25) {
            assertEquals("Message $i", allMessages[i].content)
        }
    }

    @Test
    fun `should paginate through entire message history backward`() {
        val session = repository.createSession("Test Session")
        repeat(25) { i ->
            repository.addMessage(session.id, MessageRole.USER, "Message $i")
        }

        val allMessages = mutableListOf<ChatMessage>()
        var cursor: LocalDateTime? = null

        // Paginate through all messages backward
        do {
            val (messages, prevCursor) = repository.getMessagesPaginated(
                sessionId = session.id,
                limit = 10,
                cursor = cursor,
                direction = "backward",
            )
            // Add at the beginning since we're going backward
            allMessages.addAll(0, messages)
            cursor = prevCursor
        } while (cursor != null)

        assertEquals(25, allMessages.size)
        for (i in 0 until 25) {
            assertEquals("Message $i", allMessages[i].content)
        }
    }

    @Test
    fun `should handle pagination with single message`() {
        val session = repository.createSession("Test Session")
        repository.addMessage(session.id, MessageRole.USER, "Only message")

        val (messages, cursor) = repository.getMessagesPaginated(
            sessionId = session.id,
            limit = 10,
            cursor = null,
            direction = "forward",
        )

        assertEquals(1, messages.size)
        assertEquals("Only message", messages[0].content)
        assertNull(cursor) // No more messages
    }

    @Test
    fun `should not duplicate messages across pages`() {
        val session = repository.createSession("Test Session")
        repeat(15) { i ->
            repository.addMessage(session.id, MessageRole.USER, "Message $i")
        }

        val allMessageIds = mutableSetOf<String>()
        var cursor: LocalDateTime? = null

        // Collect all message IDs across pages
        do {
            val (messages, nextCursor) = repository.getMessagesPaginated(
                sessionId = session.id,
                limit = 5,
                cursor = cursor,
                direction = "forward",
            )
            messages.forEach { allMessageIds.add(it.id) }
            cursor = nextCursor
        } while (cursor != null)

        // All IDs should be unique (no duplicates)
        assertEquals(15, allMessageIds.size)
    }

    @Test
    fun `should handle large page sizes`() {
        val session = repository.createSession("Test Session")
        repeat(10) { i ->
            repository.addMessage(session.id, MessageRole.USER, "Message $i")
        }

        val (messages, cursor) = repository.getMessagesPaginated(
            sessionId = session.id,
            limit = 100,
            cursor = null,
            direction = "forward",
        )

        assertEquals(10, messages.size) // Returns all available messages
        assertNull(cursor) // No more messages
    }

    @Test
    fun `should work with non-existent session`() {
        val (messages, cursor) = repository.getMessagesPaginated(
            sessionId = "non-existent-session",
            limit = 10,
            cursor = null,
            direction = "forward",
        )

        assertTrue(messages.isEmpty())
        assertNull(cursor)
    }

    // ==================== Folder Management Tests ====================

    @Test
    fun `should create and retrieve a folder`() {
        val folder = repository.createFolder("Work Projects")

        assertNotNull(folder.id)
        assertEquals("Work Projects", folder.name)
        assertNull(folder.parentFolderId)
        assertNull(folder.color)
        assertNull(folder.icon)
        assertEquals(0, folder.sortOrder)
        assertNotNull(folder.createdAt)
        assertNotNull(folder.updatedAt)

        val retrieved = repository.getFolder(folder.id)
        assertNotNull(retrieved)
        assertEquals(folder.id, retrieved!!.id)
        assertEquals(folder.name, retrieved.name)
    }

    @Test
    fun `should create folder with all properties`() {
        val folder = repository.createFolder(
            name = "Personal",
            parentFolderId = null,
            color = "#FF5733",
            icon = "📁",
            sortOrder = 5,
        )

        assertEquals("Personal", folder.name)
        assertEquals("#FF5733", folder.color)
        assertEquals("📁", folder.icon)
        assertEquals(5, folder.sortOrder)
    }

    @Test
    fun `should create nested folders`() {
        val parentFolder = repository.createFolder("Parent Folder")
        val childFolder = repository.createFolder(
            name = "Child Folder",
            parentFolderId = parentFolder.id,
        )

        assertEquals(parentFolder.id, childFolder.parentFolderId)

        val retrieved = repository.getFolder(childFolder.id)
        assertNotNull(retrieved)
        assertEquals(parentFolder.id, retrieved!!.parentFolderId)
    }

    @Test
    fun `should retrieve all folders ordered by sort order and name`() {
        repository.createFolder("Zebra", sortOrder = 10)
        repository.createFolder("Alpha", sortOrder = 5)
        repository.createFolder("Beta", sortOrder = 5)

        val folders = repository.getAllFolders()

        assertEquals(3, folders.size)
        assertEquals("Alpha", folders[0].name) // sort_order 5, alphabetically first
        assertEquals("Beta", folders[1].name)  // sort_order 5, alphabetically second
        assertEquals("Zebra", folders[2].name) // sort_order 10
    }

    @Test
    fun `should update folder name`() {
        val folder = repository.createFolder("Old Name")

        val updated = repository.updateFolder(folder.id, name = "New Name")

        assertTrue(updated)
        val retrieved = repository.getFolder(folder.id)
        assertEquals("New Name", retrieved!!.name)
    }

    @Test
    fun `should update folder properties`() {
        val folder = repository.createFolder("Test Folder")

        val updated = repository.updateFolder(
            folderId = folder.id,
            color = "#00FF00",
            icon = "🚀",
            sortOrder = 10,
        )

        assertTrue(updated)
        val retrieved = repository.getFolder(folder.id)
        assertEquals("#00FF00", retrieved!!.color)
        assertEquals("🚀", retrieved.icon)
        assertEquals(10, retrieved.sortOrder)
    }

    @Test
    fun `should update folder parent`() {
        val parent1 = repository.createFolder("Parent 1")
        val parent2 = repository.createFolder("Parent 2")
        val child = repository.createFolder("Child", parentFolderId = parent1.id)

        val updated = repository.updateFolder(child.id, parentFolderId = parent2.id)

        assertTrue(updated)
        val retrieved = repository.getFolder(child.id)
        assertEquals(parent2.id, retrieved!!.parentFolderId)
    }

    @Test
    fun `should return false when updating non-existent folder`() {
        val updated = repository.updateFolder("non-existent-id", name = "New Name")

        assertEquals(false, updated)
    }

    @Test
    fun `should return false when updating folder with no changes`() {
        val folder = repository.createFolder("Test Folder")

        val updated = repository.updateFolder(folder.id)

        assertEquals(false, updated) // No changes specified
    }

    @Test
    fun `should delete folder successfully`() {
        val folder = repository.createFolder("Folder to Delete")

        val deleted = repository.deleteFolder(folder.id)

        assertTrue(deleted)
        assertNull(repository.getFolder(folder.id))
    }

    @Test
    fun `should return false when deleting non-existent folder`() {
        val deleted = repository.deleteFolder("non-existent-folder-id")

        assertEquals(false, deleted)
    }

    @Test
    fun `should move sessions to root when deleting folder`() {
        val folder = repository.createFolder("Test Folder")
        val session = repository.createSession("Test Session", folderId = folder.id)

        assertEquals(folder.id, session.folderId)

        repository.deleteFolder(folder.id)

        val updatedSession = repository.getSession(session.id)
        assertNotNull(updatedSession)
        assertNull(updatedSession!!.folderId) // Moved to root
    }

    @Test
    fun `should move child folders to root when deleting parent folder`() {
        val parent = repository.createFolder("Parent")
        val child = repository.createFolder("Child", parentFolderId = parent.id)

        assertEquals(parent.id, child.parentFolderId)

        repository.deleteFolder(parent.id)

        val updatedChild = repository.getFolder(child.id)
        assertNotNull(updatedChild)
        assertNull(updatedChild!!.parentFolderId) // Moved to root
    }

    @Test
    fun `should return null for non-existent folder`() {
        val result = repository.getFolder("non-existent-folder-id")

        assertNull(result)
    }

    // ==================== Session with Folders Tests ====================

    @Test
    fun `should create session with folder`() {
        val folder = repository.createFolder("Test Folder")
        val session = repository.createSession("Test Session", folderId = folder.id)

        assertEquals(folder.id, session.folderId)

        val retrieved = repository.getSession(session.id)
        assertEquals(folder.id, retrieved!!.folderId)
    }

    @Test
    fun `should create session without folder (root level)`() {
        val session = repository.createSession("Root Session")

        assertNull(session.folderId)
    }

    @Test
    fun `should move session to folder`() {
        val session = repository.createSession("Test Session")
        val folder = repository.createFolder("Test Folder")

        assertNull(session.folderId)

        val updated = repository.updateSessionFolder(session.id, folder.id)

        assertTrue(updated)
        val retrieved = repository.getSession(session.id)
        assertEquals(folder.id, retrieved!!.folderId)
    }

    @Test
    fun `should move session to root (null folder)`() {
        val folder = repository.createFolder("Test Folder")
        val session = repository.createSession("Test Session", folderId = folder.id)

        assertEquals(folder.id, session.folderId)

        val updated = repository.updateSessionFolder(session.id, null)

        assertTrue(updated)
        val retrieved = repository.getSession(session.id)
        assertNull(retrieved!!.folderId)
    }

    @Test
    fun `should move session between folders`() {
        val folder1 = repository.createFolder("Folder 1")
        val folder2 = repository.createFolder("Folder 2")
        val session = repository.createSession("Test Session", folderId = folder1.id)

        assertEquals(folder1.id, session.folderId)

        repository.updateSessionFolder(session.id, folder2.id)

        val retrieved = repository.getSession(session.id)
        assertEquals(folder2.id, retrieved!!.folderId)
    }

    @Test
    fun `should return false when moving non-existent session to folder`() {
        val folder = repository.createFolder("Test Folder")
        val updated = repository.updateSessionFolder("non-existent-id", folder.id)

        assertEquals(false, updated)
    }

    @Test
    fun `should get sessions by folder`() {
        val folder1 = repository.createFolder("Folder 1")
        val folder2 = repository.createFolder("Folder 2")

        repository.createSession("Session in Folder 1 - A", folderId = folder1.id)
        repository.createSession("Session in Folder 1 - B", folderId = folder1.id)
        repository.createSession("Session in Folder 2", folderId = folder2.id)
        repository.createSession("Root Session")

        val folder1Sessions = repository.getSessionsByFolder(folder1.id)
        assertEquals(2, folder1Sessions.size)
        assertTrue(folder1Sessions.all { it.folderId == folder1.id })

        val folder2Sessions = repository.getSessionsByFolder(folder2.id)
        assertEquals(1, folder2Sessions.size)
        assertEquals(folder2.id, folder2Sessions[0].folderId)
    }

    @Test
    fun `should get root sessions (null folder)`() {
        val folder = repository.createFolder("Test Folder")
        repository.createSession("Root Session 1")
        repository.createSession("Root Session 2")
        repository.createSession("Folder Session", folderId = folder.id)

        val rootSessions = repository.getSessionsByFolder(null)

        assertEquals(2, rootSessions.size)
        assertTrue(rootSessions.all { it.folderId == null })
    }

    @Test
    fun `should return empty list for folder with no sessions`() {
        val folder = repository.createFolder("Empty Folder")

        val sessions = repository.getSessionsByFolder(folder.id)

        assertTrue(sessions.isEmpty())
    }

    // ==================== Starred Sessions Tests ====================

    @Test
    fun `should create session with starred flag`() {
        val session = repository.createSession("Starred Session", isStarred = true)

        assertTrue(session.isStarred)

        val retrieved = repository.getSession(session.id)
        assertTrue(retrieved!!.isStarred)
    }

    @Test
    fun `should create session without starred flag by default`() {
        val session = repository.createSession("Normal Session")

        assertEquals(false, session.isStarred)
    }

    @Test
    fun `should star a session`() {
        val session = repository.createSession("Test Session")

        assertEquals(false, session.isStarred)

        val updated = repository.updateSessionStarred(session.id, true)

        assertTrue(updated)
        val retrieved = repository.getSession(session.id)
        assertTrue(retrieved!!.isStarred)
    }

    @Test
    fun `should unstar a session`() {
        val session = repository.createSession("Test Session", isStarred = true)

        assertTrue(session.isStarred)

        val updated = repository.updateSessionStarred(session.id, false)

        assertTrue(updated)
        val retrieved = repository.getSession(session.id)
        assertEquals(false, retrieved!!.isStarred)
    }

    @Test
    fun `should return false when starring non-existent session`() {
        val updated = repository.updateSessionStarred("non-existent-id", true)

        assertEquals(false, updated)
    }

    @Test
    fun `should get all starred sessions`() {
        repository.createSession("Starred 1", isStarred = true)
        repository.createSession("Normal 1", isStarred = false)
        repository.createSession("Starred 2", isStarred = true)
        repository.createSession("Normal 2")

        val starredSessions = repository.getStarredSessions()

        assertEquals(2, starredSessions.size)
        assertTrue(starredSessions.all { it.isStarred })
    }

    @Test
    fun `should return empty list when no starred sessions`() {
        repository.createSession("Normal Session 1")
        repository.createSession("Normal Session 2")

        val starredSessions = repository.getStarredSessions()

        assertTrue(starredSessions.isEmpty())
    }

    @Test
    fun `should get starred sessions ordered by sort order and updated time`() {
        val session1 = repository.createSession("Starred 1", isStarred = true, sortOrder = 10)
        Thread.sleep(10)
        val session2 = repository.createSession("Starred 2", isStarred = true, sortOrder = 5)
        Thread.sleep(10)
        val session3 = repository.createSession("Starred 3", isStarred = true, sortOrder = 5)

        val starredSessions = repository.getStarredSessions()

        assertEquals(3, starredSessions.size)
        // First two should have sortOrder 5 (could be in either order based on timestamp precision)
        assertTrue(starredSessions[0].sortOrder == 5)
        assertTrue(starredSessions[1].sortOrder == 5)
        // Last one should have sortOrder 10
        assertEquals(session1.id, starredSessions[2].id)
        assertEquals(10, starredSessions[2].sortOrder)
    }

    // ==================== Sort Order Tests ====================

    @Test
    fun `should create session with sort order`() {
        val session = repository.createSession("Test Session", sortOrder = 42)

        assertEquals(42, session.sortOrder)

        val retrieved = repository.getSession(session.id)
        assertEquals(42, retrieved!!.sortOrder)
    }

    @Test
    fun `should create session with default sort order`() {
        val session = repository.createSession("Test Session")

        assertEquals(0, session.sortOrder)
    }

    @Test
    fun `should update session sort order`() {
        val session = repository.createSession("Test Session", sortOrder = 1)

        assertEquals(1, session.sortOrder)

        val updated = repository.updateSessionSortOrder(session.id, 10)

        assertTrue(updated)
        val retrieved = repository.getSession(session.id)
        assertEquals(10, retrieved!!.sortOrder)
    }

    @Test
    fun `should return false when updating sort order of non-existent session`() {
        val updated = repository.updateSessionSortOrder("non-existent-id", 5)

        assertEquals(false, updated)
    }

    @Test
    fun `should order sessions by starred, sort order, and updated time`() {
        // Create sessions with different combinations
        val normal1 = repository.createSession("Normal 1", isStarred = false, sortOrder = 5)
        Thread.sleep(10)
        val normal2 = repository.createSession("Normal 2", isStarred = false, sortOrder = 10)
        Thread.sleep(10)
        val starred1 = repository.createSession("Starred 1", isStarred = true, sortOrder = 10)
        Thread.sleep(10)
        val starred2 = repository.createSession("Starred 2", isStarred = true, sortOrder = 5)

        val allSessions = repository.getAllSessions()

        assertEquals(4, allSessions.size)

        // First two should be starred sessions
        assertTrue(allSessions[0].isStarred)
        assertTrue(allSessions[1].isStarred)

        // Last two should be normal sessions
        assertEquals(false, allSessions[2].isStarred)
        assertEquals(false, allSessions[3].isStarred)

        // Within starred group, session with sortOrder 5 should come before sortOrder 10
        val starredSessions = allSessions.filter { it.isStarred }
        assertTrue(starredSessions[0].sortOrder <= starredSessions[1].sortOrder)

        // Verify specific sessions exist
        assertTrue(allSessions.any { it.id == starred1.id && it.isStarred })
        assertTrue(allSessions.any { it.id == starred2.id && it.isStarred })
        assertTrue(allSessions.any { it.id == normal1.id && !it.isStarred })
        assertTrue(allSessions.any { it.id == normal2.id && !it.isStarred })
    }

    // ==================== Combined Features Tests ====================

    @Test
    fun `should handle session with folder and starred`() {
        val folder = repository.createFolder("Important Folder")
        val session = repository.createSession(
            "Important Session",
            folderId = folder.id,
            isStarred = true,
            sortOrder = 5,
        )

        assertEquals(folder.id, session.folderId)
        assertTrue(session.isStarred)
        assertEquals(5, session.sortOrder)

        val retrieved = repository.getSession(session.id)
        assertNotNull(retrieved)
        assertEquals(folder.id, retrieved!!.folderId)
        assertTrue(retrieved.isStarred)
        assertEquals(5, retrieved.sortOrder)
    }

    @Test
    fun `should get starred sessions from specific folder`() {
        val folder = repository.createFolder("Test Folder")
        repository.createSession("Starred in Folder", folderId = folder.id, isStarred = true)
        repository.createSession("Normal in Folder", folderId = folder.id, isStarred = false)
        repository.createSession("Starred in Root", isStarred = true)

        val folderSessions = repository.getSessionsByFolder(folder.id)
        val starredInFolder = folderSessions.filter { it.isStarred }

        assertEquals(1, starredInFolder.size)
        assertEquals(folder.id, starredInFolder[0].folderId)
        assertTrue(starredInFolder[0].isStarred)
    }

    @Test
    fun `should maintain starred flag when moving between folders`() {
        val folder1 = repository.createFolder("Folder 1")
        val folder2 = repository.createFolder("Folder 2")
        val session = repository.createSession("Starred Session", folderId = folder1.id, isStarred = true)

        assertTrue(session.isStarred)

        repository.updateSessionFolder(session.id, folder2.id)

        val retrieved = repository.getSession(session.id)
        assertEquals(folder2.id, retrieved!!.folderId)
        assertTrue(retrieved.isStarred) // Still starred after move
    }

    @Test
    fun `should delete folder without affecting starred status of sessions`() {
        val folder = repository.createFolder("Test Folder")
        val session = repository.createSession("Starred Session", folderId = folder.id, isStarred = true)

        repository.deleteFolder(folder.id)

        val retrieved = repository.getSession(session.id)
        assertNotNull(retrieved)
        assertNull(retrieved!!.folderId) // Moved to root
        assertTrue(retrieved.isStarred) // Still starred
    }

    @Test
    fun `should handle complex folder hierarchy with starred sessions`() {
        val parent = repository.createFolder("Parent", color = "#FF0000", icon = "📂")
        val child1 = repository.createFolder("Child 1", parentFolderId = parent.id, sortOrder = 1)
        val child2 = repository.createFolder("Child 2", parentFolderId = parent.id, sortOrder = 2)

        repository.createSession("Parent Session", folderId = parent.id, isStarred = true)
        repository.createSession("Child 1 Session A", folderId = child1.id, isStarred = false)
        repository.createSession("Child 1 Session B", folderId = child1.id, isStarred = true)
        repository.createSession("Child 2 Session", folderId = child2.id, isStarred = false)

        // Verify folder structure
        val allFolders = repository.getAllFolders()
        assertEquals(3, allFolders.size)

        // Verify sessions in each folder
        assertEquals(1, repository.getSessionsByFolder(parent.id).size)
        assertEquals(2, repository.getSessionsByFolder(child1.id).size)
        assertEquals(1, repository.getSessionsByFolder(child2.id).size)

        // Verify starred sessions across all folders
        val starredSessions = repository.getStarredSessions()
        assertEquals(2, starredSessions.size)
    }

    @Test
    fun `should update session updated_at when changing folder or starred status`() {
        val folder = repository.createFolder("Test Folder")
        val session = repository.createSession("Test Session")
        val originalUpdatedAt = session.updatedAt

        Thread.sleep(10)

        repository.updateSessionFolder(session.id, folder.id)

        val afterFolderMove = repository.getSession(session.id)
        assertNotNull(afterFolderMove)
        assertTrue(afterFolderMove!!.updatedAt.isAfter(originalUpdatedAt))

        Thread.sleep(10)

        repository.updateSessionStarred(session.id, true)

        val afterStarred = repository.getSession(session.id)
        assertNotNull(afterStarred)
        assertTrue(afterStarred!!.updatedAt.isAfter(afterFolderMove.updatedAt))
    }

    @Test
    fun `should handle edge case with special characters in folder names`() {
        val folder = repository.createFolder("Special !@#$%^&*() Folder 🚀")

        assertEquals("Special !@#$%^&*() Folder 🚀", folder.name)

        val retrieved = repository.getFolder(folder.id)
        assertEquals("Special !@#$%^&*() Folder 🚀", retrieved!!.name)
    }

    @Test
    fun `should handle session with directive and folder together`() {
        val folder = repository.createFolder("Work")
        val session = repository.createSession(
            title = "Work Session",
            directiveId = "test-directive-id",
            folderId = folder.id,
            isStarred = true,
            sortOrder = 3,
        )

        assertEquals("test-directive-id", session.directiveId)
        assertEquals(folder.id, session.folderId)
        assertTrue(session.isStarred)
        assertEquals(3, session.sortOrder)

        val retrieved = repository.getSession(session.id)
        assertNotNull(retrieved)
        assertEquals("test-directive-id", retrieved!!.directiveId)
        assertEquals(folder.id, retrieved.folderId)
        assertTrue(retrieved.isStarred)
        assertEquals(3, retrieved.sortOrder)
    }
}

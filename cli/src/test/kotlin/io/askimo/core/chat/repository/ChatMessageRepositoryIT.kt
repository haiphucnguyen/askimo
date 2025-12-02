/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.repository

import io.askimo.core.chat.domain.ChatMessage
import io.askimo.core.chat.domain.ChatSession
import io.askimo.core.context.MessageRole
import io.askimo.core.db.DatabaseManager
import io.askimo.core.util.AskimoHome
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDateTime

class ChatMessageRepositoryIT {

    private lateinit var testSession: ChatSession

    @BeforeEach
    fun setUp() {
        testSession = sessionRepository.createSession(ChatSession(id = "", title = "Test Session"))
    }

    @AfterEach
    fun tearDown() {
        if (::testSession.isInitialized) {
            sessionRepository.deleteSession(testSession.id)
        }
    }

    companion object {
        private lateinit var testBaseScope: AskimoHome.TestBaseScope
        private lateinit var databaseManager: DatabaseManager
        private lateinit var messageRepository: ChatMessageRepository
        private lateinit var sessionRepository: ChatSessionRepository

        @JvmStatic
        @BeforeAll
        fun setUpClass(@TempDir tempDir: Path) {
            testBaseScope = AskimoHome.withTestBase(tempDir)

            databaseManager = DatabaseManager.getTestInstance(this)

            // Get singleton repositories from DatabaseManager
            sessionRepository = databaseManager.getChatSessionRepository()
            messageRepository = databaseManager.getChatMessageRepository()
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
    fun `should add and retrieve messages for a session`() {
        val message1 = messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "Hello",
            ),
        )
        val message2 = messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.ASSISTANT,
                content = "Hi there!",
            ),
        )

        val messages = messageRepository.getMessages(testSession.id)

        assertEquals(2, messages.size)
        assertEquals(message1.id, messages[0].id)
        assertEquals(message2.id, messages[1].id)
    }

    @Test
    fun `should retrieve recent messages in chronological order`() {
        repeat(25) { i ->
            messageRepository.addMessage(
                ChatMessage(
                    id = "",
                    sessionId = testSession.id,
                    role = MessageRole.USER,
                    content = "Message $i",
                ),
            )
        }

        val recentMessages = messageRepository.getRecentMessages(testSession.id, limit = 20)

        assertEquals(20, recentMessages.size)
        assertEquals("Message 5", recentMessages[0].content)
        assertEquals("Message 6", recentMessages[1].content)
        assertEquals("Message 24", recentMessages[19].content)
    }

    @Test
    fun `should count messages correctly`() {
        assertEquals(0, messageRepository.getMessageCount(testSession.id))

        messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "Message 1",
            ),
        )

        assertEquals(1, messageRepository.getMessageCount(testSession.id))

        messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.ASSISTANT,
                content = "Message 2",
            ),
        )

        assertEquals(2, messageRepository.getMessageCount(testSession.id))
    }

    @Test
    fun `should retrieve messages after specific message`() {
        val message1 = messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "First",
            ),
        )
        val message2 = messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.ASSISTANT,
                content = "Second",
            ),
        )
        val message3 = messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "Third",
            ),
        )
        val message4 = messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.ASSISTANT,
                content = "Fourth",
            ),
        )

        val messagesAfter = messageRepository.getMessagesAfter(testSession.id, message1.id, limit = 10)

        assertEquals(3, messagesAfter.size)
        assertEquals(message2.id, messagesAfter[0].id)
        assertEquals(message3.id, messagesAfter[1].id)
        assertEquals(message4.id, messagesAfter[2].id)
    }

    @Test
    fun `should handle different message roles correctly`() {
        messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "User message",
            ),
        )
        messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.ASSISTANT,
                content = "Assistant message",
            ),
        )
        messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.SYSTEM,
                content = "System message",
            ),
        )

        val messages = messageRepository.getMessages(testSession.id)

        assertEquals(3, messages.size)
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals(MessageRole.ASSISTANT, messages[1].role)
        assertEquals(MessageRole.SYSTEM, messages[2].role)
    }

    @Test
    fun `should handle empty messages list`() {
        val messages = messageRepository.getMessages(testSession.id)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `should limit recent messages correctly`() {
        repeat(10) { i ->
            messageRepository.addMessage(
                ChatMessage(
                    id = "",
                    sessionId = testSession.id,
                    role = MessageRole.USER,
                    content = "Message $i",
                ),
            )
        }

        val recentMessages = messageRepository.getRecentMessages(testSession.id, limit = 5)

        assertEquals(5, recentMessages.size)
        assertEquals("Message 5", recentMessages[0].content)
        assertEquals("Message 9", recentMessages[4].content)
    }

    @Test
    fun `should search messages by content`() {
        messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "Hello world",
            ),
        )
        messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "Goodbye world",
            ),
        )
        messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "Something else",
            ),
        )

        val results = messageRepository.searchMessages(testSession.id, "world")

        assertEquals(2, results.size)
        assertTrue(results.all { it.content.contains("world") })
    }

    @Test
    fun `should mark message as outdated`() {
        val message = messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "Message",
            ),
        )

        val marked = messageRepository.markMessageAsOutdated(message.id)

        assertEquals(1, marked)
        val messages = messageRepository.getMessages(testSession.id)
        assertTrue(messages[0].isOutdated)
    }

    @Test
    fun `should mark messages as outdated after specific message`() {
        val message1 = messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "First",
            ),
        )
        messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.ASSISTANT,
                content = "Second",
            ),
        )
        messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "Third",
            ),
        )

        val marked = messageRepository.markMessagesAsOutdatedAfter(testSession.id, message1.id)

        assertEquals(2, marked)
        val messages = messageRepository.getMessages(testSession.id)
        assertEquals(false, messages[0].isOutdated) // First message not marked
        assertEquals(true, messages[1].isOutdated) // Second message marked
        assertEquals(true, messages[2].isOutdated) // Third message marked
    }

    @Test
    fun `should get only active messages`() {
        messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "Active 1",
            ),
        )
        val outdatedMessage = messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.ASSISTANT,
                content = "Outdated",
            ),
        )
        messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "Active 2",
            ),
        )

        messageRepository.markMessageAsOutdated(outdatedMessage.id)

        val activeMessages = messageRepository.getActiveMessages(testSession.id)

        assertEquals(2, activeMessages.size)
        assertEquals("Active 1", activeMessages[0].content)
        assertEquals("Active 2", activeMessages[1].content)
    }

    @Test
    fun `should get recent active messages with database filtering`() {
        repeat(30) { i ->
            val message = messageRepository.addMessage(
                ChatMessage(
                    id = "",
                    sessionId = testSession.id,
                    role = MessageRole.USER,
                    content = "Message $i",
                ),
            )
            // Mark every other message as outdated
            if (i % 2 == 0) {
                messageRepository.markMessageAsOutdated(message.id)
            }
        }

        val activeMessages = messageRepository.getRecentActiveMessages(testSession.id, limit = 10)

        assertEquals(10, activeMessages.size)
        // All should be active (odd numbered)
        assertTrue(activeMessages.all { !it.isOutdated })
    }

    @Test
    fun `should paginate messages forward from start`() {
        repeat(5) { i ->
            messageRepository.addMessage(
                ChatMessage(
                    id = "",
                    sessionId = testSession.id,
                    role = MessageRole.USER,
                    content = "Message $i",
                ),
            )
        }

        val (messages, nextCursor) = messageRepository.getMessagesPaginated(
            sessionId = testSession.id,
            limit = 3,
            cursor = null,
            direction = PaginationDirection.FORWARD,
        )

        assertEquals(3, messages.size)
        assertEquals("Message 0", messages[0].content)
        assertEquals("Message 1", messages[1].content)
        assertEquals("Message 2", messages[2].content)
        assertNotNull(nextCursor)
    }

    @Test
    fun `should paginate messages backward from end`() {
        repeat(5) { i ->
            messageRepository.addMessage(
                ChatMessage(
                    id = "",
                    sessionId = testSession.id,
                    role = MessageRole.USER,
                    content = "Message $i",
                ),
            )
        }

        val (messages, nextCursor) = messageRepository.getMessagesPaginated(
            sessionId = testSession.id,
            limit = 3,
            cursor = null,
            direction = PaginationDirection.BACKWARD,
        )

        assertEquals(3, messages.size)
        assertEquals("Message 2", messages[0].content)
        assertEquals("Message 3", messages[1].content)
        assertEquals("Message 4", messages[2].content)
        assertNotNull(nextCursor)
    }

    @Test
    fun `should return null cursor when no more messages`() {
        repeat(3) { i ->
            messageRepository.addMessage(
                ChatMessage(
                    id = "",
                    sessionId = testSession.id,
                    role = MessageRole.USER,
                    content = "Message $i",
                ),
            )
        }

        val (messages, nextCursor) = messageRepository.getMessagesPaginated(
            sessionId = testSession.id,
            limit = 5,
            cursor = null,
            direction = PaginationDirection.FORWARD,
        )

        assertEquals(3, messages.size)
        assertEquals(null, nextCursor)
    }

    @Test
    fun `should delete all messages for a session`() {
        messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.USER,
                content = "Message 1",
            ),
        )
        messageRepository.addMessage(
            ChatMessage(
                id = "",
                sessionId = testSession.id,
                role = MessageRole.ASSISTANT,
                content = "Message 2",
            ),
        )

        val deleted = messageRepository.deleteMessagesBySession(testSession.id)

        assertEquals(2, deleted)
        val messages = messageRepository.getMessages(testSession.id)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `should paginate through entire message history forward`() {
        repeat(10) { i ->
            messageRepository.addMessage(
                ChatMessage(
                    id = "",
                    sessionId = testSession.id,
                    role = MessageRole.USER,
                    content = "Message $i",
                ),
            )
        }

        val allMessages = mutableListOf<ChatMessage>()
        var cursor: LocalDateTime? = null

        do {
            val (messages, nextCursor) = messageRepository.getMessagesPaginated(
                sessionId = testSession.id,
                limit = 3,
                cursor = cursor,
                direction = PaginationDirection.FORWARD,
            )
            allMessages.addAll(messages)
            cursor = nextCursor
        } while (cursor != null)

        assertEquals(10, allMessages.size)
        assertEquals("Message 0", allMessages.first().content)
        assertEquals("Message 9", allMessages.last().content)
    }

    @Test
    fun `should not duplicate messages across pages`() {
        repeat(10) { i ->
            messageRepository.addMessage(
                ChatMessage(
                    id = "",
                    sessionId = testSession.id,
                    role = MessageRole.USER,
                    content = "Message $i",
                ),
            )
        }

        val page1 = messageRepository.getMessagesPaginated(
            sessionId = testSession.id,
            limit = 5,
            cursor = null,
            direction = PaginationDirection.FORWARD,
        )

        val page2 = messageRepository.getMessagesPaginated(
            sessionId = testSession.id,
            limit = 5,
            cursor = page1.second,
            direction = PaginationDirection.FORWARD,
        )

        val allMessageIds = (page1.first + page2.first).map { it.id }
        assertEquals(allMessageIds.size, allMessageIds.toSet().size) // No duplicates
    }
}

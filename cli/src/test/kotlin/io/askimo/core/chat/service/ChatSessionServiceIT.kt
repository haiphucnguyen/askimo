/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.service

import io.askimo.core.chat.domain.ChatMessage
import io.askimo.core.chat.domain.ChatSession
import io.askimo.core.chat.domain.ConversationSummary
import io.askimo.core.chat.repository.ChatFolderRepository
import io.askimo.core.chat.repository.ChatMessageRepository
import io.askimo.core.chat.repository.ChatSessionRepository
import io.askimo.core.chat.repository.ConversationSummaryRepository
import io.askimo.core.context.AppContextFactory
import io.askimo.core.context.ExecutionMode
import io.askimo.core.context.MessageRole
import io.askimo.core.db.DatabaseManager
import io.askimo.core.util.AskimoHome
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ChatSessionServiceIT {

    @AfterEach
    fun tearDown() {
        sessionRepository.deleteAll()
        folderRepository.deleteAll()
    }

    companion object {
        private lateinit var testBaseScope: AskimoHome.TestBaseScope
        private lateinit var databaseManager: DatabaseManager
        private lateinit var service: ChatSessionService
        private lateinit var sessionRepository: ChatSessionRepository
        private lateinit var messageRepository: ChatMessageRepository
        private lateinit var summaryRepository: ConversationSummaryRepository
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

            val appContext = AppContextFactory.createAppContext(mode = ExecutionMode.DESKTOP)

            service = ChatSessionService(
                sessionRepository = sessionRepository,
                messageRepository = messageRepository,
                conversationSummaryRepository = summaryRepository,
                folderRepository = folderRepository,
                appContext = appContext,
            )
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
    fun `should delete session with all related data through service`() {
        val session = sessionRepository.createSession(ChatSession(id = "", title = "Test Session"))

        // Add messages
        service.addMessage(
            ChatMessage(
                id = "",
                sessionId = session.id,
                role = MessageRole.USER,
                content = "Message 1",
            ),
        )
        service.addMessage(
            ChatMessage(
                id = "",
                sessionId = session.id,
                role = MessageRole.ASSISTANT,
                content = "Message 2",
            ),
        )

        // Add summary
        val message = service.addMessage(
            ChatMessage(
                id = "",
                sessionId = session.id,
                role = MessageRole.USER,
                content = "Last message",
            ),
        )
        service.saveSummary(
            ConversationSummary(
                sessionId = session.id,
                keyFacts = mapOf("test" to "data"),
                mainTopics = listOf("testing"),
                recentContext = "Test context",
                lastSummarizedMessageId = message.id,
            ),
        )

        // Delete through service (should delete session, messages, and summary)
        val deleted = service.deleteSession(session.id)

        Assertions.assertTrue(deleted)
        Assertions.assertNull(sessionRepository.getSession(session.id))
        Assertions.assertEquals(0, messageRepository.getMessageCount(session.id))
        Assertions.assertNull(summaryRepository.getConversationSummary(session.id))
    }

    @Test
    fun `should add message and update session timestamp through service`() {
        val session = sessionRepository.createSession(ChatSession(id = "", title = "Test"))
        val originalUpdatedAt = session.updatedAt

        Thread.sleep(10)

        service.addMessage(
            ChatMessage(
                id = "",
                sessionId = session.id,
                role = MessageRole.USER,
                content = "New message",
            ),
        )

        val updated = sessionRepository.getSession(session.id)
        Assertions.assertTrue(updated!!.updatedAt.isAfter(originalUpdatedAt))
    }

    @Test
    fun `should delete folder and move sessions through service`() {
        val folder = service.createFolder("Test Folder")
        val session1 = sessionRepository.createSession(
            ChatSession(id = "", title = "Session 1", folderId = folder.id),
        )
        val session2 = sessionRepository.createSession(
            ChatSession(id = "", title = "Session 2", folderId = folder.id),
        )

        // Service should coordinate moving sessions before deleting folder
        service.deleteFolder(folder.id)

        // Folder should be deleted
        Assertions.assertNull(folderRepository.getFolder(folder.id))

        // Sessions should be moved to root
        val retrievedSession1 = sessionRepository.getSession(session1.id)
        val retrievedSession2 = sessionRepository.getSession(session2.id)
        Assertions.assertNull(retrievedSession1!!.folderId)
        Assertions.assertNull(retrievedSession2!!.folderId)
    }

    @Test
    fun `should delete folder and move child folders through service`() {
        val parent = service.createFolder("Parent")
        val child1 = service.createFolder("Child 1", parentFolderId = parent.id)
        val child2 = service.createFolder("Child 2", parentFolderId = parent.id)

        service.deleteFolder(parent.id)

        Assertions.assertNull(folderRepository.getFolder(parent.id))

        val retrievedChild1 = folderRepository.getFolder(child1.id)
        val retrievedChild2 = folderRepository.getFolder(child2.id)
        assertNotNull(retrievedChild1)
        assertNotNull(retrievedChild2)
        Assertions.assertNull(retrievedChild1!!.parentFolderId)
        Assertions.assertNull(retrievedChild2!!.parentFolderId)
    }

    @Test
    fun `should get messages through service`() {
        val session = sessionRepository.createSession(ChatSession(id = "", title = "Test"))

        service.addMessage(
            ChatMessage(
                id = "",
                sessionId = session.id,
                role = MessageRole.USER,
                content = "Message 1",
            ),
        )
        service.addMessage(
            ChatMessage(
                id = "",
                sessionId = session.id,
                role = MessageRole.ASSISTANT,
                content = "Message 2",
            ),
        )

        val messages = service.getMessages(session.id)

        Assertions.assertEquals(2, messages.size)
        Assertions.assertEquals("Message 1", messages[0].content)
        Assertions.assertEquals("Message 2", messages[1].content)
    }

    @Test
    fun `should get recent active messages through service`() {
        val session = sessionRepository.createSession(ChatSession(id = "", title = "Test"))

        repeat(30) { i ->
            val message = service.addMessage(
                ChatMessage(
                    id = "",
                    sessionId = session.id,
                    role = MessageRole.USER,
                    content = "Message $i",
                ),
            )
            // Mark some as outdated
            if (i % 3 == 0) {
                service.markMessageAsOutdated(message.id)
            }
        }

        val activeMessages = service.getRecentActiveMessages(session.id, limit = 10)

        Assertions.assertEquals(10, activeMessages.size)
        Assertions.assertTrue(activeMessages.all { !it.isOutdated })
    }

    @Test
    fun `should coordinate summary operations through service`() {
        val session = sessionRepository.createSession(ChatSession(id = "", title = "Test"))
        val message = service.addMessage(
            ChatMessage(
                id = "",
                sessionId = session.id,
                role = MessageRole.USER,
                content = "Test message",
            ),
        )

        val summary = ConversationSummary(
            sessionId = session.id,
            keyFacts = mapOf("key" to "value"),
            mainTopics = listOf("topic"),
            recentContext = "Context",
            lastSummarizedMessageId = message.id,
        )

        service.saveSummary(summary)

        val retrieved = service.getConversationSummary(session.id)
        assertNotNull(retrieved)
        Assertions.assertEquals(session.id, retrieved!!.sessionId)
    }

    @Test
    fun `should get sessions by folder through service`() {
        val folder = service.createFolder("Work")

        val session1 = sessionRepository.createSession(
            ChatSession(id = "", title = "Work Session 1", folderId = folder.id),
        )
        val session2 = sessionRepository.createSession(
            ChatSession(id = "", title = "Work Session 2", folderId = folder.id),
        )
        sessionRepository.createSession(
            ChatSession(id = "", title = "Personal Session"),
        )

        val folderSessions = service.getSessionsByFolder(folder.id)

        Assertions.assertEquals(2, folderSessions.size)
        Assertions.assertTrue(folderSessions.any { it.id == session1.id })
        Assertions.assertTrue(folderSessions.any { it.id == session2.id })
    }

    @Test
    fun `should manage starred sessions through service`() {
        val session = sessionRepository.createSession(ChatSession(id = "", title = "Test"))

        service.updateSessionStarred(session.id, true)

        val starredSessions = service.getStarredSessions()
        Assertions.assertEquals(1, starredSessions.size)
        Assertions.assertEquals(session.id, starredSessions[0].id)
    }

    @Test
    fun `should rename session through service`() {
        val session = sessionRepository.createSession(ChatSession(id = "", title = "Old Title"))

        service.renameTitle(session.id, "New Title")

        val updated = service.getSessionById(session.id)
        Assertions.assertEquals("New Title", updated!!.title)
    }

    @Test
    fun `should mark messages as outdated through service`() {
        val session = sessionRepository.createSession(ChatSession(id = "", title = "Test"))

        val message1 = service.addMessage(
            ChatMessage(
                id = "",
                sessionId = session.id,
                role = MessageRole.USER,
                content = "Message 1",
            ),
        )
        service.addMessage(
            ChatMessage(
                id = "",
                sessionId = session.id,
                role = MessageRole.ASSISTANT,
                content = "Message 2",
            ),
        )
        service.addMessage(
            ChatMessage(
                id = "",
                sessionId = session.id,
                role = MessageRole.USER,
                content = "Message 3",
            ),
        )

        service.markMessagesAsOutdatedAfter(session.id, message1.id)

        val activeMessages = service.getActiveMessages(session.id)
        Assertions.assertEquals(1, activeMessages.size)
        Assertions.assertEquals("Message 1", activeMessages[0].content)
    }
}

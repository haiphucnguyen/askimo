/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.service

import io.askimo.core.chat.domain.ChatMessage
import io.askimo.core.chat.domain.ChatSession
import io.askimo.core.chat.repository.ChatFolderRepository
import io.askimo.core.chat.repository.ChatMessageRepository
import io.askimo.core.chat.repository.ChatSessionRepository
import io.askimo.core.context.AppContextFactory
import io.askimo.core.context.ExecutionMode
import io.askimo.core.context.MessageRole
import io.askimo.core.db.DatabaseManager
import io.askimo.core.util.AskimoHome
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDateTime

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
        private lateinit var folderRepository: ChatFolderRepository

        @JvmStatic
        @BeforeAll
        fun setUpClass(@TempDir tempDir: Path) {
            testBaseScope = AskimoHome.withTestBase(tempDir)

            databaseManager = DatabaseManager.getInMemoryTestInstance(this)

            sessionRepository = databaseManager.getChatSessionRepository()
            messageRepository = databaseManager.getChatMessageRepository()
            folderRepository = databaseManager.getChatFolderRepository()

            val appContext = AppContextFactory.createAppContext(mode = ExecutionMode.DESKTOP)

            service = ChatSessionService(
                sessionRepository = sessionRepository,
                messageRepository = messageRepository,
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
            // Reset the singleton to close any file-based database that might have been created
            DatabaseManager.reset()
            if (::testBaseScope.isInitialized) {
                testBaseScope.close()
            }
        }
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
        assertTrue(updated!!.updatedAt.isAfter(originalUpdatedAt))
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
        assertNull(folderRepository.getFolder(folder.id))

        // Sessions should be moved to root
        val retrievedSession1 = sessionRepository.getSession(session1.id)
        val retrievedSession2 = sessionRepository.getSession(session2.id)
        assertNull(retrievedSession1!!.folderId)
        assertNull(retrievedSession2!!.folderId)
    }

    @Test
    fun `should delete folder and move child folders through service`() {
        val parent = service.createFolder("Parent")
        val child1 = service.createFolder("Child 1", parentFolderId = parent.id)
        val child2 = service.createFolder("Child 2", parentFolderId = parent.id)

        service.deleteFolder(parent.id)

        assertNull(folderRepository.getFolder(parent.id))

        val retrievedChild1 = folderRepository.getFolder(child1.id)
        val retrievedChild2 = folderRepository.getFolder(child2.id)
        assertNotNull(retrievedChild1)
        assertNotNull(retrievedChild2)
        assertNull(retrievedChild1.parentFolderId)
        assertNull(retrievedChild2.parentFolderId)
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

        assertEquals(2, messages.size)
        assertEquals("Message 1", messages[0].content)
        assertEquals("Message 2", messages[1].content)
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

        assertEquals(10, activeMessages.size)
        assertTrue(activeMessages.all { !it.isOutdated })
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

        assertEquals(2, folderSessions.size)
        assertTrue(folderSessions.any { it.id == session1.id })
        assertTrue(folderSessions.any { it.id == session2.id })
    }

    @Test
    fun `should manage starred sessions through service`() {
        val session = sessionRepository.createSession(ChatSession(id = "", title = "Test"))

        service.updateSessionStarred(session.id, true)

        val starredSessions = service.getStarredSessions()
        assertEquals(1, starredSessions.size)
        assertEquals(session.id, starredSessions[0].id)
    }

    @Test
    fun `should rename session through service`() {
        val session = sessionRepository.createSession(ChatSession(id = "", title = "Old Title"))

        service.renameTitle(session.id, "New Title")

        val updated = service.getSessionById(session.id)
        assertEquals("New Title", updated!!.title)
    }

    @Test
    fun `should mark messages as outdated through service`() {
        val session = sessionRepository.createSession(ChatSession(id = "", title = "Test"))

        val baseTime = LocalDateTime.now()

        val message1 = service.addMessage(
            ChatMessage(
                id = "",
                sessionId = session.id,
                role = MessageRole.USER,
                content = "Message 1",
                createdAt = baseTime,
            ),
        )
        service.addMessage(
            ChatMessage(
                id = "",
                sessionId = session.id,
                role = MessageRole.ASSISTANT,
                content = "Message 2",
                createdAt = baseTime.plusSeconds(1),
            ),
        )
        service.addMessage(
            ChatMessage(
                id = "",
                sessionId = session.id,
                role = MessageRole.USER,
                content = "Message 3",
                createdAt = baseTime.plusSeconds(2),
            ),
        )

        service.markMessagesAsOutdatedAfter(session.id, message1.id)

        val activeMessages = service.getActiveMessages(session.id)
        assertEquals(1, activeMessages.size)
        assertEquals("Message 1", activeMessages[0].content)
    }
}

/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.repository

import io.askimo.core.chat.domain.ChatSession
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

class ChatFolderRepositoryIT {

    @AfterEach
    fun tearDown() {
        sessionRepository.deleteAll()
        folderRepository.deleteAll()
    }

    companion object {
        private lateinit var testBaseScope: AskimoHome.TestBaseScope
        private lateinit var databaseManager: DatabaseManager
        private lateinit var folderRepository: ChatFolderRepository
        private lateinit var sessionRepository: ChatSessionRepository

        @JvmStatic
        @BeforeAll
        fun setUpClass(@TempDir tempDir: Path) {
            testBaseScope = AskimoHome.withTestBase(tempDir)

            databaseManager = DatabaseManager.getTestInstance(this)

            folderRepository = databaseManager.getChatFolderRepository()
            sessionRepository = databaseManager.getChatSessionRepository()
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
    fun `should create and retrieve a folder`() {
        val folder = folderRepository.createFolder(
            name = "Work Projects",
        )

        assertNotNull(folder.id)
        Assertions.assertEquals("Work Projects", folder.name)
        assertNotNull(folder.createdAt)
        assertNotNull(folder.updatedAt)

        val retrieved = folderRepository.getFolder(folder.id)
        assertNotNull(retrieved)
        Assertions.assertEquals(folder.id, retrieved.id)
        Assertions.assertEquals(folder.name, retrieved.name)
    }

    @Test
    fun `should create folder with all properties`() {
        val folder = folderRepository.createFolder(
            name = "Important",
            color = "#FF0000",
            icon = "star",
            sortOrder = 5,
        )

        val retrieved = folderRepository.getFolder(folder.id)
        assertNotNull(retrieved)
        Assertions.assertEquals("Important", retrieved!!.name)
        Assertions.assertEquals("#FF0000", retrieved.color)
        Assertions.assertEquals("star", retrieved.icon)
        Assertions.assertEquals(5, retrieved.sortOrder)
    }

    @Test
    fun `should create nested folders`() {
        val parent = folderRepository.createFolder(name = "Parent")
        val child = folderRepository.createFolder(
            name = "Child",
            parentFolderId = parent.id,
        )

        val retrieved = folderRepository.getFolder(child.id)
        assertNotNull(retrieved)
        Assertions.assertEquals(parent.id, retrieved!!.parentFolderId)
    }

    @Test
    fun `should retrieve all folders ordered by sort order and name`() {
        folderRepository.createFolder(name = "C Folder", sortOrder = 1)
        folderRepository.createFolder(name = "A Folder", sortOrder = 1)
        folderRepository.createFolder(name = "B Folder", sortOrder = 2)

        val folders = folderRepository.getAllFolders()

        Assertions.assertEquals(3, folders.size)
        Assertions.assertEquals("A Folder", folders[0].name)
        Assertions.assertEquals("C Folder", folders[1].name)
        Assertions.assertEquals("B Folder", folders[2].name)
    }

    @Test
    fun `should update folder name`() {
        val folder = folderRepository.createFolder(name = "Old Name")

        val updated = folderRepository.updateFolder(folder.id, name = "New Name")

        Assertions.assertTrue(updated)
        val retrieved = folderRepository.getFolder(folder.id)
        Assertions.assertEquals("New Name", retrieved!!.name)
    }

    @Test
    fun `should update folder properties`() {
        val folder = folderRepository.createFolder(name = "Test")

        folderRepository.updateFolder(
            folderId = folder.id,
            color = "#00FF00",
            icon = "folder",
            sortOrder = 10,
        )

        val retrieved = folderRepository.getFolder(folder.id)
        Assertions.assertEquals("#00FF00", retrieved!!.color)
        Assertions.assertEquals("folder", retrieved.icon)
        Assertions.assertEquals(10, retrieved.sortOrder)
    }

    @Test
    fun `should update folder parent`() {
        val parent1 = folderRepository.createFolder(name = "Parent 1")
        val parent2 = folderRepository.createFolder(name = "Parent 2")
        val child = folderRepository.createFolder(name = "Child", parentFolderId = parent1.id)

        val updated = folderRepository.updateFolder(child.id, parentFolderId = parent2.id)

        Assertions.assertTrue(updated)
        val retrieved = folderRepository.getFolder(child.id)
        Assertions.assertEquals(parent2.id, retrieved!!.parentFolderId)
    }

    @Test
    fun `should move folder to root`() {
        val parent = folderRepository.createFolder(name = "Parent")
        val child = folderRepository.createFolder(name = "Child", parentFolderId = parent.id)

        // Manually set parent to null in the database by moving to another parent then root
        val tempParent = folderRepository.createFolder(name = "Temp")
        folderRepository.updateFolder(child.id, parentFolderId = tempParent.id)

        // Now delete temp parent which should move child to root
        folderRepository.moveChildFoldersToRoot(tempParent.id)
        folderRepository.deleteFolder(tempParent.id)

        val retrieved = folderRepository.getFolder(child.id)
        Assertions.assertNull(retrieved!!.parentFolderId)
    }

    @Test
    fun `should return false when updating non-existent folder`() {
        val updated = folderRepository.updateFolder("non-existent-id", name = "New Name")
        Assertions.assertFalse(updated)
    }

    @Test
    fun `should return false when updating folder with no changes`() {
        val folder = folderRepository.createFolder(name = "Test")
        val updated = folderRepository.updateFolder(folder.id)
        Assertions.assertFalse(updated)
    }

    @Test
    fun `should delete folder successfully`() {
        val folder = folderRepository.createFolder(name = "To Delete")

        val deleted = folderRepository.deleteFolder(folder.id)

        Assertions.assertTrue(deleted)
        Assertions.assertNull(folderRepository.getFolder(folder.id))
    }

    @Test
    fun `should return false when deleting non-existent folder`() {
        val deleted = folderRepository.deleteFolder("non-existent-id")
        Assertions.assertFalse(deleted)
    }

    @Test
    fun `should move sessions to root when deleting folder`() {
        val folder = folderRepository.createFolder(name = "Project Folder")
        val session = sessionRepository.createSession(
            ChatSession(id = "", title = "Session", folderId = folder.id),
        )

        // Move sessions before deleting folder (coordinated by service layer)
        sessionRepository.moveSessionsToRoot(folder.id)
        folderRepository.deleteFolder(folder.id)

        val retrieved = sessionRepository.getSession(session.id)
        Assertions.assertNull(retrieved!!.folderId)
        Assertions.assertNull(folderRepository.getFolder(folder.id))
    }

    @Test
    fun `should move child folders to root when deleting parent folder`() {
        val parent = folderRepository.createFolder(name = "Parent")
        val child = folderRepository.createFolder(name = "Child", parentFolderId = parent.id)

        folderRepository.moveChildFoldersToRoot(parent.id)
        folderRepository.deleteFolder(parent.id)

        val retrievedChild = folderRepository.getFolder(child.id)
        assertNotNull(retrievedChild)
        Assertions.assertNull(retrievedChild!!.parentFolderId)
        Assertions.assertNull(folderRepository.getFolder(parent.id))
    }

    @Test
    fun `should return null for non-existent folder`() {
        val result = folderRepository.getFolder("non-existent-id")
        Assertions.assertNull(result)
    }

    @Test
    fun `should handle multiple levels of folder nesting`() {
        val level1 = folderRepository.createFolder(name = "Level 1")
        val level2 = folderRepository.createFolder(name = "Level 2", parentFolderId = level1.id)
        val level3 = folderRepository.createFolder(name = "Level 3", parentFolderId = level2.id)

        val retrieved = folderRepository.getFolder(level3.id)
        Assertions.assertEquals(level2.id, retrieved!!.parentFolderId)

        val retrievedLevel2 = folderRepository.getFolder(level2.id)
        Assertions.assertEquals(level1.id, retrievedLevel2!!.parentFolderId)
    }

    @Test
    fun `should support sessions in different folders`() {
        val folder1 = folderRepository.createFolder(name = "Folder 1")
        val folder2 = folderRepository.createFolder(name = "Folder 2")

        val session1 = sessionRepository.createSession(
            ChatSession(id = "", title = "Session 1", folderId = folder1.id),
        )
        val session2 = sessionRepository.createSession(
            ChatSession(id = "", title = "Session 2", folderId = folder2.id),
        )

        val folder1Sessions = sessionRepository.getSessionsByFolder(folder1.id)
        val folder2Sessions = sessionRepository.getSessionsByFolder(folder2.id)

        Assertions.assertEquals(1, folder1Sessions.size)
        Assertions.assertEquals(session1.id, folder1Sessions[0].id)

        Assertions.assertEquals(1, folder2Sessions.size)
        Assertions.assertEquals(session2.id, folder2Sessions[0].id)
    }

    @Test
    fun `should support sessions at root level (no folder)`() {
        val rootSession = sessionRepository.createSession(
            ChatSession(id = "", title = "Root Session"),
        )

        val rootSessions = sessionRepository.getSessionsByFolder(null)

        Assertions.assertEquals(1, rootSessions.size)
        Assertions.assertEquals(rootSession.id, rootSessions[0].id)
    }

    @Test
    fun `should move session between folders`() {
        val folder1 = folderRepository.createFolder(name = "Folder 1")
        val folder2 = folderRepository.createFolder(name = "Folder 2")

        val session = sessionRepository.createSession(
            ChatSession(id = "", title = "Session", folderId = folder1.id),
        )

        sessionRepository.updateSessionFolder(session.id, folder2.id)

        val retrieved = sessionRepository.getSession(session.id)
        Assertions.assertEquals(folder2.id, retrieved!!.folderId)
    }

    @Test
    fun `should move session to root level`() {
        val folder = folderRepository.createFolder(name = "Folder")
        val session = sessionRepository.createSession(
            ChatSession(id = "", title = "Session", folderId = folder.id),
        )

        sessionRepository.updateSessionFolder(session.id, null)

        val retrieved = sessionRepository.getSession(session.id)
        Assertions.assertNull(retrieved!!.folderId)
    }
}

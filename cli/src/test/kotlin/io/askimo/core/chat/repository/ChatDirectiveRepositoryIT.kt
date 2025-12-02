/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.repository

import io.askimo.core.chat.domain.ChatDirective
import io.askimo.core.db.DatabaseManager
import io.askimo.core.util.AskimoHome
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDateTime

class ChatDirectiveRepositoryIT {

    @AfterEach
    fun tearDown() {
        repository.list().forEach { directive ->
            repository.delete(directive.id)
        }
    }

    companion object {
        private lateinit var testBaseScope: AskimoHome.TestBaseScope
        private lateinit var databaseManager: DatabaseManager
        private lateinit var repository: ChatDirectiveRepository

        @JvmStatic
        @BeforeAll
        fun setUpClass(@TempDir tempDir: Path) {
            testBaseScope = AskimoHome.withTestBase(tempDir)

            databaseManager = DatabaseManager.getTestInstance(this)

            repository = databaseManager.getChatDirectiveRepository()
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
    fun `should save and retrieve a chat directive`() {
        val directive = ChatDirective(
            name = "concise-code",
            content = "Provide concise code examples without verbose explanations.",
            createdAt = LocalDateTime.now(),
        )

        val saved = repository.save(directive)

        Assertions.assertEquals(directive.id, saved.id)
        Assertions.assertEquals("concise-code", saved.name)
        Assertions.assertEquals("Provide concise code examples without verbose explanations.", saved.content)
        assertNotNull(saved.createdAt)

        val retrieved = repository.get(saved.id)
        assertNotNull(retrieved)
        Assertions.assertEquals(directive.id, retrieved.id)
        Assertions.assertEquals(directive.name, retrieved.name)
        Assertions.assertEquals(directive.content, retrieved.content)
    }

    @Test
    fun `should return null for non-existent directive`() {
        val result = repository.get("non-existent")

        assertNull(result)
    }

    @Test
    fun `should update existing directive on save with same id`() {
        val original = ChatDirective(
            name = "test-directive",
            content = "Original content",
        )
        val saved = repository.save(original)

        val updated = saved.copy(
            name = "updated-name",
            content = "Updated content",
        )
        repository.save(updated)

        val retrieved = repository.get(saved.id)
        assertNotNull(retrieved)
        Assertions.assertEquals("updated-name", retrieved.name)
        Assertions.assertEquals("Updated content", retrieved.content)
    }

    @Test
    fun `should list all directives ordered by name`() {
        repository.save(ChatDirective(name = "zebra", content = "Last alphabetically"))
        repository.save(ChatDirective(name = "alpha", content = "First alphabetically"))
        repository.save(ChatDirective(name = "middle", content = "Middle alphabetically"))

        val directives = repository.list()

        Assertions.assertEquals(3, directives.size)
        Assertions.assertEquals("alpha", directives[0].name)
        Assertions.assertEquals("middle", directives[1].name)
        Assertions.assertEquals("zebra", directives[2].name)
    }

    @Test
    fun `should update existing directive`() {
        val directive = ChatDirective(name = "test", content = "Original content")
        val saved = repository.save(directive)

        val updated = saved.copy(name = "updated-test", content = "Updated content")
        val result = repository.update(updated)

        Assertions.assertTrue(result)

        val retrieved = repository.get(saved.id)
        assertNotNull(retrieved)
        Assertions.assertEquals("updated-test", retrieved.name)
        Assertions.assertEquals("Updated content", retrieved.content)
    }

    @Test
    fun `should return false when updating non-existent directive`() {
        val directive = ChatDirective(name = "non-existent", content = "Some content")
        val result = repository.update(directive)

        Assertions.assertFalse(result)
    }

    @Test
    fun `should delete existing directive`() {
        val saved = repository.save(ChatDirective(name = "to-delete", content = "Content"))

        Assertions.assertTrue(repository.exists(saved.id))

        val deleted = repository.delete(saved.id)

        Assertions.assertTrue(deleted)
        Assertions.assertFalse(repository.exists(saved.id))
        assertNull(repository.get(saved.id))
    }

    @Test
    fun `should return false when deleting non-existent directive`() {
        val deleted = repository.delete("non-existent-id")

        Assertions.assertFalse(deleted)
    }

    @Test
    fun `should check if directive exists`() {
        val directive = ChatDirective(name = "test-exists", content = "Content")

        Assertions.assertFalse(repository.exists(directive.id))

        val saved = repository.save(directive)

        Assertions.assertTrue(repository.exists(saved.id))
    }

    @Test
    fun `should get multiple directives by ids`() {
        val saved1 = repository.save(ChatDirective(name = "directive1", content = "Content 1"))
        val saved2 = repository.save(ChatDirective(name = "directive2", content = "Content 2"))
        val saved3 = repository.save(ChatDirective(name = "directive3", content = "Content 3"))

        val directives = repository.getByIds(listOf(saved1.id, saved3.id, "non-existent"))

        Assertions.assertEquals(2, directives.size)
        Assertions.assertEquals("directive1", directives[0].name)
        Assertions.assertEquals("directive3", directives[1].name)
    }

    @Test
    fun `should return empty list when getting directives with empty ids list`() {
        repository.save(ChatDirective(name = "directive1", content = "Content 1"))

        val directives = repository.getByIds(emptyList())

        Assertions.assertTrue(directives.isEmpty())
    }

    @Test
    fun `should get multiple directives by names`() {
        repository.save(ChatDirective(name = "directive1", content = "Content 1"))
        repository.save(ChatDirective(name = "directive2", content = "Content 2"))
        repository.save(ChatDirective(name = "directive3", content = "Content 3"))

        val directives = repository.getByNames(listOf("directive1", "directive3", "non-existent"))

        Assertions.assertEquals(2, directives.size)
        Assertions.assertEquals("directive1", directives[0].name)
        Assertions.assertEquals("directive3", directives[1].name)
    }

    @Test
    fun `should return empty list when getting directives with empty names list`() {
        repository.save(ChatDirective(name = "directive1", content = "Content 1"))

        val directives = repository.getByNames(emptyList())

        Assertions.assertTrue(directives.isEmpty())
    }

    @Test
    fun `should handle special characters in directive content`() {
        val specialContent = """
            Special characters: !@#$%^&*()
            Unicode: émoji 🎉
            Lists:
                - Item 1
                - Item 2
            Code: `inline` and ```block```
        """.trimIndent()

        val directive = ChatDirective(name = "special-chars", content = specialContent)
        val saved = repository.save(directive)

        val retrieved = repository.get(saved.id)
        assertNotNull(retrieved)
        Assertions.assertEquals(specialContent, retrieved.content)
    }

    @Test
    fun `should enforce maximum name length`() {
        val longName = "a".repeat(DIRECTIVE_NAME_MAX_LENGTH + 1)
        val directive = ChatDirective(name = longName, content = "Content")

        val exception = assertThrows<IllegalArgumentException> {
            repository.save(directive)
        }

        Assertions.assertTrue(exception.message!!.contains("cannot exceed ${DIRECTIVE_NAME_MAX_LENGTH} characters"))
    }

    @Test
    fun `should enforce maximum content length`() {
        val longContent = "a".repeat(DIRECTIVE_CONTENT_MAX_LENGTH + 1)
        val directive = ChatDirective(name = "test", content = longContent)

        val exception = assertThrows<IllegalArgumentException> {
            repository.save(directive)
        }

        Assertions.assertTrue(exception.message!!.contains("cannot exceed ${DIRECTIVE_CONTENT_MAX_LENGTH} characters"))
    }

    @Test
    fun `should allow maximum length name`() {
        val maxName = "a".repeat(DIRECTIVE_NAME_MAX_LENGTH)
        val directive = ChatDirective(name = maxName, content = "Content")

        Assertions.assertDoesNotThrow {
            val saved = repository.save(directive)

            val retrieved = repository.get(saved.id)
            assertNotNull(retrieved)
            Assertions.assertEquals(maxName, retrieved.name)
        }
    }

    @Test
    fun `should allow maximum length content`() {
        val maxContent = "a".repeat(DIRECTIVE_CONTENT_MAX_LENGTH)
        val directive = ChatDirective(name = "test", content = maxContent)

        Assertions.assertDoesNotThrow {
            val saved = repository.save(directive)

            val retrieved = repository.get(saved.id)
            assertNotNull(retrieved)
            Assertions.assertEquals(maxContent, retrieved.content)
        }
    }
}

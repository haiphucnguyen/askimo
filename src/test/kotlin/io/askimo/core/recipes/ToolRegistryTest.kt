/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.recipes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ToolRegistryTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `defaults include git and io tools with expected keys`() {
        val reg = ToolRegistry.defaults()
        val keys = reg.keys()
        assertTrue("stagedDiff" in keys)
        assertTrue("status" in keys)
        assertTrue("branch" in keys)
        assertTrue("commit" in keys)
        assertTrue("writeFile" in keys)
    }

    @Test
    fun `allow filter restricts available tools`() {
        val reg = ToolRegistry.defaults(allow = setOf("writeFile"))
        val keys = reg.keys().sorted()
        assertEquals(listOf("writeFile"), keys)
    }

    @Test
    fun `invoke works for IoTools writeFile using array args`() {
        val reg = ToolRegistry.defaults()
        val target = tempDir.resolve("out.txt").toAbsolutePath().toString()

        val result = reg.invoke("writeFile", arrayOf(target, "hello world")) as String
        assertTrue(result.contains("wrote:"))
        assertTrue(Files.exists(Path.of(target)))
        assertEquals("hello world", Files.readString(Path.of(target)))
    }

    @Test
    fun `invoke on missing tool throws error with available list`() {
        val reg = ToolRegistry.defaults()
        val ex =
            assertThrows(IllegalStateException::class.java) {
                reg.invoke("missing", null)
            }
        // Error message should include available keys
        assertContains(ex.message ?: "", "Tool not found or not allowed: missing")
        assertContains(ex.message ?: "", "Available:")
        assertContains(ex.message ?: "", "writeFile")
        assertContains(ex.message ?: "", "status")
    }
}

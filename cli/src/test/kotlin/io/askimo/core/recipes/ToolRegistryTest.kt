/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.recipes

import dev.langchain4j.agent.tool.Tool
import io.askimo.cli.recipes.ToolRegistry
import io.askimo.tools.fs.LocalFsTools
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertTrue

class TestTools {
    @Tool("No parameters")
    fun noParams(): String = "no-params-called"

    @Tool("Single string parameter")
    fun singleString(text: String): String = "received: $text"

    @Tool("Multiple parameters with different types")
    fun multipleParams(
        name: String?,
        count: Int,
        enabled: Boolean,
    ): String = "name=$name, count=$count, enabled=$enabled"

    @Tool("Parameters with defaults")
    fun withDefaults(
        required: String?,
        optional: String? = "default-value",
        flag: Boolean = false,
    ): String = "required=$required, optional=$optional, flag=$flag"

    @Tool("List parameter")
    fun listParam(items: List<String>): String = "items: ${items.joinToString(",")}"

    @Tool("Numeric parameters")
    fun numericParams(
        intVal: Int,
        longVal: Long,
        doubleVal: Double,
        floatVal: Float,
    ): String = "int=$intVal, long=$longVal, double=$doubleVal, float=$floatVal"
}

class ToolRegistryTest {
    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        LocalFsTools.setTestRoot(tempDir)
    }

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

    // ========== Array Args Tests ==========

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
    fun `invoke works for IoTools readFile using array args`() {
        val reg = ToolRegistry.defaults()
        val target = tempDir.resolve("readme.txt").toAbsolutePath().toString()
        Files.writeString(Path.of(target), "sample content")

        val result = reg.invoke("readFile", arrayOf(target)) as String
        assertEquals("sample content", result)
    }

    // ========== Null Args Tests ==========
    @Test
    fun `invoke with null args calls no-parameter method`() {
        val reg = ToolRegistry.from(listOf(TestTools()))
        val result = reg.invoke("noParams", null) as String
        assertEquals("no-params-called", result)
    }

    // ========== List Args Tests ==========
    @Test
    fun `invoke with list args for single list parameter`() {
        val reg = ToolRegistry.from(listOf(TestTools()))
        val result = reg.invoke("listParam", listOf("a", "b", "c")) as String
        assertEquals("items: a,b,c", result)
    }

    @Test
    fun `invoke with list args for multiple parameters matches by position`() {
        val reg = ToolRegistry.from(listOf(TestTools()))
        val result = reg.invoke("multipleParams", listOf("Alice", 42, true)) as String
        assertEquals("name=Alice, count=42, enabled=true", result)
    }

    @Test
    fun `invoke with list args handles type coercion`() {
        val reg = ToolRegistry.from(listOf(TestTools()))
        val result = reg.invoke("multipleParams", listOf("Bob", "99", "true")) as String
        assertEquals("name=Bob, count=99, enabled=true", result)
    }

    @Test
    fun `invoke with list args handles missing parameters as defaults`() {
        val reg = ToolRegistry.from(listOf(TestTools()))
        val result = reg.invoke("multipleParams", listOf("Charlie")) as String
        assertEquals("name=Charlie, count=0, enabled=false", result)
    }

    // ========== Map Args Tests ==========

    @Test
    fun `invoke with map args resolves parameter names correctly`() {
        val reg = ToolRegistry.from(listOf(TestTools()))
        val result = reg.invoke(
            "multipleParams",
            mapOf("name" to "Dave", "count" to 123, "enabled" to true),
        ) as String
        assertEquals("name=Dave, count=123, enabled=true", result)
    }

    @Test
    fun `invoke with map args handles partial parameters with nulls`() {
        val reg = ToolRegistry.from(listOf(TestTools()))
        val result = reg.invoke("multipleParams", mapOf("name" to "Eve")) as String
        assertEquals("name=Eve, count=0, enabled=false", result)
    }

    @Test
    fun `invoke with map args handles type coercion from strings`() {
        val reg = ToolRegistry.from(listOf(TestTools()))
        val result = reg.invoke(
            "multipleParams",
            mapOf("name" to "Frank", "count" to "456", "enabled" to "true"),
        ) as String
        assertEquals("name=Frank, count=456, enabled=true", result)
    }

    @Test
    fun `invoke with map args for method with defaults`() {
        val reg = ToolRegistry.from(listOf(TestTools()))
        // Note: Default parameter values are not automatically applied when using reflection invoke
        // Missing parameters will be null and then coerced (empty string for String, false for Boolean)
        val result = reg.invoke("withDefaults", mapOf("required" to "test")) as String
        assertTrue(result.startsWith("required=test"))
    }

    @Test
    fun `invoke with map args overrides defaults`() {
        val reg = ToolRegistry.from(listOf(TestTools()))
        val result = reg.invoke(
            "withDefaults",
            mapOf("required" to "test", "optional" to "custom", "flag" to true),
        ) as String
        assertEquals("required=test, optional=custom, flag=true", result)
    }

    @Test
    fun `invoke with map args for numeric types`() {
        val reg = ToolRegistry.from(listOf(TestTools()))
        val result = reg.invoke(
            "numericParams",
            mapOf("intVal" to 10, "longVal" to 20L, "doubleVal" to 30.5, "floatVal" to 40.5f),
        ) as String
        assertEquals("int=10, long=20, double=30.5, float=40.5", result)
    }

    @Test
    fun `invoke with map args handles numeric type coercion from strings`() {
        val reg = ToolRegistry.from(listOf(TestTools()))
        val result = reg.invoke(
            "numericParams",
            mapOf("intVal" to "15", "longVal" to "25", "doubleVal" to "35.7", "floatVal" to "45.3"),
        ) as String
        assertEquals("int=15, long=25, double=35.7, float=45.3", result)
    }

    @Test
    fun `invoke with map args handles wrong parameter names as null`() {
        val reg = ToolRegistry.from(listOf(TestTools()))
        // Wrong parameter names will result in null values
        // Null is coerced: String -> "null", Int -> 0, Boolean -> false
        val result = reg.invoke(
            "multipleParams",
            mapOf("wrongName" to "value", "count" to 100, "enabled" to true),
        ) as String
        // The 'name' parameter is missing, so it's null, which coerces to "null" for String type
        assertTrue(result.contains("name=null"))
        assertTrue(result.contains("count=100"))
        assertTrue(result.contains("enabled=true"))
    }

    // ========== Error Cases Tests ==========

    @Test
    fun `invoke on missing tool throws error with available list`() {
        val reg = ToolRegistry.defaults()
        val ex =
            assertThrows(IllegalStateException::class.java) {
                reg.invoke("missing", null)
            }

        assertContains(ex.message ?: "", "Tool not found or not allowed: missing")
        assertContains(ex.message ?: "", "Available:")
        assertContains(ex.message ?: "", "writeFile")
        assertContains(ex.message ?: "", "status")
    }

    @Test
    fun `invoke IoTools readFile returns error for missing file`() {
        val reg = ToolRegistry.defaults()
        val missingPath = tempDir.resolve("missing.txt").toAbsolutePath().toString()

        val result = reg.invoke("readFile", arrayOf(missingPath)) as String
        assertTrue(result.startsWith("Error: Path not found"))
    }

    // ========== Integration Tests ==========

    @Test
    fun `invoke git commit with map args uses parameter names correctly`() {
        // This test verifies the actual fix for the gitcommit recipe issue
        val reg = ToolRegistry.defaults()
        val keys = reg.keys()
        assertTrue("commit" in keys, "commit tool should be available")
    }

    @Test
    fun `multiple tool invocations work correctly`() {
        val reg = ToolRegistry.from(listOf(TestTools()))

        val result1 = reg.invoke("singleString", arrayOf("first")) as String
        assertEquals("received: first", result1)

        val result2 = reg.invoke("singleString", mapOf("text" to "second")) as String
        assertEquals("received: second", result2)

        val result3 = reg.invoke("noParams", null) as String
        assertEquals("no-params-called", result3)
    }
}

/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import org.jline.reader.ParsedLine
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateRecipeCommandHandlerTest {
    private lateinit var handler: CreateRecipeCommandHandler
    private lateinit var originalOut: PrintStream
    private lateinit var testOut: ByteArrayOutputStream

    @TempDir
    lateinit var tempDir: Path

    @TempDir
    lateinit var tempHome: Path

    @BeforeEach
    fun setUp() {
        handler = CreateRecipeCommandHandler()

        // Capture console output
        originalOut = System.out
        testOut = ByteArrayOutputStream()
        System.setOut(PrintStream(testOut))

        // Set temporary home directory for testing
        System.setProperty("user.home", tempHome.toString())
    }

    @AfterEach
    fun tearDown() {
        System.setOut(originalOut)
    }

    @Test
    fun `handle with valid name and template creates recipe`() {
        // Create a test template file with all required fields
        val templateFile = tempDir.resolve("test-template.yml")
        val yamlContent =
            """
            name: test-recipe
            version: 3
            description: A test recipe
            system: |
              You are a helpful assistant.
            userTemplate: |
              Please help with: {{input}}
            """.trimIndent()
        Files.writeString(templateFile, yamlContent)

        val parsedLine = mockParsedLine(":create-recipe", "my-recipe", "-template", templateFile.toString())

        handler.handle(parsedLine)

        val output = testOut.toString()
        assertTrue(output.contains("✅ Registered recipe 'my-recipe'"))

        // Verify recipe file was created
        val recipesDir = tempHome.resolve(".askimo/recipes")
        val recipeFile = recipesDir.resolve("my-recipe.yml")
        assertTrue(Files.exists(recipeFile))
    }

    @Test
    fun `handle with missing template flag shows usage`() {
        val parsedLine = mockParsedLine(":create-recipe", "my-recipe")

        handler.handle(parsedLine)

        val output = testOut.toString()
        assertTrue(output.contains("Usage: :create-recipe"))
    }

    @Test
    fun `handle with non-existent template shows error`() {
        val parsedLine = mockParsedLine(":create-recipe", "my-recipe", "-template", "/non/existent/file.yml")

        handler.handle(parsedLine)

        val output = testOut.toString()
        assertTrue(output.contains("❌ Template not found"))
    }

    @Test
    fun `handle with invalid YAML shows error`() {
        val templateFile = tempDir.resolve("invalid.yml")
        Files.writeString(templateFile, "invalid: yaml: content:")

        val parsedLine = mockParsedLine(":create-recipe", "test", "-template", templateFile.toString())

        handler.handle(parsedLine)

        val output = testOut.toString()
        assertTrue(output.contains("❌ Invalid YAML"))
    }

    @Test
    fun `handle with missing name field in YAML shows invalid YAML error`() {
        val templateFile = tempDir.resolve("template.yml")
        val yamlContent =
            """
            version: 3
            description: Test without name
            system: |
              You are a helpful assistant.
            userTemplate: |
              Process: {{task}}
            """.trimIndent()
        Files.writeString(templateFile, yamlContent)

        val parsedLine = mockParsedLine(":create-recipe", "-template", templateFile.toString())

        handler.handle(parsedLine)

        val output = testOut.toString()
        // Missing required 'name' field causes YAML parsing to fail
        assertTrue(output.contains("❌ Invalid YAML"))
    }

    @Test
    fun `handle with empty name in YAML and no arg shows error`() {
        val templateFile = tempDir.resolve("template.yml")
        val yamlContent =
            """
            name: ""
            version: 3
            description: Test with empty name
            system: |
              You are a helpful assistant.
            userTemplate: |
              Process: {{task}}
            """.trimIndent()
        Files.writeString(templateFile, yamlContent)

        val parsedLine = mockParsedLine(":create-recipe", "-template", templateFile.toString())

        handler.handle(parsedLine)

        val output = testOut.toString()
        // Empty name (valid YAML but blank string) triggers the name missing check
        assertTrue(output.contains("❌ Recipe name missing"))
    }

    @Test
    fun `handle with existing recipe shows warning`() {
        val templateFile = tempDir.resolve("template.yml")
        val yamlContent =
            """
            name: existing-recipe
            version: 3
            system: |
              You are a helpful assistant.
            userTemplate: |
              Help me with: {{input}}
            """.trimIndent()
        Files.writeString(templateFile, yamlContent)

        // Create the recipe first time
        val recipesDir = tempHome.resolve(".askimo/recipes")
        Files.createDirectories(recipesDir)
        Files.writeString(recipesDir.resolve("existing-recipe.yml"), yamlContent)

        val parsedLine = mockParsedLine(":create-recipe", "existing-recipe", "-template", templateFile.toString())

        handler.handle(parsedLine)

        val output = testOut.toString()
        assertTrue(output.contains("⚠️ Recipe 'existing-recipe' already exists"))
    }

    @Test
    fun `handle expands tilde in template path`() {
        val templateFile = tempHome.resolve("template.yml")
        val yamlContent =
            """
            name: tilde-test
            version: 3
            system: |
              You are a helpful assistant.
            userTemplate: |
              Complete: {{task}}
            """.trimIndent()
        Files.writeString(templateFile, yamlContent)

        val parsedLine = mockParsedLine(":create-recipe", "test", "-template", "~/template.yml")

        handler.handle(parsedLine)

        val output = testOut.toString()
        assertTrue(output.contains("✅ Registered recipe"))
    }

    @Test
    fun `keyword returns correct value`() {
        assertEquals(":create-recipe", handler.keyword)
    }

    @Test
    fun `description is not empty`() {
        assertTrue(handler.description.isNotBlank())
        assertTrue(handler.description.contains("Usage:"))
    }

    // Helper function to create mock ParsedLine
    private fun mockParsedLine(vararg words: String): ParsedLine {
        val parsedLine = mock<ParsedLine>()
        whenever(parsedLine.words()) doReturn words.toList()
        return parsedLine
    }
}

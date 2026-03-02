/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.cli.recipes.RecipeDef
import io.askimo.core.util.AskimoHome
import io.askimo.core.util.Yaml.yamlMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateRecipeCommandHandlerTest : CommandHandlerTestBase() {
    private lateinit var handler: CreateRecipeCommandHandler

    @TempDir
    lateinit var tempDir: Path

    @TempDir
    lateinit var tempHome: Path

    private lateinit var testBaseScope: AskimoHome.TestBaseScope

    @BeforeEach
    fun setUp() {
        handler = CreateRecipeCommandHandler()
        // Use thread-local override — AskimoHome.base() resolves to tempHome/personal/
        testBaseScope = AskimoHome.withTestBase(tempHome)
    }

    @AfterEach
    fun tearDown() {
        testBaseScope.close()
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

        val parsedLine = mockParsedLine(":create-recipe", "my-recipe", "-f", templateFile.toString())

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(output.contains("✅ Registered recipe 'my-recipe'"))

        // Verify recipe file was created
        val recipeFile = AskimoHome.recipesDir().resolve("my-recipe.yml")
        assertTrue(Files.exists(recipeFile))
    }

    @Test
    fun `handle with missing template flag shows usage`() {
        val parsedLine = mockParsedLine(":create-recipe", "my-recipe")

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(output.contains("Usage: :create-recipe"))
    }

    @Test
    fun `handle with non-existent template shows error`() {
        val parsedLine = mockParsedLine(":create-recipe", "my-recipe", "-f", "/non/existent/file.yml")

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(output.contains("❌ Template not found"))
    }

    @Test
    fun `handle with invalid YAML shows error`() {
        val templateFile = tempDir.resolve("invalid.yml")
        Files.writeString(templateFile, "invalid: yaml: content:")

        val parsedLine = mockParsedLine(":create-recipe", "test", "-f", templateFile.toString())

        handler.handle(parsedLine)

        val output = getOutput()
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

        val parsedLine = mockParsedLine(":create-recipe", "-f", templateFile.toString())

        handler.handle(parsedLine)

        val output = getOutput()
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

        val parsedLine = mockParsedLine(":create-recipe", "-f", templateFile.toString())

        handler.handle(parsedLine)

        val output = getOutput()
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

        // Pre-create the recipe in the profile-aware recipes dir
        val recipesDir = AskimoHome.recipesDir()
        Files.createDirectories(recipesDir)
        Files.writeString(recipesDir.resolve("existing-recipe.yml"), yamlContent)

        val parsedLine = mockParsedLine(":create-recipe", "existing-recipe", "-f", templateFile.toString())

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(output.contains("⚠️ Recipe 'existing-recipe' already exists"))
    }

    @Test
    fun `handle expands tilde in template path`() {
        // Write the template into the user home that AskimoHome.userHome() resolves.
        // Since we are using withTestBase (not overriding user.home), we write to
        // the real user home but use a unique name to avoid collisions.
        val realHome = Paths.get(System.getProperty("user.home"))
        val templateFile = realHome.resolve("askimo-tilde-test-template.yml")
        try {
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

            val parsedLine = mockParsedLine(":create-recipe", "tilde-test", "-f", "~/askimo-tilde-test-template.yml")

            handler.handle(parsedLine)

            val output = getOutput()
            assertTrue(output.contains("✅ Registered recipe"))
        } finally {
            Files.deleteIfExists(templateFile)
        }
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

    @Test
    fun `handle with real gitcommit template preserves vars`() {
        // Use the real template shipped with the repo (now in src/main/resources)
        val templatePath =
            Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "templates", "gitcommit.yml")
        assertTrue(Files.exists(templatePath))

        val parsedLine =
            mockParsedLine(":create-recipe", "gitcommit-test", "-f", templatePath.toString())

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(output.contains("✅ Registered recipe 'gitcommit-test'"))

        val recipeFile = AskimoHome.recipesDir().resolve("gitcommit-test.yml")
        assertTrue(Files.exists(recipeFile))

        val def = yamlMapper.readValue(Files.readString(recipeFile), RecipeDef::class.java)
        assertTrue(def.vars.containsKey("diff"))
        assertTrue(def.vars.containsKey("status"))
        assertTrue(def.vars.containsKey("branch"))
    }
}

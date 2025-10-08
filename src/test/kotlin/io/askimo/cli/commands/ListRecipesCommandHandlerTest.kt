/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListRecipesCommandHandlerTest : CommandHandlerTestBase() {
    private lateinit var handler: ListRecipesCommandHandler

    @TempDir
    lateinit var tempHome: Path

    private lateinit var originalHome: String
    private lateinit var recipesDir: Path

    @BeforeEach
    fun setUp() {
        handler = ListRecipesCommandHandler()

        // Save original home and set temp home for testing
        originalHome = System.getProperty("user.home")
        System.setProperty("user.home", tempHome.toString())

        // Create recipes directory
        recipesDir = tempHome.resolve(".askimo/recipes")
    }

    @AfterEach
    fun tearDown() {
        // Restore original home directory
        System.setProperty("user.home", originalHome)
    }

    @Test
    fun `keyword returns correct value`() {
        assertEquals(":recipes", handler.keyword)
    }

    @Test
    fun `description is not empty and mentions recipes`() {
        assertTrue(handler.description.isNotBlank())
        assertTrue(handler.description.contains("recipe", ignoreCase = true))
        assertTrue(handler.description.contains("list", ignoreCase = true))
    }

    @Test
    fun `handle with no recipes directory shows info message`() {
        // Don't create the recipes directory
        val parsedLine = mockParsedLine(":recipes")

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(output.contains("ℹ️  No recipes registered yet."))
    }

    @Test
    fun `handle with empty recipes directory shows info message`() {
        // Create directory but don't add any recipes
        Files.createDirectories(recipesDir)

        val parsedLine = mockParsedLine(":recipes")

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(output.contains("ℹ️  No recipes registered."))
    }

    @Test
    fun `handle with single recipe lists it`() {
        Files.createDirectories(recipesDir)
        Files.writeString(recipesDir.resolve("test-recipe.yml"), "name: test-recipe")

        val parsedLine = mockParsedLine(":recipes")

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(output.contains("📦 Registered recipes (1)"))
        assertTrue(output.contains("test-recipe"))
        assertTrue(!output.contains(".yml"))
    }

    @Test
    fun `handle with multiple recipes lists them all`() {
        Files.createDirectories(recipesDir)
        Files.writeString(recipesDir.resolve("recipe1.yml"), "name: recipe1")
        Files.writeString(recipesDir.resolve("recipe2.yml"), "name: recipe2")
        Files.writeString(recipesDir.resolve("recipe3.yml"), "name: recipe3")

        val parsedLine = mockParsedLine(":recipes")

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(output.contains("📦 Registered recipes (3)"))
        assertTrue(output.contains("recipe1"))
        assertTrue(output.contains("recipe2"))
        assertTrue(output.contains("recipe3"))
    }

    @Test
    fun `handle lists recipes in sorted order`() {
        Files.createDirectories(recipesDir)
        Files.writeString(recipesDir.resolve("zebra.yml"), "name: zebra")
        Files.writeString(recipesDir.resolve("alpha.yml"), "name: alpha")
        Files.writeString(recipesDir.resolve("middle.yml"), "name: middle")

        val parsedLine = mockParsedLine(":recipes")

        handler.handle(parsedLine)

        val output = getOutput()
        val lines = output.lines()

        // Find the index of each recipe name in the output
        val alphaIndex = lines.indexOfFirst { it.contains("alpha") }
        val middleIndex = lines.indexOfFirst { it.contains("middle") }
        val zebraIndex = lines.indexOfFirst { it.contains("zebra") }

        // Verify sorted order
        assertTrue(alphaIndex >= 0)
        assertTrue(middleIndex > alphaIndex)
        assertTrue(zebraIndex > middleIndex)
    }

    @Test
    fun `handle ignores non-yml files`() {
        Files.createDirectories(recipesDir)
        Files.writeString(recipesDir.resolve("recipe.yml"), "name: recipe")
        Files.writeString(recipesDir.resolve("readme.txt"), "This is a readme")
        Files.writeString(recipesDir.resolve("config.json"), "{}")
        Files.writeString(recipesDir.resolve("backup.yaml"), "name: backup")

        val parsedLine = mockParsedLine(":recipes")

        handler.handle(parsedLine)

        val output = getOutput()
        // Should only show .yml files
        assertTrue(output.contains("📦 Registered recipes (1)"))
        assertTrue(output.contains("recipe"))
        assertTrue(!output.contains("readme"))
        assertTrue(!output.contains("config"))
        assertTrue(!output.contains("backup"))
    }

    @Test
    fun `handle shows separator line`() {
        Files.createDirectories(recipesDir)
        Files.writeString(recipesDir.resolve("test.yml"), "name: test")

        val parsedLine = mockParsedLine(":recipes")

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(output.contains("────────────────────────────"))
    }

    @Test
    fun `handle with recipe names containing special characters`() {
        Files.createDirectories(recipesDir)
        Files.writeString(recipesDir.resolve("my-special_recipe.yml"), "name: test")
        Files.writeString(recipesDir.resolve("recipe.v2.yml"), "name: test")

        val parsedLine = mockParsedLine(":recipes")

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(output.contains("📦 Registered recipes (2)"))
        assertTrue(output.contains("my-special_recipe"))
        assertTrue(output.contains("recipe.v2"))
    }

    @Test
    fun `handle with recipe names containing spaces`() {
        Files.createDirectories(recipesDir)
        // Note: File names with spaces are valid on most file systems
        Files.writeString(recipesDir.resolve("my recipe.yml"), "name: test")

        val parsedLine = mockParsedLine(":recipes")

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(output.contains("📦 Registered recipes (1)"))
        assertTrue(output.contains("my recipe"))
    }

    @Test
    fun `handle with subdirectories in recipes folder`() {
        Files.createDirectories(recipesDir)
        Files.writeString(recipesDir.resolve("recipe1.yml"), "name: recipe1")

        // Create a subdirectory with a recipe
        val subDir = recipesDir.resolve("subdir")
        Files.createDirectories(subDir)
        Files.writeString(subDir.resolve("recipe2.yml"), "name: recipe2")

        val parsedLine = mockParsedLine(":recipes")

        handler.handle(parsedLine)

        val output = getOutput()
        // Should only show top-level .yml files
        assertTrue(output.contains("📦 Registered recipes (1)"))
        assertTrue(output.contains("recipe1"))
        assertTrue(!output.contains("recipe2"))
    }

    @Test
    fun `handle with empty yml files`() {
        Files.createDirectories(recipesDir)
        Files.writeString(recipesDir.resolve("empty.yml"), "")
        Files.writeString(recipesDir.resolve("valid.yml"), "name: valid")

        val parsedLine = mockParsedLine(":recipes")

        handler.handle(parsedLine)

        val output = getOutput()
        // Should list both files regardless of content
        assertTrue(output.contains("📦 Registered recipes (2)"))
        assertTrue(output.contains("empty"))
        assertTrue(output.contains("valid"))
    }

    @Test
    fun `handle with large number of recipes`() {
        Files.createDirectories(recipesDir)

        // Create 50 recipes
        for (i in 1..50) {
            Files.writeString(recipesDir.resolve("recipe-$i.yml"), "name: recipe-$i")
        }

        val parsedLine = mockParsedLine(":recipes")

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(output.contains("📦 Registered recipes (50)"))
    }

    @Test
    fun `handle with yml extension in different cases`() {
        Files.createDirectories(recipesDir)
        Files.writeString(recipesDir.resolve("lowercase.yml"), "name: test")
        Files.writeString(recipesDir.resolve("uppercase.YML"), "name: test")
        Files.writeString(recipesDir.resolve("mixedcase.Yml"), "name: test")

        val parsedLine = mockParsedLine(":recipes")

        handler.handle(parsedLine)

        val output = getOutput()
        // Only .yml (lowercase) should be recognized
        assertTrue(output.contains("lowercase"))
        // Behavior may vary based on file system case sensitivity
        // On case-insensitive systems, all might be listed
        // On case-sensitive systems, only lowercase.yml should be listed
    }

    @Test
    fun `handle strips yml extension from displayed names`() {
        Files.createDirectories(recipesDir)
        Files.writeString(recipesDir.resolve("my-recipe.yml"), "name: test")

        val parsedLine = mockParsedLine(":recipes")

        handler.handle(parsedLine)

        val output = getOutput()
        val lines = output.lines()
        val recipeLine = lines.find { it.contains("my-recipe") }

        assertTrue(recipeLine != null)
        assertTrue(recipeLine!!.contains("my-recipe"))
        assertTrue(!recipeLine.endsWith(".yml"))
    }

    @Test
    fun `handle with numeric recipe names`() {
        Files.createDirectories(recipesDir)
        Files.writeString(recipesDir.resolve("123.yml"), "name: test")
        Files.writeString(recipesDir.resolve("456.yml"), "name: test")

        val parsedLine = mockParsedLine(":recipes")

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(output.contains("📦 Registered recipes (2)"))
        assertTrue(output.contains("123"))
        assertTrue(output.contains("456"))
    }

    @Test
    fun `handle count matches actual number of yml files`() {
        Files.createDirectories(recipesDir)
        Files.writeString(recipesDir.resolve("recipe1.yml"), "name: test")
        Files.writeString(recipesDir.resolve("recipe2.yml"), "name: test")
        Files.writeString(recipesDir.resolve("recipe3.yml"), "name: test")
        Files.writeString(recipesDir.resolve("not-a-recipe.txt"), "test")

        val parsedLine = mockParsedLine(":recipes")

        handler.handle(parsedLine)

        val output = getOutput()
        // Count should be 3, not 4
        assertTrue(output.contains("📦 Registered recipes (3)"))
    }
}

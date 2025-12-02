/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.recipes

import io.askimo.cli.recipes.DefaultRecipeInitializer
import io.askimo.core.util.AskimoHome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultRecipeInitializerTest {
    private lateinit var tempDir: Path
    private lateinit var originalRecipesDir: Path

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("askimo-test")
        originalRecipesDir = AskimoHome.recipesDir()

        System.setProperty("user.home", tempDir.toString())
    }

    @AfterEach
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should create gitcommit template when it doesn't exist`() {
        DefaultRecipeInitializer.initializeDefaultTemplates()

        val recipesDir = tempDir.resolve(".askimo").resolve("recipes")
        val gitcommitFile = recipesDir.resolve("gitcommit.yml")

        assertTrue(recipesDir.exists(), "Recipes directory should be created")
        assertTrue(gitcommitFile.exists(), "gitcommit.yml should be created")

        val content = gitcommitFile.readText()
        assertTrue(content.contains("name: gitCommit"), "Template should contain the correct content")
        assertTrue(content.contains("stagedDiff"), "Template should contain git tools")
    }

    @Test
    fun `should not overwrite existing template`() {
        val recipesDir = tempDir.resolve(".askimo").resolve("recipes")
        Files.createDirectories(recipesDir)
        val gitcommitFile = recipesDir.resolve("gitcommit.yml")
        val existingContent = "name: customGitCommit\nversion: 999"
        gitcommitFile.toFile().writeText(existingContent)

        DefaultRecipeInitializer.initializeDefaultTemplates()

        val content = gitcommitFile.readText()
        assertTrue(content.contains("customGitCommit"), "Should preserve existing content")
        assertTrue(content.contains("version: 999"), "Should preserve existing version")
        assertFalse(content.contains("name: gitCommit"), "Should not overwrite with default content")
    }
}

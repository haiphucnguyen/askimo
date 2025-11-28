/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.util.AskimoHome
import io.askimo.core.util.logger
import org.jline.reader.ParsedLine
import java.nio.file.Files

class DeleteRecipeCommandHandler : CommandHandler {
    private val log = logger<DeleteRecipeCommandHandler>()

    override val keyword = ":delete-recipe"
    override val description = "Delete a registered recipe from ~/.askimo/recipe\nUsage: :delete-recipe <name> | :delete-recipe --all"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        if (args.isEmpty()) {
            log.info("Usage: :delete-recipe <name> | :delete-recipe --all")
            return
        }

        val firstArg = args[0]

        // Handle --all option to delete all recipes
        if (firstArg == "--all") {
            deleteAllRecipes()
            return
        }

        // Handle single recipe deletion
        val name = firstArg
        val path = AskimoHome.recipesDir().resolve("$name.yml")
        if (!Files.exists(path)) {
            log.info("❌ Recipe '$name' not found.")
            return
        }

        print("⚠️  Delete recipe '$name'? [y/N]: ")
        val confirm = readlnOrNull()?.trim()?.lowercase()
        if (confirm != "y") {
            log.info("✋ Aborted.")
            return
        }

        try {
            Files.delete(path)
            log.info("🗑️  Deleted '$name'")
        } catch (e: Exception) {
            log.info("❌ Failed to delete: ${e.message}")
            log.error("Failed to delete $name", e)
        }
    }

    private fun deleteAllRecipes() {
        val dir = AskimoHome.recipesDir()
        if (!Files.exists(dir)) {
            log.info("ℹ️  No recipes directory found.")
            return
        }

        val recipeFiles = Files
            .list(dir)
            .filter { it.fileName.toString().endsWith(".yml") }
            .sorted()
            .toList()

        if (recipeFiles.isEmpty()) {
            log.info("ℹ️  No recipes found to delete.")
            return
        }

        log.info("📦 Found ${recipeFiles.size} recipe(s) to delete:")
        recipeFiles.forEach { file ->
            log.info("  • ${file.fileName.toString().removeSuffix(".yml")}")
        }

        print("⚠️  Delete ALL ${recipeFiles.size} recipe(s)? This cannot be undone! [y/N]: ")
        val confirm = readlnOrNull()?.trim()?.lowercase()
        if (confirm != "y") {
            log.info("✋ Aborted.")
            return
        }

        var deletedCount = 0
        var failedCount = 0

        recipeFiles.forEach { file ->
            try {
                Files.delete(file)
                deletedCount++
                log.info("🗑️  Deleted '${file.fileName.toString().removeSuffix(".yml")}'")
            } catch (e: Exception) {
                failedCount++
                log.info("❌ Failed to delete '${file.fileName.toString().removeSuffix(".yml")}': ${e.message}")
                log.error("Failed to delete ${file.fileName.toString().removeSuffix(".yml")}", e)
            }
        }

        if (failedCount == 0) {
            log.info("✅ Successfully deleted all $deletedCount recipe(s).")
        } else {
            log.info("⚠️  Deleted $deletedCount recipe(s), failed to delete $failedCount recipe(s).")
        }
    }
}

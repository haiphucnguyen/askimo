/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.logging.display
import io.askimo.core.logging.displayError
import io.askimo.core.logging.logger
import io.askimo.core.util.AskimoHome
import org.jline.reader.ParsedLine
import java.nio.file.Files
import kotlin.inc
import kotlin.toString

class DeleteRecipeCommandHandler : CommandHandler {
    private val log = logger<DeleteRecipeCommandHandler>()

    override val keyword = ":delete-recipe"
    override val description = "Delete a registered recipe from ~/.askimo/recipe\nUsage: :delete-recipe <name> | :delete-recipe --all"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        if (args.isEmpty()) {
            log.display("Usage: :delete-recipe <name> | :delete-recipe --all")
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
            log.display("❌ Recipe '$name' not found.")
            return
        }

        print("⚠️  Delete recipe '$name'? [y/N]: ")
        val confirm = readlnOrNull()?.trim()?.lowercase()
        if (confirm != "y") {
            log.display("✋ Aborted.")
            return
        }

        try {
            Files.delete(path)
            log.display("🗑️  Deleted '$name'")
        } catch (e: Exception) {
            log.display("❌ Failed to delete: ${e.message}")
            log.error("Failed to delete $name", e)
        }
    }

    private fun deleteAllRecipes() {
        val dir = AskimoHome.recipesDir()
        if (!Files.exists(dir)) {
            log.display("ℹ️  No recipes directory found.")
            return
        }

        val recipeFiles = Files
            .list(dir)
            .filter { it.fileName.toString().endsWith(".yml") }
            .sorted()
            .toList()

        if (recipeFiles.isEmpty()) {
            log.display("ℹ️  No recipes found to delete.")
            return
        }

        log.display("📦 Found ${recipeFiles.size} recipe(s) to delete:")
        recipeFiles.forEach { file ->
            log.display("  • ${file.fileName.toString().removeSuffix(".yml")}")
        }

        print("⚠️  Delete ALL ${recipeFiles.size} recipe(s)? This cannot be undone! [y/N]: ")
        val confirm = readlnOrNull()?.trim()?.lowercase()
        if (confirm != "y") {
            log.display("✋ Aborted.")
            return
        }

        var deletedCount = 0
        var failedCount = 0

        recipeFiles.forEach { file ->
            val recipeName = file.fileName.toString().removeSuffix(".yml")
            try {
                Files.delete(file)
                deletedCount++
                log.display("🗑️  Deleted '$recipeName'")
            } catch (e: Exception) {
                failedCount++
                log.displayError("❌ Failed to delete '$recipeName': ${e.message}", e)
            }
        }

        if (failedCount == 0) {
            log.display("✅ Successfully deleted all $deletedCount recipe(s).")
        } else {
            log.display("⚠️  Deleted $deletedCount recipe(s), failed to delete $failedCount recipe(s).")
        }
    }
}

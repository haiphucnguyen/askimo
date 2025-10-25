/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.util.AskimoHome
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.info
import org.jline.reader.ParsedLine
import java.nio.file.Files

class DeleteRecipeCommandHandler : CommandHandler {
    override val keyword = ":delete-recipe"
    override val description = "Delete a registered recipe from ~/.askimo/recipe\nUsage: :delete-recipe <name> | :delete-recipe --all"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        if (args.isEmpty()) {
            info("Usage: :delete-recipe <name> | :delete-recipe --all")
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
            info("❌ Recipe '$name' not found.")
            return
        }

        print("⚠️  Delete recipe '$name'? [y/N]: ")
        val confirm = readlnOrNull()?.trim()?.lowercase()
        if (confirm != "y") {
            info("✋ Aborted.")
            return
        }

        try {
            Files.delete(path)
            info("🗑️  Deleted '$name'")
        } catch (e: Exception) {
            info("❌ Failed to delete: ${e.message}")
            debug(e)
        }
    }

    private fun deleteAllRecipes() {
        val dir = AskimoHome.recipesDir()
        if (!Files.exists(dir)) {
            info("ℹ️  No recipes directory found.")
            return
        }

        val recipeFiles = Files
            .list(dir)
            .filter { it.fileName.toString().endsWith(".yml") }
            .sorted()
            .toList()

        if (recipeFiles.isEmpty()) {
            info("ℹ️  No recipes found to delete.")
            return
        }

        info("📦 Found ${recipeFiles.size} recipe(s) to delete:")
        recipeFiles.forEach { file ->
            info("  • ${file.fileName.toString().removeSuffix(".yml")}")
        }

        print("⚠️  Delete ALL ${recipeFiles.size} recipe(s)? This cannot be undone! [y/N]: ")
        val confirm = readlnOrNull()?.trim()?.lowercase()
        if (confirm != "y") {
            info("✋ Aborted.")
            return
        }

        var deletedCount = 0
        var failedCount = 0

        recipeFiles.forEach { file ->
            try {
                Files.delete(file)
                deletedCount++
                info("🗑️  Deleted '${file.fileName.toString().removeSuffix(".yml")}'")
            } catch (e: Exception) {
                failedCount++
                info("❌ Failed to delete '${file.fileName.toString().removeSuffix(".yml")}': ${e.message}")
                debug(e)
            }
        }

        if (failedCount == 0) {
            info("✅ Successfully deleted all $deletedCount recipe(s).")
        } else {
            info("⚠️  Deleted $deletedCount recipe(s), failed to delete $failedCount recipe(s).")
        }
    }
}

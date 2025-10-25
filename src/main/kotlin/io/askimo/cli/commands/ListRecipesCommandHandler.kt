/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.util.AskimoHome
import io.askimo.core.util.Logger.info
import io.askimo.core.recipes.RecipeRegistry
import org.jline.reader.ParsedLine
import java.nio.file.Files

class ListRecipesCommandHandler : CommandHandler {
    override val keyword = ":recipes"
    override val description = "List all registered commands in ~/.askimo/recipes"

    override fun handle(line: ParsedLine) {
        val dir = AskimoHome.recipesDir()
        if (!Files.exists(dir)) {
            info("ℹ️  No recipes registered yet.")
            return
        }

        val files =
            Files
                .list(dir)
                .filter { it.fileName.toString().endsWith(".yml") }
                .sorted()
                .toList()

        if (files.isEmpty()) {
            info("ℹ️  No recipes registered.")
            return
        }

        info("📦 Registered recipes (${files.size})")
        info("──────────────────────────────")

        val registry = RecipeRegistry()
        files.forEach { file ->
            val recipeName = file.fileName.toString().removeSuffix(".yml")
            try {
                val recipe = registry.load(recipeName)
                val description = recipe.description ?: "No description"
                info("$recipeName - $description")
            } catch (e: Exception) {
                // If we can't load the recipe, just show the name
                info("$recipeName - (Unable to load description)")
            }
        }
    }
}

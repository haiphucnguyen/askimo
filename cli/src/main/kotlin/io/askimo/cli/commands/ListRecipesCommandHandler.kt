/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.cli.recipes.RecipeRegistry
import io.askimo.core.logging.display
import io.askimo.core.logging.logger
import io.askimo.core.util.AskimoHome
import org.jline.reader.ParsedLine
import java.nio.file.Files

class ListRecipesCommandHandler : CommandHandler {
    private val log = logger<ListRecipesCommandHandler>()
    override val keyword = ":recipes"
    override val description = "List all registered commands in ~/.askimo/recipes"

    override fun handle(line: ParsedLine) {
        val dir = AskimoHome.recipesDir()
        if (!Files.exists(dir)) {
            log.display("ℹ️  No recipes registered yet.")
            return
        }

        val files =
            Files
                .list(dir)
                .filter { it.fileName.toString().endsWith(".yml") }
                .sorted()
                .toList()

        if (files.isEmpty()) {
            log.display("ℹ️  No recipes registered.")
            return
        }

        log.display("📦 Registered recipes (${files.size})")
        log.display("──────────────────────────────")

        val registry = RecipeRegistry()
        files.forEach { file ->
            val recipeName = file.fileName.toString().removeSuffix(".yml")
            try {
                val recipe = registry.load(recipeName)
                val description = recipe.description ?: "No description"
                log.display("$recipeName - $description")
            } catch (e: Exception) {
                // If we can't load the recipe, just show the name
                log.display("$recipeName - (Unable to load description)")
                log.error("Failed to load recipe $recipeName", e)
            }
        }
    }
}

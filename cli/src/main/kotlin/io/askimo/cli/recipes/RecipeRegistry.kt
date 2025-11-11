/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.recipes

import io.askimo.core.util.AskimoHome
import io.askimo.core.util.Yaml.yamlMapper
import java.nio.file.Files
import java.nio.file.Path

class RecipeRegistry(
    private val baseDir: Path = AskimoHome.recipesDir(),
) {
    fun load(name: String): RecipeDef {
        val file = baseDir.resolve("$name.yml")
        if (!Files.exists(file)) {
            throw RecipeNotFoundException(
                "Recipe '$name' not found.\n" +
                    "💡 Use '--recipes' to list all available recipes.",
            )
        }
        return Files
            .newBufferedReader(file)
            .use { yamlMapper.readValue(it, RecipeDef::class.java) }
            .fixWhenField()
    }
}

private fun RecipeDef.fixWhenField(): RecipeDef = this

/**
 * Exception thrown when a recipe is not found in the registry.
 */
class RecipeNotFoundException(message: String) : RuntimeException(message)

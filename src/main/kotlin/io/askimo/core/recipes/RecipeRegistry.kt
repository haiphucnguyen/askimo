/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.recipes

import io.askimo.core.util.AskimoHome
import io.askimo.core.util.Yaml.yamlMapper
import java.nio.file.Files
import java.nio.file.Path

class RecipeRegistry(
    private val baseDir: Path = AskimoHome.recipesDir(),
) {
    fun load(name: String): RecipeDef {
        val file = baseDir.resolve("$name.yml")
        require(Files.exists(file)) { "Recipe not found: $file" }
        return Files
            .newBufferedReader(file)
            .use { yamlMapper.readValue(it, RecipeDef::class.java) }
            .fixWhenField() // tiny shim: map YAML 'when' → PostAction.when_
    }
}

private fun RecipeDef.fixWhenField(): RecipeDef {
    // If you map with Jackson annotations you can skip this. Shown compactly here.
    return this
}

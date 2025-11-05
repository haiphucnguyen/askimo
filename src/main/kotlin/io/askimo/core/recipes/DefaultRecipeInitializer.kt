/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.recipes

import io.askimo.core.util.AskimoHome
import io.askimo.core.util.Logger
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.writeText

object DefaultRecipeInitializer {
    private val bundledTemplates = listOf("gitcommit.yml", "summarize.yml")

    fun initializeDefaultTemplates() {
        val recipesDir = AskimoHome.recipesDir()

        // Ensure recipes directory exists
        if (!recipesDir.exists()) {
            Files.createDirectories(recipesDir)
        }

        bundledTemplates.forEach { templateName ->
            val recipePath = recipesDir.resolve(templateName)

            // Only create if it doesn't exist (preserve user customizations)
            if (!recipePath.exists()) {
                val resourceContent = javaClass.classLoader
                    .getResourceAsStream("templates/$templateName")
                    ?.readBytes()
                    ?.toString(Charsets.UTF_8)

                if (resourceContent != null) {
                    recipePath.writeText(resourceContent)
                    Logger.debug("Created default template: $recipePath")
                } else {
                    Logger.debug("Could not load bundled template: $templateName")
                }
            }
        }
    }
}

/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.session.ModelService
import io.askimo.core.session.Session
import io.askimo.core.util.Logger.info
import org.jline.reader.ParsedLine

/**
 * Handles the command to list available models for the current provider.
 *
 * This class retrieves and displays all models that can be used with the currently selected
 * provider. If no models are available, it provides helpful guidance on how to set up the
 * provider correctly, with provider-specific instructions.
 */
class ModelsCommandHandler(
    private val session: Session,
) : CommandHandler {
    override val keyword: String = ":models"

    override val description: String = "List available models for the current provider"

    override fun handle(line: ParsedLine) {
        val provider = session.params.currentProvider

        when (val result = ModelService.getAvailableModels(provider, session)) {
            is ModelService.ModelsResult.Success -> {
                info("Available models for provider '${provider.name.lowercase()}':")
                result.models.forEach { info("- $it") }
                info("\n💡 Use `:set-param model <modelName>` to choose one of these models.")
            }
            is ModelService.ModelsResult.Error -> {
                info("❌ ${result.message}")
                result.helpText?.let { info("\n💡 $it") }
            }
        }
    }
}

/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ProviderRegistry
import io.askimo.core.providers.ProviderSettings
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
        val factory = ProviderRegistry.getFactory(provider)

        if (factory == null) {
            info("❌ No model factory registered for provider: ${provider.name.lowercase()}")
            return
        }

        val settings = session.params.providerSettings[provider] ?: factory.defaultSettings()

        @Suppress("UNCHECKED_CAST")
        val models = (factory as ChatModelFactory<ProviderSettings>).availableModels(settings)

        if (models.isEmpty()) {
            info("❌ No models available for ${provider.name.lowercase()}")
            info("\n💡 ${factory.getNoModelsHelpText()}")
        } else {
            info("Available models for provider '${provider.name.lowercase()}':")
            models.forEach { info("- $it") }
            info("\n💡 Use `:set-param model <modelName>` to choose one of these models.")
        }
    }
}

/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.providers.ModelProvider.OLLAMA
import io.askimo.core.providers.ModelProvider.OPEN_AI
import io.askimo.core.providers.ProviderRegistry
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

        val models =
            factory.availableModels(
                session.params.providerSettings[session.params.currentProvider]
                    ?: factory.defaultSettings(),
            )
        if (models.isEmpty()) {
            info("⚠️ No models available for provider: ${provider.name.lowercase()}")

            when (provider) {
                OLLAMA -> {
                    info(
                        """
                        💡 You may not have any models installed yet.

                        ➡️ Visit https://ollama.com/library to browse available models.
                        📥 Then run: `ollama pull <modelName>` to install a model locally.

                        Example:
                        ollama pull llama3
                        """.trimIndent(),
                    )
                }
                OPEN_AI -> {
                    info(
                        """
                        💡 One possible reason is that you haven't provided your OpenAI API key yet.

                        1. Get your API key from: https://platform.openai.com/account/api-keys
                        2. Then set it in the CLI using: :set-param api_key YOUR_API_KEY_HERE

                        Get an API key here:
                        https://platform.openai.com/api-keys
                        """.trimIndent(),
                    )
                }
                else -> {}
            }
        } else {
            info("Available models for provider '${provider.name.lowercase()}':")
            models.forEach { info("- $it") }
        }

        info("\n💡 Use `:set-param model <modelName>` to choose one of these models.")
    }
}

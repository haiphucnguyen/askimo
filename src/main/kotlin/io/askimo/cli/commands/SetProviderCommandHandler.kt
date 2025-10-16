/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ProviderRegistry
import io.askimo.core.providers.ProviderValidator
import io.askimo.core.session.MemoryPolicy.KEEP_PER_PROVIDER_MODEL
import io.askimo.core.session.Session
import io.askimo.core.session.SessionConfigManager
import io.askimo.core.util.Logger.info
import org.jline.reader.ParsedLine

/**
 * Handles the command to change the active model provider.
 *
 * This class allows users to switch between different AI model providers (like OpenAI, Ollama)
 * and automatically configures default settings for the selected provider. It validates that
 * the provider exists and is properly registered, then updates the session configuration
 * accordingly.
 */
class SetProviderCommandHandler(
    private val session: Session,
) : CommandHandler {
    override val keyword: String = ":set-provider"
    override val description: String = "Set the current model provider (e.g., :set-provider openai)"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        if (args.isEmpty()) {
            info("❌ Usage: :set-provider <provider>")
            return
        }

        val input = args[0].trim().uppercase()
        val provider = runCatching { ModelProvider.valueOf(input) }.getOrNull()

        if (provider == null) {
            info("❌ Unknown provider: '$input'")
            info("💡 Use `:providers` to list all supported model providers.")
            return
        }

        if (!ProviderRegistry.getSupportedProviders().contains(provider)) {
            info("❌ Provider '$input' is not registered.")
            info("💡 Use `:providers` to see which providers are currently available.")
            return
        }

        val factory = session.getModelFactory(provider)
        if (factory == null) {
            info("❌ No factory registered for provider: ${provider.name.lowercase()}")
            return
        }

        // ✅ Switch provider and apply default settings if not already stored
        session.params.currentProvider = provider
        val providerSettings = session.getOrCreateProviderSettings(provider)
        session.setProviderSetting(
            provider,
            providerSettings,
        )

        var model = session.params.getModel(provider)
        if (model.isBlank()) {
            // Use default model if available, else the first discovered
            model = providerSettings.defaultModel
        }
        session.params.model = model

        SessionConfigManager.save(session.params)
        session.rebuildActiveChatService(KEEP_PER_PROVIDER_MODEL)

        info("✅ Model provider set to: ${provider.name.lowercase()}")
        info("💡 Use `:models` to list all available models for this provider.")
        info("💡 Then use `:set-param model <modelName>` to choose one.")

        if (!ProviderValidator.validate(provider, session.getCurrentProviderSettings())) {
            info("⚠️  This provider isn't fully configured yet.")
            info(ProviderValidator.getHelpText(provider))
            info("👉 Once you're ready, use `:set-param model <modelName>` to choose a model and start chatting.")
        }
    }
}

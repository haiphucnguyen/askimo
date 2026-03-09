/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.context.AppContext
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.ModelChangedEvent
import io.askimo.core.logging.display
import io.askimo.core.logging.logger
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ProviderRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private val appContext: AppContext,
) : CommandHandler {
    private val log = logger<SetProviderCommandHandler>()
    override val keyword: String = ":set-provider"
    override val description: String = "Set the current model provider (e.g., :set-provider openai)"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        if (args.isEmpty()) {
            log.display("❌ Usage: :set-provider <provider>")
            return
        }

        val input = args[0].trim().uppercase()
        val provider = runCatching { ModelProvider.valueOf(input) }.getOrNull()

        if (provider == null) {
            log.display("❌ Unknown provider: '$input'")
            log.display("💡 Use `:providers` to list all supported model providers.")
            return
        }

        if (!ProviderRegistry.getSupportedProviders().contains(provider)) {
            log.display("❌ Provider '$input' is not registered.")
            log.display("💡 Use `:providers` to see which providers are currently available.")
            return
        }

        val factory = appContext.getModelFactory(provider)
        if (factory == null) {
            log.display("❌ No factory registered for provider: ${provider.name.lowercase()}")
            return
        }

        // ✅ Switch provider and apply default settings if not already stored
        appContext.params.currentProvider = provider
        val providerSettings = appContext.getOrCreateProviderSettings(provider)
        appContext.setProviderSetting(
            provider,
            providerSettings,
        )

        appContext.params.model = appContext.params.getModel(provider)

        appContext.save()
        CoroutineScope(Dispatchers.Default).launch {
            EventBus.emit(ModelChangedEvent(provider, ""))
        }

        log.display("✅ Model provider set to: ${provider.name.lowercase()}")
        log.display("💡 Use `:models` to list all available models for this provider.")
        log.display("💡 Then use `:set-param model <modelName>` to choose one.")

        val settings = appContext.getCurrentProviderSettings()
        if (!settings.validate()) {
            log.display("⚠️  This provider isn't fully configured yet.")
            log.display(settings.getSetupHelpText(io.askimo.core.providers.DefaultMessageResolver.resolver))
            log.display("👉 Once you're ready, use `:set-param model <modelName>` to choose a model and start chatting.")
        }
    }
}

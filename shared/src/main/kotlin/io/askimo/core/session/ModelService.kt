/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ProviderRegistry

/**
 * Service for managing model-related operations.
 */
object ModelService {
    /**
     * Result of fetching available models.
     */
    sealed class ModelsResult {
        data class Success(val models: List<String>) : ModelsResult()
        data class Error(val message: String, val helpText: String? = null) : ModelsResult()
    }

    /**
     * Gets available models for a provider with user-friendly error messages.
     *
     * @param provider The provider to get models for
     * @param session The current session
     * @return ModelsResult containing either the list of models or an error message
     */
    fun getAvailableModels(provider: ModelProvider, session: Session): ModelsResult {
        val factory = ProviderRegistry.getFactory(provider)

        if (factory == null) {
            return ModelsResult.Error(
                message = "No model factory registered for provider: ${provider.name.lowercase()}",
                helpText = null,
            )
        }

        val models = factory.availableModels(
            session.params.providerSettings[provider] ?: factory.defaultSettings(),
        )

        if (models.isEmpty()) {
            val (message, helpText) = when (provider) {
                ModelProvider.OLLAMA -> Pair(
                    "No models available for Ollama",
                    """
                    You may not have any models installed yet.

                    Visit https://ollama.com/library to browse available models.
                    Then run: ollama pull <modelName> to install a model locally.

                    Example:
                    ollama pull llama3
                    """.trimIndent(),
                )
                ModelProvider.OPENAI -> Pair(
                    "No models available for OpenAI",
                    """
                    One possible reason is that you haven't provided your OpenAI API key yet.

                    1. Get your API key from: https://platform.openai.com/account/api-keys
                    2. Then set it in the Settings

                    Get an API key here:
                    https://platform.openai.com/api-keys
                    """.trimIndent(),
                )
                else -> Pair(
                    "No models available for provider: ${provider.name.lowercase()}",
                    "Please check your provider configuration.",
                )
            }
            return ModelsResult.Error(message, helpText)
        }

        return ModelsResult.Success(models)
    }

    /**
     * Changes the model for a session and rebuilds the chat service.
     *
     * @param session The session to update
     * @param newModel The new model name
     * @return true if successful, false otherwise
     */
    fun changeModel(session: Session, newModel: String): Boolean = try {
        // Update the model in session params
        session.params.model = newModel

        // Rebuild the chat service with the new model
        // Use KEEP_PER_PROVIDER_MODEL to preserve conversation history
        session.rebuildActiveChatService(MemoryPolicy.KEEP_PER_PROVIDER_MODEL)

        true
    } catch (_: Exception) {
        false
    }
}

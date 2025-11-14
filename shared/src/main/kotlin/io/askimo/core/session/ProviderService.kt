/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ProviderRegistry
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.ProviderValidator
import io.askimo.core.providers.anthropic.AnthropicSettings
import io.askimo.core.providers.gemini.GeminiSettings
import io.askimo.core.providers.ollama.OllamaSettings
import io.askimo.core.providers.openai.OpenAiSettings
import io.askimo.core.providers.xai.XAiSettings

/**
 * Represents a configurable field when setting up a provider.
 */
sealed class ProviderConfigField {
    abstract val name: String
    abstract val label: String
    abstract val description: String
    abstract val required: Boolean

    data class ApiKeyField(
        override val name: String = "apiKey",
        override val label: String = "API Key",
        override val description: String,
        override val required: Boolean = true,
        val value: String = "",
        val hasExistingValue: Boolean = false,
    ) : ProviderConfigField()

    data class BaseUrlField(
        override val name: String = "baseUrl",
        override val label: String = "Base URL",
        override val description: String,
        override val required: Boolean = true,
        val value: String = "",
    ) : ProviderConfigField()
}

/**
 * Result of a provider connection test.
 */
sealed class ProviderTestResult {
    data object Success : ProviderTestResult()
    data class Failure(val message: String, val helpText: String? = null) : ProviderTestResult()
}

/**
 * Service for managing provider selection and configuration.
 */
object ProviderService {
    /**
     * Gets all available providers.
     */
    fun getAvailableProviders(): List<ModelProvider> = ProviderRegistry.getSupportedProviders().toList()

    /**
     * Gets the configuration fields required for a provider.
     */
    fun getProviderConfigFields(provider: ModelProvider, existingSettings: ProviderSettings? = null): List<ProviderConfigField> {
        // Helper to check if API key is already stored securely
        fun isApiKeyStored(apiKey: String?): Boolean = apiKey != null && (apiKey == "***keychain***" || apiKey.startsWith("encrypted:") || apiKey.isNotBlank())

        return when (provider) {
            ModelProvider.OPENAI -> {
                val settings = existingSettings as? OpenAiSettings
                val hasStoredKey = isApiKeyStored(settings?.apiKey)
                listOf(
                    ProviderConfigField.ApiKeyField(
                        description = if (hasStoredKey) {
                            "API key already stored securely. Leave blank to keep existing key, or enter a new one to update."
                        } else {
                            "Your OpenAI API key from https://platform.openai.com/account/api-keys"
                        },
                        value = "",
                        hasExistingValue = hasStoredKey,
                    ),
                )
            }
            ModelProvider.XAI -> {
                val settings = existingSettings as? XAiSettings
                val hasStoredKey = isApiKeyStored(settings?.apiKey)
                listOf(
                    ProviderConfigField.ApiKeyField(
                        description = if (hasStoredKey) {
                            "API key already stored securely. Leave blank to keep existing key, or enter a new one to update."
                        } else {
                            "Your XAI API key from https://console.x.ai/"
                        },
                        value = "",
                        hasExistingValue = hasStoredKey,
                    ),
                )
            }
            ModelProvider.GEMINI -> {
                val settings = existingSettings as? GeminiSettings
                val hasStoredKey = isApiKeyStored(settings?.apiKey)
                listOf(
                    ProviderConfigField.ApiKeyField(
                        description = if (hasStoredKey) {
                            "API key already stored securely. Leave blank to keep existing key, or enter a new one to update."
                        } else {
                            "Your Google Gemini API key from https://makersuite.google.com/"
                        },
                        value = "",
                        hasExistingValue = hasStoredKey,
                    ),
                )
            }
            ModelProvider.ANTHROPIC -> {
                val settings = existingSettings as? AnthropicSettings
                val hasStoredKey = isApiKeyStored(settings?.apiKey)
                listOf(
                    ProviderConfigField.ApiKeyField(
                        description = if (hasStoredKey) {
                            "API key already stored securely. Leave blank to keep existing key, or enter a new one to update."
                        } else {
                            "Your Anthropic API key from https://console.anthropic.com/account/keys"
                        },
                        value = "",
                        hasExistingValue = hasStoredKey,
                    ),
                )
            }
            ModelProvider.OLLAMA -> listOf(
                ProviderConfigField.BaseUrlField(
                    description = "Ollama server URL (default: http://localhost:11434)",
                    value = (existingSettings as? OllamaSettings)?.baseUrl ?: "http://localhost:11434",
                ),
            )
            else -> emptyList()
        }
    }

    /**
     * Creates provider settings from configuration fields.
     */
    fun createProviderSettings(
        provider: ModelProvider,
        fields: Map<String, String>,
        existingSettings: ProviderSettings? = null,
    ): ProviderSettings {
        val factory = ProviderRegistry.getFactory(provider)
        val defaults = factory?.defaultSettings() ?: return existingSettings ?: throw IllegalStateException("No factory for provider")

        return when (provider) {
            ModelProvider.OPENAI -> {
                val current = existingSettings as? OpenAiSettings ?: defaults as OpenAiSettings
                val newApiKey = fields["apiKey"]?.takeIf { it.isNotBlank() } ?: current.apiKey
                current.copy(apiKey = newApiKey)
            }
            ModelProvider.XAI -> {
                val current = existingSettings as? XAiSettings ?: defaults as XAiSettings
                val newApiKey = fields["apiKey"]?.takeIf { it.isNotBlank() } ?: current.apiKey
                current.copy(apiKey = newApiKey)
            }
            ModelProvider.GEMINI -> {
                val current = existingSettings as? GeminiSettings ?: defaults as GeminiSettings
                val newApiKey = fields["apiKey"]?.takeIf { it.isNotBlank() } ?: current.apiKey
                current.copy(apiKey = newApiKey)
            }
            ModelProvider.ANTHROPIC -> {
                val current = existingSettings as? AnthropicSettings ?: defaults as AnthropicSettings
                val newApiKey = fields["apiKey"]?.takeIf { it.isNotBlank() } ?: current.apiKey
                current.copy(apiKey = newApiKey)
            }
            ModelProvider.OLLAMA -> {
                val current = existingSettings as? OllamaSettings ?: defaults as OllamaSettings
                current.copy(baseUrl = fields["baseUrl"] ?: current.baseUrl)
            }
            else -> existingSettings ?: defaults
        }
    }

    /**
     * Tests if the provider can be connected with the given settings.
     */
    fun testProviderConnection(provider: ModelProvider, settings: ProviderSettings): ProviderTestResult {
        val isValid = ProviderValidator.validate(provider, settings)
        return if (isValid) {
            ProviderTestResult.Success
        } else {
            val helpText = ProviderValidator.getHelpText(provider)
            ProviderTestResult.Failure(
                message = "Cannot connect to ${provider.name.lowercase()} provider",
                helpText = helpText,
            )
        }
    }

    /**
     * Changes the active provider in a session.
     */
    fun changeProvider(
        session: Session,
        provider: ModelProvider,
        settings: ProviderSettings,
    ): Boolean = try {
        // Update session parameters
        session.params.currentProvider = provider
        session.setProviderSetting(provider, settings)

        // Get or set default model
        var model = session.params.getModel(provider)
        if (model.isBlank()) {
            model = settings.defaultModel
        }
        session.params.model = model

        // Save configuration
        SessionConfigManager.save(session.params)

        // Rebuild chat service
        session.rebuildActiveChatService(MemoryPolicy.KEEP_PER_PROVIDER_MODEL)

        true
    } catch (_: Exception) {
        false
    }

    /**
     * Validates that all required fields are filled.
     */
    fun validateConfigFields(fields: Map<String, String>, configFields: List<ProviderConfigField>): Boolean = configFields.all { field ->
        if (field.required) {
            // For API key fields with existing values, blank is acceptable (means keep existing)
            if (field is ProviderConfigField.ApiKeyField && field.hasExistingValue) {
                true
            } else {
                val value = fields[field.name]
                value != null && value.isNotBlank()
            }
        } else {
            true
        }
    }
}

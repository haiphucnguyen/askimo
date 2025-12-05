/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

/**
 * Marker interface for model provider-specific configuration settings.
 *
 * This interface is implemented by various provider-specific settings classes
 * that contain configuration parameters needed for different LLM providers
 * (like OpenAI, Ollama, etc.). Each implementation contains the specific
 * parameters required by its respective provider.
 */
interface ProviderSettings {
    val defaultModel: String

    /**
     * Provider-wide presets that control style and verbosity.
     */
    var presets: Presets

    /**
     * Returns a human-readable description of the provider settings.
     *
     * This method returns a list of strings where each string represents
     * a key configuration parameter in a formatted way. Implementations
     * should include the most important settings and may hide sensitive
     * information (like API keys) for security reasons.
     *
     * @return A list of strings describing the current configuration settings
     */
    fun describe(): List<String>

    /**
     * Returns the list of configurable fields for this provider's settings.
     */
    fun getFields(): List<SettingField>

    /**
     * Updates a field value in the settings and returns the updated settings.
     */
    fun updateField(fieldName: String, value: String): ProviderSettings

    /**
     * Validates that this provider's settings are properly configured.
     * Each provider can implement its own validation logic.
     *
     * @return true if settings are valid and provider is ready to use, false otherwise
     */
    fun validate(): Boolean = true

    /**
     * Returns helpful guidance text to display when settings validation fails.
     * Each provider can provide specific instructions for setup.
     *
     * @param messageResolver Function to resolve i18n message keys. For CLI, pass default English resolver.
     * @return Help text explaining how to properly configure this provider
     */
    fun getSetupHelpText(messageResolver: (String) -> String): String = "Please check your provider configuration."

    /**
     * Returns the configuration fields required to set up this provider.
     * Each provider can define what fields users need to configure.
     *
     * @param messageResolver Function to resolve i18n message keys. For CLI, pass default English resolver.
     * @return List of configuration fields for UI display
     */
    fun getConfigFields(messageResolver: (String) -> String): List<ProviderConfigField> = emptyList()

    /**
     * Applies configuration field values to create updated settings.
     * Each provider handles how to merge new values with existing settings.
     *
     * @param fields Map of field names to their new values
     * @return Updated settings with the new field values applied
     */
    fun applyConfigFields(fields: Map<String, String>): ProviderSettings = this

    /**
     * Creates a deep copy of these settings to avoid shared mutable state.
     * Used for defensive copying when loading/saving sessions.
     *
     * @return A new instance with the same values
     */
    fun deepCopy(): ProviderSettings
}

interface HasApiKey {
    var apiKey: String

    /**
     * Masks the API key for safe logging and display.
     * Shows first 4 characters followed by asterisks, or just asterisks for short/special keys.
     */
    fun maskApiKey(): String {
        if (apiKey.isBlank()) return "****"
        if (apiKey == "***keychain***" || apiKey.startsWith("encrypted:")) return "****"

        return when {
            apiKey.length <= 4 -> "****"
            else -> "${apiKey.take(4)}****"
        }
    }
}

interface HasBaseUrl {
    var baseUrl: String
}

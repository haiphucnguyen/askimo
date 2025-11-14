/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.Style
import io.askimo.core.providers.Verbosity
import io.askimo.core.providers.anthropic.AnthropicSettings
import io.askimo.core.providers.gemini.GeminiSettings
import io.askimo.core.providers.ollama.OllamaSettings
import io.askimo.core.providers.openai.OpenAiSettings
import io.askimo.core.providers.xai.XAiSettings

/**
 * Represents a configuration field in provider settings.
 */
sealed class SettingField {
    abstract val name: String
    abstract val label: String
    abstract val description: String

    data class TextField(
        override val name: String,
        override val label: String,
        override val description: String,
        val value: String,
        val isPassword: Boolean = false,
    ) : SettingField()

    data class EnumField(
        override val name: String,
        override val label: String,
        override val description: String,
        val value: String,
        val options: List<EnumOption>,
    ) : SettingField()

    data class EnumOption(
        val value: String,
        val label: String,
        val description: String,
    )
}

/**
 * Service for managing provider settings configuration.
 */
object SettingsService {
    /**
     * Gets the list of configurable fields for a provider's settings.
     */
    fun getSettingsFields(provider: ModelProvider, settings: ProviderSettings): List<SettingField> = when (provider) {
        ModelProvider.OLLAMA -> getOllamaFields(settings as? OllamaSettings ?: OllamaSettings())
        ModelProvider.OPENAI -> getOpenAiFields(settings as? OpenAiSettings ?: OpenAiSettings())
        ModelProvider.ANTHROPIC -> getAnthropicFields(settings as? AnthropicSettings ?: AnthropicSettings())
        ModelProvider.GEMINI -> getGeminiFields(settings as? GeminiSettings ?: GeminiSettings())
        ModelProvider.XAI -> getXAiFields(settings as? XAiSettings ?: XAiSettings())
        else -> emptyList()
    }

    /**
     * Updates a field value in the settings and returns the updated settings.
     */
    fun updateSettingField(
        provider: ModelProvider,
        settings: ProviderSettings,
        fieldName: String,
        value: String,
    ): ProviderSettings = when (provider) {
        ModelProvider.OLLAMA -> updateOllamaField(settings as OllamaSettings, fieldName, value)
        ModelProvider.OPENAI -> updateOpenAiField(settings as OpenAiSettings, fieldName, value)
        ModelProvider.ANTHROPIC -> updateAnthropicField(settings as AnthropicSettings, fieldName, value)
        ModelProvider.GEMINI -> updateGeminiField(settings as GeminiSettings, fieldName, value)
        ModelProvider.XAI -> updateXAiField(settings as XAiSettings, fieldName, value)
        else -> settings
    }

    private fun getStyleOptions() = listOf(
        SettingField.EnumOption("PRECISE", "Precise", "Focused, deterministic responses with minimal creativity"),
        SettingField.EnumOption("BALANCED", "Balanced", "Moderate creativity with good coherence"),
        SettingField.EnumOption("CREATIVE", "Creative", "More varied and creative responses"),
    )

    private fun getVerbosityOptions() = listOf(
        SettingField.EnumOption("SHORT", "Short", "Concise, brief responses"),
        SettingField.EnumOption("NORMAL", "Normal", "Standard response length"),
        SettingField.EnumOption("LONG", "Long", "Detailed, comprehensive responses"),
    )

    // Ollama Settings
    private fun getOllamaFields(settings: OllamaSettings) = listOf(
        SettingField.TextField(
            name = "baseUrl",
            label = "Base URL",
            description = "Ollama server URL",
            value = settings.baseUrl,
        ),
        SettingField.EnumField(
            name = "style",
            label = "Style",
            description = "Response style preference",
            value = settings.presets.style.name,
            options = getStyleOptions(),
        ),
        SettingField.EnumField(
            name = "verbosity",
            label = "Verbosity",
            description = "Response length preference",
            value = settings.presets.verbosity.name,
            options = getVerbosityOptions(),
        ),
    )

    private fun updateOllamaField(settings: OllamaSettings, fieldName: String, value: String): OllamaSettings = when (fieldName) {
        "baseUrl" -> settings.copy(baseUrl = value)
        "style" -> settings.copy(presets = settings.presets.copy(style = Style.valueOf(value)))
        "verbosity" -> settings.copy(presets = settings.presets.copy(verbosity = Verbosity.valueOf(value)))
        else -> settings
    }

    // OpenAI Settings
    private fun getOpenAiFields(settings: OpenAiSettings) = listOf(
        SettingField.TextField(
            name = "apiKey",
            label = "API Key",
            description = "OpenAI API key",
            value = settings.apiKey,
            isPassword = true,
        ),
        SettingField.EnumField(
            name = "style",
            label = "Style",
            description = "Response style preference",
            value = settings.presets.style.name,
            options = getStyleOptions(),
        ),
        SettingField.EnumField(
            name = "verbosity",
            label = "Verbosity",
            description = "Response length preference",
            value = settings.presets.verbosity.name,
            options = getVerbosityOptions(),
        ),
    )

    private fun updateOpenAiField(settings: OpenAiSettings, fieldName: String, value: String): OpenAiSettings = when (fieldName) {
        "apiKey" -> settings.copy(apiKey = value)
        "style" -> settings.copy(presets = settings.presets.copy(style = Style.valueOf(value)))
        "verbosity" -> settings.copy(presets = settings.presets.copy(verbosity = Verbosity.valueOf(value)))
        else -> settings
    }

    // Anthropic Settings
    private fun getAnthropicFields(settings: AnthropicSettings) = listOf(
        SettingField.TextField(
            name = "apiKey",
            label = "API Key",
            description = "Anthropic API key",
            value = settings.apiKey,
            isPassword = true,
        ),
        SettingField.TextField(
            name = "baseUrl",
            label = "Base URL",
            description = "Anthropic API base URL",
            value = settings.baseUrl,
        ),
        SettingField.EnumField(
            name = "style",
            label = "Style",
            description = "Response style preference",
            value = settings.presets.style.name,
            options = getStyleOptions(),
        ),
        SettingField.EnumField(
            name = "verbosity",
            label = "Verbosity",
            description = "Response length preference",
            value = settings.presets.verbosity.name,
            options = getVerbosityOptions(),
        ),
    )

    private fun updateAnthropicField(settings: AnthropicSettings, fieldName: String, value: String): AnthropicSettings = when (fieldName) {
        "apiKey" -> settings.copy(apiKey = value)
        "baseUrl" -> settings.copy(baseUrl = value)
        "style" -> settings.copy(presets = settings.presets.copy(style = Style.valueOf(value)))
        "verbosity" -> settings.copy(presets = settings.presets.copy(verbosity = Verbosity.valueOf(value)))
        else -> settings
    }

    // Gemini Settings
    private fun getGeminiFields(settings: GeminiSettings) = listOf(
        SettingField.TextField(
            name = "apiKey",
            label = "API Key",
            description = "Google Gemini API key",
            value = settings.apiKey,
            isPassword = true,
        ),
        SettingField.TextField(
            name = "baseUrl",
            label = "Base URL",
            description = "Gemini API base URL",
            value = settings.baseUrl,
        ),
        SettingField.EnumField(
            name = "style",
            label = "Style",
            description = "Response style preference",
            value = settings.presets.style.name,
            options = getStyleOptions(),
        ),
        SettingField.EnumField(
            name = "verbosity",
            label = "Verbosity",
            description = "Response length preference",
            value = settings.presets.verbosity.name,
            options = getVerbosityOptions(),
        ),
    )

    private fun updateGeminiField(settings: GeminiSettings, fieldName: String, value: String): GeminiSettings = when (fieldName) {
        "apiKey" -> settings.copy(apiKey = value)
        "baseUrl" -> settings.copy(baseUrl = value)
        "style" -> settings.copy(presets = settings.presets.copy(style = Style.valueOf(value)))
        "verbosity" -> settings.copy(presets = settings.presets.copy(verbosity = Verbosity.valueOf(value)))
        else -> settings
    }

    // XAi Settings
    private fun getXAiFields(settings: XAiSettings) = listOf(
        SettingField.TextField(
            name = "apiKey",
            label = "API Key",
            description = "xAI API key",
            value = settings.apiKey,
            isPassword = true,
        ),
        SettingField.TextField(
            name = "baseUrl",
            label = "Base URL",
            description = "xAI API base URL",
            value = settings.baseUrl,
        ),
        SettingField.EnumField(
            name = "style",
            label = "Style",
            description = "Response style preference",
            value = settings.presets.style.name,
            options = getStyleOptions(),
        ),
        SettingField.EnumField(
            name = "verbosity",
            label = "Verbosity",
            description = "Response length preference",
            value = settings.presets.verbosity.name,
            options = getVerbosityOptions(),
        ),
    )

    private fun updateXAiField(settings: XAiSettings, fieldName: String, value: String): XAiSettings = when (fieldName) {
        "apiKey" -> settings.copy(apiKey = value)
        "baseUrl" -> settings.copy(baseUrl = value)
        "style" -> settings.copy(presets = settings.presets.copy(style = Style.valueOf(value)))
        "verbosity" -> settings.copy(presets = settings.presets.copy(verbosity = Verbosity.valueOf(value)))
        else -> settings
    }
}

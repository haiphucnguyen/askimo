/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.gemini

import io.askimo.core.providers.HasApiKey
import io.askimo.core.providers.Presets
import io.askimo.core.providers.ProviderConfigField
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.SettingField
import io.askimo.core.providers.Style
import io.askimo.core.providers.Verbosity
import io.askimo.core.providers.createCommonPresetFields
import io.askimo.core.providers.updatePresetField
import kotlinx.serialization.Serializable

@Serializable
data class GeminiSettings(
    val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta/openai",
    override var apiKey: String = "",
    override val defaultModel: String = "gemini-2.5-flash",
    override var presets: Presets = Presets(Style.BALANCED, Verbosity.NORMAL),
) : ProviderSettings,
    HasApiKey {
    override fun describe(): List<String> = listOf(
        "apiKey:  ${apiKey.take(5)}***",
        "baseUrl: $baseUrl",
        "presets: $presets",
    )

    override fun getFields(): List<SettingField> = listOf(
        SettingField.TextField(
            name = SettingField.API_KEY,
            label = "API Key",
            description = "Google Gemini API key",
            value = apiKey,
            isPassword = true,
        ),
        SettingField.TextField(
            name = SettingField.BASE_URL,
            label = "Base URL",
            description = "Gemini API base URL",
            value = baseUrl,
        ),
    ) + createCommonPresetFields(presets)

    override fun updateField(fieldName: String, value: String): ProviderSettings {
        updatePresetField(presets, fieldName, value)?.let { return copy(presets = it) }

        return when (fieldName) {
            SettingField.API_KEY -> copy(apiKey = value)
            SettingField.BASE_URL -> copy(baseUrl = value)
            else -> this
        }
    }

    override fun validate(): Boolean = apiKey.isNotBlank()

    override fun getSetupHelpText(): String = """
        💡 To use Google Gemini, you need to provide an API key.

        1. Get your API key from: https://aistudio.google.com/app/apikey
        2. Then set it in the Settings or using: :change-settings

        Learn more: https://ai.google.dev/gemini-api/docs/api-key
    """.trimIndent()

    override fun getConfigFields(): List<ProviderConfigField> {
        val hasStoredKey = apiKey.isNotBlank() && (apiKey == "***keychain***" || apiKey.startsWith("encrypted:"))
        return listOf(
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

    override fun applyConfigFields(fields: Map<String, String>): ProviderSettings {
        val newApiKey = fields["apiKey"]?.takeIf { it.isNotBlank() } ?: apiKey
        return copy(apiKey = newApiKey)
    }

    override fun deepCopy(): ProviderSettings = copy()
}

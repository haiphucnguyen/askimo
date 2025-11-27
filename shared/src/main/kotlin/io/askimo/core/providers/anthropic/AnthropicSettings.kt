/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.anthropic

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
data class AnthropicSettings(
    val baseUrl: String = "https://api.anthropic.com/v1",
    override var apiKey: String = "default",
    override val defaultModel: String = "claude-sonnet-4-5",
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
            description = "Anthropic API key",
            value = apiKey,
            isPassword = true,
        ),
        SettingField.TextField(
            name = SettingField.BASE_URL,
            label = "Base URL",
            description = "Anthropic API base URL",
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
        💡 To use Anthropic Claude, you need to provide an API key.

        1. Get your API key from: https://console.anthropic.com/settings/keys
        2. Then set it in the Settings or using: :change-settings

        Learn more: https://docs.anthropic.com/claude/reference/getting-started-with-the-api
    """.trimIndent()

    override fun getConfigFields(): List<ProviderConfigField> {
        val hasStoredKey = apiKey.isNotBlank() && (apiKey == "***keychain***" || apiKey.startsWith("encrypted:"))
        return listOf(
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

    override fun applyConfigFields(fields: Map<String, String>): ProviderSettings {
        val newApiKey = fields["apiKey"]?.takeIf { it.isNotBlank() } ?: apiKey
        return copy(apiKey = newApiKey)
    }

    override fun deepCopy(): ProviderSettings = copy()
}

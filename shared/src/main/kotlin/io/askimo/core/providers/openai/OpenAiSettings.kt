/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.openai

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
data class OpenAiSettings(
    override var apiKey: String = "",
    override val defaultModel: String = "gpt-4o",
    override var presets: Presets = Presets(Style.BALANCED, Verbosity.NORMAL),
) : ProviderSettings,
    HasApiKey {
    override fun describe(): List<String> = listOf(
        "apiKey:  ${apiKey.take(5)}***",
        "presets: $presets",
    )

    override fun toString(): String = "OpenAiSettings(apiKey=****, presets=$presets)"

    override fun getFields(): List<SettingField> = listOf(
        SettingField.TextField(
            name = SettingField.API_KEY,
            label = "API Key",
            description = "OpenAI API key",
            value = apiKey,
            isPassword = true,
        ),
    ) + createCommonPresetFields(presets)

    override fun updateField(fieldName: String, value: String): ProviderSettings {
        updatePresetField(presets, fieldName, value)?.let { return copy(presets = it) }

        return when (fieldName) {
            SettingField.API_KEY -> copy(apiKey = value)
            else -> this
        }
    }

    override fun validate(): Boolean = apiKey.isNotBlank()

    override fun getSetupHelpText(): String = """
        💡 To use OpenAI, you need to provide an API key.

        1. Get your API key from: https://platform.openai.com/account/api-keys
        2. Then set it in the Settings or using: :change-settings

        Get an API key here: https://platform.openai.com/api-keys
    """.trimIndent()

    override fun getConfigFields(): List<ProviderConfigField> {
        val hasStoredKey = apiKey.isNotBlank() && (apiKey == "***keychain***" || apiKey.startsWith("encrypted:"))
        return listOf(
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

    override fun applyConfigFields(fields: Map<String, String>): ProviderSettings {
        val newApiKey = fields["apiKey"]?.takeIf { it.isNotBlank() } ?: apiKey
        return copy(apiKey = newApiKey)
    }

    override fun deepCopy(): ProviderSettings = copy()
}

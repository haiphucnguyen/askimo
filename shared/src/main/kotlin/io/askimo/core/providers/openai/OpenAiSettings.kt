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
        "apiKey:  ${maskApiKey()}",
        "presets: $presets",
    )

    override fun toString(): String = "OpenAiSettings(apiKey=${maskApiKey()}, presets=$presets)"

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

    override fun getSetupHelpText(messageResolver: (String) -> String): String = messageResolver("provider.openai.setup.help")

    override fun getConfigFields(messageResolver: (String) -> String): List<ProviderConfigField> {
        val hasStoredKey = apiKey.isNotBlank() && (apiKey == "***keychain***" || apiKey.startsWith("encrypted:"))

        val description = if (hasStoredKey) {
            messageResolver("provider.openai.apikey.stored")
        } else {
            messageResolver("provider.openai.apikey.description")
        }

        return listOf(
            ProviderConfigField.ApiKeyField(
                description = description,
                value = apiKey,
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

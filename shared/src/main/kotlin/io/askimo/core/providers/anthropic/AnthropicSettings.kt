/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.anthropic

import io.askimo.core.providers.HasApiKey
import io.askimo.core.providers.ProviderConfigField
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.SettingField
import kotlinx.serialization.Serializable

@Serializable
data class AnthropicSettings(
    val baseUrl: String = "https://api.anthropic.com/v1",
    override var apiKey: String = "default",
    override val defaultModel: String = "claude-sonnet-4-5",
    val enableAiSummarization: Boolean = true,
    val summarizerModel: String = "claude-3-5-haiku-20241022", // Fast and cheap model for summarization
) : ProviderSettings,
    HasApiKey {
    override fun describe(): List<String> = listOf(
        "apiKey:  ${maskApiKey()}",
        "baseUrl: $baseUrl",
    )

    override fun toString(): String = "AnthropicSettings(baseUrl=$baseUrl, apiKey=${maskApiKey()})"

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
    )

    override fun updateField(fieldName: String, value: String): ProviderSettings = when (fieldName) {
        SettingField.API_KEY -> copy(apiKey = value)
        SettingField.BASE_URL -> copy(baseUrl = value)
        else -> this
    }

    override fun validate(): Boolean = apiKey.isNotBlank()

    override fun getSetupHelpText(messageResolver: (String) -> String): String = messageResolver("provider.anthropic.setup.help")

    override fun getConfigFields(messageResolver: (String) -> String): List<ProviderConfigField> {
        val hasStoredKey = apiKey.isNotBlank() && (apiKey == "***keychain***" || apiKey.startsWith("encrypted:"))

        val description = if (hasStoredKey) {
            messageResolver("provider.anthropic.apikey.stored")
        } else {
            messageResolver("provider.anthropic.apikey.description")
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

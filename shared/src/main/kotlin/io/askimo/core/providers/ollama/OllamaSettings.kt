/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.ollama

import io.askimo.core.providers.HasBaseUrl
import io.askimo.core.providers.ProviderConfigField
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.SettingField
import kotlinx.serialization.Serializable

@Serializable
data class OllamaSettings(
    override var baseUrl: String = "http://localhost:11434/v1",
    override val defaultModel: String = "",
) : ProviderSettings,
    HasBaseUrl {
    override fun describe(): List<String> = listOf(
        "baseUrl:     $baseUrl",
    )

    override fun getFields(): List<SettingField> = listOf(
        SettingField.TextField(
            name = SettingField.BASE_URL,
            label = "Base URL",
            description = "Ollama server URL",
            value = baseUrl,
        ),
    )

    override fun updateField(fieldName: String, value: String): ProviderSettings = when (fieldName) {
        SettingField.BASE_URL -> copy(baseUrl = value)
        else -> this
    }

    override fun getSetupHelpText(messageResolver: (String) -> String): String = messageResolver("provider.ollama.setup.help")

    override fun getConfigFields(messageResolver: (String) -> String): List<ProviderConfigField> = listOf(
        ProviderConfigField.BaseUrlField(
            description = messageResolver("provider.ollama.baseurl.description"),
            value = baseUrl,
        ),
    )

    override fun applyConfigFields(fields: Map<String, String>): ProviderSettings {
        val newBaseUrl = fields["baseUrl"] ?: baseUrl
        return copy(baseUrl = newBaseUrl)
    }

    override fun deepCopy(): ProviderSettings = copy()
}

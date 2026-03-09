/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.context

import com.fasterxml.jackson.annotation.JsonIgnore
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.SettingField

data class AppContextParams(
    /**
     * The currently active model provider.
     */
    var currentProvider: ModelProvider = ModelProvider.UNKNOWN,
    /**
     * Maps each provider to its settings (e.g., API key, temperature, base URL).
     */
    var providerSettings: MutableMap<ModelProvider, ProviderSettings> = mutableMapOf(),
) {
    companion object {
        fun noOp(): AppContextParams = AppContextParams()
    }

    /**
     * Current model for the active provider — reads/writes defaultModel on the active provider's settings.
     */
    @get:JsonIgnore
    @set:JsonIgnore
    var model: String
        get() = getModel(currentProvider)
        set(value) {
            val settings = providerSettings[currentProvider] ?: return
            providerSettings[currentProvider] = settings.updateField(SettingField.DEFAULT_MODEL, value)
        }

    /**
     * Gets the last-used model for a given provider, sourced from its defaultModel setting.
     */
    fun getModel(provider: ModelProvider): String = providerSettings[provider]?.defaultModel ?: ""

    override fun toString(): String {
        val maskedSettings = providerSettings.mapValues { (_, settings) ->
            settings.toString()
        }
        return "AppContextParams(currentProvider=$currentProvider, providerSettings=$maskedSettings)"
    }
}

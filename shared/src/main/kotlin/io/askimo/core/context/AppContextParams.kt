/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.context

import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ProviderSettings
import kotlinx.serialization.Serializable

@Serializable
data class AppContextParams(
    /**
     * Maps each provider to its last-used model name.
     */
    var models: MutableMap<ModelProvider, String> = mutableMapOf(),
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
     * Current model for the active provider.
     */
    var model: String
        get() = models[currentProvider] ?: ""
        set(value) {
            models[currentProvider] = value
        }

    /**
     * Gets the last-used model for a given provider.
     */
    fun getModel(provider: ModelProvider): String = models[provider] ?: ""

    override fun toString(): String {
        val maskedSettings = providerSettings.mapValues { (_, settings) ->
            settings.toString()
        }
        return "AppContextParams(models=$models, currentProvider=$currentProvider, providerSettings=$maskedSettings)"
    }
}

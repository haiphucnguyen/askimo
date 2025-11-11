/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ProviderSettings
import kotlinx.serialization.Serializable

@Serializable
data class SessionParams(
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
        fun noOp(): SessionParams = SessionParams()
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
}

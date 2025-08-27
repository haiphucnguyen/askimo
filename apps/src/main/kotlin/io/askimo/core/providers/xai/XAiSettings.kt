/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.xai

import io.askimo.core.providers.HasApiKey
import io.askimo.core.providers.Presets
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.Style
import io.askimo.core.providers.Verbosity
import kotlinx.serialization.Serializable

@Serializable
data class XAiSettings(
    val baseUrl: String = "https://api.x.ai/v1",
    override var apiKey: String = "",
    override val defaultModel: String = "grok-4",
    override var presets: Presets = Presets(Style.BALANCED, Verbosity.NORMAL),
) : ProviderSettings,
    HasApiKey {
    override fun describe(): List<String> =
        listOf(
            "apiKey:  ${apiKey.take(5)}***",
            "baseUrl: $baseUrl",
            "presets: $presets",
        )
}

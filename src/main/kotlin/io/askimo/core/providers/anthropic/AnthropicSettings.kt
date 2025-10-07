/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.anthropic

import io.askimo.core.providers.HasApiKey
import io.askimo.core.providers.Presets
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.Style
import io.askimo.core.providers.Verbosity
import kotlinx.serialization.Serializable

@Serializable
data class AnthropicSettings(
    val baseUrl: String = "https://api.anthropic.com/v1",
    override var apiKey: String = "default",
    override val defaultModel: String = "claude-sonnet-4-5",
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

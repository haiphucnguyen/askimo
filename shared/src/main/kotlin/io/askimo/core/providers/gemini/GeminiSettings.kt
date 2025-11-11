/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.gemini

import io.askimo.core.providers.HasApiKey
import io.askimo.core.providers.Presets
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.Style
import io.askimo.core.providers.Verbosity
import kotlinx.serialization.Serializable

@Serializable
data class GeminiSettings(
    val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta/openai",
    override var apiKey: String = "",
    override val defaultModel: String = "gemini-2.5-flash",
    override var presets: Presets = Presets(Style.BALANCED, Verbosity.NORMAL),
) : ProviderSettings,
    HasApiKey {
    override fun describe(): List<String> = listOf(
        "apiKey:  ${apiKey.take(5)}***",
        "baseUrl: $baseUrl",
        "presets: $presets",
    )
}

package io.askimo.core.providers.openai

import io.askimo.core.providers.Presets
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.Style
import io.askimo.core.providers.Verbosity
import kotlinx.serialization.Serializable

@Serializable
data class OpenAiSettings(
    var apiKey: String = "",
    override var presets: Presets = Presets(Style.BALANCED, Verbosity.NORMAL),
) : ProviderSettings {
    override fun describe(): List<String> =
        listOf(
            "apiKey:      ${apiKey.take(5)}***",
            "presets: $presets",
        )

    override fun toString(): String = "OpenAiSettings(apiKey=****, presets=$presets)"
}

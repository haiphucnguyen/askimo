package io.askimo.core.providers.ollama

import io.askimo.core.providers.Presets
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.Style
import io.askimo.core.providers.Verbosity
import kotlinx.serialization.Serializable

@Serializable
data class OllamaSettings(
    var baseUrl: String = "http://localhost:11434",
    override var presets: Presets = Presets(Style.BALANCED, Verbosity.NORMAL),
) : ProviderSettings {
    override fun describe(): List<String> = listOf(
        "baseUrl:     $baseUrl",
        "presets: $presets",
    )
}

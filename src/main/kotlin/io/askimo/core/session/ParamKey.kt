package io.askimo.core.session

import io.askimo.core.providers.HasApiKey
import io.askimo.core.providers.HasBaseUrl
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.Style
import io.askimo.core.providers.Verbosity

enum class ParamKey(
    val key: String,
    val type: String,
    val description: String,
    val secure: Boolean = false,
    val isSupported: (ProviderSettings) -> Boolean,
    val getValue: (SessionParams, ProviderSettings) -> Any?,
    val setValue: (SessionParams, ProviderSettings, String) -> Unit,
    val suggestions: List<String> = emptyList(),
) {
    MODEL_NAME(
        key = "model",
        type = "String",
        description = "Model name to use (e.g., gpt-4o, grok-4, llama3)",
        isSupported = { true },
        getValue = { params, _ -> params.model },
        setValue = { params, _, v -> params.model = v },
    ),

    STYLE(
        key = "style",
        type = "Enum(precise|balanced|creative)",
        description = "Output style (determinism vs. creativity)",
        isSupported = { true },
        getValue = { _, settings ->
            settings.presets.style.name
                .lowercase()
        },
        setValue = { _, settings, v ->
            val style =
                when (v.trim().lowercase()) {
                    "precise" -> Style.PRECISE
                    "balanced" -> Style.BALANCED
                    "creative" -> Style.CREATIVE
                    else -> throw IllegalArgumentException("Use: precise | balanced | creative")
                }
            settings.presets = settings.presets.copy(style = style)
        },
        suggestions = listOf("precise", "balanced", "creative"),
    ),

    VERBOSITY(
        key = "verbosity",
        type = "Enum(short|normal|long)",
        description = "Controls response length/cost",
        isSupported = { true },
        getValue = { _, settings ->
            settings.presets.verbosity.name
                .lowercase()
        },
        setValue = { _, settings, v ->
            val vb =
                when (v.trim().lowercase()) {
                    "short" -> Verbosity.SHORT
                    "normal" -> Verbosity.NORMAL
                    "long" -> Verbosity.LONG
                    else -> throw IllegalArgumentException("Use: short | normal | long")
                }
            settings.presets = settings.presets.copy(verbosity = vb)
        },
        suggestions = listOf("short", "normal", "long"),
    ),

    API_KEY(
        key = "api_key",
        type = "String",
        secure = true,
        description = "API key for the current provider",
        isSupported = { it is HasApiKey },
        getValue = { _, settings -> (settings as HasApiKey).apiKey },
        setValue = { _, settings, v -> (settings as HasApiKey).apiKey = v },
    ),

    // ✅ Generic base URL (works for OpenAI, xAI, OpenRouter, etc.)
    BASE_URL(
        key = "base_url",
        type = "String",
        description = "Base URL for the current provider",
        isSupported = { it is HasBaseUrl },
        getValue = { _, settings -> (settings as HasBaseUrl).baseUrl },
        setValue = { _, settings, v -> (settings as HasBaseUrl).baseUrl = v },
    ),
    ;

    companion object {
        fun all(): List<ParamKey> = entries

        fun fromInput(key: String): ParamKey? = entries.find { it.key.equals(key, ignoreCase = true) }

        // List only the parameters supported by THIS provider's settings
        fun supportedFor(settings: ProviderSettings): List<ParamKey> = entries.filter { it.isSupported(settings) }
    }

    fun applyTo(
        params: SessionParams,
        providerSettings: ProviderSettings,
        value: String,
    ) {
        try {
            setValue(params, providerSettings, value)
        } catch (e: Exception) {
            throw IllegalArgumentException("❌ Failed to set '$key': expected type $type. ${e.message}")
        }
    }
}

package io.askimo.core.providers

/**
 * Marker interface for model provider-specific configuration settings.
 *
 * This interface is implemented by various provider-specific settings classes
 * that contain configuration parameters needed for different LLM providers
 * (like OpenAI, Ollama, etc.). Each implementation contains the specific
 * parameters required by its respective provider.
 */
interface ProviderSettings {
    val defaultModel: String

    /**
     * Provider-wide presets that control style and verbosity.
     */
    var presets: Presets

    /**
     * Returns a human-readable description of the provider settings.
     *
     * This method returns a list of strings where each string represents
     * a key configuration parameter in a formatted way. Implementations
     * should include the most important settings and may hide sensitive
     * information (like API keys) for security reasons.
     *
     * @return A list of strings describing the current configuration settings
     */
    fun describe(): List<String>
}

interface HasApiKey {
    var apiKey: String
}

interface HasBaseUrl {
    var baseUrl: String
}

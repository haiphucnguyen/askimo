package io.askimo.cli.commands

import io.askimo.core.providers.ProviderSettings
import io.askimo.core.session.ParamKey
import io.askimo.core.session.Session
import io.askimo.core.util.Masking
import org.jline.reader.ParsedLine

/**
 * Handles the command to display model parameters.
 *
 * This class provides functionality to view the current model parameters or list all available
 * parameter keys for the active provider. It helps users understand what parameters can be
 * configured and their current values.
 */
class ParamsCommandHandler(
    private val session: Session,
) : CommandHandler {
    override val keyword = ":params"
    override val description = "Show current provider, model, and configurable parameters"

    override fun handle(line: ParsedLine) {
        if (line.words().contains("--list")) {
            listKeys()
        } else {
            showParams()
        }
    }

    private fun showParams() {
        val provider = session.getActiveProvider()
        val settings = session.getCurrentProviderSettings()
        val model = resolveCurrentModel(settings)

        println("Provider : ${provider.name.lowercase()}")
        println("Model    : $model")
        println()

        val keys = ParamKey.supportedFor(settings)
        if (keys.isEmpty()) {
            println("No configurable parameters for this provider.")
            return
        }

        println("Parameters (current values):")
        keys.forEach { key ->
            val raw = safeGetValue(key, settings)
            val shown =
                if (key.secure) {
                    Masking.maskSecret(raw)
                } else {
                    (raw ?: "").toString()
                }
            val sugg =
                if (key.suggestions.isNotEmpty()) {
                    "  (suggestions: ${key.suggestions.joinToString(", ")})"
                } else {
                    ""
                }

            println("  ${key.key} = $shown  (${key.type}) – ${key.description}$sugg")
        }

        println()
        println("Use :setparam <key> <value> to change parameters.")
        println("Use :params --list to see available keys without values.")
    }

    private fun listKeys() {
        val provider = session.getActiveProvider()
        val settings = session.getCurrentProviderSettings()
        val model = resolveCurrentModel(settings)

        println("Available parameter keys for $model ($provider):")
        ParamKey.supportedFor(settings).forEach { key ->
            val secureBadge = if (key.secure) " 🔒" else ""
            val sugg =
                if (key.suggestions.isNotEmpty()) {
                    "  (suggestions: ${key.suggestions.joinToString(", ")})"
                } else {
                    ""
                }

            println("  ${key.key}$secureBadge (${key.type}) – ${key.description}$sugg")
        }
    }

    private fun resolveCurrentModel(settings: ProviderSettings): String {
        val m = session.params.model
        return m.ifBlank { settings.defaultModel }
    }

    private fun safeGetValue(
        key: ParamKey,
        settings: ProviderSettings,
    ): Any? =
        try {
            key.getValue(session.params, settings)
        } catch (_: Exception) {
            "<?>"
        }
}

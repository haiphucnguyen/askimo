/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.context.AppContext
import io.askimo.core.context.ParamKey
import io.askimo.core.logging.display
import io.askimo.core.logging.logger
import io.askimo.core.providers.ProviderSettings
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
    private val appContext: AppContext,
) : CommandHandler {
    private val log = logger<ParamsCommandHandler>()
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
        val provider = appContext.getActiveProvider()
        val settings = appContext.getCurrentProviderSettings()
        val model = resolveCurrentModel(settings)

        log.display("Provider : ${provider.name.lowercase()}")
        log.display("Model    : $model")

        val keys = ParamKey.supportedFor(settings)
        if (keys.isEmpty()) {
            log.display("No configurable parameters for this provider.")
            return
        }

        log.display("Parameters (current values):")
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

            log.display("  ${key.key} = $shown  (${key.type}) – ${key.description}$sugg")
        }

        log.display("Use :set-param <key> <value> to change parameters.")
        log.display("Use :params --list to see available keys without values.")
    }

    private fun listKeys() {
        val provider = appContext.getActiveProvider()
        val settings = appContext.getCurrentProviderSettings()
        val model = resolveCurrentModel(settings)

        log.display("Available parameter keys for $model ($provider):")
        ParamKey.supportedFor(settings).forEach { key ->
            val secureBadge = if (key.secure) " 🔒" else ""
            val sugg =
                if (key.suggestions.isNotEmpty()) {
                    "  (suggestions: ${key.suggestions.joinToString(", ")})"
                } else {
                    ""
                }

            log.display("  ${key.key}$secureBadge (${key.type}) – ${key.description}$sugg")
        }
    }

    private fun resolveCurrentModel(settings: ProviderSettings): String {
        val m = appContext.params.model
        return m.ifBlank { settings.defaultModel }
    }

    private fun safeGetValue(
        key: ParamKey,
        settings: ProviderSettings,
    ): Any? = try {
        key.getValue(appContext.params, settings)
    } catch (_: Exception) {
        "<?>"
    }
}

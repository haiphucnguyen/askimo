/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.context.AppContext
import io.askimo.core.context.AppContextConfigManager
import io.askimo.core.context.MemoryPolicy
import io.askimo.core.context.ParamKey
import io.askimo.core.logging.display
import io.askimo.core.logging.displayError
import io.askimo.core.logging.logger
import org.jline.reader.ParsedLine

/**
 * Handles the command to set model parameters.
 *
 * This class allows users to configure specific parameters for the current model provider,
 * such as temperature, max tokens, or API keys. It validates the parameter key and value,
 * updates the session configuration, and rebuilds the active chat model with the new settings.
 */
class SetParamCommandHandler(
    private val appContext: AppContext,
) : CommandHandler {
    private val log = logger<SetParamCommandHandler>()
    override val keyword = ":set-param"
    override val description = "Set a model parameter (use :params --list for available keys)"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)

        if (args.size != 2) {
            log.display("Usage: :set-param <key> <value>")
            log.display("Type :params --list to see valid keys and descriptions.")
            return
        }

        val keyInput = args[0]
        val valueInput = args[1]

        val key = ParamKey.fromInput(keyInput)
        if (key == null) {
            log.display("Unknown parameter: '$keyInput'. Try :params --list to see valid keys.")
            return
        }

        try {
            val provider = appContext.params.currentProvider
            val factory = appContext.getModelFactory(provider)
            if (factory == null) {
                log.display("❌ No model factory registered for provider: ${provider.name.lowercase()}")
                return
            }

            val providerSettings =
                appContext.params.providerSettings
                    .getOrPut(provider) { factory.defaultSettings() }

            key.applyTo(appContext.params, providerSettings, valueInput)

            appContext.params.providerSettings[provider] = providerSettings
            AppContextConfigManager.save(appContext.params)

            appContext.rebuildActiveChatClient(MemoryPolicy.KEEP_PER_PROVIDER_MODEL)
            log.display("✅ '${key.key}' is updated")
        } catch (e: IllegalArgumentException) {
            log.displayError("❌ Invalid value for '$keyInput': ${e.message}", e)
        }
    }
}

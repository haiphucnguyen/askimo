/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.context.AppContext
import io.askimo.core.context.getConfigInfo
import io.askimo.core.logging.display
import io.askimo.core.logging.logger
import org.jline.reader.ParsedLine

/**
 * Handles the command to display the current configuration.
 *
 * This class provides a summary view of the active configuration, including the current
 * provider, model, and all configured settings. It helps users understand the current state
 * of their chat environment.
 */
class ConfigCommandHandler(
    private val appContext: AppContext,
) : CommandHandler {
    private val log = logger<ConfigCommandHandler>()
    override val keyword: String = ":config"
    override val description: String = "Show the current provider, model, and settings."

    override fun handle(line: ParsedLine) {
        val configInfo = appContext.getConfigInfo()

        log.display("🔧 Current configuration:")
        log.display("  Provider:    ${configInfo.provider}")
        log.display("  Model:       ${configInfo.model}")
        log.display("  Settings:")

        configInfo.settingsDescription.forEach {
            log.display("    $it")
        }
    }
}

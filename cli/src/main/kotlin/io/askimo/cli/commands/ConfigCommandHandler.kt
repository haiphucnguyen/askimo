/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.session.Session
import io.askimo.core.util.AskimoHome
import io.askimo.core.util.Logger.info
import org.jline.reader.ParsedLine

/**
 * Handles the command to display the current configuration.
 *
 * This class provides a summary view of the active configuration, including the current
 * provider, model, and all configured settings. It helps users understand the current state
 * of their chat environment.
 */
class ConfigCommandHandler(
    private val session: Session,
) : CommandHandler {
    override val keyword: String = ":config"
    override val description: String = "Show the current provider, model, and settings."

    override fun handle(line: ParsedLine) {
        val provider = session.getActiveProvider()
        val settings = session.getCurrentProviderSettings()

        info("🔧 Current configuration:")
        info("  Provider:    $provider")
        info("  Model:       ${if (session.hasChatService()) session.params.model else "(not set)"}")
        info("  Settings:")

        settings.describe().forEach {
            info("    $it")
        }

        val scope = session.scope
        if (scope == null) {
            info("  Active project: (none)")
        } else {
            val exists =
                try {
                    java.nio.file.Files
                        .isDirectory(scope.projectDir)
                } catch (_: Exception) {
                    false
                }
            val home = AskimoHome.userHome().toString()
            val rootDisp = scope.projectDir.toString().replaceFirst(home, "~")
            info("  Active project:")
            info("    Name:       ${scope.projectName}")
            info("    Root:       $rootDisp${if (exists) "" else "  (missing)"}")
        }
    }
}

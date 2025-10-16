/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.project.ProjectStore
import io.askimo.core.session.Session
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

        val active = ProjectStore.getActive()
        if (active == null) {
            info("  Active project: (none)")
        } else {
            val (meta, ptr) = active
            val exists =
                try {
                    java.nio.file.Files
                        .isDirectory(
                            java.nio.file.Paths
                                .get(meta.root),
                        )
                } catch (_: Exception) {
                    false
                }
            val home = System.getProperty("user.home")
            val rootDisp = meta.root.replaceFirst(home, "~")
            info("  Active project:")
            info("    Name:       ${meta.name}")
            info("    ID:         ${meta.id}")
            info("    Root:       $rootDisp${if (exists) "" else "  (missing)"}")
            info("    Selected:   ${ptr.selectedAt}")
            info("    Created:    ${meta.createdAt}")
            info("    Last used:  ${meta.lastUsedAt}")
        }
    }
}

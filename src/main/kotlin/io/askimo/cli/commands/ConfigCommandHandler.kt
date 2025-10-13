/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.project.ProjectStore
import io.askimo.core.session.Session
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

        println("🔧 Current configuration:")
        println("  Provider:    $provider")
        println("  Model:       ${if (session.hasChatService()) session.params.model else "(not set)"}")
        println("  Settings:")

        settings.describe().forEach {
            println("    $it")
        }

        val active = ProjectStore.getActive()
        if (active == null) {
            println("  Active project: (none)")
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
            println("  Active project:")
            println("    Name:       ${meta.name}")
            println("    ID:         ${meta.id}")
            println("    Root:       $rootDisp${if (exists) "" else "  (missing)"}")
            println("    Selected:   ${ptr.selectedAt}")
            println("    Created:    ${meta.createdAt}")
            println("    Last used:  ${meta.lastUsedAt}")
        }
    }
}

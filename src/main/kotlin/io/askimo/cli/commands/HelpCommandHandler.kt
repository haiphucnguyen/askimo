/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.util.Logger.info
import org.jline.reader.ParsedLine

/**
 * Handles the command to display help information.
 *
 * This class provides a centralized help system that lists all available commands in the
 * application along with their descriptions. It serves as the main entry point for users
 * to discover the CLI's capabilities.
 */
class HelpCommandHandler : CommandHandler {
    override val keyword = ":help"
    override val description = "Show available commands"

    private var commands: List<CommandHandler> = emptyList()

    fun setCommands(commands: List<CommandHandler>) {
        this.commands = commands
    }

    override fun handle(line: ParsedLine) {
        info("Available commands:\n")
        commands.sortedBy { it.keyword }.forEach {
            info("  ${it.keyword.padEnd(14)} - ${it.description}")
        }
    }
}

/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.logging.display
import io.askimo.core.logging.logger
import org.jline.reader.ParsedLine

/**
 * Handles the command to display help information.
 *
 * This class provides a centralized help system that lists all available commands in the
 * application along with their descriptions. It serves as the main entry point for users
 * to discover the CLI's capabilities.
 */
class HelpCommandHandler : CommandHandler {
    private val log = logger<HelpCommandHandler>()
    override val keyword = ":help"
    override val description = "Show available commands"

    private var commands: List<CommandHandler> = emptyList()
    private var isNonInteractiveMode = false

    fun setCommands(commands: List<CommandHandler>) {
        this.commands = commands
    }

    fun setNonInteractiveMode(nonInteractive: Boolean) {
        this.isNonInteractiveMode = nonInteractive
    }

    override fun handle(line: ParsedLine) {
        val modeText = if (isNonInteractiveMode) "non-interactive" else "interactive"

        log.display("Available commands ($modeText mode):\n")

        commands.forEach { handler ->
            val commandName = if (isNonInteractiveMode) {
                "--" + handler.keyword.removePrefix(":")
            } else {
                handler.keyword
            }

            formatCommand(commandName, handler.description)
        }

        if (isNonInteractiveMode) {
            log.display("\n💡 Multiple commands can be combined in a single invocation:")
            log.display("   Example: askimo --set-provider openai --set-param api_key sk-abc123 --set-param model gpt-4")
            log.display("\nFor interactive mode, run 'askimo' without arguments and use ':command' format.")
        } else {
            log.display("\nFor non-interactive mode, use 'askimo --command' format from command line.")
        }
    }

    private fun formatCommand(commandName: String, description: String) {
        // Replace interactive examples with non-interactive ones when in non-interactive mode
        val adjustedDescription = if (isNonInteractiveMode) {
            description.replace(Regex(":([a-z-]+)")) { matchResult ->
                "--${matchResult.groupValues[1]}"
            }
        } else {
            description
        }

        val lines = adjustedDescription.split('\n')
        val mainDescription = lines[0]
        val additionalLines = lines.drop(1)

        // Format main command line
        log.display("  ${commandName.padEnd(18)} - $mainDescription")

        // Format usage and additional lines with proper indentation
        additionalLines.forEach { line ->
            when {
                line.trim().startsWith("Usage:") -> {
                    log.display("${" ".repeat(22)}$line")
                }
                line.trim().startsWith("Example:") -> {
                    log.display("${" ".repeat(22)}$line")
                }
                line.trim().startsWith("Options:") -> {
                    log.display("${" ".repeat(22)}$line")
                }
                line.trim().startsWith("--") -> {
                    log.display("${" ".repeat(24)}$line")
                }
                line.trim().isNotEmpty() -> {
                    log.display("${" ".repeat(22)}$line")
                }
                else -> {
                    log.display("")
                }
            }
        }
    }
}

/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import org.jline.reader.ParsedLine

/**
 * Interface for handling CLI commands.
 *
 * CommandHandler implementations process specific commands in the CLI application.
 * Each handler is responsible for a single command identified by its keyword.
 */
interface CommandHandler {
    /**
     * The command identifier that triggers this handler.
     * Usually starts with a colon (e.g., ":help").
     */
    val keyword: String

    /**
     * A brief description of what the command does.
     * Used when displaying help information.
     */
    val description: String

    /**
     * Processes the command with the given parsed line.
     *
     * @param line The parsed command line to process
     */
    fun handle(line: ParsedLine)
}

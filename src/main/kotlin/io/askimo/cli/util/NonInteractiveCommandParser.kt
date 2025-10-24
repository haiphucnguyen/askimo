/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.util

import org.jline.reader.ParsedLine

/**
 * Utility for parsing non-interactive command line arguments into ParsedLine objects
 * that can be used with existing interactive command handlers.
 */
object NonInteractiveCommandParser {
    /**
     * Creates an empty ParsedLine for commands that don't require parameters.
     * Used for commands like --list-providers, --list-projects, --list-recipes.
     */
    fun createEmptyParsedLine(): ParsedLine = EmptyParsedLine()

    /**
     * Creates a ParsedLine for commands that require parameters.
     * Used for commands like --set-provider <provider>.
     *
     * @param interactiveCommand The interactive command format (e.g., ":set-provider")
     * @param parameters The command parameters
     * @return ParsedLine that can be used with existing command handlers
     */
    fun createParameterizedParsedLine(
        interactiveCommand: String,
        vararg parameters: String,
    ): ParsedLine = ParameterizedParsedLine(interactiveCommand, parameters.toList())

    /**
     * Parses command line arguments for a specific flag and returns the argument value.
     *
     * @param args Command line arguments array
     * @param flag The flag to look for (e.g., "--set-provider")
     * @return The argument value following the flag, or null if not found or no value
     */
    fun extractFlagValue(
        args: Array<String>,
        flag: String,
    ): String? {
        val flagIndex = args.indexOfFirst { it == flag }
        return if (flagIndex != -1 && flagIndex + 1 < args.size) {
            args[flagIndex + 1]
        } else {
            null
        }
    }

    /**
     * Extracts all arguments following a specific flag until the next flag or end of arguments.
     * This is a generic method that lets command handlers do their own parsing and validation.
     *
     * @param args Command line arguments array
     * @param flag The flag to look for (e.g., "--create-recipe")
     * @return List of arguments following the flag, or null if flag not found
     */
    fun extractFlagArguments(
        args: Array<String>,
        flag: String,
    ): List<String>? {
        val flagIndex = args.indexOfFirst { it == flag }
        if (flagIndex == -1) return null

        val arguments = mutableListOf<String>()
        var i = flagIndex + 1

        // Collect all arguments until we hit another flag (starts with -) or end of args
        while (i < args.size && !args[i].startsWith("-")) {
            arguments.add(args[i])
            i++
        }

        // Also collect flag-value pairs (like -f file.yml)
        while (i < args.size) {
            if (args[i].startsWith("-") && i + 1 < args.size && !args[i + 1].startsWith("-")) {
                arguments.add(args[i]) // Add the flag
                arguments.add(args[i + 1]) // Add the value
                i += 2
            } else {
                break
            }
        }

        return arguments
    }

    private class EmptyParsedLine : ParsedLine {
        override fun word() = ""

        override fun wordCursor() = 0

        override fun wordIndex() = 0

        override fun words() = emptyList<String>()

        override fun line() = ""

        override fun cursor() = 0
    }

    private class ParameterizedParsedLine(
        private val command: String,
        private val parameters: List<String>,
    ) : ParsedLine {
        private val allWords = listOf(command) + parameters
        private val fullLine = allWords.joinToString(" ")

        override fun word() = parameters.firstOrNull() ?: ""

        override fun wordCursor() = 0

        override fun wordIndex() = 1

        override fun words() = allWords

        override fun line() = fullLine

        override fun cursor() = fullLine.length
    }
}

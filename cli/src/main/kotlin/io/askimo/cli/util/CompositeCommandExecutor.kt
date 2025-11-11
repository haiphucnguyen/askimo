/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.util

import io.askimo.cli.commands.CommandHandler
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.info
import org.jline.reader.ParsedLine

/**
 * Executes multiple non-interactive commands in sequence.
 *
 * This utility allows users to chain multiple commands together in a single invocation,
 * such as: askimo --set-provider openai --set-param api_key sk-abc123
 *
 * Each command is identified by its flag (e.g., --set-provider) and executed with its
 * corresponding arguments in the order they appear on the command line.
 */
object CompositeCommandExecutor {
    /**
     * Detects if the arguments contain multiple non-interactive command flags.
     *
     * @param args Command line arguments
     * @param handlers List of available command handlers
     * @return true if multiple command flags are found
     */
    fun hasMultipleCommands(
        args: Array<String>,
        handlers: List<CommandHandler>,
    ): Boolean {
        val flags = handlers.map { keywordToFlag(it.keyword) }.toSet()
        val foundFlags = args.count { it in flags }
        return foundFlags > 1
    }

    /**
     * Parses and executes multiple commands from command line arguments.
     *
     * @param args Command line arguments array
     * @param handlers Map of flag to CommandHandler
     */
    fun executeCommands(
        args: Array<String>,
        handlers: Map<String, CommandHandler>,
    ) {
        val commands = parseCommands(args, handlers.keys)

        if (commands.isEmpty()) {
            info("❌ No valid commands found")
            return
        }

        debug("🔄 Executing ${commands.size} command(s) in sequence")

        // Execute commands in sequence
        for ((flag, cmdArgs) in commands) {
            val handler = handlers[flag]
            if (handler != null) {
                debug("⚙️  Executing: $flag ${cmdArgs.joinToString(" ")}")
                val keyword = flagToKeyword(flag)
                val parsedLine = createParsedLine(keyword, cmdArgs)
                handler.handle(parsedLine)
            } else {
                info("⚠️  Skipping unknown command: $flag")
            }
        }
    }

    /**
     * Parses command line arguments into a list of (flag, arguments) pairs.
     *
     * @param args Command line arguments
     * @param validFlags Set of valid command flags
     * @return List of (flag, arguments) pairs in order of appearance
     */
    private fun parseCommands(
        args: Array<String>,
        validFlags: Set<String>,
    ): List<Pair<String, List<String>>> {
        val commands = mutableListOf<Pair<String, List<String>>>()
        var i = 0

        while (i < args.size) {
            val arg = args[i]

            if (arg in validFlags) {
                // Found a command flag, collect its arguments
                val cmdArgs = mutableListOf<String>()
                i++

                // Collect arguments until we hit another valid flag or end of args
                while (i < args.size && args[i] !in validFlags) {
                    cmdArgs.add(args[i])
                    i++
                }

                commands.add(arg to cmdArgs)
            } else {
                // Skip unknown arguments
                i++
            }
        }

        return commands
    }

    /**
     * Converts a command keyword (e.g., ":set-provider") to a flag (e.g., "--set-provider").
     */
    private fun keywordToFlag(keyword: String): String = "--" + keyword.removePrefix(":")

    /**
     * Converts a flag (e.g., "--set-provider") to a keyword (e.g., ":set-provider").
     */
    private fun flagToKeyword(flag: String): String = ":" + flag.removePrefix("--")

    /**
     * Creates a ParsedLine from a keyword and arguments.
     */
    private fun createParsedLine(
        keyword: String,
        args: List<String>,
    ): ParsedLine = object : ParsedLine {
        override fun word(): String = keyword
        override fun wordCursor(): Int = 0
        override fun wordIndex(): Int = 0
        override fun words(): List<String> = listOf(keyword) + args
        override fun line(): String = (listOf(keyword) + args).joinToString(" ")
        override fun cursor(): Int = line().length
    }
}

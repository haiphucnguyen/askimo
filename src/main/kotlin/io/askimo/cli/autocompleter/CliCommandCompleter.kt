/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.autocompleter

import io.askimo.core.session.ParamKey
import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine

/**
 * Auto completer for CLI commands and their parameters.
 * Provides completion for all available CLI commands and their respective parameters.
 */
class CliCommandCompleter : Completer {
    companion object {
        // Define available commands with their descriptions
        private val COMMANDS =
            mapOf(
                ":help" to "Show help information for all available commands",
                ":config" to "Display current configuration settings",
                ":params" to "List all available session parameters",
                ":set-param" to "Set a session parameter value",
                ":providers" to "List all available AI providers",
                ":set-provider" to "Set the active AI provider",
                ":models" to "Show available models for the current provider",
                ":copy" to "Copy the last response to clipboard",
                ":clear-memory" to "Clear the conversation memory",
                ":create-project" to "Create a new project",
                ":projects" to "List all available projects",
                ":use-project" to "Set the active project",
                ":delete-project" to "Delete a project",
                ":create-recipe" to "Create a new recipe",
                ":delete-recipe" to "Delete a recipe",
                ":recipes" to "List all available recipes",
                ":delete-all-projects" to "Delete all projects",
                ":history" to "Show command history",
                ":agent" to "Run coding assistant for code modifications, documentation, refactoring",
            )
    }

    override fun complete(
        reader: LineReader,
        line: ParsedLine,
        candidates: MutableList<Candidate>,
    ) {
        val words = line.words()
        if (words.isEmpty()) return

        when (words.size) {
            1 -> {
                // Complete command names
                val partial = words[0]

                // Try exact prefix matching for complete commands
                COMMANDS.forEach { (command, description) ->
                    if (command.startsWith(partial, ignoreCase = true)) {
                        candidates.add(
                            Candidate(
                                command,
                                command,
                                null,
                                description,
                                null,
                                null,
                                true,
                            ),
                        )
                    }
                }

                // Also suggest commands that contain the partial text in any word segment
                if (partial.startsWith(":") && partial.length > 1) {
                    val partialWithoutColon = partial.substring(1)

                    COMMANDS.keys.forEach { command ->
                        // Check if any word segment in the command starts with the partial
                        val commandParts = command.substring(1).split("-") // Remove : and split by -
                        val hasMatchingSegment =
                            commandParts.any { part ->
                                part.startsWith(partialWithoutColon, ignoreCase = true)
                            }

                        // Only suggest if we haven't already added this command through exact prefix matching
                        val alreadyAdded = candidates.any { it.value() == command }

                        if (hasMatchingSegment && !alreadyAdded) {
                            candidates.add(
                                Candidate(
                                    command,
                                    command,
                                    null,
                                    COMMANDS[command] ?: "",
                                    null,
                                    null,
                                    true,
                                ),
                            )
                        }
                    }
                }

                // Also suggest incremental completions for compound commands (existing logic)
                if (partial.startsWith(":") && partial.length > 1) {
                    val partialWithoutColon = partial.substring(1)

                    // Find commands that start with the partial and suggest the next word segment
                    COMMANDS.keys.forEach { command ->
                        val commandWithoutColon = command.substring(1)
                        if (commandWithoutColon.startsWith(partialWithoutColon, ignoreCase = true) &&
                            commandWithoutColon.length > partialWithoutColon.length &&
                            !command.equals(partial, ignoreCase = true) // Don't suggest if it's already an exact match
                        ) {
                            // Find the next word boundary (hyphen or end)
                            val remaining = commandWithoutColon.substring(partialWithoutColon.length)
                            val nextHyphen = remaining.indexOf('-')

                            val nextSegment =
                                if (nextHyphen != -1) {
                                    remaining.substring(0, nextHyphen + 1) // Include the hyphen
                                } else {
                                    remaining // Rest of the word
                                }

                            val suggestion = partial + nextSegment

                            // Only add if this creates a partial command (not a complete one we already added)
                            if (suggestion != command) {
                                val description = "Complete to: $command"

                                // Avoid duplicate suggestions
                                if (candidates.none { it.value() == suggestion }) {
                                    candidates.add(
                                        Candidate(
                                            suggestion,
                                            suggestion,
                                            null,
                                            description,
                                            null,
                                            null,
                                            true,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                // Complete parameters for commands that need them
                when (words[0]) {
                    ":set-param" -> {
                        // Complete parameter names for set-param command
                        ParamKey.all().forEach { param ->
                            candidates.add(
                                Candidate(
                                    param.key,
                                    param.key,
                                    null,
                                    "${param.type} – ${param.description}",
                                    null,
                                    null,
                                    true,
                                ),
                            )
                        }
                    }
                }
            }
            3 -> {
                // Complete values for set-param command
                if (words[0] == ":set-param") {
                    val param = ParamKey.fromInput(words[1])
                    param?.suggestions?.forEach {
                        candidates.add(Candidate(it))
                    }
                }
            }
        }
    }
}

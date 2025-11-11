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

        private const val MAX_SUGGESTIONS = 5
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
                val tempCandidates = mutableListOf<Candidate>()

                // Try exact prefix matching for complete commands (highest priority)
                COMMANDS.forEach { (command, description) ->
                    if (command.startsWith(partial, ignoreCase = true)) {
                        tempCandidates.add(
                            Candidate(
                                command,
                                command,
                                null,
                                description,
                                null,
                                null,
                                false,
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
                        val alreadyAdded = tempCandidates.any { it.value() == command }

                        if (hasMatchingSegment && !alreadyAdded && tempCandidates.size < MAX_SUGGESTIONS) {
                            tempCandidates.add(
                                Candidate(
                                    command,
                                    command,
                                    null,
                                    COMMANDS[command] ?: "",
                                    null,
                                    null,
                                    false,
                                ),
                            )
                        }
                    }
                }

                // Also suggest incremental completions for compound commands (existing logic)
                if (partial.startsWith(":") && partial.length > 1 && tempCandidates.size < MAX_SUGGESTIONS) {
                    val partialWithoutColon = partial.substring(1)

                    // Find commands that start with the partial and suggest the next word segment
                    COMMANDS.keys.forEach { command ->
                        if (tempCandidates.size >= MAX_SUGGESTIONS) return@forEach

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
                                if (tempCandidates.none { it.value() == suggestion }) {
                                    tempCandidates.add(
                                        Candidate(
                                            suggestion,
                                            suggestion,
                                            null,
                                            description,
                                            null,
                                            null,
                                            false,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                // Add only top 5 suggestions to the main candidates list
                candidates.addAll(tempCandidates.take(MAX_SUGGESTIONS))
            }
            2 -> {
                // Complete parameters for commands that need them
                when (words[0]) {
                    ":set-param" -> {
                        // Complete parameter names for set-param command
                        val tempCandidates = mutableListOf<Candidate>()
                        ParamKey.all().forEach { param ->
                            tempCandidates.add(
                                Candidate(
                                    param.key,
                                    param.key,
                                    null,
                                    "${param.type} – ${param.description}",
                                    null,
                                    null,
                                    false,
                                ),
                            )
                        }
                        candidates.addAll(tempCandidates.take(MAX_SUGGESTIONS))
                    }
                }
            }
            3 -> {
                // Complete values for set-param command
                if (words[0] == ":set-param") {
                    val param = ParamKey.fromInput(words[1])
                    val tempCandidates = mutableListOf<Candidate>()
                    param?.suggestions?.forEach {
                        tempCandidates.add(Candidate(it, it, null, null, null, null, false))
                    }
                    candidates.addAll(tempCandidates.take(MAX_SUGGESTIONS))
                }
            }
        }
    }
}

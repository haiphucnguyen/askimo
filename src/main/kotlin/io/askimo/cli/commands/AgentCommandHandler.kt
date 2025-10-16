/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.project.Budgets
import io.askimo.core.project.CodingAssistant
import io.askimo.core.project.DiffGenerator
import io.askimo.core.project.PatchApplier
import io.askimo.core.project.ProjectStore
import io.askimo.core.session.Session
import io.askimo.core.util.Logger.info
import org.jline.reader.ParsedLine

/**
 * Handles the `:agent` command to explicitly trigger coding assistant mode.
 *
 * This command bypasses intent detection and directly runs the coding assistant
 * for code modifications, documentation, refactoring, etc.
 *
 * Usage: :agent <your coding request>
 *
 * Examples:
 * - :agent write kdoc for CreateRecipeCommandHandler
 * - :agent add error handling to the parseArgs method
 * - :agent refactor the expandHome method to be more readable
 * - :agent create unit tests for this class
 */
class AgentCommandHandler(
    private val session: Session,
) : CommandHandler {
    override val keyword: String = ":agent"
    override val description: String =
        "Run coding assistant for code modifications, documentation, refactoring.\n" +
            "Usage: :agent <your coding request>\n" +
            "Example: :agent write kdoc for CreateRecipeCommandHandler"

    /**
     * Cached DiffGenerator instance to avoid recreating it on every command.
     * Initialized lazily when first accessed.
     */
    private val diffGenerator: DiffGenerator by lazy {
        DiffGenerator(session.getChatService())
    }

    /**
     * Cached CodingAssistant instance to avoid recreating it on every command.
     * Initialized lazily when first accessed.
     */
    private val codingAssistant: CodingAssistant by lazy {
        CodingAssistant(diffGenerator, PatchApplier(), Budgets())
    }

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1) // Remove ":agent" from the command

        if (args.isEmpty()) {
            info("Usage: :agent <your coding request>")
            info("Example: :agent write kdoc for CreateRecipeCommandHandler")
            return
        }

        val prompt = args.joinToString(" ")

        val active = ProjectStore.getActive()
        if (active == null) {
            info("❌ No active project. Use :use-project <name> to set an active project first.")
            info("💡 Create a project with :create-project <name> <path>")
            return
        }

        val (meta, _) = active

        try {
            info("🤖 Running coding assistant for: $prompt")
            codingAssistant.run(prompt, meta)

            session.lastResponse = "Applied coding assistant for: $prompt"
            info("✅ Coding assistant completed")
        } catch (e: Exception) {
            info("❌ Coding assistant failed: ${e.message}")
            session.lastResponse = "Coding assistant failed: ${e.message}"
        }
    }
}

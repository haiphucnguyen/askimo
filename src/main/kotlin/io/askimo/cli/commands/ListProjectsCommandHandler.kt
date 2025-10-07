/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.project.ProjectStore
import org.jline.reader.ParsedLine

/**
 * Command handler for the :projects directive in the Askimo CLI.
 *
 * Prints a numbered list of all projects registered in ProjectStore.
 * If no projects exist yet, it displays a friendly hint on how to create one.
 */
class ListProjectsCommandHandler : CommandHandler {
    override val keyword: String = ":projects"
    override val description: String = "List all saved Askimo projects"

    override fun handle(line: ParsedLine) {
        val projects = ProjectStore.list()
        if (projects.isEmpty()) {
            println("ℹ️  No projects saved yet. Use :create-project to add one.")
            return
        }
        println("📚 Projects:")
        projects.forEachIndexed { i, p ->
            println("  ${i + 1}. ${p.name}  →  ${p.dir}")
        }
    }
}

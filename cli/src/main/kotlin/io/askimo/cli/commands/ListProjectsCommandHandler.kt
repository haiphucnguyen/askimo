/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.project.ProjectMeta
import io.askimo.core.project.ProjectStore
import io.askimo.core.util.AskimoHome
import io.askimo.core.util.logger
import org.jline.reader.ParsedLine

/**
 * Command handler for the :projects directive in the Askimo CLI.
 *
 * Prints a numbered list of all projects registered in ProjectStore.
 * If no projects exist yet, it displays a friendly hint on how to create one.
 */
class ListProjectsCommandHandler : CommandHandler {
    private val log = logger<ListProjectsCommandHandler>()
    override val keyword: String = ":projects"
    override val description: String = "List all saved Askimo projects"

    override fun handle(line: ParsedLine) {
        val metas = ProjectStore.list()
        if (metas.isEmpty()) {
            log.info("ℹ️  No projects saved yet. Use :create-project to add one.")
            return
        }

        val home = AskimoHome.userHome().toString()

        // Sort by lastUsedAt desc (fallback to createdAt)
        val projects =
            metas.sortedWith(
                compareByDescending<ProjectMeta> { it.lastUsedAt }
                    .thenBy { it.name.lowercase() },
            )

        log.info("📚 Projects:")
        projects.forEachIndexed { i, p ->
            val rootDisp = p.root.replaceFirst(home, "~")
            val exists =
                try {
                    java.nio.file.Files
                        .isDirectory(
                            java.nio.file.Paths
                                .get(p.root),
                        )
                } catch (_: Exception) {
                    false
                }
            val missing = if (exists) "" else "  (missing)"

            log.info(
                "%2d. %-20s  id=%s\n      ↳ %s%s".format(
                    i + 1,
                    p.name,
                    p.id,
                    rootDisp,
                    missing,
                ),
            )
        }
    }
}

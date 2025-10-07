/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.project.PgVectorAdmin
import io.askimo.core.project.PostgresContainerManager
import io.askimo.core.project.ProjectStore
import org.jline.reader.ParsedLine

class DeleteProjectCommandHandler : CommandHandler {
    override val keyword: String = ":delete-project"
    override val description: String =
        "Delete a saved project: removes it from ~/.askimo/projects.json and drops its pgvector embedding table.\n" +
            "Usage: :delete-project <project-name>"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        if (args.isEmpty()) {
            println("Usage: :delete-project <project-name>")
            return
        }

        val name = args.first()
        val entry = ProjectStore.get(name)
        if (entry == null) {
            println("❌ Project '$name' not found. Use :projects to list.")
            return
        }

        // Remove from project store
        val removed = ProjectStore.delete(name)
        if (!removed) {
            println("ℹ️  Project '$name' was not removed (already missing).")
            return
        }

        println("🗂️  Removed project '$name' from registry.")

        // Drop pgvector table for this project
        println("🐘 Ensuring Postgres+pgvector is running…")
        val pg =
            try {
                PostgresContainerManager.startIfNeeded()
            } catch (e: Exception) {
                println("❌ Failed to start Postgres container: ${e.message}")
                e.printStackTrace()
                return
            }

        try {
            val base = System.getenv("ASKIMO_EMBED_TABLE") ?: "askimo_embeddings"
            val table = PgVectorAdmin.projectTableName(base, name)
            PgVectorAdmin.dropProjectTable(pg.jdbcUrl, pg.username, pg.password, base, name)
            println("🧹 Dropped embeddings table \"$table\" for project '$name'.")
        } catch (e: Exception) {
            println("❌ Failed to drop embeddings table for '$name': ${e.message}")
            e.printStackTrace()
        }
    }
}

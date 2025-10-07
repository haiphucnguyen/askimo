/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.project.PgVectorIndexer
import io.askimo.core.project.PostgresContainerManager
import io.askimo.core.project.ProjectStore
import io.askimo.core.session.Session
import org.jline.reader.ParsedLine
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Command handler for the :project directive.
 *
 * Activates a previously saved Askimo project by name, verifies the
 * saved directory still exists, ensures a local Postgres instance with
 * the pgvector extension is running (via PostgresContainerManager), and
 * configures the current Session to use Retrieval-Augmented Generation
 * (RAG) backed by PgVectorIndexer for the selected project.
 */
class UseProjectCommandHandler(
    private val session: Session,
) : CommandHandler {
    override val keyword: String = ":project"
    override val description: String =
        "Activate a saved project (sets scope and enables RAG).\nUsage: :project <project-name>"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        val name = args.firstOrNull()
        if (name.isNullOrBlank()) {
            println("Usage: :project <project-name>")
            return
        }

        val entry = ProjectStore.get(name)
        if (entry == null) {
            println("❌ Project '$name' not found. Use :projects to list.")
            return
        }

        val projectPath = Paths.get(entry.dir)
        if (!Files.isDirectory(projectPath)) {
            println("⚠️  Saved path does not exist anymore: $projectPath")
            return
        }

        println("🐘 Ensuring Postgres+pgvector is running…")
        val pg =
            try {
                PostgresContainerManager.startIfNeeded()
            } catch (e: Exception) {
                println("❌ Failed to start Postgres container: ${e.message}")
                e.printStackTrace()
                return
            }

        val indexer =
            PgVectorIndexer(
                pgUrl = pg.jdbcUrl,
                pgUser = pg.username,
                pgPass = pg.password,
                projectId = entry.name,
                session = session,
            )

        session.setScope(entry)
        session.enableRagWith(indexer)
        println("✅ Active project: '${entry.name}'  →  ${entry.dir}")
        println("🧠 RAG enabled for '${entry.name}'.")
    }
}

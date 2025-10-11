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
        "Activate a saved project (sets active pointer, session scope, and enables RAG).\n" +
            "Usage: :project <project-name|project-id>"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        val key = args.firstOrNull()
        if (key.isNullOrBlank()) {
            println("Usage: :project <project-name|project-id>")
            return
        }

        // Resolve by id first, then by name (case-insensitive)
        val meta = ProjectStore.getById(key) ?: ProjectStore.getByName(key)
        if (meta == null) {
            println("❌ Project '$key' not found. Use :projects to list.")
            return
        }

        val projectPath =
            java.nio.file.Paths
                .get(meta.root)
        if (!java.nio.file.Files
                .isDirectory(projectPath)
        ) {
            println("⚠️ Saved path does not exist anymore: ${meta.root}")
            return
        }

        // Set active pointer right away
        try {
            ProjectStore.setActive(meta.id)
        } catch (e: Exception) {
            println("⚠️ Could not set active project pointer: ${e.message}")
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

        // NOTE: MVP still keys embeddings by project *name*.
        // When you switch indexer to use meta.id, update projectId below.
        val indexer =
            PgVectorIndexer(
                pgUrl = pg.jdbcUrl,
                pgUser = pg.username,
                pgPass = pg.password,
                projectId = meta.name, // MVP: keep name; later: meta.id
                session = session,
            )

        // Session now uses ProjectMeta directly (per your change)
        session.setScope(meta)
        session.enableRagWith(indexer)

        println("✅ Active project: '${meta.name}'  (id=${meta.id})")
        println("   ↳ ${meta.root}")
        println("🧠 RAG enabled for '${meta.name}'.")
    }
}

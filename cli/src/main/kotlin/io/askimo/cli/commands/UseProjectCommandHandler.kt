/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.logging.display
import io.askimo.core.logging.displayError
import io.askimo.core.logging.logger
import io.askimo.core.project.FileWatcherManager
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
    private val log = logger<UseProjectCommandHandler>()
    override val keyword: String = ":use-project"
    override val description: String =
        "Activate a saved project (sets active pointer, session scope, and enables RAG).\n" +
            "Usage: :use-project <project-name|project-id>"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        val key = args.firstOrNull()
        if (key.isNullOrBlank()) {
            log.display("Usage: :use-project <project-name|project-id>")
            return
        }

        // Resolve by id first, then by name (case-insensitive)
        val meta = ProjectStore.getById(key) ?: ProjectStore.getByName(key)
        if (meta == null) {
            log.display("❌ Project '$key' not found. Use :projects to list.")
            return
        }

        val projectPath = Paths.get(meta.root)
        if (!Files.isDirectory(projectPath)) {
            log.display("⚠️ Saved path does not exist anymore: ${meta.root}")
            return
        }

        log.display("🐘 Ensuring Postgres+pgvector is running…")
        var indexer: PgVectorIndexer? = null
        try {
            val pg = PostgresContainerManager.startIfNeeded()
            log.display("✅ Postgres ready on ${pg.jdbcUrl}")

            indexer = PgVectorIndexer(
                projectId = meta.name,
                session = session,
            )
        } catch (e: Exception) {
            log.display("⚠️ Failed to start Postgres container: ${e.message}")
            log.display("📝 Proceeding without vector indexing - you can enable it later when Docker is available.")
            log.displayError("Failed to launch the vector db", e)
        }

        session.setScope(meta)

        log.display("✅ Active project: '${meta.name}'  (id=${meta.id})")
        log.display("   ↳ ${meta.root}")

        if (indexer != null) {
            session.enableRagWith(indexer)

            // Start file watcher for the project (this will automatically stop any existing watcher)
            FileWatcherManager.startWatchingProject(projectPath, indexer)
            log.display("👁️  File watcher started - changes will be automatically indexed.")
            log.display("🧠 RAG enabled for '${meta.name}'.")
        } else {
            log.display("📝 Project activated without vector indexing. Start Docker and use indexing commands to enable RAG later.")
        }
    }
}

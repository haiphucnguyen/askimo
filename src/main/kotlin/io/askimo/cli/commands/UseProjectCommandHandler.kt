/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.project.FileWatcherManager
import io.askimo.core.project.PgVectorIndexer
import io.askimo.core.project.PostgresContainerManager
import io.askimo.core.project.ProjectStore
import io.askimo.core.session.Session
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.info
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
    override val keyword: String = ":use-project"
    override val description: String =
        "Activate a saved project (sets active pointer, session scope, and enables RAG).\n" +
            "Usage: :use-project <project-name|project-id>"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        val key = args.firstOrNull()
        if (key.isNullOrBlank()) {
            info("Usage: :use-project <project-name|project-id>")
            return
        }

        // Resolve by id first, then by name (case-insensitive)
        val meta = ProjectStore.getById(key) ?: ProjectStore.getByName(key)
        if (meta == null) {
            info("❌ Project '$key' not found. Use :projects to list.")
            return
        }

        val projectPath = Paths.get(meta.root)
        if (!Files.isDirectory(projectPath)) {
            info("⚠️ Saved path does not exist anymore: ${meta.root}")
            return
        }

        // Set active pointer right away
        try {
            ProjectStore.setActive(meta.id)
        } catch (e: Exception) {
            info("⚠️ Could not set active project pointer: ${e.message}")
            debug(e)
        }

        info("🐘 Ensuring Postgres+pgvector is running…")
        val pg =
            try {
                PostgresContainerManager.startIfNeeded()
            } catch (e: Exception) {
                info("❌ Failed to start Postgres container: ${e.message}")
                debug(e)
                return
            }

        val indexer =
            PgVectorIndexer(
                projectId = meta.name,
                session = session,
            )

        session.setScope(meta)
        session.enableRagWith(indexer)

        info("✅ Active project: '${meta.name}'  (id=${meta.id})")
        info("   ↳ ${meta.root}")

        // Start file watcher for the project (this will automatically stop any existing watcher)
        FileWatcherManager.startWatchingProject(projectPath, indexer)
        info("👁️  File watcher started - changes will be automatically indexed.")
        info("🧠 RAG enabled for '${meta.name}'.")
    }
}

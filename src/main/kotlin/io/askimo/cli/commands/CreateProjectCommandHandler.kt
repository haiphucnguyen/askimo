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

class CreateProjectCommandHandler(
    private val session: Session,
) : CommandHandler {
    override val keyword: String = ":create-project"
    override val description: String =
        "Create a project, auto-start Postgres+pgvector (Testcontainers), and index the folder.\n" +
            "Usage: :create-project -n <project-name> -d <project-folder>"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        val (name, dir) =
            parseArgs(args) ?: run {
                info("Usage: :create-project -n <project-name> -d <project-folder>")
                return
            }

        val projectPath = Paths.get(dir).toAbsolutePath().normalize()
        if (!Files.exists(projectPath) || !Files.isDirectory(projectPath)) {
            info("❌ Folder does not exist or is not a directory: $projectPath")
            return
        }

        if (ProjectStore.getByName(name) != null) {
            info("⚠️ Project '$name' already exists. Use ':project $name' to activate it.")
            return
        }

        info("🐘 Starting local Postgres+pgvector (Testcontainers)…")
        val pg =
            try {
                PostgresContainerManager.startIfNeeded()
            } catch (e: Exception) {
                info("❌ Failed to start Postgres container: ${e.message}")
                debug(e)
                return
            }
        info("✅ Postgres ready on ${pg.jdbcUrl}")

        val indexer =
            PgVectorIndexer(
                projectId = name,
                session = session,
            )

        info("🔎 Indexing project '$name' at $projectPath …")
        try {
            val count = indexer.indexProject(projectPath)
            info("✅ Indexed $count documents into pgvector (project '$name').")
        } catch (e: Exception) {
            info("❌ Index failed: ${e.message}")
            debug(e)
        }

        val meta =
            try {
                ProjectStore.create(name, projectPath.toString())
            } catch (e: IllegalStateException) {
                info("⚠️ ${e.message}")
                debug(e)
                ProjectStore.getByName(name) ?: return
            }

        info("🗂️  Saved project '${meta.name}' as ${meta.id} → ${meta.root}")
        info("⭐ Active project set to '${meta.name}'")

        // Keep existing session wiring (compat shim for old type if needed)
        session.setScope(meta)
        session.enableRagWith(indexer)

        // Start file watcher for the project
        FileWatcherManager.startWatchingProject(projectPath, indexer)
        info("👁️  File watcher started - changes will be automatically indexed.")
        info("🧠 RAG enabled for project '${meta.name}' (scope set).")
    }

    private fun parseArgs(args: List<String>): Pair<String, String>? {
        var name: String? = null
        var dir: String? = null
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-n", "--name" -> if (++i < args.size) name = args[i]
                "-d", "--dir", "--folder" -> if (++i < args.size) dir = args[i]
            }
            i++
        }
        return if (!name.isNullOrBlank() && !dir.isNullOrBlank()) name!! to dir!! else null
    }
}

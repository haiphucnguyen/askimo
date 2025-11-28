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
import io.askimo.core.util.logger
import org.jline.reader.ParsedLine
import java.nio.file.Files
import java.nio.file.Paths

class CreateProjectCommandHandler(
    private val session: Session,
) : CommandHandler {
    private val log = logger<CreateProjectCommandHandler>()
    override val keyword: String = ":create-project"
    override val description: String =
        "Create a project, auto-start Postgres+pgvector (Testcontainers), and index the folder.\n" +
            "Usage: :create-project -n <project-name> -d <project-folder>"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        val (name, dir) =
            parseArgs(args) ?: run {
                log.info("Usage: :create-project -n <project-name> -d <project-folder>")
                return
            }

        val projectPath = Paths.get(dir).toAbsolutePath().normalize()
        if (!Files.exists(projectPath) || !Files.isDirectory(projectPath)) {
            log.info("❌ Folder does not exist or is not a directory: $projectPath")
            return
        }

        if (ProjectStore.getByName(name) != null) {
            log.info("⚠️ Project '$name' already exists. Use ':project $name' to activate it.")
            return
        }

        log.info("🐘 Starting local Postgres+pgvector (Testcontainers)…")
        var indexer: PgVectorIndexer? = null
        try {
            val pg = PostgresContainerManager.startIfNeeded()
            log.info("✅ Postgres ready on ${pg.jdbcUrl}")

            indexer = PgVectorIndexer(
                projectId = name,
                session = session,
            )

            log.info("🔎 Indexing project '$name' at $projectPath …")
            val count = indexer.indexProject(projectPath)
            log.info("✅ Indexed $count documents into pgvector (project '$name').")
        } catch (e: Exception) {
            log.info("⚠️ Failed to start Postgres container or index project: ${e.message}")
            log.info("📝 Proceeding without vector indexing - you can index later when Docker is available.")
            log.error("Failed to create project", e)
        }

        val meta =
            try {
                ProjectStore.create(name, projectPath.toString())
            } catch (e: IllegalStateException) {
                log.info("⚠️ ${e.message}")
                log.error("Failed to create project", e)
                ProjectStore.getByName(name) ?: return
            }

        log.info("🗂️  Saved project '${meta.name}' as ${meta.id} → ${meta.root}")
        log.info("⭐ Active project set to '${meta.name}'")

        // Keep existing session wiring (compat shim for old type if needed)
        session.setScope(meta)

        if (indexer != null) {
            session.enableRagWith(indexer)

            // Start file watcher for the project
            FileWatcherManager.startWatchingProject(projectPath, indexer)
            log.info("👁️  File watcher started - changes will be automatically indexed.")
            log.info("🧠 RAG enabled for project '${meta.name}' (scope set).")
        } else {
            log.info("📝 Project created without vector indexing. Start Docker and use indexing commands to enable RAG later.")
        }
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
        return if (!name.isNullOrBlank() && !dir.isNullOrBlank()) name to dir else null
    }
}

/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.logging.display
import io.askimo.core.logging.displayError
import io.askimo.core.logging.logger
import io.askimo.core.project.PgVectorAdmin
import io.askimo.core.project.PostgresContainerManager
import io.askimo.core.project.ProjectStore
import org.jline.reader.ParsedLine

class DeleteProjectCommandHandler : CommandHandler {
    private val log = logger<DeleteProjectCommandHandler>()
    override val keyword: String = ":delete-project"
    override val description: String =
        "Delete a saved project (soft delete its metadata file under ~/.askimo/projects) and drop its pgvector embeddings.\n" +
            "Usage: :delete-project <project-name> | :delete-project --all"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        if (args.isEmpty()) {
            log.display("Usage: :delete-project <project-name> | :delete-project --all")
            return
        }

        val firstArg = args.first()

        // Handle --all option to delete all projects
        if (firstArg == "--all") {
            deleteAllProjects()
            return
        }

        // Handle single project deletion
        val name = firstArg

        // Lookup by name in the new per-project store
        val meta = ProjectStore.getByName(name)
        if (meta == null) {
            log.display("❌ Project '$name' not found. Use :projects to list.")
            return
        }

        print("⚠️  Delete project '$name'? [y/N]: ")
        val confirm = readlnOrNull()?.trim()?.lowercase()
        if (confirm != "y") {
            log.display("✋ Aborted.")
            return
        }

        // 1) Soft-delete the per-project file (moves to ~/.askimo/trash)
        val removed = ProjectStore.softDelete(meta.id)
        if (!removed) {
            log.display("ℹ️  Project '${meta.name}' was not removed (already missing).")
            return
        }
        log.display("🗂️  Removed project '${meta.name}' (id=${meta.id}) from registry.")

        // 2) Drop pgvector table for this project (still keyed by project *name* in MVP)
        log.display("🐘 Ensuring Postgres+pgvector is running…")
        val pg =
            try {
                PostgresContainerManager.startIfNeeded()
            } catch (e: Exception) {
                log.displayError("⚠️ Soft-deleted metadata, but could not connect to Postgres to drop embeddings: ${e.message}", e)
                return
            }

        try {
            val base = System.getenv("ASKIMO_EMBED_TABLE") ?: "askimo_embeddings"
            val table = PgVectorAdmin.projectTableName(base, meta.name)
            PgVectorAdmin.dropProjectTable(pg.jdbcUrl, pg.username, pg.password, base, meta.name)
            log.display("🧹 Dropped embeddings table \"$table\" for project '${meta.name}'.")
        } catch (e: Exception) {
            log.displayError("⚠️ Metadata removed, but failed to drop embeddings for '${meta.name}': ${e.message}", e)
            return
        }
    }

    private fun deleteAllProjects() {
        val projects = try {
            ProjectStore.list()
        } catch (e: Exception) {
            log.displayError("❌ Could not list projects: ${e.message}", e)
            return
        }

        if (projects.isEmpty()) {
            log.display("ℹ️  No projects found.")
            return
        }

        log.display("🗂️  Found ${projects.size} project(s) to delete:")
        projects.forEachIndexed { i, p -> log.display("   ${i + 1}. ${p.name} (id=${p.id})") }

        print("⚠️  Delete ALL ${projects.size} project(s)? This cannot be undone! [y/N]: ")
        val confirm = readlnOrNull()?.trim()?.lowercase()
        if (confirm != "y") {
            log.display("✋ Aborted.")
            return
        }

        var deletedCount = 0
        var failedCount = 0

        // 1) Soft-delete all project metadata
        for (meta in projects) {
            try {
                val removed = ProjectStore.softDelete(meta.id)
                if (removed) {
                    deletedCount++
                    log.display("🗂️  Soft-deleted '${meta.name}' (id=${meta.id}).")
                } else {
                    log.display("ℹ️  '${meta.name}' (id=${meta.id}) was already removed.")
                }
            } catch (e: Exception) {
                failedCount++
                log.displayError("⚠️  Failed to remove '${meta.name}': ${e.message}", e)
            }
        }

        // 2) Drop all pgvector tables
        log.display("🐘 Ensuring Postgres+pgvector is running…")
        val pg = try {
            PostgresContainerManager.startIfNeeded()
        } catch (e: Exception) {
            log.displayError("⚠️  Metadata removed, but could not connect to Postgres to drop embeddings: ${e.message}", e)
            return
        }

        val base = System.getenv("ASKIMO_EMBED_TABLE") ?: "askimo_embeddings"
        var embeddingsDropped = 0
        var embeddingsFailed = 0

        for (meta in projects) {
            try {
                val table = PgVectorAdmin.projectTableName(base, meta.name)
                PgVectorAdmin.dropProjectTable(pg.jdbcUrl, pg.username, pg.password, base, meta.name)
                embeddingsDropped++
                log.display("🧹 Dropped embeddings table \"$table\" for '${meta.name}'.")
            } catch (e: Exception) {
                embeddingsFailed++
                log.displayError("⚠️  Failed to drop embeddings for '${meta.name}': ${e.message}", e)
            }
        }

        // Summary
        if (failedCount == 0 && embeddingsFailed == 0) {
            log.display("✅ Successfully deleted all $deletedCount project(s) and their embeddings.")
        } else {
            log.display("⚠️  Deleted $deletedCount project(s), failed $failedCount. Dropped $embeddingsDropped embeddings, failed $embeddingsFailed.")
        }
    }
}

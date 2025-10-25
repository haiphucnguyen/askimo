/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.project.PgVectorAdmin
import io.askimo.core.project.PostgresContainerManager
import io.askimo.core.project.ProjectStore
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.info
import org.jline.reader.ParsedLine

class DeleteProjectCommandHandler : CommandHandler {
    override val keyword: String = ":delete-project"
    override val description: String =
        "Delete a saved project (soft delete its metadata file under ~/.askimo/projects) and drop its pgvector embeddings.\n" +
            "Usage: :delete-project <project-name> | :delete-project --all"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        if (args.isEmpty()) {
            info("Usage: :delete-project <project-name> | :delete-project --all")
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
            info("❌ Project '$name' not found. Use :projects to list.")
            return
        }

        print("⚠️  Delete project '$name'? [y/N]: ")
        val confirm = readlnOrNull()?.trim()?.lowercase()
        if (confirm != "y") {
            info("✋ Aborted.")
            return
        }

        // 1) Soft-delete the per-project file (moves to ~/.askimo/trash)
        val removed = ProjectStore.softDelete(meta.id)
        if (!removed) {
            info("ℹ️  Project '${meta.name}' was not removed (already missing).")
            return
        }
        info("🗂️  Removed project '${meta.name}' (id=${meta.id}) from registry.")

        // 2) Drop pgvector table for this project (still keyed by project *name* in MVP)
        info("🐘 Ensuring Postgres+pgvector is running…")
        val pg =
            try {
                PostgresContainerManager.startIfNeeded()
            } catch (e: Exception) {
                info("⚠️ Soft-deleted metadata, but could not connect to Postgres to drop embeddings: ${e.message}")
                debug(e)
                return
            }

        try {
            val base = System.getenv("ASKIMO_EMBED_TABLE") ?: "askimo_embeddings"
            val table = PgVectorAdmin.projectTableName(base, meta.name) // MVP: table keyed by name
            PgVectorAdmin.dropProjectTable(pg.jdbcUrl, pg.username, pg.password, base, meta.name)
            info("🧹 Dropped embeddings table \"$table\" for project '${meta.name}'.")
        } catch (e: Exception) {
            info("⚠️ Metadata removed, but failed to drop embeddings for '${meta.name}': ${e.message}")
            debug(e)
        }
    }

    private fun deleteAllProjects() {
        val projects = try {
            ProjectStore.list()
        } catch (e: Exception) {
            info("❌ Could not list projects: ${e.message}")
            debug(e)
            return
        }

        if (projects.isEmpty()) {
            info("ℹ️  No projects found.")
            return
        }

        info("🗂️  Found ${projects.size} project(s) to delete:")
        projects.forEachIndexed { i, p -> info("   ${i + 1}. ${p.name} (id=${p.id})") }

        print("⚠️  Delete ALL ${projects.size} project(s)? This cannot be undone! [y/N]: ")
        val confirm = readlnOrNull()?.trim()?.lowercase()
        if (confirm != "y") {
            info("✋ Aborted.")
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
                    info("🗂️  Soft-deleted '${meta.name}' (id=${meta.id}).")
                } else {
                    info("ℹ️  '${meta.name}' (id=${meta.id}) was already removed.")
                }
            } catch (e: Exception) {
                failedCount++
                info("⚠️  Failed to remove '${meta.name}': ${e.message}")
                debug(e)
            }
        }

        // 2) Drop all pgvector tables
        info("🐘 Ensuring Postgres+pgvector is running…")
        val pg = try {
            PostgresContainerManager.startIfNeeded()
        } catch (e: Exception) {
            info("⚠️  Metadata removed, but could not connect to Postgres to drop embeddings: ${e.message}")
            debug(e)
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
                info("🧹 Dropped embeddings table \"$table\" for '${meta.name}'.")
            } catch (e: Exception) {
                embeddingsFailed++
                info("⚠️  Failed to drop embeddings for '${meta.name}': ${e.message}")
                debug(e)
            }
        }

        // Summary
        if (failedCount == 0 && embeddingsFailed == 0) {
            info("✅ Successfully deleted all $deletedCount project(s) and their embeddings.")
        } else {
            info("⚠️  Deleted $deletedCount project(s), failed $failedCount. Dropped $embeddingsDropped embeddings, failed $embeddingsFailed.")
        }
    }
}

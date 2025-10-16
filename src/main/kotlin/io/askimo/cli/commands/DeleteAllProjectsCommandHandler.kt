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

class DeleteAllProjectsCommandHandler : CommandHandler {
    override val keyword: String = ":delete-all-projects"
    override val description: String =
        """
        Delete ALL saved projects (soft delete their metadata under ~/.askimo/projects) and drop their pgvector embeddings.
        Usage: :delete-all-projects [--force] [--dry-run] [--keep-embeddings]

        Options:
          --force            Skip interactive confirmation.
          --dry-run          Show what would be deleted; do not change anything.
          --keep-embeddings  Do NOT drop pgvector tables (only soft-delete metadata).
        """.trimIndent()

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1).toSet()
        val force = "--force" in args
        val dryRun = "--dry-run" in args
        val keepEmbeddings = "--keep-embeddings" in args

        val projects =
            try {
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

        info("🗂️  Found ${projects.size} project(s):")
        projects.forEachIndexed { i, p -> info("   ${i + 1}. ${p.name} (id=${p.id})") }

        if (dryRun) {
            info(
                "🔎 DRY RUN: Would soft-delete ${projects.size} metadata file(s) " +
                    (if (keepEmbeddings) "and keep embeddings" else "and drop corresponding embeddings tables") + ".",
            )
            return
        }

        if (!force) {
            info(
                "\n⚠️  This will DELETE ALL projects (soft-delete metadata) " +
                    (if (keepEmbeddings) "and keep embeddings tables." else "and DROP their embeddings tables.") +
                    "\nType EXACTLY: DELETE ALL  to continue, or anything else to abort.",
            )
            val confirm = readLine()?.trim()
            if (confirm != "DELETE ALL") {
                info("✅ Aborted.")
                return
            }
        }

        // 1) Soft-delete all project entries
        val softDeleteFailures = mutableListOf<Pair<String, Throwable>>()
        var softDeletedCount = 0
        for (meta in projects) {
            try {
                val removed = ProjectStore.softDelete(meta.id)
                if (removed) {
                    softDeletedCount++
                    info("🗑️  Soft-deleted '${meta.name}' (id=${meta.id}).")
                } else {
                    info("ℹ️  '${meta.name}' (id=${meta.id}) was already removed.")
                }
            } catch (e: Exception) {
                softDeleteFailures += meta.name to e
                info("⚠️  Failed to remove '${meta.name}': ${e.message}")
            }
        }

        // 2) Embeddings cleanup (optional)
        var embeddingsDropped = 0
        val embeddingFailures = mutableListOf<Pair<String, Throwable>>()
        if (!keepEmbeddings) {
            info("🐘 Ensuring Postgres+pgvector is running…")
            val pg =
                try {
                    PostgresContainerManager.startIfNeeded()
                } catch (e: Exception) {
                    info("⚠️  Metadata removed, but could not connect to Postgres to drop embeddings: ${e.message}")
                    null
                }

            if (pg != null) {
                val base = System.getenv("ASKIMO_EMBED_TABLE") ?: "askimo_embeddings"
                for (meta in projects) {
                    try {
                        val table = PgVectorAdmin.projectTableName(base, meta.name)
                        PgVectorAdmin.dropProjectTable(pg.jdbcUrl, pg.username, pg.password, base, meta.name)
                        embeddingsDropped++
                        info("🧹 Dropped embeddings table \"$table\" for '${meta.name}'.")
                    } catch (e: Exception) {
                        embeddingFailures += meta.name to e
                        info("⚠️  Failed to drop embeddings for '${meta.name}': ${e.message}")
                    }
                }
            }
        }

        info("\n===== Summary =====")
        info("Soft-deleted metadata: $softDeletedCount / ${projects.size}")
        if (softDeleteFailures.isNotEmpty()) {
            info("Soft-delete failures (${softDeleteFailures.size}):")
            softDeleteFailures.forEach { (name, ex) ->
                info("  - $name → ${ex.message}")
            }
        }

        if (!keepEmbeddings) {
            info("Embeddings tables dropped: $embeddingsDropped / ${projects.size}")
            if (embeddingFailures.isNotEmpty()) {
                info("Embeddings drop failures (${embeddingFailures.size}):")
                embeddingFailures.forEach { (name, ex) ->
                    info("  - $name → ${ex.message}")
                }
            }
        } else {
            info("Embeddings: kept (per --keep-embeddings).")
        }

        info("===================")
    }
}

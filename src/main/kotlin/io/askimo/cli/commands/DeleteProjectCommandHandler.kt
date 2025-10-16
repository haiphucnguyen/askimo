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
            "Usage: :delete-project <project-name>"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        if (args.isEmpty()) {
            info("Usage: :delete-project <project-name>")
            return
        }

        val name = args.first()

        // Lookup by name in the new per-project store
        val meta = ProjectStore.getByName(name)
        if (meta == null) {
            info("❌ Project '$name' not found. Use :projects to list.")
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
}

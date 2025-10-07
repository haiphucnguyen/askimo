/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import java.sql.DriverManager

object PgVectorAdmin {
    fun dropProjectTable(
        jdbcUrl: String,
        user: String,
        pass: String,
        baseTable: String = System.getenv("ASKIMO_EMBED_TABLE") ?: "askimo_embeddings",
        projectId: String,
    ) {
        val table = projectTableName(baseTable, projectId)
        DriverManager.getConnection(jdbcUrl, user, pass).use { conn ->
            conn.createStatement().use { st ->
                st.execute("""DROP TABLE IF EXISTS "$table" CASCADE;""")
            }
        }
    }

    fun projectTableName(
        base: String,
        projectId: String,
    ): String = base + "__" + slug(projectId)

    private fun slug(s: String): String = s.lowercase().replace("""[^a-z0-9]+""".toRegex(), "_").trim('_')
}

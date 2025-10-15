/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import io.askimo.core.config.AppConfig
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

object PostgresContainerManager {
    @Volatile private var container: PostgreSQLContainer<*>? = null

    fun startIfNeeded(): PostgreSQLContainer<*> =
        synchronized(this) {
            container?.takeIf { it.isRunning }?.let { return it }

            ensureTestcontainersReuseEnabled()
            val image = DockerImageName.parse("pgvector/pgvector:0.8.1-pg18-trixie")

            // clean up any old same-named container to avoid 409 conflicts
            runCatching { Runtime.getRuntime().exec(arrayOf("docker", "rm", "-f", "askimo-pg")).waitFor() }

            val c =
                PostgreSQLContainer(image)
                    .withDatabaseName("askimo")
                    .withUsername("askimo")
                    .withPassword("askimo")
                    .withStartupAttempts(1)
                    .withStartupTimeout(java.time.Duration.ofSeconds(60))
                    .withReuse(true)
                    .apply { start() }

            System.setProperty("ASKIMO_PG_URL", c.jdbcUrl)
            System.setProperty("ASKIMO_PG_USER", c.username)
            System.setProperty("ASKIMO_PG_PASS", c.password)
            AppConfig.reload()

            ensurePgVector(c)
            Runtime.getRuntime().addShutdownHook(Thread { runCatching { c.stop() } })
            container = c
            c
        }

    private fun ensurePgVector(c: PostgreSQLContainer<*>) {
        java.sql.DriverManager.getConnection(c.jdbcUrl, c.username, c.password).use { conn ->
            conn.createStatement().use { st -> st.execute("CREATE EXTENSION IF NOT EXISTS vector;") }
        }
    }

    private fun ensureTestcontainersReuseEnabled() {
        System.setProperty("testcontainers.reuse.enable", "true")
    }
}

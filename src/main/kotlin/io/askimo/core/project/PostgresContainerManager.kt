/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import com.github.dockerjava.api.model.Bind
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

object PostgresContainerManager {
    @Volatile private var container: PostgreSQLContainer<*>? = null

    fun startIfNeeded(): PostgreSQLContainer<*> =
        synchronized(this) {
            container?.takeIf { it.isRunning }?.let { return it }

            // pick your image; pgvector already includes the extension
            val image = DockerImageName.parse("pgvector/pgvector:0.8.1-pg18-trixie")

            // clean up any old same-named container to avoid 409 conflicts
            runCatching { Runtime.getRuntime().exec(arrayOf("docker", "rm", "-f", "askimo-pg")).waitFor() }

            val c =
                PostgreSQLContainer(image)
                    .withDatabaseName("askimo")
                    .withUsername("askimo")
                    .withPassword("askimo")
                    // use a named Docker volume -> /var/lib/postgresql/data
                    .withCreateContainerCmdModifier { cmd ->
                        val hostConfig = cmd.hostConfig
                        hostConfig?.withBinds(Bind.parse("askimo-pgdata:/var/lib/postgresql/data"))
                        cmd.withHostConfig(hostConfig).withName("askimo-pg")
                    }.apply { start() }

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
}

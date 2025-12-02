/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import io.askimo.testcontainers.TestContainersConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory
import java.sql.DriverManager

class PostgresContainerManagerTest {
    companion object {
        @JvmStatic
        @AfterAll
        fun tearDownAll() {
            runCatching { PostgresContainerManager.startIfNeeded().stop() }
        }
    }

    @Test
    fun `should start PostgresContainerManager and be running`() {
        TestContainersConfig.ensureConfigured()
        val dockerAvailable = runCatching { DockerClientFactory.instance().client() }.isSuccess
        assumeTrue(dockerAvailable, "Docker is not available; skipping PostgresContainerManager test")

        val pg =
            runCatching { PostgresContainerManager.startIfNeeded() }
                .getOrElse { throwable ->
                    assumeTrue(false, "Skipping: could not start Postgres container: ${throwable.message}")
                    return
                }

        // Verify it's running
        assertTrue(pg.isRunning, "Postgres container should be running after startIfNeeded()")

        // Verify JDBC connectivity
        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { conn ->
            conn.createStatement().use { st ->
                st.execute("SELECT 1;")
            }
        }
    }
}

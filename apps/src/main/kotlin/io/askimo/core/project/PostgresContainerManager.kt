package io.askimo.core.project

import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager

object PostgresContainerManager {
    // Keep a single container per JVM
    @Volatile private var container: PostgreSQLContainer<*>? = null

    fun startIfNeeded(): PostgreSQLContainer<*> {
        synchronized(this) {
            if (container?.isRunning == true) return container!!

            val c = PostgreSQLContainer("ankane/pgvector:latest")
                .withDatabaseName("askimo")
                .withUsername("askimo")
                .withPassword("askimo")

            c.start()
            // Ensure pgvector extension is available
            ensurePgVector(c)
            // Stop cleanly on JVM shutdown
            Runtime.getRuntime().addShutdownHook(Thread { runCatching { c.stop() } })
            container = c
            return c
        }
    }

    private fun ensurePgVector(c: PostgreSQLContainer<*>) {
        DriverManager.getConnection(c.jdbcUrl, c.username, c.password).use { conn ->
            conn.createStatement().use { st ->
                st.execute("CREATE EXTENSION IF NOT EXISTS vector;")
            }
        }
    }
}

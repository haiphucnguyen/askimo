/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.project.PostgresContainerManager
import io.askimo.core.providers.ModelProvider
import io.askimo.core.session.Session
import io.askimo.core.session.SessionParams
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import org.testcontainers.DockerClientFactory
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.ollama.OllamaContainer
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.sql.DriverManager

@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
class CreateProjectCommandHandlerTest : CommandHandlerTestBase() {
    @Container
    @JvmField
    val ollama: OllamaContainer =
        OllamaContainer(DockerImageName.parse("ollama/ollama:latest"))

    companion object {
        @JvmStatic
        @AfterAll
        fun tearDownAll() {
            runCatching { PostgresContainerManager.startIfNeeded().stop() }
        }
    }

    @Test
    fun `should start postgres container and be connectable`() {
        val dockerAvailable = runCatching { DockerClientFactory.instance().client() }.isSuccess
        assumeTrue(dockerAvailable, "Docker is not available; skipping Postgres container test")

        val canStart = runCatching { PostgresContainerManager.startIfNeeded().also { it.stop() } }.isSuccess
        assumeTrue(canStart, "Skipping: could not start Postgres container in this environment")

        val host = ollama.host
        val port = ollama.getMappedPort(11434)
        val baseUrl = "http://$host:$port"
        System.setProperty("OLLAMA_URL", baseUrl)
        System.setProperty("OLLAMA_EMBED_MODEL", "nomic-embed-text")

        val pull = ollama.execInContainer("ollama", "pull", "nomic-embed-text")
        assumeTrue(pull.exitCode == 0, "Skipping: could not pull 'nomic-embed-text' in Ollama container")

        val tempDir = Files.createTempDirectory("askimo-test-project").toAbsolutePath()

        val session = Session(SessionParams.noOp())
        session.params.currentProvider = ModelProvider.OLLAMA
        val handler = CreateProjectCommandHandler(session)

        val projectName =
            "pg-test-project-" +
                java.util.UUID
                    .randomUUID()
                    .toString()
                    .take(8)
        val line =
            mockParsedLine(
                ":create-project",
                "-n",
                projectName,
                "-d",
                tempDir.toString(),
            )

        // when
        handler.handle(line)

        val output = getOutput()
        assertTrue(output.contains("Postgres ready on"), "Expected handler to report Postgres readiness. Output: \n$output")

        // then: container is running and JDBC is connectable
        val pg = PostgresContainerManager.startIfNeeded()
        assertTrue(pg.isRunning, "Postgres container should be running")

        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { conn ->
            conn.createStatement().use { st ->
                st.execute("SELECT 1;")
            }
        }
    }
}

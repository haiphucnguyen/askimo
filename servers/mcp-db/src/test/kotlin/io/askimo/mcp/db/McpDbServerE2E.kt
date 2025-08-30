/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.mcp.db

import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.testcontainers.containers.PostgreSQLContainer
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CountDownLatch

class McpDbServerE2E {
    /** Spawn server JVM process. We keep STDERR as a pipe so we can wait for BOOTSTRAP-OK. */
    private fun spawnServer(
        args: List<String>,
        env: Map<String, String>,
    ): Process {
        val javaBin = File(System.getProperty("java.home"), "bin/java").absolutePath
        val cp = System.getProperty("java.class.path")
        val cmd = mutableListOf(javaBin, "-cp", cp, "io.askimo.mcp.db.DbToolKt") + args
        val pb = ProcessBuilder(cmd)
        pb.redirectError(ProcessBuilder.Redirect.PIPE)
        pb.environment().putAll(env)
        return pb.start()
    }

    /** Wait up to timeout for "BOOTSTRAP-OK" on stderr (server signals readiness). */
    private fun waitForBootstrap(
        proc: Process,
        timeoutMs: Long = 10_000,
    ): Boolean {
        val latch = CountDownLatch(1)

        val t =
            kotlin.concurrent.thread(start = true, isDaemon = true, name = "bootstrap-waiter") {
                BufferedReader(
                    InputStreamReader(proc.errorStream, StandardCharsets.UTF_8),
                ).use { br ->
                    while (proc.isAlive) {
                        val line = br.readLine() ?: break
                        if (line.contains("BOOTSTRAP-OK")) {
                            latch.countDown()
                            break
                        }
                    }
                }
            }

        val ok = latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!ok) t.interrupt()
        return ok
    }

    @Test
    fun `ping and listSchemas via env password`() =
        runBlocking {
            val pg =
                PostgreSQLContainer("postgres:16-alpine").apply {
                    withStartupTimeout(Duration.ofSeconds(60))
                    start()
                }

            // Spawn server with env creds (preferred for automated tests)
            val proc =
                spawnServer(
                    args = listOf("--db-id", "e2e-env"),
                    env =
                        mapOf(
                            "DB_URL" to pg.jdbcUrl,
                            "DB_USER" to pg.username,
                            "DB_PASSWORD" to pg.password,
                        ),
                )

            try {
                // Wait for bootstrap marker so we know DB init succeeded and MCP is about to start
                val ready = waitForBootstrap(proc, timeoutMs = 10_000)
                assertTrue(ready, "Server did not emit BOOTSTRAP-OK (stderr). Exit=${if (proc.isAlive) "alive" else proc.exitValue()}")

                // MCP client over stdio (server stdout -> client input; client output -> server stdin)
                val client = Client(Implementation("askimo-e2e", "0.0.1"))
                val transport =
                    StdioClientTransport(
                        proc.inputStream.asSource().buffered(),
                        proc.outputStream.asSink().buffered(),
                    )

                try {
                    withTimeout(100_000) { client.connect(transport) }
                } catch (e: Exception) {
                    val stderr = proc.errorStream.readAllBytes().toString(Charsets.UTF_8)
                    val state = if (proc.isAlive) "alive" else "exit=${proc.exitValue()}"
                    fail("connect() failed ($state): ${e.message}\nSTDERR:\n$stderr")
                }

                val ping = requireNotNull(client.callTool("db.ping", JsonObject(emptyMap()))) { "null ping result" }
                val pingText =
                    ping.content
                        .filterIsInstance<TextContent>()
                        .firstOrNull()
                        ?.text
                assertEquals("ok", pingText)

                val schemas = requireNotNull(client.callTool("db.listSchemas", JsonObject(emptyMap()))) { "null listSchemas result" }
                val names = schemas.content.mapNotNull { (it as? TextContent)?.text }
                assertTrue(names.isNotEmpty(), "expected at least one schema")
                assertTrue(names.any { it.equals("public", ignoreCase = true) }, "expected to contain 'public'")

                client.close()
            } finally {
                proc.destroy()
                proc.waitFor()
                pg.stop()
            }
        }
}

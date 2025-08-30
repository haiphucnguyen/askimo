/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.db

/** Live, attached DB session (backed by an MCP server process under the hood). */
interface DbRuntime {
    val id: String

    suspend fun ping(): String

    suspend fun listSchemas(): List<String>

    /** Tear down MCP client & child process. Safe to call multiple times. */
    suspend fun close()
}

/** Factory that resolves secrets, spawns askimo-mcp-db, connects MCP, and returns a DbRuntime. */
interface DbLauncher {
    suspend fun launch(conn: DbConnection): DbRuntime
}

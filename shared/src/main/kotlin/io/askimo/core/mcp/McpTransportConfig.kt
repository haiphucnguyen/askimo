/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Base class for transport configurations.
 * Sealed class ensures type-safety and exhaustive when expressions.
 */
@Serializable
sealed class McpTransportConfig {
    abstract val id: String
    abstract val name: String
    abstract val description: String?
}

/**
 * Configuration for stdio-based MCP transport
 */
@Serializable
@SerialName("stdio")
data class StdioMcpTransportConfig(
    override val id: String,
    override val name: String,
    override val description: String? = null,
    val command: List<String>,
    val env: Map<String, String> = emptyMap(),
) : McpTransportConfig()

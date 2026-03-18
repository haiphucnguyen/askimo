/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp.config

import io.askimo.core.mcp.McpInstanceData

/**
 * Root wrapper used exclusively for YAML (de)serialisation of MCP instances.
 *
 * Jackson requires a top-level object when reading/writing a list, so all
 * [McpInstanceData] entries are nested under the `instances` key in the file.
 * Shared by [ProjectMcpInstancesConfig] and [GlobalMcpInstancesConfig].
 */
internal data class InstancesWrapper(
    val instances: List<McpInstanceData>,
)

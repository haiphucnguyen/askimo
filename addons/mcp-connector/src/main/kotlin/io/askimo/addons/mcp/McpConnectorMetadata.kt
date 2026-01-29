/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.addons.mcp
data class McpConnectorMetadata(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String = "Askimo Team",
    val homepage: String? = null,
)

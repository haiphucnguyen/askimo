/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp

import kotlinx.serialization.Serializable

/**
 * Generic MCP server definition.
 * Can represent ANY MCP server (MongoDB, PostgreSQL, custom, etc.)
 * without needing code changes.
 */
@Serializable
data class McpServerDefinition(
    val id: String,
    val name: String,
    val description: String,
    val transportType: TransportType,

    val stdioConfig: StdioConfig? = null,
    val httpConfig: HttpConfig? = null,

    // Metadata
    val version: String = "1.0.0",
    val author: String? = null,
    val tags: List<String> = emptyList(),
) {
    init {
        when (transportType) {
            TransportType.STDIO -> require(stdioConfig != null) {
                "stdioConfig is required for STDIO transport type"
            }
            TransportType.HTTP -> require(httpConfig != null) {
                "httpConfig is required for HTTP transport type"
            }
        }
    }
}

@Serializable
enum class TransportType {
    STDIO,
    HTTP,
}

/**
 * Configuration for stdio-based MCP servers
 */
@Serializable
data class StdioConfig(
    /**
     * Command template with variable placeholders.
     * Examples:
     * - ["npx", "-y", "mongodb-mcp-server@latest"]  // No variables
     * - ["node", "{{scriptPath}}", "--port", "{{port}}"]  // With variables
     * - ["python", "server.py", "{{?debug:--verbose}}"]  // Conditional flag
     */
    val commandTemplate: List<String>,

    /**
     * Environment variable templates (optional).
     * Examples:
     * - {"API_KEY": "{{apiKey}}"}  // Variable
     * - {"NODE_ENV": "production"}  // Static value
     * - {"MONGO_URI": "mongodb://{{host}}:{{port}}/{{db}}"}  // Multiple variables
     */
    val envTemplate: Map<String, String> = emptyMap(),

    /**
     * Working directory for process execution (optional).
     * Can also contain variables: "{{projectRoot}}/scripts"
     */
    val workingDirectory: String? = null,
)

/**
 * Configuration for HTTP-based MCP servers (remote/hosted servers)
 */
@Serializable
data class HttpConfig(
    /**
     * URL template for the MCP server endpoint.
     * - SSE mode:        "https://{{host}}/sse"
     * - Streamable mode: "https://{{host}}/mcp"
     */
    val urlTemplate: String,

    /**
     * HTTP header templates sent with every request (optional).
     * Examples:
     * - {"Authorization": "Bearer {{apiKey}}"}
     * - {"X-Api-Key": "{{apiKey}}"}
     */
    val headersTemplate: Map<String, String> = emptyMap(),

    /**
     * Request/connection timeout in milliseconds. Defaults to 60 s.
     */
    val timeoutMs: Long = 60_000,

    /**
     * Transport mode for the HTTP connection.
     * - SSE:        legacy Server-Sent Events transport (HttpMcpTransport)
     * - STREAMABLE: modern streamable HTTP transport (StreamableHttpMcpTransport)
     */
    val mode: HttpTransportMode = HttpTransportMode.STREAMABLE,
)

@Serializable
enum class HttpTransportMode {
    /**
     * Legacy SSE-based transport (HttpMcpTransport).
     * Use for servers that only support the older SSE MCP protocol.
     */
    SSE,

    /**
     * Modern streamable HTTP transport (StreamableHttpMcpTransport).
     * Preferred for all new servers — supports bidirectional streaming.
     */
    STREAMABLE,
}

/**
 * Defines a configuration parameter that users must provide
 */
@Serializable
data class Parameter(
    /**
     * Unique key used in templates as {{key}}
     */
    val key: String,

    /**
     * Display label for UI
     */
    val label: String,

    /**
     * Parameter type
     */
    val type: ParameterType,

    /**
     * Whether this parameter is required
     */
    val required: Boolean = true,

    /**
     * Default value if not provided
     */
    val defaultValue: String? = null,

    /**
     * Help text for users
     */
    val description: String? = null,

    /**
     * Placeholder text for input fields
     */
    val placeholder: String? = null,

    /**
     * Validation pattern (regex)
     */
    val validationPattern: String? = null,

    /**
     * Where this parameter is used
     */
    val location: ParameterLocation = ParameterLocation.COMMAND,

    /**
     * If true, this parameter value can contain comma-separated entries.
     * For ENVIRONMENT location: "KEY1=value1,KEY2=value2"
     * For COMMAND location: "arg1,arg2,arg3"
     * The system will split by comma when processing.
     */
    val allowMultiple: Boolean = false,
)

@Serializable
enum class ParameterLocation {
    /**
     * Parameter goes into command template (commandTemplate)
     */
    COMMAND,

    /**
     * Parameter goes into environment variables (envTemplate)
     */
    ENVIRONMENT,

    /**
     * Parameter can be used in both command and environment places
     */
    BOTH,

    /**
     * Parameter goes into HTTP header templates (headersTemplate)
     */
    HTTP_HEADER,

    /**
     * Parameter goes into the HTTP URL template (urlTemplate)
     */
    HTTP_URL,
}

@Serializable
enum class ParameterType {
    /**
     * Plain text string
     */
    STRING,

    /**
     * Numeric value
     */
    NUMBER,

    /**
     * Boolean flag (true/false)
     */
    BOOLEAN,

    /**
     * URL/URI
     */
    URL,

    /**
     * Sensitive data (passwords, API keys) - should be masked in UI
     */
    SECRET,

    /**
     * File path
     */
    PATH,
}

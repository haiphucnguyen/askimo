/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp.connectors

import dev.langchain4j.mcp.client.transport.McpTransport
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport
import io.askimo.core.logging.logger
import io.askimo.core.mcp.HttpMcpTransportConfig
import io.askimo.core.mcp.HttpTransportMode
import io.askimo.core.mcp.McpConnector
import io.askimo.core.mcp.ValidationResult
import java.net.URI
import java.time.Duration

/**
 * Connector for HTTP-based MCP servers (remote/hosted servers).
 * Supports both legacy SSE (HttpMcpTransport) and modern
 * streamable HTTP (StreamableHttpMcpTransport) modes.
 */
class HttpMcpConnector(
    private val config: HttpMcpTransportConfig,
) : McpConnector() {

    private val log = logger<HttpMcpConnector>()

    override suspend fun createTransport(): McpTransport {
        val timeout = Duration.ofMillis(config.timeoutMs)
        log.debug(
            "Creating HTTP MCP transport: mode={}, url={}, timeout={}ms",
            config.mode,
            config.url,
            config.timeoutMs,
        )

        return when (config.mode) {
            HttpTransportMode.SSE -> {
                val builder = StreamableHttpMcpTransport.builder()
                    .url(config.url)
                    .timeout(timeout)
                    .logRequests(log.isDebugEnabled)
                    .logResponses(log.isTraceEnabled)
                    .logger(log)

                if (config.headers.isNotEmpty()) {
                    builder.customHeaders(config.headers)
                }

                builder.build()
            }

            HttpTransportMode.STREAMABLE -> {
                val builder = StreamableHttpMcpTransport.builder()
                    .url(config.url)
                    .timeout(timeout)
                    .logRequests(log.isDebugEnabled)
                    .logResponses(log.isTraceEnabled)
                    .logger(log)

                if (config.headers.isNotEmpty()) {
                    builder.customHeaders(config.headers)
                }

                builder.build()
            }
        }
    }

    override fun validate(): ValidationResult {
        val errors = mutableListOf<String>()

        if (config.url.isBlank()) {
            errors.add("URL cannot be blank")
        } else {
            try {
                val uri = URI(config.url)
                if (uri.scheme == null || !uri.scheme.startsWith("http")) {
                    errors.add("URL must use http or https scheme: ${config.url}")
                }
            } catch (_: Exception) {
                errors.add("Invalid URL: ${config.url}")
            }
        }

        if (config.timeoutMs <= 0) {
            errors.add("Timeout must be positive, got ${config.timeoutMs}")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
        )
    }
}

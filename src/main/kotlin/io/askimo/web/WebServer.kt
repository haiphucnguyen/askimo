/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.web

import io.askimo.core.providers.chat
import io.askimo.core.session.SessionFactory
import io.askimo.core.util.appJson
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.application
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val message: String,
)

@Serializable
data class ChatResponse(
    val reply: String,
)

class WebServer(
    private val host: String = "127.0.0.1",
    private val startPort: Int = 8080,
) {
    private var server: EmbeddedServer<*, *>? = null
    private var boundPort: Int? = null

    /** The actual port the server bound to (after auto-increment), or null if not started. */
    val port: Int? get() = boundPort

    fun start(wait: Boolean = false) {
        if (server != null) return

        val maxAttempts = 200 // try up to 200 consecutive ports
        var attemptPort = startPort
        var lastError: Throwable? = null

        while (attemptPort < startPort + maxAttempts) {
            try {
                val engine =
                    embeddedServer(CIO, host = host, port = attemptPort) {
                        install(ContentNegotiation) { json(appJson) }

                        val session = SessionFactory.createSession()
                        println("Web server running at http://$host:$attemptPort")
                        routing {
                            post("/api/chat/stream") {
                                val raw = call.receiveText()
                                val req =
                                    try {
                                        appJson.decodeFromString<ChatRequest>(raw)
                                    } catch (e: Exception) {
                                        application.log.error(
                                            "Failed to parse ChatRequest: ${e.message}. raw=$raw",
                                            e,
                                        )
                                        call.respond(
                                            HttpStatusCode.BadRequest,
                                            "Invalid payload: ${e.message}",
                                        )
                                        return@post
                                    }

                                call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
                                call.response.headers.append("X-Accel-Buffering", "no")
                                call.response.headers.append(HttpHeaders.Connection, "keep-alive")
                                call.response.headers.append("X-Askimo-Format", "markdown")

                                call.respondTextWriter(contentType = ContentType.Text.Plain) {
                                    session.getChatService().chat(req.message) { token ->
                                        write(token)
                                        flush()
                                    }
                                }
                            }
                            staticResources("/", basePackage = "public") {
                                staticResources("index.html", "public")
                            }
                        }
                    }

                // Try to start on this port; will throw if the port is taken
                engine.start(wait = wait)

                server = engine
                boundPort = attemptPort

                println("Web server running at http://$host:$attemptPort")
                return
            } catch (t: Throwable) {
                lastError = t
                // If it's a bind error, try next port; otherwise rethrow
                val cause = t.cause ?: t
                val isBindError =
                    cause is java.net.BindException ||
                        (cause.message?.contains("Address already in use", ignoreCase = true) == true)

                if (!isBindError) {
                    throw t
                }

                println("Port $attemptPort is in use; trying ${attemptPort + 1}…")
                attemptPort++
            }
        }

        throw IllegalStateException(
            "Could not bind to any port in range $startPort..${startPort + maxAttempts - 1}",
            lastError,
        )
    }

    fun stop() {
        server?.stop()
        server = null
        boundPort = null
    }
}

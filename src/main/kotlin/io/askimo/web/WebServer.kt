package io.askimo.web

import io.askimo.core.util.appJson
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
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
    private val port: Int = 8080,
) {
    private var server: EmbeddedServer<*, *>? = null

    fun start(wait: Boolean = false) {
        if (server != null) return
        server =
            embeddedServer(CIO, host = host, port = port) {
                install(ContentNegotiation) {
                    json(appJson)
                }

                routing {
                    get("/hello") { call.respondText("Hello, Askimo with Ktor CIO!") }
                    post("/api/chat/stream") {
                        val req = call.receive<ChatRequest>()

                        call.response.headers.append(HttpHeaders.CacheControl, "no-cache")

                        call.respondTextWriter(contentType = ContentType.Text.Plain) {
                            // demo: stream the echo back in small chunks with a tiny delay
                            val text = "You said (stream): ${req.message}"
                            for (chunk in text.chunked(10)) {
                                write(chunk)
                                flush()
                                delay(40) // simulate tokenization latency
                            }
                            // final newline is nice for curl; not required
                            write("\n")
                            flush()
                        }
                    }
                    staticResources("/", basePackage = "public") {
                        staticResources("index.html", "public")
                    }
                }
            }.also { it.start(wait = wait) }

        println("Web server running at http://$host:$port  (GET /hello)")
    }

    fun stop() {
        server?.stop()
        server = null
    }
}

package io.askimo.mcp.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import javax.sql.DataSource

object DbTool {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val ds = buildDataSource(
            url  = getenv("DB_URL", "jdbc:postgresql://localhost:5432/postgres"),
            user = getenv("DB_USER", "postgres"),
            pass = getenv("DB_PASSWORD", "")
        )

        val server = Server(
            serverInfo = Implementation(name = "askimo-mcp-db", version = "0.1.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true)
                )
            )
        )

        // db.ping -> "ok"
        server.addTool(
            name = "db.ping",
            description = "Health check",
            inputSchema = Tool.Input()
        ) {
            CallToolResult(content = listOf(TextContent("ok")))
        }

        // db.listSchemas -> returns a list of TextContent
        server.addTool(
            name = "db.listSchemas",
            description = "List database schemas",
            inputSchema = Tool.Input()
        ) {
            val names = mutableListOf<String>()
            ds.connection.use { c ->
                c.metaData.schemas.use { rs ->
                    while (rs.next()) names += rs.getString("TABLE_SCHEM")
                }
            }
            CallToolResult(content = names.map { TextContent(it) })
        }

        // Connect over stdio (suspend until the client disconnects)
        val transport = StdioServerTransport(
            inputStream = System.`in`.asSource().buffered(),
            outputStream = System.out.asSink().buffered()
        )
        server.connect(transport)
    }

    private fun buildDataSource(url: String, user: String, pass: String): DataSource =
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url; username = user; password = pass
            maximumPoolSize = 4; minimumIdle = 1; isAutoCommit = true
        })

    private fun getenv(key: String, def: String) =
        System.getenv(key)?.takeIf { it.isNotBlank() } ?: def
}

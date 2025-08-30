/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.mcp.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import javax.sql.DataSource
import kotlin.system.exitProcess

private const val VERSION = "0.1.0"

private const val DEFAULT_READ_ONLY = true
private const val DEFAULT_MAX_ROWS = 100
private const val DEFAULT_TIMEOUT_SEC = 10
private const val DEFAULT_POOL_SIZE = 4
private const val DEFAULT_LOG_LEVEL = "info"

private data class Options(
    val showHelp: Boolean = false,
    val showVersion: Boolean = false,
    val dbId: String? = null,
    val url: String? = null,
    val username: String? = null,
    val passwordLiteral: String? = null,
    val passwordEnv: String? = null,
    val passwordFile: String? = null,
    val passwordStdin: Boolean = false,
    val engine: String? = null, // postgres | mysql | sqlserver | sqlite
    val readOnly: Boolean? = null,
    val maxRows: Int? = null,
    val timeoutSec: Int? = null,
    val poolSize: Int? = null,
    val logLevel: String? = null,
)

private fun usage(): String =
    """
askimo-mcp-db $VERSION — MCP server for databases (STDIN/STDOUT)

USAGE:
  askimo-mcp-db [FLAGS] [OPTIONS]

FLAGS:
  -h, --help                Show this help and exit
      --version             Show version and exit
      --read-only           Enforce read-only (DEFAULT: $DEFAULT_READ_ONLY)
      --no-read-only        Disable read-only (NOT recommended)
      --password-stdin      Read one password line from STDIN BEFORE MCP starts

OPTIONS:
      --db-id <id>          Logical name for this connection (for logs)
      --url <jdbc-url>      JDBC URL (e.g., jdbc:postgresql://host:5432/db)
      --username <user>     Database user
      --password <value>    Password (WARNING: visible in process list)
      --password-env <NAME> Read password from environment variable NAME
      --password-file <fp>  Read password from file path
      --engine <name>       postgres|mysql|sqlserver|sqlite (usually inferred)
      --max-rows <n>        Row cap per SELECT (DEFAULT: $DEFAULT_MAX_ROWS)
      --timeout-sec <n>     Query timeout seconds (DEFAULT: $DEFAULT_TIMEOUT_SEC)
      --pool-size <n>       Hikari pool size (DEFAULT: $DEFAULT_POOL_SIZE)
      --log-level <lvl>     trace|debug|info|warn|error (DEFAULT: $DEFAULT_LOG_LEVEL)

ENV FALLBACKS:
  DB_ID, DB_URL, DB_USER, DB_PASSWORD, DB_ENGINE, DB_READ_ONLY,
  DB_MAX_ROWS, DB_TIMEOUT_SEC, DB_POOL_SIZE, LOG_LEVEL
    """.trimIndent()

private fun parseArgs(argv: Array<String>): Options {
    var i = 0
    var o = Options()

    fun needValue(flag: String): String {
        if (i + 1 >= argv.size) fail("Missing value for $flag")
        return argv[++i]
    }
    while (i < argv.size) {
        when (val a = argv[i]) {
            "-h", "--help" -> o = o.copy(showHelp = true)
            "--version" -> o = o.copy(showVersion = true)
            "--db-id" -> o = o.copy(dbId = needValue(a))
            "--url" -> o = o.copy(url = needValue(a))
            "--username" -> o = o.copy(username = needValue(a))
            "--password" -> o = o.copy(passwordLiteral = needValue(a))
            "--password-env" -> o = o.copy(passwordEnv = needValue(a))
            "--password-file" -> o = o.copy(passwordFile = needValue(a))
            "--password-stdin" -> o = o.copy(passwordStdin = true)
            "--engine" -> o = o.copy(engine = needValue(a).lowercase())
            "--read-only" -> o = o.copy(readOnly = true)
            "--no-read-only" -> o = o.copy(readOnly = false)
            "--max-rows" -> o = o.copy(maxRows = needValue(a).toIntOrNull() ?: fail("Invalid --max-rows"))
            "--timeout-sec" -> o = o.copy(timeoutSec = needValue(a).toIntOrNull() ?: fail("Invalid --timeout-sec"))
            "--pool-size" -> o = o.copy(poolSize = needValue(a).toIntOrNull() ?: fail("Invalid --pool-size"))
            "--log-level" -> o = o.copy(logLevel = needValue(a).lowercase())
            else -> fail("Unknown argument: $a")
        }
        i++
    }
    return o
}

private fun mergeWithEnv(o: Options): Options {
    fun envBool(name: String): Boolean? =
        System.getenv(name)?.let {
            when (it.trim().lowercase()) {
                "1", "true", "yes", "y", "on" -> true
                "0", "false", "no", "n", "off" -> false
                else -> null
            }
        }

    fun envInt(name: String): Int? = System.getenv(name)?.trim()?.toIntOrNull()

    return o.copy(
        dbId = o.dbId ?: System.getenv("DB_ID"),
        url = o.url ?: System.getenv("DB_URL"),
        username = o.username ?: System.getenv("DB_USER"),
        passwordLiteral = o.passwordLiteral ?: System.getenv("DB_PASSWORD"),
        engine = o.engine ?: System.getenv("DB_ENGINE")?.lowercase(),
        readOnly = o.readOnly ?: envBool("DB_READ_ONLY") ?: DEFAULT_READ_ONLY,
        maxRows = o.maxRows ?: envInt("DB_MAX_ROWS") ?: DEFAULT_MAX_ROWS,
        timeoutSec = o.timeoutSec ?: envInt("DB_TIMEOUT_SEC") ?: DEFAULT_TIMEOUT_SEC,
        poolSize = o.poolSize ?: envInt("DB_POOL_SIZE") ?: DEFAULT_POOL_SIZE,
        logLevel = o.logLevel ?: System.getenv("LOG_LEVEL")?.lowercase() ?: DEFAULT_LOG_LEVEL,
    )
}

private fun resolvePassword(o: Options): String? {
    if (o.passwordStdin) {
        System.console()?.let { console ->
            System.err.print("Password: ")
            System.err.flush()
            return String(console.readPassword())
        }
        return readSingleLineFromStdin()
    }
    o.passwordEnv?.let { envName ->
        return System.getenv(envName) ?: fail("Environment variable $envName not set for --password-env")
    }
    o.passwordFile?.let { fp ->
        val file = File(expandHome(fp))
        if (!file.exists()) fail("Password file not found: $fp")
        return file.readText().trimEnd('\n', '\r')
    }
    return o.passwordLiteral
}

/** Reads a single line from System.in without over-reading (UTF-8). */
private fun readSingleLineFromStdin(): String {
    val inStream = System.`in`
    val buf = ByteArrayOutputStream(128)
    while (true) {
        val b = inStream.read()
        if (b == -1) break
        if (b == '\n'.code) break
        if (b == '\r'.code) {
            // consume optional following '\n'
            val next = inStream.read()
            if (next != '\n'.code && next != -1) {
                // cannot unread; acceptable for CR-only cases
            }
            break
        }
        buf.write(b)
    }
    return buf.toString(StandardCharsets.UTF_8)
}

private fun expandHome(path: String): String = if (path.startsWith("~")) path.replaceFirst("~", System.getProperty("user.home")) else path

private fun fail(msg: String): Nothing {
    System.err.println("error: $msg\nUse --help for usage.")
    exitProcess(2)
}

fun main(args: Array<String>) =
    runBlocking {
        var opts = parseArgs(args)
        if (opts.showHelp) {
            println(usage())
            return@runBlocking
        }
        if (opts.showVersion) {
            println("askimo-mcp-db $VERSION")
            return@runBlocking
        }

        opts = mergeWithEnv(opts)
        val url = opts.url ?: fail("Missing --url (or DB_URL)")
        val user = opts.username ?: fail("Missing --username (or DB_USER)")
        val engine =
            (opts.engine ?: inferEngine(url))
                ?: fail("Unable to infer --engine from URL. Provide --engine explicitly.")
        val password = resolvePassword(opts)
        System.setProperty(
            "org.slf4j.simpleLogger.defaultLogLevel",
            mapLogLevel(opts.logLevel),
        )

        System.err.println("askimo-mcp-db $VERSION starting (dbId=${opts.dbId ?: "-"}, engine=$engine, readOnly=${opts.readOnly})")

        val ds = buildDataSource(url, user, password ?: "", opts.poolSize ?: DEFAULT_POOL_SIZE)

        // Basic connection check (fail fast if creds/URL are wrong)
        try {
            ds.connection.use { c -> c.createStatement().use { st -> st.execute("SELECT 1") } }
        } catch (ex: Exception) {
            System.err.println("error: failed to connect to database: ${ex.message}")
            (ds as? HikariDataSource)?.close()
            exitProcess(1)
        }

        // Signal readiness to launchers (stderr), then start MCP on stdout/stdin
        System.err.println("BOOTSTRAP-OK")

        val server =
            Server(
                serverInfo = Implementation(name = "askimo-mcp-db", version = VERSION),
                options =
                    ServerOptions(
                        capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true)),
                    ),
            )

        // Tools (minimal)
        addPingTool(server)
        addListSchemasTool(server, ds)

        val transport =
            StdioServerTransport(
                inputStream = System.`in`.asSource().buffered(),
                outputStream = System.out.asSink().buffered(),
            )

        Runtime.getRuntime().addShutdownHook(
            Thread {
                (ds as? HikariDataSource)?.close()
                System.err.println("askimo-mcp-db stopped")
            },
        )

        server.connect(transport)
        kotlinx.coroutines.awaitCancellation()
    }

private fun addPingTool(server: Server) {
    server.addTool(
        name = "db.ping",
        description = "Health check (returns 'ok')",
        inputSchema = Tool.Input(),
        outputSchema = null,
    ) {
        CallToolResult(
            content = listOf(TextContent("ok")),
            isError = false,
        )
    }
}

private fun addListSchemasTool(
    server: Server,
    ds: DataSource,
) {
    server.addTool(
        name = "db.listSchemas",
        description = "List database schemas",
        inputSchema = Tool.Input(),
        outputSchema = null,
    ) {
        try {
            val names =
                buildList {
                    ds.connection.use { c ->
                        c.metaData.schemas.use { rs ->
                            while (rs.next()) {
                                val schem = rs.getString("TABLE_SCHEM")
                                if (schem != null) add(schem)
                            }
                        }
                    }
                }
            CallToolResult(
                content = names.map { TextContent(it) },
                isError = false,
            )
        } catch (ex: Exception) {
            // log to STDERR; never STDOUT (reserved for MCP frames)
            System.err.println("db.listSchemas error: ${ex.message}")
            CallToolResult(
                content = listOf(TextContent("db.listSchemas failed: ${ex.message}")),
                isError = true,
            )
        }
    }
}

private fun inferEngine(url: String): String? =
    when {
        url.startsWith("jdbc:postgresql:", true) -> "postgres"
        url.startsWith("jdbc:mysql:", true) -> "mysql"
        url.startsWith("jdbc:sqlserver:", true) -> "sqlserver"
        url.startsWith("jdbc:sqlite:", true) -> "sqlite"
        else -> null
    }

private fun buildDataSource(
    url: String,
    user: String,
    pass: String,
    pool: Int,
): DataSource {
    val cfg =
        HikariConfig().apply {
            jdbcUrl = url
            username = user
            password = pass
            maximumPoolSize = pool
            minimumIdle = 1
            isAutoCommit = true
        }
    return HikariDataSource(cfg)
}

private fun mapLogLevel(lvl: String?): String =
    when (lvl) {
        "trace", "debug", "info", "warn", "error" -> lvl!!
        else -> "info"
    }

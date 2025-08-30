/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.db.DbHelp
import io.askimo.core.db.DbRuntime
import io.askimo.core.db.redactJdbcCredentials
import io.askimo.core.session.Session
import io.askimo.core.util.summary
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jline.reader.ParsedLine

class DbUseCommandHandler(
    private val session: Session,
) : CommandHandler {
    override val keyword = ":db"
    override val description = "Manage database connections (:db use <id>, :db off, :db list, :db add, :db show <id>)"

    override fun handle(line: ParsedLine) {
        val words = line.words()
        val args = words.drop(1)

        // plain ":db" → short overview
        if (args.isEmpty()) {
            println(DbHelp.SHORT)
            return
        }

        // ":db help" / ":db --help" → all commands (long)
        if (args.size == 1 && (args[0] == "help" || args[0] == "--help")) {
            println(DbHelp.ALL)
            return
        }

        // ":db help <sub>"
        if (args.size >= 2 && args[0] == "help") {
            val h = DbHelp.longFor(args[1])
            if (h != null) println(h) else println("Unknown subcommand '${args[1]}'")
            return
        }

        val sub = args[0]
        val tail = args.drop(1)

        // ":db <sub> help" / ":db <sub> --help" → detailed help for that subcommand
        if (tail.firstOrNull() in setOf("help", "--help")) {
            val h = DbHelp.longFor(sub)
            if (h != null) println(h) else println("Unknown subcommand '$sub'")
            return
        }

        // …otherwise, dispatch to real subcommand
        when (sub) {
            "add" -> add(tail)
            "use" -> use(tail)
            "off" -> off()
            "list" -> list(tail)
            "show" -> show(tail)
            "test" -> test(tail)
            "remove" -> remove(tail)
            "rotate-secret" -> rotate(tail)
            else -> {
                println("Unknown :db subcommand '$sub'\n")
                println(DbHelp.SHORT)
            }
        }
    }

    private fun use(args: List<String>) {
        if (args.size != 1) {
            println(DbHelp.longFor("use"))
            return
        }
        val id = args[0]
        val conn = session.db.store.get(id)
        if (conn == null) {
            println("❌ No saved connection with id '$id'. Try :db list or :db add")
            return
        }

        // Tear down existing
        session.db.active?.closeSilently()
        session.db.active = null

        // Launch new (spawn askimo-mcp-db and connect over stdio)
        try {
            // suspend call wrapped for the REPL thread
            val runtime =
                runBlocking {
                    withTimeout(10_000) { session.db.launcher.launch(conn) }
                }
            session.db.active = runtime
            println("✅ Attached DB: $id (read-only=${conn.readOnly}, maxRows=${conn.maxRows}, timeoutSec=${conn.timeoutSec})")
        } catch (_: TimeoutCancellationException) {
            println("❌ Timed out connecting to '$id' (10s)")
        } catch (e: Exception) {
            println("❌ Failed to attach '$id': ${e.message ?: "unknown error"}")
        }
    }

    private fun off() {
        val active = session.db.active
        if (active == null) {
            println("ℹ️  No DB attached.")
            return
        }
        active.closeSilently()
        session.db.active = null
        println("✅ Detached DB")
    }

    private fun list(args: List<String>) {
        if (args.isNotEmpty()) {
            if (args.size == 1 && (args[0] == "help" || args[0] == "--help")) {
                println(DbHelp.longFor("list"))
                return
            }
            println(DbHelp.longFor("list"))
            return
        }
        val all = session.db.store.list()
        if (all.isEmpty()) {
            println("No saved connections. Use :db add to create one.")
            return
        }
        val current = session.db.active?.id
        all.forEach { c ->
            val mark = if (c.id == current) "*" else " "
            println("$mark ${c.id}  ${c.engine}  ${c.urlHostAndDb()}  ro=${c.readOnly} rows=${c.maxRows} t=${c.timeoutSec}s")
        }
    }

    private fun show(args: List<String>) {
        if (args.size != 1) {
            println(DbHelp.longFor("show"))
            return
        }
        val c =
            session.db.store.get(args[0]) ?: run {
                println("❌ Unknown id '${args[0]}'")
                return
            }

        println("id        : ${c.id}")
        println("engine    : ${c.engine}")
        println("url       : ${c.url.redactJdbcCredentials()}") // mask password if present
        println("user      : ${c.user}")
        println("secret    : ${c.secret.summary()}") // describe where the secret lives
        println("readOnly  : ${c.readOnly}")
        println("maxRows   : ${c.maxRows}")
        println("timeoutSec: ${c.timeoutSec}")
    }

    private fun test(args: List<String>) {
        if (args.size != 1) {
            println("Usage: :db test <id>")
            return
        }
        val c =
            session.db.store.get(args[0]) ?: run {
                println("❌ Unknown id '${args[0]}'")
                return
            }
        runBlocking {
            try {
                val temp = session.db.launcher.launch(c)
                val ok = temp.ping()
                val schemas = temp.listSchemas()
                temp.closeSilently()
                println("✅ ping=$ok, schemas=${schemas.take(3)}${if (schemas.size > 3) " …" else ""}")
            } catch (e: Exception) {
                println("❌ Test failed: ${e.message}")
            }
        }
    }

    // Stubs — you can wire your existing config manager here:
    private fun add(args: List<String>) {
        if (args.size != 1) {
            println(DbHelp.longFor("add"))
            return
        }
        println("Use guided wizard here (or flags).")
    }

    private fun remove(args: List<String>) {
        if (args.size != 1) {
            println(DbHelp.longFor("remove"))
            return
        }
        println("Remove connection…")
    }

    private fun rotate(args: List<String>) {
        if (args.size != 1) {
            println(DbHelp.longFor("rotate"))
            return
        }
        println("Rotate secret…")
    }
}

private fun DbRuntime?.closeSilently(timeoutMs: Long = 2_000) {
    val rt = this ?: return
    try {
        runBlocking { withTimeout(timeoutMs) { rt.close() } }
    } catch (_: Throwable) {
    }
}

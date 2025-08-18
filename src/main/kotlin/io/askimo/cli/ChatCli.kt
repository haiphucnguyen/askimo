package io.askimo.cli

import io.askimo.cli.Logger.log
import io.askimo.cli.autocompleter.SetParamCompleter
import io.askimo.cli.commands.ClearMemoryCommandHandler
import io.askimo.cli.commands.CommandHandler
import io.askimo.cli.commands.ConfigCommand
import io.askimo.cli.commands.CopyCommandHandler
import io.askimo.cli.commands.HelpCommandHandler
import io.askimo.cli.commands.ListProvidersCommandHandler
import io.askimo.cli.commands.ModelsCommandHandler
import io.askimo.cli.commands.ParamsCommandHandler
import io.askimo.cli.commands.SetParamCommandHandler
import io.askimo.cli.commands.SetProviderCommandHandler
import io.askimo.core.VersionInfo
import io.askimo.core.providers.NoopChatService
import io.askimo.core.providers.NoopProviderSettings
import io.askimo.core.providers.ProviderRegistry
import io.askimo.core.providers.chat
import io.askimo.core.session.Session
import io.askimo.core.session.SessionConfigManager
import io.askimo.web.WebServer
import org.jline.reader.LineReaderBuilder
import org.jline.reader.impl.DefaultParser
import org.jline.reader.impl.completer.AggregateCompleter
import org.jline.terminal.TerminalBuilder
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

fun main(args: Array<String>) {
    if (args.any { it == "--version" || it == "-v" || it == "-V" }) {
        printFullVersionInfo()
        return
    }
    if ("--web" in args) {
        val host = System.getenv("ASKIMO_WEB_HOST") ?: "127.0.0.1"
        val port = System.getenv("ASKIMO_WEB_PORT")?.toIntOrNull() ?: 8080

        val server = WebServer(host = host, port = port)
        Runtime.getRuntime().addShutdownHook(Thread { server.stop() })
        server.start(wait = true) // block until Ctrl+C
    } else {
        val session = createSession()
        try {
            if (args.isEmpty()) {
                val terminal =
                    TerminalBuilder
                        .builder()
                        .jna(true)
                        .jni(true)
                        .system(true)
                        .build()

                val commandHandlers: List<CommandHandler> =
                    listOf(
                        HelpCommandHandler(),
                        ConfigCommand(session),
                        ParamsCommandHandler(session),
                        SetParamCommandHandler(session),
                        ListProvidersCommandHandler(),
                        SetProviderCommandHandler(session),
                        ModelsCommandHandler(session),
                        CopyCommandHandler(session),
                        ClearMemoryCommandHandler(session),
                    )

                // Setup parser and completer
                val parser = DefaultParser()
                val completer =
                    AggregateCompleter(
                        SetParamCompleter(),
                    )

                val reader =
                    LineReaderBuilder
                        .builder()
                        .terminal(terminal)
                        .parser(parser)
                        .completer(completer)
                        .build()

                terminal.writer().println("askimo> Ask anything. Type :help for commands.")
                terminal.flush()

                (commandHandlers.find { it.keyword == ":help" } as? HelpCommandHandler)?.setCommands(commandHandlers)

                while (true) {
                    val input = reader.readLine("askimo> ") ?: continue
                    val parsedLine = parser.parse(input, 0)

                    // Exit handling
                    val trimmed = input.trim()
                    if (trimmed.equals("exit", true) ||
                        trimmed.equals(
                            "quit",
                            true,
                        ) ||
                        trimmed == ":exit" ||
                        trimmed == ":quit"
                    ) {
                        terminal.writer().println("Thank you for using askimo. Goodbye!")
                        terminal.flush()
                        break
                    }

                    val keyword = parsedLine.words().firstOrNull()

                    if (keyword != null && keyword.startsWith(":")) {
                        val handler = commandHandlers.find { it.keyword == keyword }
                        if (handler != null) {
                            handler.handle(parsedLine)
                        } else {
                            terminal.writer().println("❌ Unknown command: $keyword")
                            terminal.writer().println("💡 Type `:help` to see a list of available commands.")
                        }
                    } else {
                        val prompt = parsedLine.line()

                        val indicator = LoadingIndicator(reader.terminal, "Thinking…")
                        indicator.start()

                        val firstTokenSeen = AtomicBoolean(false)

                        val output =
                            session.getChatService().chat(prompt) { token ->
                                if (firstTokenSeen.compareAndSet(false, true)) {
                                    indicator.stopWithElapsed()
                                    reader.terminal.flush()
                                }
                                val w = reader.terminal.writer()
                                w.print(token)
                                w.flush()
                            }
                        if (!firstTokenSeen.get()) {
                            indicator.stopWithElapsed()
                            reader.terminal.writer().println()
                            reader.terminal.flush()
                        }
                        session.lastResponse = output
                        reader.terminal.writer().println()
                        reader.terminal.writer().flush()
                    }

                    terminal.flush()
                }
            } else {
                val userPrompt = args.joinToString(" ").trim()
                val stdinText =
                    readStdinIfAny(
                        maxBytes = 1_000_000, // ~1MB cap to avoid OOM
                        tailLines = 1500, // keep only last N lines if huge
                    )
                val prompt = buildPrompt(userPrompt, stdinText)
                val out = System.out.writer()
                val output =
                    session.getChatService().chat(prompt) { token ->
                        out.write(token)
                        out.flush()
                    }

                out.write("\n")
                out.flush()
            }
        } catch (e: IOException) {
            System.err.println("❌ Error: ${e.message}")
        }
    }
}

private fun createSession(): Session {
    val sessionParams = SessionConfigManager.load()
    log { "Current provider: $sessionParams" }

    val session = Session(sessionParams)

    val provider = session.params.currentProvider
    val modelName = session.params.getModel(provider)

    val settings =
        session.params.providerSettings[provider]
            ?: ProviderRegistry.getFactory(provider)?.defaultSettings()
            ?: NoopProviderSettings

    val memory = session.getOrCreateMemory(provider, modelName, settings)

    val factory = ProviderRegistry.getFactory(provider)
    val chatService =
        factory
            ?.create(
                modelName,
                settings,
                memory,
            )
            ?: NoopChatService

    session.setChatService(chatService)
    return session
}

private fun readStdinIfAny(
    maxBytes: Int,
    tailLines: Int,
): String {
    // If a console is attached, we're likely not in a pipeline.
    // In most Unix/Windows environments, piping detaches the console -> returns null.
    if (System.console() != null) return ""

    val inStream = System.`in`
    val buf = ByteArray(8192)
    val baos = java.io.ByteArrayOutputStream()
    var total = 0

    // Read until EOF or until we hit the cap
    while (true) {
        val n = inStream.read(buf)
        if (n == -1) break
        val allowed = kotlin.math.min(n, maxBytes - total)
        if (allowed > 0) {
            baos.write(buf, 0, allowed)
            total += allowed
        }
        if (total >= maxBytes) {
            // Drain the remainder without storing (optional)
            while (inStream.read(buf) != -1) { /* discard */ }
            break
        }
    }

    if (total == 0) return "" // nothing came through

    var text = baos.toString(java.nio.charset.StandardCharsets.UTF_8)

    // Keep only last N lines if huge (log-friendly)
    if (tailLines > 0) {
        val lines = text.split('\n')
        if (lines.size > tailLines) {
            val tail = lines.takeLast(tailLines).joinToString("\n")
            text = tail + "\n\n(…truncated to last $tailLines lines…)"
        }
    }

    return text
}

private fun buildPrompt(
    userPrompt: String,
    stdinText: String,
): String = if (stdinText.isBlank()) {
    userPrompt.ifBlank { "Analyze the following input (no stdin provided)." }
} else {
    // Attach the piped input as context
    buildString {
        appendLine(userPrompt.ifBlank { "Analyze the following input:" })
        appendLine()
        appendLine("--- Begin input ---")
        append(stdinText)
        appendLine()
        appendLine("--- End input ---")
        appendLine()
        appendLine("Return concise, actionable findings.")
    }
}

private fun printFullVersionInfo() {
    val a = VersionInfo
    println(
        """
        ${a.name} ${a.version}
        Author: ${a.author}
        Built: ${a.buildDate}
        License: ${a.license}
        Homepage: ${a.homepage}
        Build JDK: ${a.buildJdk}
        Runtime: ${a.runtimeVm} (${a.runtimeVersion})
        """.trimIndent(),
    )
}

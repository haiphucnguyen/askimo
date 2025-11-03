/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli

import io.askimo.cli.autocompleter.CliCommandCompleter
import io.askimo.cli.commands.AgentCommandHandler
import io.askimo.cli.commands.ApiKeySecurityCommandHandler
import io.askimo.cli.commands.ClearMemoryCommandHandler
import io.askimo.cli.commands.CommandHandler
import io.askimo.cli.commands.ConfigCommandHandler
import io.askimo.cli.commands.CopyCommandHandler
import io.askimo.cli.commands.CreateProjectCommandHandler
import io.askimo.cli.commands.CreateRecipeCommandHandler
import io.askimo.cli.commands.DeleteProjectCommandHandler
import io.askimo.cli.commands.DeleteRecipeCommandHandler
import io.askimo.cli.commands.FileWatcherStatusCommandHandler
import io.askimo.cli.commands.HelpCommandHandler
import io.askimo.cli.commands.ListProjectsCommandHandler
import io.askimo.cli.commands.ListProvidersCommandHandler
import io.askimo.cli.commands.ListRecipesCommandHandler
import io.askimo.cli.commands.ListSessionsCommandHandler
import io.askimo.cli.commands.ModelsCommandHandler
import io.askimo.cli.commands.NewSessionCommandHandler
import io.askimo.cli.commands.ParamsCommandHandler
import io.askimo.cli.commands.ResumeSessionCommandHandler
import io.askimo.cli.commands.SetParamCommandHandler
import io.askimo.cli.commands.SetProviderCommandHandler
import io.askimo.cli.commands.StopWatcherCommandHandler
import io.askimo.cli.commands.UseProjectCommandHandler
import io.askimo.cli.util.NonInteractiveCommandParser
import io.askimo.core.VersionInfo
import io.askimo.core.providers.sendStreamingMessageWithCallback
import io.askimo.core.recipes.RecipeExecutor
import io.askimo.core.recipes.RecipeRegistry
import io.askimo.core.recipes.ToolRegistry
import io.askimo.core.session.Session
import io.askimo.core.session.SessionFactory
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.info
import org.jline.keymap.KeyMap
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.Reference
import org.jline.reader.Widget
import org.jline.reader.impl.DefaultParser
import org.jline.reader.impl.completer.AggregateCompleter
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.utils.InfoCmp
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

fun main(args: Array<String>) {
    if (args.any { it == "--version" || it == "-v" || it == "-V" }) {
        printFullVersionInfo()
        return
    }

    val session = SessionFactory.createSession()

    // Add shutdown hook to gracefully close database connections
    Runtime.getRuntime().addShutdownHook(
        Thread {
            session.chatSessionRepository.close()
        },
    )

    // Create all command handlers once
    val commandHandlers: List<CommandHandler> =
        listOf(
            HelpCommandHandler(),
            ConfigCommandHandler(session),
            ParamsCommandHandler(session),
            SetParamCommandHandler(session),
            ListProvidersCommandHandler(),
            SetProviderCommandHandler(session),
            ModelsCommandHandler(session),
            CopyCommandHandler(session),
            ClearMemoryCommandHandler(session),
            CreateProjectCommandHandler(session),
            ListProjectsCommandHandler(),
            UseProjectCommandHandler(session),
            DeleteProjectCommandHandler(),
            FileWatcherStatusCommandHandler(),
            StopWatcherCommandHandler(),
            CreateRecipeCommandHandler(),
            DeleteRecipeCommandHandler(),
            ListRecipesCommandHandler(),
            AgentCommandHandler(session),
            ApiKeySecurityCommandHandler(session),
            NewSessionCommandHandler(session),
            ListSessionsCommandHandler(),
            ResumeSessionCommandHandler(session),
        )

    // Helper function to convert interactive keyword to non-interactive flag
    fun keywordToFlag(keyword: String): String = "--" + keyword.removePrefix(":")

    // Set up help command with all available commands for both interactive and non-interactive modes
    (commandHandlers.find { it.keyword == ":help" } as? HelpCommandHandler)?.setCommands(commandHandlers)

    // Check for non-interactive commands
    for (handler in commandHandlers) {
        val flag = keywordToFlag(handler.keyword)

        if (args.any { it == flag }) {
            // Check if command takes arguments
            val flagArgs = NonInteractiveCommandParser.extractFlagArguments(args, flag)
            if (flagArgs != null && flagArgs.isNotEmpty()) {
                // Command with arguments
                val parsedLine = NonInteractiveCommandParser.createParameterizedParsedLine(handler.keyword, *flagArgs.toTypedArray())
                handler.handle(parsedLine)
            } else {
                // Command without arguments
                handler.handle(NonInteractiveCommandParser.createEmptyParsedLine())
            }
            return
        }
    }

    try {
        val cliCommandName = args.getFlagValue("-r", "--recipe")
        if (cliCommandName != null) {
            val overrides = args.extractOverrides("--set")
            // Extract external args: all args after the recipe name that are not flags or overrides
            val recipeFlagIndex = args.indexOfFirst { it == "-r" || it == "--recipe" }
            val externalArgs =
                if (recipeFlagIndex != -1 && recipeFlagIndex + 2 <= args.size) {
                    args
                        .drop(recipeFlagIndex + 2)
                        .filter { !it.startsWith("--") && !it.startsWith("-") && !it.contains("=") }
                } else {
                    emptyList()
                }
            runYamlCommand(session, cliCommandName, overrides, externalArgs)
            return
        }

        val promptText = args.getFlagValue("-p", "--prompt")
        if (promptText != null) {
            val stdinText = readStdinIfAny()
            val prompt = buildPrompt(promptText, stdinText)
            sendChatMessage(session, prompt, showIndicator = true)
            return
        }

        if (args.isEmpty()) {
            val terminal =
                TerminalBuilder
                    .builder()
                    .jna(true)
                    .system(true)
                    .build()

            // Setup parser and completer
            val parser = DefaultParser()

            val completer =
                AggregateCompleter(
                    CliCommandCompleter(),
                )

            val reader =
                LineReaderBuilder
                    .builder()
                    .terminal(terminal)
                    .parser(parser)
                    .variable(LineReader.COMPLETION_STYLE_LIST_SELECTION, "fg:blue")
                    .variable(LineReader.LIST_MAX, 5)
                    .variable(LineReader.COMPLETION_STYLE_STARTING, "")
                    .completer(completer)
                    .build()

            // Is this a "real" terminal with raw mode & cursor addressing?
            val supportsRaw = terminal.getBooleanCapability(InfoCmp.Capability.cursor_address)

            // --- Key bindings ---
            // Get the main keymap
            @Suppress("UNCHECKED_CAST")
            val mainMap = reader.keyMaps[LineReader.MAIN] as KeyMap<Any>

            if (supportsRaw) {
                val mainMap = reader.keyMaps[LineReader.MAIN] as KeyMap<Any>
                // --- Secondary prompt style ---
                reader.setVariable(LineReader.SECONDARY_PROMPT_PATTERN, "%B..>%b ")

                // --- Widget for newline ---
                reader.widgets["insert-newline"] =
                    Widget {
                        reader.buffer.write("\n")
                        reader.callWidget(LineReader.REDRAW_LINE)
                        reader.callWidget(LineReader.REDISPLAY)
                        true
                    }

                mainMap.bind(Reference(LineReader.ACCEPT_LINE), "\r") // CR (Ctrl-M / many terms)
                mainMap.bind(Reference(LineReader.ACCEPT_LINE), "\n") // LF (some terms)
                mainMap.bind(Reference("insert-newline"), KeyMap.ctrl('J')) // Ctrl+J
                terminal.writer().println("💡 Tip: Ctrl+J for newline, Enter to send.")
            } else {
                terminal.writer().println(
                    "💡 Limited console detected. Enter submits. " +
                        "Use Alt+Enter for a newline, or enable 'Emulate terminal in output console' in your Run config.",
                )
            }

            mainMap.bind(Reference("reverse-search-history"), KeyMap.ctrl('R'))
            mainMap.bind(Reference("forward-search-history"), KeyMap.ctrl('S'))

            terminal.writer().println("askimo> Ask anything. Type :help for commands.")
            terminal.writer().println("💡 Tip 1: Press Ctrl+J for a new line, Enter to send.")
            terminal.writer().println("💡 Tip 2: Use ↑ / ↓ to browse, Ctrl+R to search history.")
            terminal.flush()

            val interactiveCommandHandlers = commandHandlers

            (interactiveCommandHandlers.find { it.keyword == ":help" } as? HelpCommandHandler)?.setCommands(interactiveCommandHandlers)

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
                    val handler = interactiveCommandHandlers.find { it.keyword == keyword }
                    if (handler != null) {
                        handler.handle(parsedLine)
                    } else {
                        terminal.writer().println("❌ Unknown command: $keyword")
                        terminal.writer().println("💡 Type `:help` to see a list of available commands.")
                    }
                } else {
                    val prompt = parsedLine.line()
                    val output = sendChatMessage(session, prompt, reader.terminal, showIndicator = true)
                    session.lastResponse = output
                    reader.terminal.writer().println()
                    reader.terminal.writer().flush()
                }

                terminal.flush()
            }
        } else {
            val userPrompt = args.joinToString(" ").trim()
            val stdinText = readStdinIfAny()
            val prompt = buildPrompt(userPrompt, stdinText)
            sendChatMessage(session, prompt, showIndicator = true)
        }
    } catch (e: IOException) {
        info("❌ Error: ${e.message}")
        debug(e)
    }
}

private fun sendChatMessage(
    session: Session,
    prompt: String,
    terminal: Terminal? = null,
    showIndicator: Boolean = true,
): String {
    val actualTerminal = terminal ?: TerminalBuilder.builder().system(true).build()

    val indicator = if (showIndicator) {
        LoadingIndicator(actualTerminal, "Thinking…").apply { start() }
    } else {
        null
    }

    val firstTokenSeen = AtomicBoolean(false)
    val mdRenderer = MarkdownJLineRenderer()
    val mdSink = MarkdownStreamingSink(actualTerminal, mdRenderer)

    // Prepare context and get the prompt to use
    val promptWithContext = session.prepareContextAndGetPrompt(prompt)

    // Stream the response directly for real-time display
    val output = session.getChatService().sendStreamingMessageWithCallback(promptWithContext) { token ->
        if (firstTokenSeen.compareAndSet(false, true)) {
            indicator?.stopWithElapsed()
            actualTerminal.flush()
        }
        mdSink.append(token)
    }

    if (!firstTokenSeen.get()) {
        indicator?.stopWithElapsed()
        actualTerminal.writer().println()
        actualTerminal.flush()
    }
    mdSink.finish()

    // Save the AI response to session
    session.saveAiResponse(output)

    return output
}

private fun readStdinIfAny(
    maxBytes: Int = 1_000_000_000, // ~1GB cap to avoid OOM
    tailLines: Int = 1500, // keep only last N lines if huge
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
            text = "$tail\n\n(…truncated to last $tailLines lines…)"
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
    info(
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

private fun Array<String>.getFlagValue(vararg flags: String): String? {
    for (i in indices) {
        if (this[i] in flags && i + 1 < size) return this[i + 1]
    }
    return null
}

private fun Array<String>.extractOverrides(flag: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    var i = 0
    while (i < size) {
        if (this[i] == flag && i + 1 < size) {
            val kv = this[i + 1]
            val idx = kv.indexOf('=')
            if (idx > 0) {
                map[kv.take(idx)] = kv.substring(idx + 1)
            }
            i += 2
            continue
        }
        i++
    }
    return map
}

private fun runYamlCommand(
    session: Session,
    name: String,
    overrides: Map<String, String>,
    externalArgs: List<String> = emptyList(),
) {
    info("🚀 Running recipe '$name'…")

    val registry = RecipeRegistry()
    // Load once to inspect allowedTools (empty ⇒ all tools)
    val def = registry.load(name)
    val toolRegistry =
        if (def.allowedTools.isEmpty()) {
            ToolRegistry.defaults()
        } else {
            info("🔒 Restricting tools to: ${def.allowedTools.sorted().joinToString(", ")}")
            ToolRegistry.defaults(allow = def.allowedTools.toSet())
        }

    val executor =
        RecipeExecutor(
            session = session,
            registry = registry,
            tools = toolRegistry,
        )

    executor.run(
        name = name,
        opts = RecipeExecutor.RunOpts(overrides = overrides, externalArgs = externalArgs),
    )
}

/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli

import io.askimo.cli.autocompleter.CliCommandCompleter
import io.askimo.cli.commands.AgentCommandHandler
import io.askimo.cli.commands.ClearMemoryCommandHandler
import io.askimo.cli.commands.CommandHandler
import io.askimo.cli.commands.ConfigCommandHandler
import io.askimo.cli.commands.CopyCommandHandler
import io.askimo.cli.commands.CreateProjectCommandHandler
import io.askimo.cli.commands.CreateRecipeCommandHandler
import io.askimo.cli.commands.DeleteProjectCommandHandler
import io.askimo.cli.commands.DeleteRecipeCommandHandler
import io.askimo.cli.commands.HelpCommandHandler
import io.askimo.cli.commands.ListProjectsCommandHandler
import io.askimo.cli.commands.ListProvidersCommandHandler
import io.askimo.cli.commands.ListRecipesCommandHandler
import io.askimo.cli.commands.ListSessionsCommandHandler
import io.askimo.cli.commands.ListToolsCommandHandler
import io.askimo.cli.commands.ModelsCommandHandler
import io.askimo.cli.commands.NewSessionCommandHandler
import io.askimo.cli.commands.ParamsCommandHandler
import io.askimo.cli.commands.ResumeSessionCommandHandler
import io.askimo.cli.commands.SetParamCommandHandler
import io.askimo.cli.commands.SetProviderCommandHandler
import io.askimo.cli.commands.UseProjectCommandHandler
import io.askimo.cli.commands.VersionDisplayCommandHandler
import io.askimo.cli.recipes.DefaultRecipeInitializer
import io.askimo.cli.recipes.RecipeExecutor
import io.askimo.cli.recipes.RecipeExecutor.RunOpts
import io.askimo.cli.recipes.RecipeRegistry
import io.askimo.cli.recipes.ToolRegistry
import io.askimo.cli.util.CompositeCommandExecutor
import io.askimo.cli.util.NonInteractiveCommandParser
import io.askimo.core.VersionInfo
import io.askimo.core.providers.sendStreamingMessageWithCallback
import io.askimo.core.session.Session
import io.askimo.core.session.SessionFactory
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.info
import io.askimo.core.util.Logger.warn
import io.askimo.core.util.RetryPresets.RECIPE_EXECUTOR_TRANSIENT_ERRORS
import io.askimo.core.util.RetryUtils
import org.jline.keymap.KeyMap
import org.jline.reader.EOFError
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.ParsedLine
import org.jline.reader.Parser.ParseContext
import org.jline.reader.Reference
import org.jline.reader.impl.DefaultParser
import org.jline.reader.impl.completer.AggregateCompleter
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.utils.InfoCmp
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

fun main(args: Array<String>) {
    // Initialize default templates on startup
    DefaultRecipeInitializer.initializeDefaultTemplates()

    val session = SessionFactory.createSession()

    // Add shutdown hook to gracefully close database connections
    Runtime.getRuntime().addShutdownHook(
        Thread {
            session.chatSessionRepository.close()
        },
    )

    // Shared command handlers available in both modes
    val sharedCommandHandlers: List<CommandHandler> =
        listOf(
            HelpCommandHandler(),
            ConfigCommandHandler(session),
            ListProvidersCommandHandler(),
            SetProviderCommandHandler(session),
            ModelsCommandHandler(session),
            ParamsCommandHandler(session),
            SetParamCommandHandler(session),
            ListToolsCommandHandler(),
            VersionDisplayCommandHandler(),
        )

    // Non-interactive mode command handlers (shared + non-interactive only)
    val nonInteractiveCommandHandlers: List<CommandHandler> = sharedCommandHandlers + listOf(
        CreateRecipeCommandHandler(),
        ListRecipesCommandHandler(),
        DeleteRecipeCommandHandler(),
    )

    // Helper function to convert interactive keyword to non-interactive flag
    fun keywordToFlag(keyword: String): String = "--" + keyword.removePrefix(":")

    // Set up help command with non-interactive commands
    (nonInteractiveCommandHandlers.find { it.keyword == ":help" } as? HelpCommandHandler)?.setCommands(nonInteractiveCommandHandlers)

    // Check for composite command (multiple non-interactive commands)
    if (CompositeCommandExecutor.hasMultipleCommands(args, nonInteractiveCommandHandlers)) {
        val commandHandlerMap = nonInteractiveCommandHandlers.associateBy { keywordToFlag(it.keyword) }
        CompositeCommandExecutor.executeCommands(args, commandHandlerMap)
        return
    }

    // Check for single non-interactive commands
    for (handler in nonInteractiveCommandHandlers) {
        val flag = keywordToFlag(handler.keyword)

        if (args.any { it == flag }) {
            // Special handling for help command to set non-interactive mode
            if (handler is HelpCommandHandler) {
                handler.setNonInteractiveMode(true)
            }

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
            try {
                runYamlCommand(session, cliCommandName, overrides, externalArgs)
            } catch (e: io.askimo.cli.recipes.RecipeNotFoundException) {
                warn(e.message ?: "Recipe not found")
            }
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

            // Clear the terminal screen when entering interactive mode
            terminal.puts(InfoCmp.Capability.clear_screen)
            terminal.flush()

            // Setup parser with multi-line support
            val parser = object : DefaultParser() {
                override fun isEscapeChar(ch: Char): Boolean = ch == '\\'

                override fun parse(line: String, cursor: Int, context: ParseContext?): ParsedLine {
                    // Check if line ends with backslash (continuation)
                    if (context == ParseContext.ACCEPT_LINE && line.trimEnd().endsWith("\\")) {
                        throw EOFError(-1, -1, "Line continuation")
                    }
                    return super.parse(line, cursor, context)
                }
            }

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
                    .variable(LineReader.SECONDARY_PROMPT_PATTERN, "%B..>%b ")
                    .completer(completer)
                    .build()

            // Get the main keymap for history search bindings
            @Suppress("UNCHECKED_CAST")
            val mainMap = reader.keyMaps[LineReader.MAIN] as KeyMap<Any>

            // Setup history search bindings
            mainMap.bind(Reference("reverse-search-history"), KeyMap.ctrl('R'))
            mainMap.bind(Reference("forward-search-history"), KeyMap.ctrl('S'))

            // Display banner and version
            displayBanner()

            terminal.writer().println("askimo> Ask anything. Type :help for commands.")
            terminal.writer().println("💡 Tip 1: End line with \\ for multi-line, Enter to send.")
            terminal.writer().println("💡 Tip 2: Use ↑ / ↓ to browse, Ctrl+R to search history.")
            terminal.flush()

            // Create command handlers for interactive mode (shared + interactive-only commands)
            val interactiveCommandHandlers: List<CommandHandler> = sharedCommandHandlers + listOf(
                CopyCommandHandler(session),
                ClearMemoryCommandHandler(session),
                ListProjectsCommandHandler(),
                CreateProjectCommandHandler(session),
                UseProjectCommandHandler(session),
                DeleteProjectCommandHandler(),
//                FileWatcherStatusCommandHandler(),
//                StopWatcherCommandHandler(),
                ListSessionsCommandHandler(),
                NewSessionCommandHandler(session),
                ResumeSessionCommandHandler(session),
                AgentCommandHandler(session),
            )

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

private fun displayBanner() {
    try {
        val bannerText = object {}::class.java.getResourceAsStream("/banner.txt")?.use { stream ->
            stream.bufferedReader().readText()
        } ?: run {
            "Welcome to Askimo CLI"
        }

        println(bannerText)
        println()

        val version = VersionInfo
        println("Author: ${version.author}")
        println("Version: ${version.version}")
        println()
    } catch (_: Exception) {
        println("Welcome to Askimo CLI")
        val version = VersionInfo
        println("Author: ${version.author}")
        println("Version: ${version.version}")
        println()
    }
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
    info("🚀 Running recipe '$name' with arguments $externalArgs…")

    val registry = RecipeRegistry()
    // Load once to inspect allowedTools (empty ⇒ all tools)
    val def = registry.load(name)
    val toolRegistry =
        if (def.allowedTools.isEmpty()) {
            ToolRegistry.defaults()
        } else {
            debug("🔒 Restricting tools to: ${def.allowedTools.sorted().joinToString(", ")}")
            ToolRegistry.defaults(allow = def.allowedTools.toSet())
        }

    val executor =
        RecipeExecutor(
            session = session,
            registry = registry,
            tools = toolRegistry,
        )

    RetryUtils.retry(RECIPE_EXECUTOR_TRANSIENT_ERRORS) {
        executor.run(
            name = name,
            opts = RunOpts(overrides = overrides, externalArgs = externalArgs),
        )
    }
}

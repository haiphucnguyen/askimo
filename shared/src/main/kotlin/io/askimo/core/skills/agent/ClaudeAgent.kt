/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.skills.agent

import io.askimo.core.logging.logger
import io.askimo.core.util.ProcessBuilderExt
import java.io.File

/**
 * External agent implementation for [Claude Code](https://docs.anthropic.com/en/docs/claude-code).
 *
 * Invocation:
 * ```
 * claude --print -
 * ```
 * - `--print`  Non-interactive mode: print the response to stdout and exit.
 * - `-`        Read the prompt from stdin.
 *
 * The combined prompt written to stdin:
 * ```
 * <system prompt>
 *
 * ---
 *
 * <user input>
 * ```
 */
class ClaudeAgent : ExternalAgent {

    private val log = logger<ClaudeAgent>()

    override val id = "claude"
    override val name = "Claude Code"

    override val commands: List<AgentCommand> = listOf(
        AgentCommand(
            name = "/help",
            description = "Show available Claude Code commands",
            usage = "/help",
        ),
        AgentCommand(
            name = "/review",
            description = "Review code in the working directory",
            usage = "/review",
        ),
        AgentCommand(
            name = "/cost",
            description = "Show token usage and cost for this session",
            usage = "/cost",
        ),
        AgentCommand(
            name = "/doctor",
            description = "Check Claude Code installation and configuration",
            usage = "/doctor",
        ),
        AgentCommand(
            name = "/compact",
            description = "Compact conversation history to save tokens",
            usage = "/compact [instructions]",
        ),
    )

    private fun resolveClaudePath(): String? = runCatching {
        val proc = ProcessBuilderExt("which", "claude")
            .redirectErrorStream(true)
            .start()
        val path = proc.inputStream.bufferedReader().readText().trim()
        if (proc.waitFor() == 0 && path.isNotBlank()) path else null
    }.getOrNull()

    override fun isAvailable(): Boolean = resolveClaudePath() != null

    override fun run(
        systemPrompt: String,
        userInput: String,
        workDir: File?,
        onToken: (String) -> Unit,
        onStatus: (String) -> Unit,
    ): Result<String> = runCatching {
        val combinedPrompt = buildPrompt(systemPrompt, userInput)
        val claudePath = resolveClaudePath() ?: error("claude CLI not found on PATH")

        log.debug("Starting claude --print for skill execution ({} chars, workDir={})", combinedPrompt.length, workDir)

        val process = ProcessBuilderExt(claudePath, "--print", "--verbose", "--output-format", "stream-json")
            .apply {
                if (workDir != null) {
                    workDir.mkdirs()
                    directory(workDir)
                }
                environment()["HOME"] = System.getProperty("user.home")
            }
            .start()

        // Write prompt to stdin then close so claude sees EOF
        process.outputStream.bufferedWriter().use { it.write(combinedPrompt) }

        // Drain stderr in background to prevent blocking
        val stderrOutput = StringBuilder()
        val stderrThread = Thread {
            process.errorStream.bufferedReader().forEachLine { line ->
                log.debug("claude stderr: {}", line)
                stderrOutput.appendLine(line)
            }
        }.also {
            it.isDaemon = true
            it.start()
        }

        // Parse stream-json events and stream content tokens to caller in real-time.
        val output = StringBuilder()
        process.inputStream.bufferedReader().forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val event = ClaudeStreamJsonEventParser.parse(line)
            if (event == null) {
                log.debug("claude unparseable line: {}", line)
                return@forEachLine
            }
            log.debug("claude event: type={} line {}", event.type, line)
            when (event.type) {
                "content_block_delta" -> {
                    @Suppress("UNCHECKED_CAST")
                    val delta = event.fields["delta"] as? Map<String, Any>
                    val text = delta?.get("text") as? String ?: return@forEachLine
                    if (text.isNotBlank()) {
                        output.append(text)
                        onToken(text)
                    }
                }

                "message_delta" -> {
                    @Suppress("UNCHECKED_CAST")
                    val usage = event.fields["usage"] as? Map<String, Any>
                    val inputTokens = usage?.get("input_tokens")
                    val outputTokens = usage?.get("output_tokens")
                    val stopReason = event.fields["stop_reason"] as? String
                    val summary = buildString {
                        if (stopReason != null) append("stop: $stopReason")
                        if (inputTokens != null) append(" | input tokens: $inputTokens")
                        if (outputTokens != null) append(" | output tokens: $outputTokens")
                    }
                    if (summary.isNotBlank()) onStatus(summary)
                }

                "tool_use" -> {
                    val toolName = event.fields["name"] as? String ?: "tool"
                    val input = event.fields["input"]
                    onStatus("tool: $toolName${if (input != null) " $input" else ""}")
                }

                else -> {
                    val rendered = event.fields.entries
                        .joinToString(", ") { "${it.key}=${it.value}" }
                    if (rendered.isNotBlank()) log.debug("claude {}: {}", event.type, rendered)
                }
            }
        }

        val exitCode = process.waitFor()
        stderrThread.join(2_000)
        if (exitCode != 0) {
            val errMsg = stderrOutput.toString().trim()
            log.warn("claude exited with code {} — stderr: {}", exitCode, errMsg)
            error("claude exited with code $exitCode${if (errMsg.isNotBlank()) ": $errMsg" else ""}")
        }

        output.toString().trimEnd()
    }.onFailure { e ->
        log.error("ClaudeAgent run failed: {}", e.message, e)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildPrompt(systemPrompt: String, userInput: String): String = buildString {
        append(systemPrompt.trim())
        if (userInput.isNotBlank()) {
            append("\n\n---\n\n")
            append(userInput.trim())
        }
        append("\n")
    }
}

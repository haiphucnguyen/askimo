/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.skills.agent

import io.askimo.core.context.AppContext
import io.askimo.core.logging.logger
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.openai.OpenAiSettings
import io.askimo.core.security.SecureKeyManager
import io.askimo.core.util.ProcessBuilderExt
import java.io.File

/**
 * External agent implementation for [OpenAI Codex CLI](https://github.com/openai/codex).
 *
 * Install: `npm install -g @openai/codex`
 * Requires an OpenAI API key (`OPENAI_API_KEY`).
 *
 * Invocation:
 * ```
 * codex exec --dangerously-bypass-approvals-and-sandbox --skip-git-repo-check \
 *            --ephemeral -C <workDir> -
 * ```
 * The combined system prompt + user input is written to **stdin** (triggered by the `-` prompt arg).
 * - `exec`                                         Non-interactive subcommand.
 * - `--dangerously-bypass-approvals-and-sandbox`   Auto-approve all tool actions, no sandbox.
 * - `--skip-git-repo-check`                        Allow running outside a git repo.
 * - `--ephemeral`                                  Do not persist session files to disk.
 * - `-C <workDir>`                                 Set the agent working directory.
 * - `-`                                            Read prompt from stdin.
 */
class CodexAgent : ExternalAgent {

    private val log = logger<CodexAgent>()

    override val id = "codex"
    override val name = "Codex (OpenAI)"
    override val installUrl = "https://github.com/openai/codex"
    override val requiresApiKey = true

    override val commands: List<AgentCommand> = listOf(
        AgentCommand(
            name = "/help",
            description = "Show available Codex commands",
            usage = "/help",
        ),
    )

    /**
     * Resolves the OpenAI API key from:
     * 1. AppContext OpenAiSettings (if initialized) — handles keychain/encrypted refs
     * 2. SecureKeyManager direct lookup by provider key "openai"
     */
    private fun resolveApiKey(): String? {
        runCatching {
            val ctx = AppContext.getInstance()
            val settings = ctx.getOrCreateProviderSettings(ModelProvider.OPENAI)
            if (settings is OpenAiSettings) {
                val raw = settings.apiKey
                if (raw.isNotBlank() && raw != "***keychain***" && !raw.startsWith("encrypted:")) {
                    return raw
                }
            }
        }
        return SecureKeyManager.retrieveSecretKey(ModelProvider.OPENAI.providerKey())
    }

    private fun resolveCodexPath(): String? = runCatching {
        val proc = ProcessBuilderExt("which", "codex")
            .redirectErrorStream(true)
            .start()
        val path = proc.inputStream.bufferedReader().readText().trim()
        if (proc.waitFor() == 0 && path.isNotBlank()) path else null
    }.getOrNull()

    override fun isBinaryAvailable(): Boolean {
        val found = resolveCodexPath() != null
        if (!found) log.debug("codex CLI not found on PATH")
        return found
    }

    override fun isConfigured(): Boolean {
        if (!isBinaryAvailable()) return false
        val hasKey = resolveApiKey()?.isNotBlank() == true
        if (!hasKey) log.debug("codex CLI found but no OPENAI_API_KEY configured")
        return hasKey
    }

    /**
     * Stores [key] securely and syncs it to AppContext OpenAiSettings so both the
     * Skills executor and the chat provider share the same key without re-entry.
     */
    override fun saveApiKey(key: String) {
        if (key.isBlank()) return
        SecureKeyManager.storeSecuredKey(ModelProvider.OPENAI.providerKey(), key)
        runCatching {
            val ctx = AppContext.getInstance()
            val settings = ctx.getOrCreateProviderSettings(ModelProvider.OPENAI)
            if (settings is OpenAiSettings) {
                settings.apiKey = key
            }
        }
        log.debug("OpenAI API key saved and synced to provider settings")
    }

    override fun run(
        systemPrompt: String,
        userInput: String,
        workDir: File?,
        onToken: (String) -> Unit,
        onStatus: (String) -> Unit,
    ): Result<String> = runCatching {
        val codexPath = resolveCodexPath() ?: error("codex CLI not found on PATH")
        val effectiveWorkDir = workDir ?: File(System.getProperty("user.home"))

        log.debug("Starting codex exec for skill execution (workDir={})", effectiveWorkDir)

        val cmd = buildList {
            add(codexPath)
            add("exec")
            add("--dangerously-bypass-approvals-and-sandbox")
            add("--skip-git-repo-check")
            add("--ephemeral")
            add("-C")
            add(effectiveWorkDir.absolutePath)
            add("-") // read prompt from stdin
        }

        val process = ProcessBuilderExt(*cmd.toTypedArray())
            .apply {
                effectiveWorkDir.mkdirs()
                directory(effectiveWorkDir)
                val env = environment()
                env["HOME"] = System.getProperty("user.home")
                resolveApiKey()?.takeIf { it.isNotBlank() }?.let { key ->
                    log.debug("Injecting OPENAI_API_KEY from Askimo provider settings")
                    env["OPENAI_API_KEY"] = key
                }
            }
            .start()

        // Write combined system prompt + user input to stdin, then close
        process.outputStream.bufferedWriter().use { writer ->
            if (systemPrompt.isNotBlank()) {
                writer.write(systemPrompt.trim())
                writer.write("\n\n---\n\n")
            }
            if (userInput.isNotBlank()) {
                writer.write(userInput.trim())
            }
        }

        val stderrLines = mutableListOf<String>()
        val stderrThread = Thread {
            process.errorStream.bufferedReader().forEachLine { line ->
                log.debug("codex stderr: {}", line)
                synchronized(stderrLines) { stderrLines.add(line) }
                if (line.isNotBlank()) onStatus(line)
            }
        }.also {
            it.isDaemon = true
            it.start()
        }

        val output = StringBuilder()
        process.inputStream.bufferedReader().forEachLine { line ->
            if (line.isNotBlank()) {
                output.appendLine(line)
                onToken(line + "\n")
            }
        }

        val exitCode = process.waitFor()
        stderrThread.join(2_000)

        if (exitCode != 0) {
            val errMsg = synchronized(stderrLines) { stderrLines.joinToString("\n").trim() }
            log.warn("codex exited with code {} — stderr: {}", exitCode, errMsg)
            error("codex exited with code $exitCode${if (errMsg.isNotBlank()) ": $errMsg" else ""}")
        }

        output.toString().trimEnd()
    }.onFailure { e ->
        log.error("CodexAgent run failed: {}", e.message, e)
    }
}

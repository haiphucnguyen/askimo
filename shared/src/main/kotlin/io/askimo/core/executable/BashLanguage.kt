/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.executable

/**
 * Runnable language implementation for Bash/sh scripts.
 *
 * Bash commands are single-line or short multi-line scripts that the shell
 * can accept directly via stdin, so no temp file is needed — the trimmed
 * code is pasted straight into the terminal.
 */
object BashLanguage : RunnableLanguage(setOf("bash", "sh"), "bash") {
    override fun buildTerminalCommand(code: String): String = code.trimEnd('\n', '\r')
}

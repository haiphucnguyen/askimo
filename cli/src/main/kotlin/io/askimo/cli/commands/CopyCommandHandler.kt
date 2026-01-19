/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.cli.context.CliInteractiveContext
import io.askimo.core.logging.display
import io.askimo.core.logging.logger
import io.askimo.core.util.ProcessBuilderExt
import org.jline.reader.ParsedLine

/**
 * Handles the command to copy the last AI response to the clipboard.
 *
 * This class provides functionality to copy the most recent AI-generated response to the
 * system clipboard, with cross-platform support for different operating systems (macOS,
 * Windows, Linux). It helps users easily extract and reuse AI responses in other applications.
 */
class CopyCommandHandler : CommandHandler {
    private val log = logger<CopyCommandHandler>()
    override val keyword: String = ":copy"

    override val description: String = "Copy the last AI response to the clipboard"

    override fun handle(line: ParsedLine) {
        val response = CliInteractiveContext.lastResponse
        if (response.isNullOrBlank()) {
            log.display("⚠️ No response to copy. Ask something first.")
            return
        }

        if (copyToClipboard(response)) {
            log.display("✅ Copied last response to clipboard.")
        }
    }

    private fun copyToClipboard(text: String): Boolean {
        return try {
            val os = System.getProperty("os.name").lowercase()

            when {
                os.contains("mac") -> {
                    val process = ProcessBuilderExt("pbcopy").start()
                    process.outputStream.use { it.write(text.toByteArray()) }
                }
                os.contains("win") -> {
                    val process = ProcessBuilderExt("cmd", "/c", "clip").start()
                    process.outputStream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                os.contains("nux") || os.contains("nix") -> {
                    val hasXclip = ProcessBuilderExt("which", "xclip").start().waitFor() == 0
                    val hasXsel = ProcessBuilderExt("which", "xsel").start().waitFor() == 0

                    when {
                        hasXclip -> {
                            val process = ProcessBuilderExt("xclip", "-selection", "clipboard").start()
                            process.outputStream.use { it.write(text.toByteArray()) }
                        }
                        hasXsel -> {
                            val process = ProcessBuilderExt("xsel", "--clipboard", "--input").start()
                            process.outputStream.use { it.write(text.toByteArray()) }
                        }
                        else -> {
                            log.display("⚠️ No clipboard utility found (xclip or xsel). Install one to enable copying.")
                            return false
                        }
                    }
                }
                else -> {
                    log.display("⚠️ Clipboard not supported on this OS.")
                    return false
                }
            }

            true
        } catch (e: Exception) {
            log.display("❌ Failed to copy to clipboard: ${e.message}")
            log.error("Failed to copy to clipboard", e)
            false
        }
    }
}

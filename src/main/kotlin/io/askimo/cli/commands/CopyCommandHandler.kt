/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.session.Session
import org.jline.reader.ParsedLine

/**
 * Handles the command to copy the last AI response to the clipboard.
 *
 * This class provides functionality to copy the most recent AI-generated response to the
 * system clipboard, with cross-platform support for different operating systems (macOS,
 * Windows, Linux). It helps users easily extract and reuse AI responses in other applications.
 */
class CopyCommandHandler(
    private val session: Session,
) : CommandHandler {
    override val keyword: String = ":copy"

    override val description: String = "Copy the last AI response to the clipboard"

    override fun handle(line: ParsedLine) {
        val response = session.lastResponse
        if (response.isNullOrBlank()) {
            println("⚠️ No response to copy. Ask something first.")
            return
        }

        if (copyToClipboard(response)) {
            println("✅ Copied last response to clipboard.")
        }
    }

    private fun copyToClipboard(text: String): Boolean {
        return try {
            val os = System.getProperty("os.name").lowercase()

            when {
                os.contains("mac") -> {
                    val process = ProcessBuilder("pbcopy").start()
                    process.outputStream.use { it.write(text.toByteArray()) }
                }
                os.contains("win") -> {
                    val process = ProcessBuilder("cmd", "/c", "clip").start()
                    process.outputStream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                os.contains("nux") || os.contains("nix") -> {
                    val hasXclip = ProcessBuilder("which", "xclip").start().waitFor() == 0
                    val hasXsel = ProcessBuilder("which", "xsel").start().waitFor() == 0

                    when {
                        hasXclip -> {
                            val process = ProcessBuilder("xclip", "-selection", "clipboard").start()
                            process.outputStream.use { it.write(text.toByteArray()) }
                        }
                        hasXsel -> {
                            val process = ProcessBuilder("xsel", "--clipboard", "--input").start()
                            process.outputStream.use { it.write(text.toByteArray()) }
                        }
                        else -> {
                            println("⚠️ No clipboard utility found (xclip or xsel). Install one to enable copying.")
                            return false
                        }
                    }
                }
                else -> {
                    println("⚠️ Clipboard not supported on this OS.")
                    return false
                }
            }

            true
        } catch (e: Exception) {
            println("❌ Failed to copy to clipboard: ${e.message}")
            false
        }
    }
}

/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import org.jline.reader.LineReader
import org.jline.reader.ParsedLine
import org.jline.terminal.Terminal
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HistoryCommandHandler(
    private val reader: LineReader,
    private val terminal: Terminal,
    private val historyFile: Path? = null,
) : CommandHandler {
    override val keyword: String = ":history"
    override val description: String = "Show recent prompts (use :history [N], or :history clear)."

    private val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    override fun handle(line: ParsedLine) {
        val arg = line.words().getOrNull(1)?.trim()
        val history = reader.history

        when {
            arg.equals("clear", ignoreCase = true) -> {
                history.purge()
                if (historyFile != null) {
                    runCatching { Files.deleteIfExists(historyFile!!) }
                }
                terminal.writer().println("🧹 History cleared.")
            }

            else -> {
                val n = arg?.toIntOrNull() ?: 20
                val total = history.size()
                if (total == 0) {
                    terminal.writer().println("(history empty)")
                    terminal.flush()
                    return
                }
                val start = (total - n).coerceAtLeast(0)
                val entries = history as Iterable<org.jline.reader.History.Entry>
                for (entry in entries.drop(start)) {
                    val ts =
                        entry
                            .time()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()
                    val oneLine = entry.line().replace("\n", "\\n")
                    terminal.writer().printf("%5d  %s  %s%n", entry.index(), timeFmt.format(ts), oneLine)
                }
            }
        }
        terminal.flush()
    }
}

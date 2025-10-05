/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import org.jline.reader.ParsedLine
import java.nio.file.Files
import java.nio.file.Paths

class DeleteCommandHandler : CommandHandler {
    override val keyword = ":delete-command"
    override val description = "Delete a registered command from ~/.askimo/commands\nUsage: :delete-command <name>"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        if (args.isEmpty()) {
            println("Usage: :delete-command <name>")
            return
        }

        val name = args[0]
        val path = Paths.get(System.getProperty("user.home"), ".askimo", "commands", "$name.yml")
        if (!Files.exists(path)) {
            println("❌ Command '$name' not found.")
            return
        }

        print("⚠️  Delete command '$name'? [y/N]: ")
        val confirm = readLine()?.trim()?.lowercase()
        if (confirm != "y") {
            println("✋ Aborted.")
            return
        }

        try {
            Files.delete(path)
            println("🗑️  Deleted '$name'")
        } catch (e: Exception) {
            println("❌ Failed to delete: ${e.message}")
        }
    }
}

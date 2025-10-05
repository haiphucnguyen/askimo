/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import org.jline.reader.ParsedLine
import java.nio.file.Files
import java.nio.file.Paths

class ListCommandsHandler : CommandHandler {
    override val keyword = ":list-commands"
    override val description = "List all registered commands in ~/.askimo/commands"

    override fun handle(line: ParsedLine) {
        val dir = Paths.get(System.getProperty("user.home"), ".askimo", "commands")
        if (!Files.exists(dir)) {
            println("ℹ️  No commands registered yet.")
            return
        }

        val files =
            Files
                .list(dir)
                .filter { it.fileName.toString().endsWith(".yml") }
                .sorted()
                .toList()

        if (files.isEmpty()) {
            println("ℹ️  No commands registered.")
            return
        }

        println("📦 Registered commands (${files.size})")
        println("────────────────────────────")
        files.forEach {
            println(it.fileName.toString().removeSuffix(".yml"))
        }
    }
}

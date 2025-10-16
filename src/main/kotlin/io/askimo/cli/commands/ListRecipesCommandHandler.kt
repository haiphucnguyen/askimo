/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.util.Logger.info
import org.jline.reader.ParsedLine
import java.nio.file.Files
import java.nio.file.Paths

class ListRecipesCommandHandler : CommandHandler {
    override val keyword = ":recipes"
    override val description = "List all registered commands in ~/.askimo/recipes"

    override fun handle(line: ParsedLine) {
        val dir = Paths.get(System.getProperty("user.home"), ".askimo", "recipes")
        if (!Files.exists(dir)) {
            info("ℹ️  No recipes registered yet.")
            return
        }

        val files =
            Files
                .list(dir)
                .filter { it.fileName.toString().endsWith(".yml") }
                .sorted()
                .toList()

        if (files.isEmpty()) {
            info("ℹ️  No recipes registered.")
            return
        }

        info("📦 Registered recipes (${files.size})")
        info("────────────────────────────")
        files.forEach {
            info(it.fileName.toString().removeSuffix(".yml"))
        }
    }
}

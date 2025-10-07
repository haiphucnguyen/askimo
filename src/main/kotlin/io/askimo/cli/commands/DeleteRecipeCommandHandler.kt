/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import org.jline.reader.ParsedLine
import java.nio.file.Files
import java.nio.file.Paths

class DeleteRecipeCommandHandler : CommandHandler {
    override val keyword = ":delete-recipe"
    override val description = "Delete a registered recipe from ~/.askimo/recipe\nUsage: :delete-recipe <name>"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        if (args.isEmpty()) {
            println("Usage: :delete-recipe <name>")
            return
        }

        val name = args[0]
        val path = Paths.get(System.getProperty("user.home"), ".askimo", "recipes", "$name.yml")
        if (!Files.exists(path)) {
            println("❌ Recipe '$name' not found.")
            return
        }

        print("⚠️  Delete recipe '$name'? [y/N]: ")
        val confirm = readlnOrNull()?.trim()?.lowercase()
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

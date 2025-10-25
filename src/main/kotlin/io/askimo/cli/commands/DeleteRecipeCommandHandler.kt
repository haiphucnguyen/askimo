/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.util.AskimoHome
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.info
import org.jline.reader.ParsedLine
import java.nio.file.Files

class DeleteRecipeCommandHandler : CommandHandler {
    override val keyword = ":delete-recipe"
    override val description = "Delete a registered recipe from ~/.askimo/recipe\nUsage: :delete-recipe <name>"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        if (args.isEmpty()) {
            info("Usage: :delete-recipe <name>")
            return
        }

        val name = args[0]
        val path = AskimoHome.recipesDir().resolve("$name.yml")
        if (!Files.exists(path)) {
            info("❌ Recipe '$name' not found.")
            return
        }

        print("⚠️  Delete recipe '$name'? [y/N]: ")
        val confirm = readlnOrNull()?.trim()?.lowercase()
        if (confirm != "y") {
            info("✋ Aborted.")
            return
        }

        try {
            Files.delete(path)
            info("🗑️  Deleted '$name'")
        } catch (e: Exception) {
            info("❌ Failed to delete: ${e.message}")
            debug(e)
        }
    }
}

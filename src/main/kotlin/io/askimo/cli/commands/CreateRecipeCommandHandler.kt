/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.recipes.RecipeDef
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.info
import io.askimo.core.util.Yaml.yamlMapper
import org.jline.reader.ParsedLine
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText

class CreateRecipeCommandHandler : CommandHandler {
    override val keyword: String = ":create-recipe"
    override val description: String =
        "Create a provider-agnostic recipe from a YAML template.\n" +
            "Usage: :create-recipe <name?> -template <file.yml>"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        val (maybeName, templatePath) =
            parseArgs(args) ?: run {
                info("Usage: :create-recipe <name?> -template <file.yml>")
                return
            }

        val src = expandHome(templatePath!!)
        if (!src.exists()) {
            info("❌ Template not found: $src")
            return
        }

        val defFromFile =
            try {
                yamlMapper.readValue(src.readText(), RecipeDef::class.java)
            } catch (e: Exception) {
                info("❌ Invalid YAML (${e.message})")
                return
            }

        val name =
            when {
                !maybeName.isNullOrBlank() -> maybeName
                defFromFile.name.isNotBlank() -> defFromFile.name
                else -> {
                    info("❌ Recipe name missing. Provide it as first arg or in YAML `name:`.")
                    return
                }
            }

        val targetDir = Paths.get(System.getProperty("user.home"), ".askimo", "recipes")
        try {
            Files.createDirectories(targetDir)
        } catch (e: Exception) {
            info("❌ Cannot create recipes dir: $targetDir (${e.message})")
            return
        }

        val dst = targetDir.resolve("$name.yml")
        if (Files.exists(dst)) {
            info("⚠️ Recipe '$name' already exists at $dst")
            info("   Delete it first or choose a different name.")
            return
        }

        // Normalize the YAML with the final name (provider-agnostic schema v3)
        val normalized = defFromFile.copy(name = name)
        val yamlOut =
            try {
                // pretty writer is fine; order may differ but schema is kept
                yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(normalized)
            } catch (e: Exception) {
                info("❌ Failed to serialize YAML (${e.message})")
                return
            }

        try {
            Files.writeString(dst, yamlOut)
        } catch (e: Exception) {
            info("❌ Failed to write: $dst (${e.message})")
            debug(e)
            return
        }

        info("✅ Registered recipe '$name' at $dst")
        info("➡  Run: askimo -r $name")
    }

    private fun parseArgs(args: List<String>): Pair<String?, String?>? {
        var name: String? = null
        var template: String? = null
        var i = 0
        if (args.isNotEmpty() && !args[0].startsWith("-")) {
            name = args[0]
            i = 1
        }
        while (i < args.size) {
            when (args[i]) {
                "-template", "--template" -> {
                    if (i + 1 < args.size) {
                        template = args[i + 1]
                        i += 2
                    } else {
                        return null
                    }
                }
                else -> return null
            }
        }
        return if (!template.isNullOrBlank()) name to template else null
    }

    private fun expandHome(raw: String): Path {
        val home = System.getProperty("user.home")
        // Replace leading ~ safely across OSes; handle both '/' and '\\' after tilde.
        val expanded = Regex("^~(?=[/\\\\]|$)").replace(raw) { home }
        return Paths.get(expanded).toAbsolutePath().normalize()
    }
}

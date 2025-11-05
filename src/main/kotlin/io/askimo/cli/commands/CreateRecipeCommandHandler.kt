/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.recipes.PostAction
import io.askimo.core.recipes.RecipeDef
import io.askimo.core.recipes.ToolRegistry
import io.askimo.core.recipes.VarCall
import io.askimo.core.util.AskimoHome
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.info
import io.askimo.core.util.Yaml.yamlMapper
import org.jline.reader.ParsedLine
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class CreateRecipeCommandHandler : CommandHandler {
    override val keyword: String = ":create-recipe"
    override val description: String =
        "Usage: :create-recipe <name?> -f <file.yml> OR :create-recipe <name?> -i\n" +
            "Create a provider-agnostic recipe from a YAML template or interactively.\n" +
            "Template mode: :create-recipe <name?> -f <file.yml>\n" +
            "Interactive mode: :create-recipe <name?> -i (guided prompts)"

    override fun handle(line: ParsedLine) {
        val args = line.words().drop(1)
        val interactive = args.any { it == "-i" || it == "--interactive" }

        // If no -f and not interactive, show usage (retain previous behavior expected by tests)
        val hasTemplateFlag = args.contains("-f") || args.contains("--file")
        if (!hasTemplateFlag && !interactive) {
            info("Usage: :create-recipe <name?> -f <file.yml> OR :create-recipe <name?> -i")
            return
        }

        if (interactive) {
            interactiveCreate(args)
            return
        }

        val (maybeName, templatePath) =
            parseArgs(args) ?: run {
                info("Usage: :create-recipe <name?> -f <file.yml> OR :create-recipe <name?> -i")
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

        writeRecipe(defFromFile.copy(name = name))
    }

    // --- Interactive creation path ---
    private fun interactiveCreate(args: List<String>) {
        info(
            """
            🧪 Interactive recipe creation (PoC)
            Press Enter to accept defaults. Type END on its own line to finish multi-line sections.
            You can use placeholders like {{arg1}}, {{arg2}} for external arguments passed when running the recipe,
            and {{varName}} for variables you define below.

            Example variable (file summarizer):
              file_content:
                tool: readFile
                args: ["{{arg1}}"]

            System example:
              You are an expert writer. Summarize {{file_content}}

            User template example:
              File path: {{arg1}}
              Content:
              {{file_content}}

            (Order matters: variables are resolved in the order you add them.)
            """.trimIndent(),
        )
        val reader = BufferedReader(InputStreamReader(System.`in`))

        fun ask(
            prompt: String,
            default: String? = null,
        ): String {
            val suffix = if (default != null) " [$default]" else ""
            print("$prompt$suffix: ")
            val line = reader.readLine()?.trim().orEmpty()
            return if (line.isEmpty() && default != null) default else line
        }

        val maybeNameArg = args.firstOrNull()?.takeIf { !it.startsWith("-") }
        val name = ask("Name", maybeNameArg ?: "myRecipe")
        val description = ask("Description", "Interactive recipe")
        val versionStr = ask("Version", "1")
        val version = versionStr.toIntOrNull() ?: 1

        // Tools list (empty => all allowed)
        val availableTools = ToolRegistry.defaults().keys().sorted()
        info("Available tools: ${availableTools.joinToString(", ")}")
        info(
            "Allowed tools help: Leave blank for ALL tools. Provide a comma list to restrict. Example: readFile,writeFile.\n" +
                "If you restrict and then reference a tool outside the list, execution will error.",
        )
        val toolsInput = ask("Allowed tools (comma-separated, blank = ALL)", "")
        val allowedTools =
            toolsInput
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .also { list ->
                    val unknown = list.filterNot { it in availableTools }
                    if (unknown.isNotEmpty()) info("⚠️ Unknown tools ignored: ${unknown.joinToString(", ")}")
                }.filter { it in availableTools }

        // Variables loop
        val vars = mutableMapOf<String, VarCall>()
        info(
            "Variables section: Each variable resolves BEFORE prompts are rendered.\n" +
                "Use them to fetch context (diffs, file content, git status, etc.).\n" +
                "Args may include placeholders ({{arg1}}, {{otherVar}}) which are substituted before the tool runs.\n" +
                "Enter '?' as variable name to show a quick example again.",
        )
        while (true) {
            val add = ask("Add variable? (y/n)", if (vars.isEmpty()) "y" else "n").lowercase()
            if (add != "y") break
            var varName = ask("  Variable name", "var${vars.size + 1}")
            if (varName == "?") {
                info(
                    "Example:\n  file_content:\n    tool: readFile\n    args: [\"{{arg1}}\"]\n  diff:\n    tool: stagedDiff\n    args: [\"--no-color\"]",
                )
                varName = ask("  Variable name", "var${vars.size + 1}")
            }
            if (varName.isBlank()) {
                info("  Skipped (blank name).")
                continue
            }
            if (!varName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
                info("  ❌ Invalid name '$varName' (must match [A-Za-z_][A-Za-z0-9_]*). Skipping.")
                continue
            }
            val toolName = ask("  Tool name", availableTools.firstOrNull() ?: "readFile")
            if (toolName !in availableTools) {
                info("  ❌ Unknown tool '$toolName'. Skipping.")
                continue
            }
            val argsRaw = ask("  Args (comma-separated; placeholders allowed; blank = none)", "")
            val argList = argsRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val call =
                if (argList.isEmpty()) VarCall(tool = toolName, args = null) else VarCall(tool = toolName, args = argList)
            if (vars.containsKey(varName)) info("  ⚠️ Overwriting existing variable '$varName'.")
            vars[varName] = call
        }

        // Multi-line capture helper
        fun captureMultiline(
            label: String,
            default: String,
            help: String,
        ): String {
            info(help)
            info("Type END on a new line to finish entering $label. Leaving blank uses default.")
            val lines = mutableListOf<String>()
            while (true) {
                val ln = reader.readLine() ?: break
                if (ln.trim() == "END") break
                lines += ln
            }
            val text = lines.joinToString("\n").trim()
            return if (text.isBlank()) default else text
        }

        val systemDefault = "You are an assistant."
        val system =
            captureMultiline(
                label = "system section",
                default = systemDefault,
                help =
                "System section: High-level role, constraints, formatting rules.\n" +
                    "It is prepended internally above the user prompt.\n" +
                    "You may reference variables (e.g. {{file_content}}) or args ({{arg1}}).\n" +
                    "Avoid user-specific transient details; keep reusable.",
            )

        val userTemplateDefault = "Input: {{arg1}}\nPlease respond."
        val userTemplate =
            captureMultiline(
                label = "userTemplate section",
                default = userTemplateDefault,
                help =
                "User template: Main task content shown to the model.\n" +
                    "Include contextual variables: e.g. \n  File path: {{arg1}}\n  Content:\n  {{file_content}}\n" +
                    "Use placeholders {{argN}} for invocation-time arguments.\n" +
                    "Don't include SYSTEM: or USER: headers; they are added automatically.",
            )

        // Assemble definition (postActions + defaults omitted for PoC)
        val def =
            RecipeDef(
                name = name,
                version = version,
                description = description.ifBlank { null },
                allowedTools = allowedTools, // empty list ⇒ all tools
                vars = vars.toMap(),
                system = system,
                userTemplate = userTemplate,
                postActions = emptyList<PostAction>(),
                defaults = emptyMap(),
            )

        writeRecipe(def)
    }

    private fun writeRecipe(def: RecipeDef) {
        val targetDir = AskimoHome.recipesDir()
        try {
            Files.createDirectories(targetDir)
        } catch (e: Exception) {
            info("❌ Cannot create recipes dir: $targetDir (${e.message})")
            return
        }

        val dst = targetDir.resolve("${def.name}.yml")
        val fileExists = Files.exists(dst)
        if (fileExists) {
            info("⚠️ Recipe '${def.name}' already exists at $dst")
            info("Do you want to overwrite it? (y/n): ")
            val response = readLine()?.trim()?.lowercase()
            if (response != "y" && response != "yes") {
                info("Operation cancelled. Choose a different name or delete the existing recipe first.")
                return
            }
        }

        val yamlOut =
            try {
                yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(def)
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

        val action = if (fileExists) "Updated" else "Registered"
        info("✅ $action recipe '${def.name}' at $dst")
        info("➡  Run: askimo -r ${def.name} <arguments>")
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
                "-f", "--file" -> {
                    if (i + 1 < args.size) {
                        template = args[i + 1]
                        i += 2
                    } else {
                        return null
                    }
                }
                "-i", "--interactive" -> {
                    i++
                }
                else -> return null
            }
        }
        return if (!template.isNullOrBlank()) name to template else null
    }

    private fun expandHome(raw: String): Path = AskimoHome.expandTilde(raw)
}

/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.recipes

import io.askimo.cli.LoadingIndicator
import io.askimo.core.providers.chat
import io.askimo.core.session.Session
import org.jline.terminal.Terminal
import java.util.concurrent.atomic.AtomicBoolean

object MiniTpl {
    private val re = Regex("\\{\\{([^}|]+)(?:\\|([^}]+))?}}")

    fun render(
        tpl: String,
        vars: Map<String, String>,
    ) = re.replace(tpl) { m ->
        val key = m.groupValues[1].trim()
        val fallback = m.groupValues.getOrNull(2)?.trim()
        vars[key] ?: fallback ?: ""
    }
}

class RecipeExecutor(
    private val session: Session,
    private val registry: RecipeRegistry,
    private val tools: ToolRegistry,
) {
    data class RunOpts(
        val overrides: Map<String, String> = emptyMap(),
        val terminal: Terminal? = null,
        val spinnerMessage: String = "Thinking…",
        val spinnerDoneLabel: String = "Done",
    )

    fun run(
        name: String,
        opts: RunOpts = RunOpts(),
    ) {
        val def = registry.load(name)

        // 1) baseline vars (defaults ⊕ overrides)
        val vars = def.defaults.toMutableMap().apply { putAll(opts.overrides) }

        // 2) pre-step: resolve declared vars via tools (generic; no git knowledge here)
        def.vars.forEach { (varName, call) ->
            val out = tools.invoke(call.tool, call.args)
            vars[varName] = out?.toString().orEmpty()
        }

        // 3) prompts — since ChatService only takes a @UserMessage string,
        // we inline the system content at the top of the user prompt.
        val systemRendered = MiniTpl.render(def.system, vars)
        val userRendered = MiniTpl.render(def.userTemplate, vars)

        var prompt =
            buildString {
                appendLine("SYSTEM:")
                appendLine(systemRendered.trim())
                appendLine()
                appendLine("USER:")
                appendLine(userRendered.trim())
            }.trim()

        detectTemplateVar(prompt)?.let {
            prompt = neutralizeForLc4j(prompt)
        }

        // 4) stream the model output and capture it
        // === spinner + streaming ===
        val indicator = opts.terminal?.let { LoadingIndicator(it, opts.spinnerMessage, opts.spinnerDoneLabel) }
        indicator?.start()

        val firstTokenSeen = AtomicBoolean(false)

        val output =
            session
                .getChatService()
                .chat(prompt) { _ ->
                    if (firstTokenSeen.compareAndSet(false, true)) {
                        indicator?.stopWithElapsed()
                        opts.terminal?.flush()
                    }
                }.trim()

        // If no tokens ever arrived, still print a nice “done” line
        if (!firstTokenSeen.get()) {
            indicator?.stopWithElapsed()
            opts.terminal?.writer()?.println()
            opts.terminal?.flush()
        }

        require(output.isNotBlank()) { "Model returned empty output" }

        val formatted = formatOutput(output, vars["format"] ?: "plain")

        // 5) post-actions (generic). We expose {{output}} to templates.
        val actionVars = vars.toMutableMap().apply { put("output", formatted) }
        def.postActions.forEach { action ->
            if (evalBool(MiniTpl.render(action.when_ ?: "true", actionVars))) {
                val resolvedArgs = resolveArgs(action.call.args, actionVars)
                tools.invoke(action.call.tool, resolvedArgs)
            }
        }
        println(formatted)
    }

    private fun resolveArgs(
        args: Any?,
        vars: Map<String, String>,
    ): Any? =
        when (args) {
            null -> null
            is String -> MiniTpl.render(args, vars)
            is List<*> -> args.map { if (it is String) MiniTpl.render(it, vars) else it }
            is Map<*, *> -> args.mapValues { (_, v) -> if (v is String) MiniTpl.render(v, vars) else v }
            else -> args
        }

    private fun evalBool(expr: String): Boolean {
        val t = expr.trim()
        if (t.equals("true", true)) return true
        if (t.equals("false", true)) return false
        val parts = t.split("==").map { it.trim().trim('"') }
        return parts.size == 2 && parts[0].equals(parts[1], true)
    }

    private fun formatOutput(
        text: String,
        mode: String,
    ): String {
        val t = text.trim()
        return when (mode.lowercase()) {
            "markdown", "md" -> {
                // strip any existing fences and re-wrap cleanly
                val clean =
                    t
                        .removePrefix("```markdown")
                        .removePrefix("```md")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                "```markdown\n$clean\n```"
            }
            "ansi" -> {
                // simple ANSI styling example: make header bold + cyan
                val lines = t.lines()
                if (lines.isEmpty()) return t
                val header = "\u001B[1;36m${lines.first()}\u001B[0m"
                (listOf(header) + lines.drop(1)).joinToString("\n")
            }
            else -> {
                // plain text: remove any markdown fences if present
                t
                    .removePrefix("```markdown")
                    .removePrefix("```md")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
            }
        }
    }

    // find the first {{...}} occurrence; returns the variable name inside, or null if none
    private fun detectTemplateVar(s: String): String? {
        val m = Regex("\\{\\{([^}]+)}}").find(s) ?: return null
        return m.groupValues[1]
    }

    // neutralize *all* occurrences so LC4J will not treat them as PromptTemplate vars
    private fun neutralizeForLc4j(s: String): String = s.replace("{{", "{\u200B{").replace("}}", "}\u200B}")
}

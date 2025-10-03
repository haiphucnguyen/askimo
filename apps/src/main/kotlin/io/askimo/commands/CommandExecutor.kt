package io.askimo.commands

import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import io.askimo.core.session.Session

object MiniTpl {
    fun render(tpl: String, vars: Map<String, String>) =
        Regex("\\{\\{([^}|]+)(?:\\|([^}]+))?}}").replace(tpl) { m ->
            val k = m.groupValues[1].trim(); val fb = m.groupValues.getOrNull(2)?.trim()
            vars[k] ?: fb ?: ""
        }
}

class CommandExecutor(
    private val session: Session,
    private val registry: CommandRegistry,
    private val tools: ToolRegistry
) {
    data class RunOpts(val overrides: Map<String, String> = emptyMap())

    fun run(name: String, opts: RunOpts = RunOpts()) {
        val def = registry.load(name)

        // 1) baseline vars
        val vars = def.defaults.toMutableMap().apply { putAll(opts.overrides) }

        // 2) pre-step: resolve declared vars via tools (generic)
        def.vars.forEach { (varName, call) ->
            val out = tools.invoke(call.tool, call.args)
            vars[varName] = out?.toString().orEmpty()
        }

        // 3) prompts
        val sys = MiniTpl.render(def.system, vars)
        val usr = MiniTpl.render(def.user_template, vars)

        // 4) model (provider-agnostic: uses active Askimo session)
        val model = session.buildChatModel()
        val req = ChatRequest.builder()
            .messages(listOf(SystemMessage(sys), UserMessage(usr)))
            .build()
        val res = model.generate(req)
        val output = res.output().content().text()?.trim().orEmpty()

        if (output.isBlank()) error("Model returned empty output")

        // 5) post actions (generic)
        val actionVars = vars.toMutableMap().apply { put("output", output) }
        def.post_actions.forEach { action ->
            if (evalBool(MiniTpl.render(action.when_ ?: "true", actionVars))) {
                val resolvedArgs = resolveArgs(action.call.args, actionVars)
                tools.invoke(action.call.tool, resolvedArgs)
            }
        }

        // Always print the output for visibility
        println(output)
    }

    private fun resolveArgs(args: Any?, vars: Map<String, String>): Any? = when (args) {
        null -> null
        is String -> MiniTpl.render(args, vars)
        is List<*> -> args.map { if (it is String) MiniTpl.render(it, vars) else it }
        is Map<*, *> -> args.mapValues { (_, v) -> if (v is String) MiniTpl.render(v, vars) else v }
        else -> args
    }

    private fun evalBool(expr: String): Boolean {
        // Minimal evaluator: supports "true"/"false" or "X == true/false"
        val t = expr.trim()
        if (t.equals("true", true)) return true
        if (t.equals("false", true)) return false
        val parts = t.split("==").map { it.trim().trim('"') }
        return if (parts.size == 2) parts[0].equals(parts[1], true) else false
    }
}
package io.askimo.core.util

object MiniTpl {
    // {{key}} with default: {{key|fallback}}
    fun render(tpl: String, vars: Map<String, Any?>): String {
        return Regex("\\{\\{([^}|]+)(?:\\|([^}]+))?}}")
            .replace(tpl) { m ->
                val key = m.groupValues[1].trim()
                val fallback = m.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
                (vars[key]?.toString() ?: fallback ?: "").trimEnd()
            }
    }
}
package io.askimo.core.util

import org.jline.reader.LineReader

object Prompts {
    lateinit var reader: org.jline.reader.LineReader

    fun init(r: LineReader) {
        reader = r
    }

    fun ask(label: String, default: String? = null): String {
        val suffix = if (default != null) " [$default]" else ""
        val line = reader.readLine("$label$suffix: ").trim()
        return if (line.isEmpty() && default != null) default else line
    }

    fun askSecret(label: String): String {
        return reader.readLine("$label: ", '*')
    }

    fun askBool(label: String, default: Boolean = true): Boolean {
        val defHint = if (default) "Y/n" else "y/N"
        while (true) {
            val s = ask("$label ($defHint)").lowercase()
            if (s.isBlank()) return default
            if (s in setOf("y", "yes")) return true
            if (s in setOf("n", "no")) return false
            println("Please answer y/n.")
        }
    }

    fun askInt(label: String, default: Int): Int {
        while (true) {
            val s = ask(label, default.toString())
            s.toIntOrNull()?.let { return it }
            println("Please enter a number.")
        }
    }
}

/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.skills.agent

/**
 * Parses a single `stream-json` event line emitted by the Gemini CLI and converts it
 * into structured [StreamJsonEvent] and a human-readable status string.
 *
 * The parser is generic — it does not hard-code tool names or parameter keys.
 * Any unknown event type is still rendered in the hierarchy format.
 *
 * Fields excluded from the status display:
 * - `timestamp` — noise, not user-relevant
 * - `role` — always "assistant", not useful
 * - `tool_id` / `tool_name` at the top level — `tool_name` is promoted to the header
 */
object GeminiStreamJsonEventParser {

    /** Fields to skip entirely in the rendered output. */
    private val EXCLUDED_FIELDS = setOf("timestamp", "role", "tool_id", "type")

    /**
     * Parsed representation of a single stream-json event line.
     *
     * @property type   The `type` field value (e.g. `"content"`, `"tool_use"`, `"result"`).
     * @property fields All remaining top-level string/boolean/nested fields excluding [EXCLUDED_FIELDS].
     */
    data class StreamJsonEvent(
        val type: String,
        val fields: Map<String, Any>, // String | Boolean | Map<String,Any> (nested object)
    )

    /**
     * Parses [line] into a [StreamJsonEvent].
     * Returns `null` if the line is blank or cannot be parsed as a JSON object with a `type` field.
     */
    fun parse(line: String): StreamJsonEvent? {
        if (line.isBlank() || !line.trimStart().startsWith("{")) return null
        val fields = parseObject(line.trim()) ?: return null
        val type = fields["type"] as? String ?: return null
        val remaining = fields.filterKeys { it !in EXCLUDED_FIELDS }
        return StreamJsonEvent(type = type, fields = remaining)
    }

    /**
     * Renders [event] as a compact human-readable multi-line string.
     *
     * Format example:
     * ```
     * tool_use
     *   write_file:
     *     content: package com.example…
     *     file_path: spring-boot-app/…
     * ```
     */
    fun render(event: StreamJsonEvent): String = buildString {
        // For tool_use: promote tool_name as a sub-header
        val toolName = event.fields["tool_name"] as? String
        if (event.type == "tool_use" && toolName != null) {
            append(event.type)
            append("\n  ")
            append(toolName)
            append(":")
            val params = event.fields["parameters"]
            if (params is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                appendFields(params as Map<String, Any>, indent = "    ")
            } else {
                // No parameters — render remaining fields
                val rest = event.fields.filterKeys { it != "tool_name" && it != "parameters" }
                appendFields(rest, indent = "    ")
            }
        } else {
            append(event.type)
            append(":")
            appendFields(event.fields, indent = "  ")
        }
    }

    private fun StringBuilder.appendFields(fields: Map<String, Any>, indent: String) {
        fields.forEach { (key, value) ->
            append("\n")
            append(indent)
            when (value) {
                is Map<*, *> -> {
                    append(key)
                    append(":")
                    @Suppress("UNCHECKED_CAST")
                    appendFields(value as Map<String, Any>, "$indent  ")
                }

                is String -> {
                    append(key)
                    append(": ")
                    // Truncate long values (e.g. file content) for display
                    val display = value.replace('\n', '↵').let {
                        if (it.length > 120) it.take(117) + "…" else it
                    }
                    append(display)
                }

                else -> {
                    append(key)
                    append(": ")
                    append(value)
                }
            }
        }
    }

    // ── Minimal JSON parser ────────────────────────────────────────────────────

    /**
     * Parses a JSON object string `{...}` into a `Map<String, Any>`.
     * Supports: string values, boolean values, nested objects.
     * Arrays and numbers are captured as raw strings.
     * Returns `null` on parse failure.
     */
    internal fun parseObject(json: String): Map<String, Any>? {
        val s = json.trim()
        if (!s.startsWith("{") || !s.endsWith("}")) return null
        val result = mutableMapOf<String, Any>()
        var i = 1 // skip opening '{'
        while (i < s.length) {
            // Skip whitespace and commas
            while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r' || s[i] == ',')) i++
            if (i >= s.length || s[i] == '}') break

            // Read key
            if (s[i] != '"') return null
            val (key, afterKey) = readString(s, i) ?: return null
            i = afterKey

            // Skip whitespace and colon
            while (i < s.length && (s[i] == ' ' || s[i] == '\t')) i++
            if (i >= s.length || s[i] != ':') return null
            i++ // skip ':'
            while (i < s.length && (s[i] == ' ' || s[i] == '\t')) i++

            // Read value
            val (value, afterValue) = readValue(s, i) ?: return null
            result[key] = value
            i = afterValue
        }
        return result
    }

    /**
     * Reads a JSON string starting at [pos] (must be `"`).
     * Returns Pair(unescaped string, index after closing quote) or null.
     */
    internal fun readString(s: String, pos: Int): Pair<String, Int>? {
        if (pos >= s.length || s[pos] != '"') return null
        val sb = StringBuilder()
        var i = pos + 1
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                when (val next = s[i + 1]) {
                    'n' -> sb.append('\n')

                    'r' -> sb.append('\r')

                    't' -> sb.append('\t')

                    '"' -> sb.append('"')

                    '\\' -> sb.append('\\')

                    '/' -> sb.append('/')

                    'u' -> {
                        val hex = s.substring(i + 2, (i + 6).coerceAtMost(s.length))
                        sb.append(hex.toIntOrNull(16)?.toChar() ?: next)
                        i += 4
                    }

                    else -> sb.append(next)
                }
                i += 2
            } else if (ch == '"') {
                return Pair(sb.toString(), i + 1)
            } else {
                sb.append(ch)
                i++
            }
        }
        return null // unterminated string
    }

    /**
     * Reads a JSON value (string, object, boolean, array, or number) starting at [pos].
     * Arrays and numbers are returned as raw strings.
     */
    internal fun readValue(s: String, pos: Int): Pair<Any, Int>? {
        if (pos >= s.length) return null
        return when (s[pos]) {
            '"' -> {
                val (str, end) = readString(s, pos) ?: return null
                Pair(str, end)
            }

            '{' -> {
                val end = findMatchingBrace(s, pos, '{', '}') ?: return null
                val nested = parseObject(s.substring(pos, end + 1)) ?: return null
                Pair(nested, end + 1)
            }

            '[' -> {
                val end = findMatchingBrace(s, pos, '[', ']') ?: return null
                Pair(s.substring(pos, end + 1), end + 1)
            }

            't' -> if (s.startsWith("true", pos)) Pair(true, pos + 4) else null

            'f' -> if (s.startsWith("false", pos)) Pair(false, pos + 5) else null

            'n' -> if (s.startsWith("null", pos)) Pair("null", pos + 4) else null

            else -> {
                // Number or other primitive — read until delimiter
                var end = pos
                while (end < s.length && s[end] != ',' && s[end] != '}' && s[end] != ']') end++
                Pair(s.substring(pos, end).trim(), end)
            }
        }
    }

    /** Finds the closing bracket matching an opening bracket, respecting nesting and strings. */
    private fun findMatchingBrace(s: String, start: Int, open: Char, close: Char): Int? {
        var depth = 0
        var i = start
        var inString = false
        while (i < s.length) {
            val ch = s[i]
            if (inString) {
                if (ch == '\\') {
                    i++ // skip escaped char
                } else if (ch == '"') {
                    inString = false
                }
            } else {
                when (ch) {
                    '"' -> inString = true

                    open -> depth++

                    close -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
            i++
        }
        return null
    }
}

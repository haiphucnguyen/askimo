/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.skills.agent

/**
 * Parses a single `stream-json` event line emitted by the Claude CLI (`claude --output-format stream-json`).
 * Reuses the same parser infrastructure as [GeminiStreamJsonEventParser].
 */
object ClaudeStreamJsonEventParser {

    private val EXCLUDED_FIELDS = setOf("type", "index")

    fun parse(line: String): GeminiStreamJsonEventParser.StreamJsonEvent? {
        if (line.isBlank() || !line.trimStart().startsWith("{")) return null
        val fields = GeminiStreamJsonEventParser.parseObject(line.trim()) ?: return null
        val type = fields["type"] as? String ?: return null
        val remaining = fields.filterKeys { it !in EXCLUDED_FIELDS }
        return GeminiStreamJsonEventParser.StreamJsonEvent(type = type, fields = remaining)
    }
}

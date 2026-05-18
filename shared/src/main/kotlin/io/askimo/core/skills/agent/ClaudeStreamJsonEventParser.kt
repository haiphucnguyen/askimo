/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Askimo
 */
package io.askimo.core.skills.agent

/**
 * Parses a single `stream-json` event line emitted by the Claude CLI (`claude --output-format stream-json`).
 * Reuses the JSON parsing infrastructure from [GeminiStreamJsonEventParser].
 *
 * Claude events embed interesting data inside `message.content[]` — a JSON array of content blocks.
 * This parser extracts that array into a typed list so [ClaudeAgent] can iterate over content blocks.
 */
object ClaudeStreamJsonEventParser {

    private val EXCLUDED_FIELDS = setOf("type", "index")

    /**
     * A parsed content block from `message.content[]`.
     * @property type   `"text"`, `"tool_use"`, or `"thinking"`
     * @property fields All remaining fields for that block (e.g. `text`, `name`, `input`, `thinking`)
     */
    data class ContentBlock(
        val type: String,
        val fields: Map<String, Any>,
    )

    fun parse(line: String): GeminiStreamJsonEventParser.StreamJsonEvent? {
        if (line.isBlank() || !line.trimStart().startsWith("{")) return null
        val fields = GeminiStreamJsonEventParser.parseObject(line.trim()) ?: return null
        val type = fields["type"] as? String ?: return null
        val remaining = fields.filterKeys { it !in EXCLUDED_FIELDS }
        return GeminiStreamJsonEventParser.StreamJsonEvent(type = type, fields = remaining)
    }

    /**
     * Extracts the `message.content[]` array from an `assistant` or `user` event's fields,
     * returning a list of [ContentBlock]s. Returns empty list if not present or unparseable.
     */
    @Suppress("UNCHECKED_CAST")
    fun extractContentBlocks(fields: Map<String, Any>): List<ContentBlock> {
        val message = fields["message"] as? Map<String, Any> ?: return emptyList()
        val contentRaw = message["content"] ?: return emptyList()

        // content is stored as a raw JSON array string by GeminiStreamJsonEventParser.readValue
        val contentJson = contentRaw as? String ?: return emptyList()
        if (!contentJson.startsWith("[")) return emptyList()

        return parseArray(contentJson).mapNotNull { item ->
            val block = item as? Map<String, Any> ?: return@mapNotNull null
            val blockType = block["type"] as? String ?: return@mapNotNull null
            ContentBlock(type = blockType, fields = block.filterKeys { it != "type" })
        }
    }

    /**
     * Extracts the `tool_use_result` from a `user` event's fields.
     * Returns the raw value (String or Map) or null.
     */
    fun extractToolUseResult(fields: Map<String, Any>): Any? = fields["tool_use_result"]

    /**
     * Parses a JSON array string `[...]` into a list of values (String, Boolean, Map, etc.).
     */
    internal fun parseArray(json: String): List<Any> {
        val s = json.trim()
        if (!s.startsWith("[") || !s.endsWith("]")) return emptyList()
        val result = mutableListOf<Any>()
        var i = 1 // skip '['
        while (i < s.length) {
            while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r' || s[i] == ',')) i++
            if (i >= s.length || s[i] == ']') break
            val (value, afterValue) = GeminiStreamJsonEventParser.readValue(s, i) ?: break
            result.add(value)
            i = afterValue
        }
        return result
    }
}

/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import kotlinx.serialization.json.Json

object PromptBuilder {
    private val json = Json { prettyPrint = false }

    data class BuiltPrompt(
        val system: String,
        val user: String,
    ) {
        /** For ChatService that only accepts a single user string, inline system first. */
        fun asSingleMessage(): String = buildString {
            appendLine("SYSTEM INSTRUCTIONS")
            appendLine("```")
            appendLine(system.trim())
            appendLine("```")
            appendLine()
            append(user)
        }
    }

    fun build(request: DiffRequest): BuiltPrompt {
        val system =
            """
            You are Askimo’s code editor.
            Given an ENV HEADER, one or more SOURCE FILES, and an INSTRUCTION,
            output a single **git unified diff** (multi-file allowed) with minimal, surgical changes.
            Rules:
            - Do not rename/move/delete files.
            - Keep within the budget described in the header.
            - Preserve formatting and line endings.
            - For documentation tasks, edit only comments/Javadoc/docstrings unless told otherwise.
            - If unsure, produce a smaller diff or a no-op.
            Output: ONLY a git unified diff (no prose, no backticks).
            """.trimIndent()

        val headerJson = json.encodeToString(request.header)

        val user =
            buildString {
                appendLine("ENV HEADER (JSON)")
                appendLine("```json")
                appendLine(headerJson)
                appendLine("```")
                appendLine()
                appendLine("INSTRUCTION")
                appendLine("```")
                appendLine(request.instruction.trim())
                appendLine("```")
                appendLine()
                appendLine("SOURCE FILES")
                request.files.forEach { f ->
                    appendLine("```file path=${f.path} eol=${f.eol}")
                    append(f.text)
                    if (!f.text.endsWith("\n")) append('\n')
                    appendLine("```")
                }
                appendLine()
                appendLine("RESPONSE FORMAT")
                appendLine("- Return ONLY a git unified diff. No prose, no backticks, no Markdown fences.")
                appendLine("- Use headers: `diff --git a/<path> b/<path>`, then `--- a/<path>`, `+++ b/<path>`, and `@@` hunks.")
            }

        return BuiltPrompt(system = system, user = user)
    }
}

/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.skills

import io.askimo.core.skills.agent.GeminiStreamJsonEventParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GeminiStreamJsonEventParserTest {

    // ── parse() ───────────────────────────────────────────────────────────────

    @Nested
    inner class Parse {

        @Test
        fun `tool_use event extracts type, tool_name and parameters`() {
            val line = """{"type":"tool_use","timestamp":"2026-05-08T18:49:16.261Z","tool_name":"write_file","tool_id":"jy4761xi","parameters":{"content":"package com.example;\n","file_path":"src/Main.java"}}"""
            val event = GeminiStreamJsonEventParser.parse(line)
            assertNotNull(event)
            assertEquals("tool_use", event!!.type)
            assertEquals("write_file", event.fields["tool_name"])
            assertNull(event.fields["timestamp"])
            assertNull(event.fields["tool_id"])
            assertNull(event.fields["type"])
            val params = event.fields["parameters"]
            assertTrue(params is Map<*, *>)
            @Suppress("UNCHECKED_CAST")
            val p = params as Map<String, Any>
            assertEquals("package com.example;\n", p["content"])
            assertEquals("src/Main.java", p["file_path"])
        }

        @Test
        fun `tool_result event extracts status`() {
            val line = """{"type":"tool_result","timestamp":"2026-05-08T18:49:16.730Z","tool_id":"4asg0jeh","status":"success"}"""
            val event = GeminiStreamJsonEventParser.parse(line)
            assertNotNull(event)
            assertEquals("tool_result", event!!.type)
            assertEquals("success", event.fields["status"])
            assertNull(event.fields["timestamp"])
            assertNull(event.fields["tool_id"])
        }

        @Test
        fun `message event extracts content and delta`() {
            val line = """{"type":"message","timestamp":"2026-05-08T18:49:26.880Z","role":"assistant","content":"Hello world","delta":true}"""
            val event = GeminiStreamJsonEventParser.parse(line)
            assertNotNull(event)
            assertEquals("message", event!!.type)
            assertEquals("Hello world", event.fields["content"])
            assertEquals(true, event.fields["delta"])
            assertNull(event.fields["role"])
            assertNull(event.fields["timestamp"])
        }

        @Test
        fun `content event extracts value`() {
            val line = """{"type":"content","value":"some streamed text"}"""
            val event = GeminiStreamJsonEventParser.parse(line)
            assertNotNull(event)
            assertEquals("content", event!!.type)
            assertEquals("some streamed text", event.fields["value"])
        }

        @Test
        fun `result event extracts content field`() {
            val line = """{"type":"result","content":"Final answer here"}"""
            val event = GeminiStreamJsonEventParser.parse(line)
            assertNotNull(event)
            assertEquals("result", event!!.type)
            assertEquals("Final answer here", event.fields["content"])
        }

        @Test
        fun `blank line returns null`() {
            assertNull(GeminiStreamJsonEventParser.parse(""))
            assertNull(GeminiStreamJsonEventParser.parse("   "))
        }

        @Test
        fun `non-json line returns null`() {
            assertNull(GeminiStreamJsonEventParser.parse("not json"))
        }

        @Test
        fun `handles escaped newlines and quotes in string values`() {
            val line = """{"type":"content","value":"line1\nline2\t\"quoted\""}"""
            val event = GeminiStreamJsonEventParser.parse(line)
            assertNotNull(event)
            assertEquals("line1\nline2\t\"quoted\"", event!!.fields["value"])
        }
    }

    // ── render() ─────────────────────────────────────────────────────────────

    @Nested
    inner class Render {

        @Test
        fun `tool_use promotes tool_name as sub-header with parameters`() {
            val line = """{"type":"tool_use","timestamp":"2026-05-08T18:49:16.261Z","tool_name":"write_file","tool_id":"jy4761xi","parameters":{"content":"package com.example;\n","file_path":"src/Main.java"}}"""
            val event = GeminiStreamJsonEventParser.parse(line)!!
            val rendered = GeminiStreamJsonEventParser.render(event)
            assertTrue(rendered.startsWith("tool_use"), "Should start with type")
            assertTrue(rendered.contains("write_file:"), "Should show tool_name as sub-header")
            assertTrue(rendered.contains("file_path: src/Main.java"), "Should show file_path parameter")
            assertTrue(rendered.contains("content:"), "Should show content parameter")
        }

        @Test
        fun `tool_result shows status`() {
            val line = """{"type":"tool_result","timestamp":"2026-05-08T18:49:16.730Z","tool_id":"4asg0jeh","status":"success"}"""
            val event = GeminiStreamJsonEventParser.parse(line)!!
            val rendered = GeminiStreamJsonEventParser.render(event)
            assertTrue(rendered.startsWith("tool_result:"))
            assertTrue(rendered.contains("status: success"))
        }

        @Test
        fun `message shows content and delta`() {
            val line = """{"type":"message","timestamp":"2026-05-08T18:49:26.880Z","role":"assistant","content":"Hello world","delta":true}"""
            val event = GeminiStreamJsonEventParser.parse(line)!!
            val rendered = GeminiStreamJsonEventParser.render(event)
            assertTrue(rendered.startsWith("message:"))
            assertTrue(rendered.contains("content: Hello world"))
            assertTrue(rendered.contains("delta: true"))
        }

        @Test
        fun `truncates long string values`() {
            val longValue = "x".repeat(200)
            val line = """{"type":"content","value":"$longValue"}"""
            val event = GeminiStreamJsonEventParser.parse(line)!!
            val rendered = GeminiStreamJsonEventParser.render(event)
            assertTrue(rendered.contains("…"), "Long value should be truncated with ellipsis")
        }
    }
}

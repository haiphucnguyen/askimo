/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.chart.renderers

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for Mermaid diagram sanitization.
 * Ensures common AI-generated syntax errors are automatically fixed.
 */
class MermaidSanitizationTest {

    @Test
    fun `test xychart title without quotes is fixed`() {
        val diagram = """
xychart-beta
    title Sample Line Chart
    x-axis [Jan, Feb, Mar, Apr, May]
    y-axis "Value" 0 --> 500
    line [120, 230, 310, 250, 400]
        """.trimIndent()

        val sanitized = sanitizeMermaidDiagram(diagram)

        assertTrue(sanitized.contains("title \"Sample Line Chart\""), "Title should be quoted")
        assertFalse(
            sanitized.contains("title Sample Line Chart") && !sanitized.contains("\"Sample Line Chart\""),
            "Unquoted title should not exist",
        )
    }

    @Test
    fun `test xychart title already quoted is not modified`() {
        val diagram = """
xychart-beta
    title "Monthly Sales"
    x-axis [Jan, Feb, Mar, Apr, May]
    y-axis "Revenue" 0 --> 500
    line [120, 230, 310, 250, 400]
        """.trimIndent()

        val sanitized = sanitizeMermaidDiagram(diagram)

        // Should remain unchanged
        assertTrue(sanitized.contains("title \"Monthly Sales\""), "Already quoted title should remain")
        assertEquals(diagram, sanitized, "Already correct diagram should not be modified")
    }

    @Test
    fun `test xychart y-axis label without quotes is fixed`() {
        val diagram = """
xychart-beta
    title "Sales"
    x-axis [Jan, Feb, Mar]
    y-axis Value 0 --> 500
    line [120, 230, 310]
        """.trimIndent()

        val sanitized = sanitizeMermaidDiagram(diagram)

        assertTrue(sanitized.contains("y-axis \"Value\" 0 -->"), "Y-axis label should be quoted")
        assertFalse(
            sanitized.contains("y-axis Value 0") && !sanitized.contains("\"Value\""),
            "Unquoted y-axis label should not exist",
        )
    }

    @Test
    fun `test xychart multi-word y-axis label without quotes is fixed`() {
        val diagram = """
xychart-beta
    title "Data"
    x-axis [1, 2, 3]
    y-axis Revenue USD 0 --> 1000
    line [100, 200, 300]
        """.trimIndent()

        val sanitized = sanitizeMermaidDiagram(diagram)

        assertTrue(
            sanitized.contains("y-axis \"Revenue USD\" 0 -->"),
            "Multi-word y-axis label should be quoted",
        )
    }

    @Test
    fun `test xychart y-axis already quoted is not modified`() {
        val diagram = """
xychart-beta
    title "Chart"
    x-axis [1, 2, 3]
    y-axis "Revenue ($)" 0 --> 500
    line [100, 200, 300]
        """.trimIndent()

        val sanitized = sanitizeMermaidDiagram(diagram)

        assertTrue(sanitized.contains("y-axis \"Revenue ($)\" 0 -->"), "Already quoted label should remain")
    }

    @Test
    fun `test complete xychart with multiple issues is fixed`() {
        val diagram = """
xychart-beta
    title Sample XY Chart
    x-axis [Jan, Feb, Mar, Apr, May]
    y-axis Value 0 --> 500
    line [120, 230, 310, 250, 400]
        """.trimIndent()

        val sanitized = sanitizeMermaidDiagram(diagram)

        // Both title and y-axis should be fixed
        assertTrue(sanitized.contains("title \"Sample XY Chart\""), "Title should be quoted")
        assertTrue(sanitized.contains("y-axis \"Value\" 0 -->"), "Y-axis label should be quoted")
    }

    @Test
    fun `test flowchart is not affected by sanitization`() {
        val diagram = """
graph TD
    A[Start] --> B[Process]
    B --> C{Decision}
    C -->|Yes| D[End]
        """.trimIndent()

        val sanitized = sanitizeMermaidDiagram(diagram)

        // Flowchart should remain unchanged
        assertEquals(diagram, sanitized, "Flowchart syntax should not be modified")
    }

    @Test
    fun `test sequence diagram is not affected by sanitization`() {
        val diagram = """
sequenceDiagram
    participant A as Alice
    participant B as Bob
    A->>B: Hello Bob
    B-->>A: Hi Alice
        """.trimIndent()

        val sanitized = sanitizeMermaidDiagram(diagram)

        // Sequence diagram should remain unchanged
        assertEquals(diagram, sanitized, "Sequence diagram syntax should not be modified")
    }

    @Test
    fun `test xychart with special characters in title`() {
        val diagram = """
xychart-beta
    title Monthly Sales (2024)
    x-axis [Jan, Feb, Mar]
    y-axis "Revenue" 0 --> 500
    line [120, 230, 310]
        """.trimIndent()

        val sanitized = sanitizeMermaidDiagram(diagram)

        assertTrue(
            sanitized.contains("title \"Monthly Sales (2024)\""),
            "Title with special chars should be quoted",
        )
    }

    @Test
    fun `test trim whitespace`() {
        val diagram = """

        xychart-beta
            title Sample Chart
            x-axis [1, 2, 3]

        """.trimIndent()

        val sanitized = sanitizeMermaidDiagram(diagram)

        // Should be trimmed
        assertTrue(sanitized.startsWith("xychart-beta"), "Leading whitespace should be trimmed")
        assertFalse(sanitized.startsWith(" "), "Should not start with space")
    }
}

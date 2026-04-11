/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.ui.plan

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Unit tests for [PlanPdfExporter.normalizePdfText].
 *
 * These tests verify that Unicode characters outside the Latin-1 range supported
 * by OpenPDF's built-in Helvetica/Courier fonts are correctly replaced, and in
 * particular that adjacent whitespace is never swallowed.
 */
class PlanExportNormalizeTest {

    private fun normalize(text: String) = PlanPdfExporter.normalizePdfText(text)

    // ── Non-breaking / special hyphens ────────────────────────────────────────

    @Test
    fun `U+2011 non-breaking hyphen between words preserves surrounding spaces`() {
        // "Feb 2022‑Present" — space is a regular ASCII space (0x20) before "2022"
        val input = "Feb 2022\u2011Present"
        val result = normalize(input)
        assertEquals("Feb 2022-Present", result)
    }

    @Test
    fun `U+2011 non-breaking hyphen - no spaces around it`() {
        assertEquals("word-word", normalize("word\u2011word"))
    }

    @Test
    fun `U+2011 non-breaking hyphen - space before only`() {
        assertEquals("word -word", normalize("word \u2011word"))
    }

    @Test
    fun `U+2011 non-breaking hyphen - space after only`() {
        assertEquals("word- word", normalize("word\u2011 word"))
    }

    @Test
    fun `U+2013 en dash is replaced with hyphen-minus`() {
        assertEquals("2020-2024", normalize("2020\u20132024"))
    }

    @Test
    fun `U+2014 em dash is replaced with hyphen-minus`() {
        assertEquals("word-word", normalize("word\u2014word"))
    }

    @Test
    fun `U+2012 figure dash is replaced`() {
        assertEquals("a-b", normalize("a\u2012b"))
    }

    @Test
    fun `U+2015 horizontal bar is replaced`() {
        assertEquals("a-b", normalize("a\u2015b"))
    }

    // ── No-break space variants ────────────────────────────────────────────────

    @Test
    fun `U+00A0 no-break space becomes regular space`() {
        val result = normalize("Feb\u00A02022")
        assertEquals("Feb 2022", result)
        // Ensure result contains only codepoints <= 255 and specifically a plain space
        assertEquals(' ', result[3])
    }

    @Test
    fun `U+202F narrow no-break space is replaced by second pass`() {
        // U+202F = 8239, above 255 — will become '?' without explicit handling
        // This test documents the current behaviour; add explicit handling if '?' is undesirable.
        val input = "Feb\u202F2022"
        val result = normalize(input)
        // After normalization no character should be above Latin-1 range
        assertFalse(result.any { it.code > 255 }, "Result should contain only Latin-1 characters, got: $result")
    }

    @Test
    fun `U+2009 thin space is replaced by second pass`() {
        val input = "Feb\u20092022"
        val result = normalize(input)
        assertFalse(result.any { it.code > 255 }, "Result should contain only Latin-1 characters, got: $result")
    }

    // ── Quotation marks ───────────────────────────────────────────────────────

    @Test
    fun `U+2018 and U+2019 curly single quotes become straight apostrophes`() {
        assertEquals("it's", normalize("it\u2019s"))
        assertEquals("'quoted'", normalize("\u2018quoted\u2019"))
    }

    @Test
    fun `U+201C and U+201D curly double quotes become straight quotes`() {
        assertEquals("\"hello\"", normalize("\u201Chello\u201D"))
    }

    // ── Ellipsis ──────────────────────────────────────────────────────────────

    @Test
    fun `U+2026 horizontal ellipsis becomes three dots`() {
        assertEquals("wait...", normalize("wait\u2026"))
    }

    // ── Invisible / joining characters ────────────────────────────────────────

    @Test
    fun `zero-width space U+200B is removed`() {
        assertEquals("word", normalize("wo\u200Brd"))
    }

    @Test
    fun `zero-width joiner U+200D is removed`() {
        assertEquals("word", normalize("wo\u200Drd"))
    }

    @Test
    fun `BOM U+FEFF is removed`() {
        assertEquals("text", normalize("\uFEFFtext"))
    }

    // ── Second-pass fallback ──────────────────────────────────────────────────

    @Test
    fun `characters above Latin-1 not handled by pass1 become question mark`() {
        // U+4E2D (中) is a CJK character, codepoint 20013 > 255
        val result = normalize("Hello\u4E2DWorld")
        assertEquals("Hello?World", result)
    }

    @Test
    fun `plain ASCII text is unchanged`() {
        val plain = "Ukraine War (Feb 2022-Present), key events."
        assertEquals(plain, normalize(plain))
    }

    @Test
    fun `mixed real-world text`() {
        // Simulates: "Ukraine War (Feb 2022‑Present)" with U+2011 non-breaking hyphen
        val input = "Ukraine War (Feb 2022\u2011Present)"
        val result = normalize(input)
        assertEquals("Ukraine War (Feb 2022-Present)", result)
        assertFalse(result.any { it.code > 255 })
    }

    @Test
    fun `section heading with en dash year range`() {
        // "## 2. Core Data (2020–2024)" with U+2013 en dash
        val input = "## 2. Core Data (2020\u20132024)"
        val result = normalize(input)
        assertEquals("## 2. Core Data (2020-2024)", result)
    }

    @Test
    fun `no-break space before year with non-breaking hyphen`() {
        // Worst case: "Feb\u00A02022\u2011Present" — NBSP before year
        val input = "Feb\u00A02022\u2011Present"
        val result = normalize(input)
        assertEquals("Feb 2022-Present", result)
        assertFalse(result.any { it.code > 255 })
    }

    @Test
    fun `narrow no-break space before year with non-breaking hyphen`() {
        // "Feb\u202F2022\u2011Present" — narrow NBSP (U+202F, code 8239) before year
        // This is the exact case causing "Feb?2022-Present" in the PDF export
        val input = "Feb\u202F2022\u2011Present"
        val result = normalize(input)
        // Should not contain any non-Latin-1 characters, and no '?' from a space
        assertFalse(result.any { it.code > 255 }, "Non-Latin-1 char found in: $result")
        // The narrow NBSP should become a regular space, not '?'
        assertEquals("Feb 2022-Present", result)
    }
}

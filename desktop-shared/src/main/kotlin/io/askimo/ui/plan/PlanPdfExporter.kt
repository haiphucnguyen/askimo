/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.ui.plan

import com.lowagie.text.Chunk
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.FontFactory
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfPageEventHelper
import com.lowagie.text.pdf.PdfWriter
import com.lowagie.text.pdf.draw.LineSeparator
import io.askimo.core.logging.logger
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a markdown string to a PDF file using OpenPDF.
 *
 * Handles: headings (H1–H3), horizontal rules, bullet lists, code fences,
 * blockquotes, GFM tables, blank lines, and inline **bold** / *italic* / `code`.
 *
 * Unicode characters outside the Latin-1 range (which OpenPDF's built-in fonts
 * do not support) are normalised via [normalizePdfText] before rendering.
 */
internal object PlanPdfExporter {

    private val log = logger<PlanPdfExporter>()

    private val fontNormal get() = FontFactory.getFont(FontFactory.HELVETICA, 11f, Font.NORMAL, Color.BLACK)
    private val fontBold get() = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f, Font.BOLD, Color.BLACK)
    private val fontH1 get() = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20f, Font.BOLD, Color(30, 90, 180))
    private val fontH2 get() = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15f, Font.BOLD, Color(50, 50, 50))
    private val fontH3 get() = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f, Font.BOLD, Color(80, 80, 80))
    private val fontCode get() = FontFactory.getFont(FontFactory.COURIER, 9f, Font.NORMAL, Color(60, 60, 60))
    private val fontItalic get() = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11f, Font.ITALIC, Color.BLACK)
    private val fontFooter get() = FontFactory.getFont(FontFactory.HELVETICA, 8f, Font.ITALIC, Color(130, 130, 130))

    /**
     * Adds a copyright + page-number footer to every PDF page via OpenPDF's event system.
     */
    private class FooterPageEvent(
        private val copyright: String,
        private val fontFooter: Font,
    ) : PdfPageEventHelper() {
        override fun onEndPage(writer: PdfWriter, document: Document) {
            val cb = writer.directContent
            val left = document.left()
            val right = document.right()
            val bottom = document.bottom() - 18f
            val width = right - left

            // Separator line
            cb.setLineWidth(0.5f)
            cb.setColorStroke(Color(200, 200, 200))
            cb.moveTo(left, bottom + 10f)
            cb.lineTo(right, bottom + 10f)
            cb.stroke()

            val copyrightTable = PdfPTable(2).apply {
                totalWidth = width
                isLockedWidth = true
            }
            copyrightTable.addCell(
                PdfPCell(Phrase(copyright, fontFooter)).apply {
                    border = Rectangle.NO_BORDER
                    setPaddingTop(2f)
                },
            )
            copyrightTable.addCell(
                PdfPCell(Phrase("Page ${writer.pageNumber}", fontFooter)).apply {
                    border = Rectangle.NO_BORDER
                    horizontalAlignment = Element.ALIGN_RIGHT
                    setPaddingTop(2f)
                },
            )
            copyrightTable.writeSelectedRows(0, -1, left, bottom + 8f, cb)
        }
    }

    fun export(markdown: String, copyright: String, targetFile: File) {
        targetFile.parentFile?.mkdirs()

        val bos = ByteArrayOutputStream(65536)
        val sanitizedMarkdown = normalizePdfText(markdown)
        val document = Document(PageSize.A4, 72f, 72f, 72f, 90f)
        val writer = PdfWriter.getInstance(document, bos)
        writer.pageEvent = FooterPageEvent(copyright, fontFooter)
        document.open()

        renderMarkdownToDocument(document, sanitizedMarkdown)

        document.close()

        val bytes = bos.toByteArray()
        if (bytes.isEmpty()) error("PDF rendering produced 0 bytes")
        FileOutputStream(targetFile).use { it.write(bytes) }
        log.debug("PDF written: {} bytes -> {}", bytes.size, targetFile.absolutePath)
    }

    /**
     * Replaces Unicode characters that fall outside the Latin-1 range supported
     * by OpenPDF's built-in Helvetica/Courier fonts with their closest ASCII equivalents.
     *
     * U+2011 (NON-BREAKING HYPHEN) is the trickiest: it acts as a word-joiner in
     * OpenPDF's simple text shaper, causing the space *before* the preceding token
     * to be collapsed (e.g. "Feb 2022‑Present" → "Feb2022-Present").
     * We replace every occurrence with a plain hyphen-minus; surrounding spaces are
     * already present in the source text and are preserved by the plain ASCII hyphen.
     *
     * Additionally, any remaining characters outside Latin-1 (codepoint > 255) that
     * are not handled explicitly below are replaced with '?' to prevent silent drops.
     */
    internal fun normalizePdfText(text: String): String {
        val pass1 = text
            // ── Hyphens / dashes ─────────────────────────────────────────────────
            .replace('\u2011', '-') // NON-BREAKING HYPHEN
            .replace('\u2012', '-') // FIGURE DASH
            .replace('\u2013', '-') // EN DASH
            .replace('\u2014', '-') // EM DASH
            .replace('\u2015', '-') // HORIZONTAL BAR
            // ── Quotation marks ───────────────────────────────────────────────────
            .replace('\u2018', '\'') // LEFT SINGLE QUOTATION MARK
            .replace('\u2019', '\'') // RIGHT SINGLE QUOTATION MARK
            .replace('\u201C', '"') // LEFT DOUBLE QUOTATION MARK
            .replace('\u201D', '"') // RIGHT DOUBLE QUOTATION MARK
            // ── Ellipsis / misc ───────────────────────────────────────────────────
            .replace("\u2026", "...") // HORIZONTAL ELLIPSIS → three dots
            .replace('\u00A0', ' ') // NO-BREAK SPACE (Latin-1, code 160) → regular space
            // ── Space variants above Latin-1 (would otherwise become '?') ─────────
            .replace('\u202F', ' ') // NARROW NO-BREAK SPACE (e.g. "Feb\u202F2022") → space
            .replace('\u2009', ' ') // THIN SPACE → space
            .replace('\u2008', ' ') // PUNCTUATION SPACE → space
            .replace('\u2007', ' ') // FIGURE SPACE → space
            .replace('\u2006', ' ') // SIX-PER-EM SPACE → space
            .replace('\u2005', ' ') // FOUR-PER-EM SPACE → space
            .replace('\u2004', ' ') // THREE-PER-EM SPACE → space
            .replace('\u2003', ' ') // EM SPACE → space
            .replace('\u2002', ' ') // EN SPACE → space
            .replace('\u2001', ' ') // EM QUAD → space
            .replace('\u2000', ' ') // EN QUAD → space
            .replace('\u205F', ' ') // MEDIUM MATHEMATICAL SPACE → space
            .replace('\u3000', ' ') // IDEOGRAPHIC SPACE → space
            // ── Bullet variants ───────────────────────────────────────────────────
            .replace('\u2023', '-') // TRIANGULAR BULLET
            .replace('\u25CF', '-') // BLACK CIRCLE
            // ── Joining / invisible characters ────────────────────────────────────
            .replace("\u200B", "") // ZERO WIDTH SPACE
            .replace("\u200C", "") // ZERO WIDTH NON-JOINER
            .replace("\u200D", "") // ZERO WIDTH JOINER
            .replace("\uFEFF", "") // BYTE ORDER MARK / ZERO WIDTH NO-BREAK SPACE

        // Second pass: replace any remaining non-Latin-1 character (codepoint > 255)
        // with '?' so OpenPDF never silently drops a character and swallows adjacent spaces.
        return buildString(pass1.length) {
            for (ch in pass1) {
                append(if (ch.code > 255) '?' else ch)
            }
        }
    }

    /**
     * Walks markdown line-by-line and adds OpenPDF elements to [doc].
     * Handles: # headings, --- rules, - bullet lists, ``` code blocks,
     * > blockquotes, blank lines, and inline **bold** / `code`.
     */
    private fun renderMarkdownToDocument(doc: Document, markdown: String) {
        val lines = markdown.lines()
        var i = 0
        var inCodeBlock = false
        val codeBuffer = StringBuilder()

        while (i < lines.size) {
            val line = lines[i]

            // ── Code fence ────────────────────────────────────────────────────
            if (line.trimStart().startsWith("```")) {
                if (!inCodeBlock) {
                    inCodeBlock = true
                    codeBuffer.clear()
                } else {
                    inCodeBlock = false
                    val codePara = Paragraph().apply {
                        alignment = Element.ALIGN_LEFT
                        spacingBefore = 4f
                        spacingAfter = 4f
                    }
                    codeBuffer.toString().lines().forEach { codeLine ->
                        codePara.add(Chunk("$codeLine\n", fontCode))
                    }
                    doc.add(codePara)
                    codeBuffer.clear()
                }
                i++
                continue
            }
            if (inCodeBlock) {
                codeBuffer.appendLine(line)
                i++
                continue
            }

            // ── Markdown table ────────────────────────────────────────────────
            if (isTableRow(line) && i + 1 < lines.size && isSeparatorRow(lines[i + 1])) {
                val tableLines = mutableListOf<String>()
                var j = i
                while (j < lines.size && isTableRow(lines[j])) {
                    tableLines.add(lines[j])
                    j++
                }
                val headerCells = parseTableRow(tableLines[0])
                val dataRows = tableLines.drop(2).map { parseTableRow(it) }
                val colCount = headerCells.size.coerceAtLeast(1)

                val table = PdfPTable(colCount).apply {
                    widthPercentage = 100f
                    setSpacingBefore(8f)
                    setSpacingAfter(8f)
                }

                // Header row
                headerCells.forEach { cell ->
                    table.addCell(
                        PdfPCell(buildInlinePhrase(cell, fontBold)).apply {
                            backgroundColor = Color(230, 235, 245)
                            setPadding(5f)
                            borderColor = Color(180, 180, 180)
                        },
                    )
                }
                // Data rows
                dataRows.forEach { row ->
                    val cells = if (row.size < colCount) {
                        row + List(colCount - row.size) { "" }
                    } else {
                        row.take(colCount)
                    }
                    cells.forEach { cell ->
                        table.addCell(
                            PdfPCell(buildInlinePhrase(cell)).apply {
                                setPadding(4f)
                                borderColor = Color(200, 200, 200)
                            },
                        )
                    }
                }

                doc.add(table)
                i = j
                continue
            }

            when {
                line.startsWith("# ") -> {
                    val p = Paragraph().apply {
                        font = fontH1
                        spacingBefore = 8f
                        spacingAfter = 4f
                    }
                    buildInlinePhrase(line.removePrefix("# "), fontH1).forEach { p.add(it as Element) }
                    doc.add(p)
                    doc.add(LineSeparator(1.5f, 100f, Color(70, 130, 200), Element.ALIGN_LEFT, -2f))
                }

                line.startsWith("## ") -> {
                    val p = Paragraph().apply {
                        font = fontH2
                        spacingBefore = 12f
                        spacingAfter = 4f
                    }
                    buildInlinePhrase(line.removePrefix("## "), fontH2).forEach { p.add(it as Element) }
                    doc.add(p)
                    doc.add(LineSeparator(0.5f, 100f, Color(200, 200, 200), Element.ALIGN_LEFT, -2f))
                }

                line.startsWith("### ") -> {
                    val p = Paragraph().apply {
                        font = fontH3
                        spacingBefore = 8f
                        spacingAfter = 2f
                    }
                    buildInlinePhrase(line.removePrefix("### "), fontH3).forEach { p.add(it as Element) }
                    doc.add(p)
                }

                line == "---" || line == "***" -> {
                    doc.add(
                        Paragraph(" ").apply {
                            spacingBefore = 4f
                            spacingAfter = 4f
                        },
                    )
                    doc.add(LineSeparator(0.5f, 100f, Color(180, 180, 180), Element.ALIGN_CENTER, 0f))
                }

                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    val text = line.trimStart().removePrefix("- ").removePrefix("* ")
                    // Use a Paragraph with an explicit bullet rather than ListItem,
                    // which silently drops the bullet symbol when not inside a List container.
                    val bulletPhrase = buildInlinePhrase(text)
                    bulletPhrase.add(0, Chunk("\u2022  ", fontNormal)) // • prefix
                    doc.add(
                        Paragraph(bulletPhrase).apply {
                            indentationLeft = 16f
                            firstLineIndent = -12f // hanging indent so bullet sticks out
                            spacingAfter = 2f
                        },
                    )
                }

                line.trimStart().startsWith("> ") -> {
                    val text = line.trimStart().removePrefix("> ")
                    doc.add(
                        Paragraph(buildInlinePhrase(text)).apply {
                            indentationLeft = 20f
                            spacingBefore = 2f
                            spacingAfter = 2f
                        },
                    )
                }

                line.isBlank() -> doc.add(Paragraph(" ").apply { spacingAfter = 4f })

                else -> doc.add(Paragraph(buildInlinePhrase(line)).apply { spacingAfter = 2f })
            }
            i++
        }
    }

    /** Returns true if [line] looks like a GFM table row (starts with `|`). */
    private fun isTableRow(line: String): Boolean {
        val t = line.trim()
        return t.startsWith("|") && t.length > 1
    }

    /**
     * Returns true if [line] is a GFM separator row (e.g. `|---|:---:|---`).
     * A separator row contains ONLY `|`, `-`, `:`, and spaces.
     */
    private fun isSeparatorRow(line: String): Boolean {
        val t = line.trim()
        if (!t.startsWith("|")) return false
        return t.matches(Regex("[|:\\-\\s]+")) && t.contains('-')
    }

    /** Splits a GFM table row into trimmed cell strings. */
    private fun parseTableRow(line: String): List<String> {
        val t = line.trim().removePrefix("|").removeSuffix("|")
        return t.split("|").map { it.trim() }
    }

    /**
     * Parses inline markdown for a text segment and returns a [Phrase] with typed font runs.
     *
     * Handles (in priority order):
     * - `**bold**` or `__bold__`     → [fontBold]
     * - `*italic*` or `_italic_`     → [fontItalic]
     * - `` `code` ``                 → [fontCode]
     * - `\X` escape sequences        → literal X (covers `\-`, `\*`, `\.`, `\(`, `\)`, etc.)
     *
     * @param baseFont Font for un-marked plain text. Defaults to [fontNormal].
     *                 Pass [fontBold] for table header cells so plain header text is bold.
     */
    private fun buildInlinePhrase(text: String, baseFont: Font = fontNormal): Phrase {
        val phrase = Phrase()
        val normalizedText = normalizePdfText(text)
        val regex = Regex(
            """\*\*(.+?)\*\*""" + // **bold**
                """|__(.+?)__""" + // __bold__
                """|\*(?!\*)(.+?)(?<!\*)\*""" + // *italic* (not **)
                """|_(?!_)(.+?)(?<!_)_""" + // _italic_ (not __)
                """|`(.+?)`""" + // `code`
                """|\\.{1}""", // \X escape sequence
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        var last = 0
        for (match in regex.findAll(normalizedText)) {
            if (match.range.first > last) {
                phrase.add(Chunk(normalizedText.substring(last, match.range.first), baseFont))
            }
            val raw = match.value
            when {
                match.groupValues[1].isNotEmpty() -> phrase.add(Chunk(match.groupValues[1], fontBold))

                // **bold**
                match.groupValues[2].isNotEmpty() -> phrase.add(Chunk(match.groupValues[2], fontBold))

                // __bold__
                match.groupValues[3].isNotEmpty() -> phrase.add(Chunk(match.groupValues[3], fontItalic))

                // *italic*
                match.groupValues[4].isNotEmpty() -> phrase.add(Chunk(match.groupValues[4], fontItalic))

                // _italic_
                match.groupValues[5].isNotEmpty() -> phrase.add(Chunk(match.groupValues[5], fontCode))

                // `code`
                raw.startsWith("\\") && raw.length == 2 -> phrase.add(Chunk(raw.substring(1), baseFont)) // \X escape
            }
            last = match.range.last + 1
        }
        if (last < normalizedText.length) phrase.add(Chunk(normalizedText.substring(last), baseFont))
        return phrase
    }
}

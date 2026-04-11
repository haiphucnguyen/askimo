/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.ui.plan

import io.askimo.core.logging.logger
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a markdown string to a Word (.docx) file using Apache POI.
 *
 * Handles: headings (H1–H3), horizontal rules, bullet lists, blank lines,
 * and inline **bold** formatting.
 */
internal object PlanWordExporter {

    private val log = logger<PlanWordExporter>()

    fun export(markdown: String, copyright: String, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        XWPFDocument().use { doc ->
            writeMarkdownToWord(doc, markdown)

            doc.createParagraph().createRun().addBreak()
            doc.createParagraph().also { p ->
                p.alignment = ParagraphAlignment.CENTER
                p.createRun().setText("\u2500".repeat(60))
            }
            doc.createParagraph().also { p ->
                p.alignment = ParagraphAlignment.CENTER
                p.createRun().also { r ->
                    r.setText(copyright)
                    r.fontSize = 8
                    r.isItalic = true
                    r.color = "888888"
                }
            }

            FileOutputStream(targetFile).use { doc.write(it) }
        }
        log.debug("Word document written -> {}", targetFile.absolutePath)
    }

    private fun writeMarkdownToWord(doc: XWPFDocument, markdown: String) {
        val lines = markdown.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("# ") -> doc.createParagraph().also { p ->
                    p.style = "Heading1"
                    p.createRun().setText(line.removePrefix("# "))
                }

                line.startsWith("## ") -> doc.createParagraph().also { p ->
                    p.style = "Heading2"
                    p.createRun().setText(line.removePrefix("## "))
                }

                line.startsWith("### ") -> doc.createParagraph().also { p ->
                    p.style = "Heading3"
                    p.createRun().setText(line.removePrefix("### "))
                }

                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    val text = line.trimStart().removePrefix("- ").removePrefix("* ")
                    doc.createParagraph().also { p ->
                        p.style = "ListParagraph"
                        applyInlineWordFormatting(p.createRun(), text)
                    }
                }

                line == "---" || line == "***" -> doc.createParagraph().also { p ->
                    p.alignment = ParagraphAlignment.CENTER
                    p.createRun().setText("\u2500".repeat(40))
                }

                line.isBlank() -> {
                    if (i > 0 && lines[i - 1].isNotBlank()) doc.createParagraph()
                }

                else -> doc.createParagraph().also { p ->
                    applyInlineWordFormatting(p.createRun(), line)
                }
            }
            i++
        }
    }

    private fun applyInlineWordFormatting(run: org.apache.poi.xwpf.usermodel.XWPFRun, text: String) {
        if (text.startsWith("**") && text.endsWith("**") && text.length > 4) {
            run.isBold = true
            run.setText(text.removeSurrounding("**"))
        } else {
            run.setText(text)
        }
    }
}

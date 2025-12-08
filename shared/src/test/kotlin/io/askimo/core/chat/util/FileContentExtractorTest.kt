/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileContentExtractorTest {

    @Test
    fun `should support PDF files`() {
        assertTrue(FileContentExtractor.isSupported("test.pdf"))
    }

    @Test
    fun `should support DOCX files`() {
        assertTrue(FileContentExtractor.isSupported("test.docx"))
    }

    @Test
    fun `should support DOC files`() {
        assertTrue(FileContentExtractor.isSupported("test.doc"))
    }

    @Test
    fun `should support text files`() {
        assertTrue(FileContentExtractor.isSupported("test.txt"))
    }

    @Test
    fun `should not support images`() {
        assertFalse(FileContentExtractor.isSupported("test.png"))
        assertFalse(FileContentExtractor.isSupported("test.jpg"))
    }

    @Test
    fun `should detect RTF as supported`(@TempDir tempDir: Path) {
        val rtfFile = tempDir.resolve("test.rtf").toFile()
        rtfFile.writeText("{\\rtf1\\ansi\\deff0 Test RTF content}")

        assertTrue(FileContentExtractor.isSupported(rtfFile))
    }

    @Test
    fun `should extract text from plain text file`(@TempDir tempDir: Path) {
        val textFile = tempDir.resolve("test.txt").toFile()
        val testContent = "Hello, World!"
        textFile.writeText(testContent)

        val extracted = FileContentExtractor.extractContent(textFile)
        assertTrue(extracted.contains("Hello, World!"))
    }
}

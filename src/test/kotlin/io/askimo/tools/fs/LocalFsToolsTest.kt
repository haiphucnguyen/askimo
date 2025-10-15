/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.tools.fs

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalFsToolsTest {
    private fun tools(root: Path): LocalFsTools = LocalFsTools(allowedRoot = root, cwd = root)

    @Test
    @DisplayName("readText: reads UTF-8 text, rejects binary and too-large files")
    fun testReadText(
        @TempDir tmp: Path,
    ) {
        val t = tools(tmp)

        // small UTF-8 text file
        val txt = tmp.resolve("notes.txt").apply { writeText("hello world") }
        val ok = t.readText(txt.toString())
        assertEquals(true, ok["ok"], "Expected ok=true for small UTF-8 text")
        assertEquals("hello world", ok["text"])
        assertEquals(Files.size(txt), ok["bytes"])
        assertTrue((ok["path"] as String).endsWith("notes.txt"))

        // binary-looking file (contains NUL byte)
        val bin = tmp.resolve("bin.dat").apply { writeBytes(byteArrayOf(0x41, 0x00, 0x42)) }
        val binRes = t.readText(bin.toString())
        assertEquals(false, binRes["ok"], "Binary file should be rejected")
        assertEquals("binary", binRes["error"]) // error code

        // too large file (> 100 KB default)
        val big = tmp.resolve("big.txt")
        Files.newOutputStream(big, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { os ->
            val oneKb = ByteArray(1024) { 'a'.code.toByte() }
            repeat(101) { os.write(oneKb) } // 101 KB
        }
        val bigRes = t.readText(big.toString())
        assertEquals(false, bigRes["ok"], "File larger than default 100KB should be rejected")
        assertEquals("too_large", bigRes["error"]) // error code
    }

    @Test
    @DisplayName("countEntries: counts files/dirs/bytes; recursive and hidden flags work")
    fun testCountEntries(
        @TempDir tmp: Path,
    ) {
        val t = tools(tmp)

        // Structure:
        // tmp/
        //   a.txt (3 bytes)
        //   sub/
        //     b.md (2 bytes)
        //   .hdir/
        //     c.log (4 bytes)
        val a = tmp.resolve("a.txt").apply { writeText("hey") }
        val sub = tmp.resolve("sub").apply { createDirectories() }
        val b = sub.resolve("b.md").apply { writeText("ok") }
        val hdir = tmp.resolve(".hdir").apply { createDirectories() }
        val c = hdir.resolve("c.log").apply { writeText("data") }

        val nonRec = t.countEntries(tmp.toString(), recursive = false, includeHidden = false)
        assertEquals(true, nonRec["ok"]) // ok present
        assertEquals(1L, nonRec["dirs"], "Should count only sub (not root, not hidden dir)")
        assertEquals(1L, nonRec["files"], "Should count only a.txt (exclude hidden)")
        assertEquals(Files.size(a), nonRec["bytes"], "Bytes should be size of visible files only")

        val recNoHidden = t.countEntries(tmp.toString(), recursive = true, includeHidden = false)
        assertEquals(1L, recNoHidden["dirs"], "Only 'sub' should be counted; hidden dir excluded")
        // Note: Files inside hidden directories are NOT considered hidden themselves by isHidden(p)
        // so .hdir/c.log is still counted when includeHidden=false.
        assertEquals(3L, recNoHidden["files"], "Should count a.txt, sub/b.md, and .hdir/c.log")
        assertEquals(Files.size(a) + Files.size(b) + Files.size(c), recNoHidden["bytes"])

        val recWithHidden = t.countEntries(tmp.toString(), recursive = true, includeHidden = true)
        assertEquals(2L, recWithHidden["dirs"], "Both 'sub' and '.hdir' counted when includeHidden=true")
        assertEquals(3L, recWithHidden["files"], "a.txt, b.md, and .hdir/c.log")
        assertEquals(Files.size(a) + Files.size(b) + Files.size(c), recWithHidden["bytes"])

        // human string present
        assertTrue((recWithHidden["human"] as String).isNotBlank())
    }

    @Test
    @DisplayName("filesByType: filters by extensions and category; supports pagination and recursion")
    fun testFilesByType(
        @TempDir tmp: Path,
    ) {
        val t = tools(tmp)

        // Create files
        val imgDir = tmp.resolve("imgs").apply { createDirectories() }
        val nested = imgDir.resolve("nested").apply { createDirectories() }
        val f1 =
            imgDir.resolve("a.PNG").apply {
                createFile()
                writeBytes(ByteArray(10))
            }
        val f2 =
            imgDir.resolve("b.png").apply {
                createFile()
                writeBytes(ByteArray(20))
            }
        val f3 =
            nested.resolve("c.jpg").apply {
                createFile()
                writeBytes(ByteArray(30))
            }
        val doc = tmp.resolve("readme.md").apply { writeText("# readme") }

        // Filter via extensions string with mixed case and spaces, non-recursive
        val r1 = t.filesByType(imgDir.toString(), extensions = " PNG, jpg ", recursive = false, limit = 10)
        assertEquals(imgDir.toString(), r1["directory"])
        assertEquals(2, r1["count"], "Non-recursive should match only a.PNG and b.png")
        val files1 = (r1["files"] as List<*>).map { it.toString() }.toSet()
        assertTrue(files1.contains("a.PNG") || files1.contains("a.png"))
        assertTrue(files1.contains("b.png"))

        // Category filter with recursion to include nested jpg
        val r2 = t.filesByType(tmp.toString(), category = "image", recursive = true, limit = 10)
        assertEquals(3, r2["count"], "Recursive image count should include nested jpg as well")
        val files2 = (r2["files"] as List<*>).map { it.toString() }
        // Normalize path separators for cross-platform compatibility (Windows vs Unix)
        val files2Norm = files2.map { it.replace('\\', '/') }
        assertTrue(files2Norm.any { it.endsWith("a.PNG") || it.endsWith("a.png") })
        assertTrue(files2Norm.any { it.endsWith("b.png") })
        assertTrue(files2Norm.any { it.endsWith("imgs/nested/c.jpg") || it.endsWith("nested/c.jpg") })

        // Pagination: limit=2 on the full recursive list under tmp
        val rPage1 = t.filesByType(tmp.toString(), category = "image", recursive = true, limit = 2)
        assertEquals(3, rPage1["count"]) // total count
        val page1 = rPage1["files"] as List<*>
        assertEquals(2, page1.size)
        val cursor = rPage1["nextCursor"] as String?
        assertNotNull(cursor)
        val rPage2 = t.filesByType(tmp.toString(), category = "image", recursive = true, limit = 2, cursor = cursor)
        val page2 = rPage2["files"] as List<*>
        assertEquals(1, page2.size)
        assertNull(rPage2["nextCursor"], "No next cursor after final page")
    }

    @Test
    @DisplayName("totalSizeByType: sums bytes and respects extensions precedence over category")
    fun testTotalSizeByType(
        @TempDir tmp: Path,
    ) {
        val t = tools(tmp)

        val d = tmp.resolve("data").apply { createDirectories() }
        val p1 = d.resolve("one.PDF").apply { writeBytes(ByteArray(5)) }
        val p2 = d.resolve("two.pdf").apply { writeBytes(ByteArray(7)) }
        val i1 = d.resolve("pic.png").apply { writeBytes(ByteArray(11)) }

        // category would include png and pdf, but we pass extensions -> precedence
        val res = t.totalSizeByType(d.toString(), extensions = "['PDF', ' pdf ' ]", category = "image", recursive = false)
        assertEquals(d.toString(), res["directory"])
        assertEquals(2L, res["count"], "Only PDFs should be counted due to extensions precedence")
        assertEquals(Files.size(p1) + Files.size(p2), res["bytes"])
        val matched = res["matchedExtensions"] as Set<*>
        assertEquals(setOf("pdf"), matched.toSet(), "Normalize and dedupe extensions")
        val human = res["human"] as String
        assertTrue(human.endsWith("B") || human.endsWith("KB") || human.endsWith("MB"))

        // Recursive: include subfiles
        val sub = d.resolve("sub").apply { createDirectories() }
        val p3 = sub.resolve("three.pdf").apply { writeBytes(ByteArray(13)) }
        val resRec = t.totalSizeByType(d.toString(), extensions = "pdf", recursive = true)
        assertEquals(3L, resRec["count"])
        assertEquals(Files.size(p1) + Files.size(p2) + Files.size(p3), resRec["bytes"])
    }
}

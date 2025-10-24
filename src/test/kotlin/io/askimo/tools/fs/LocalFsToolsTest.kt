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
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalFsToolsTest {
    @Test
    @DisplayName("countEntries: counts files/dirs/bytes; recursive and hidden flags work")
    fun testCountEntries(
        @TempDir tmp: Path,
    ) {
        LocalFsTools.setTestRoot(tmp)

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

        val nonRec = LocalFsTools.countEntries(tmp.toString(), recursive = false, includeHidden = false)
        assertEquals(true, nonRec["ok"])
        assertEquals(1L, nonRec["dirs"], "Should count only sub (not root, not hidden dir)")
        assertEquals(1L, nonRec["files"], "Should count only a.txt (exclude hidden)")
        assertEquals(Files.size(a), nonRec["bytes"], "Bytes should be size of visible files only")

        val recNoHidden = LocalFsTools.countEntries(tmp.toString(), recursive = true, includeHidden = false)
        assertEquals(1L, recNoHidden["dirs"], "Only 'sub' should be counted; hidden dir excluded")
        assertEquals(3L, recNoHidden["files"], "Should count a.txt, sub/b.md, and .hdir/c.log")
        assertEquals(Files.size(a) + Files.size(b) + Files.size(c), recNoHidden["bytes"])

        val recWithHidden = LocalFsTools.countEntries(tmp.toString(), recursive = true, includeHidden = true)
        assertEquals(2L, recWithHidden["dirs"], "Both 'sub' and '.hdir' counted when includeHidden=true")
        assertEquals(3L, recWithHidden["files"], "a.txt, b.md, and .hdir/c.log")
        assertEquals(Files.size(a) + Files.size(b) + Files.size(c), recWithHidden["bytes"])

        assertTrue((recWithHidden["human"] as String).isNotBlank())
    }

    @Test
    @DisplayName("filesByType: filters by extensions and category; supports pagination and recursion")
    fun testFilesByType(
        @TempDir tmp: Path,
    ) {
        LocalFsTools.setTestRoot(tmp)

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

        val r1 = LocalFsTools.filesByType(imgDir.toString(), extensions = " PNG, jpg ", recursive = false, limit = 10)
        assertEquals(imgDir.toString(), r1["directory"])
        assertEquals(2, r1["count"], "Non-recursive should match only a.PNG and b.png")
        val files1 = (r1["files"] as List<*>).map { it.toString() }.toSet()
        assertTrue(files1.contains("a.PNG") || files1.contains("a.png"))
        assertTrue(files1.contains("b.png"))

        val r2 = LocalFsTools.filesByType(tmp.toString(), category = "image", recursive = true, limit = 10)
        assertEquals(3, r2["count"], "Recursive image count should include nested jpg as well")
        val files2 = (r2["files"] as List<*>).map { it.toString() }
        val files2Norm = files2.map { it.replace('\\', '/') }
        assertTrue(files2Norm.any { it.endsWith("a.PNG") || it.endsWith("a.png") })
        assertTrue(files2Norm.any { it.endsWith("b.png") })
        assertTrue(files2Norm.any { it.endsWith("imgs/nested/c.jpg") || it.endsWith("nested/c.jpg") })

        val rPage1 = LocalFsTools.filesByType(tmp.toString(), category = "image", recursive = true, limit = 2)
        assertEquals(3, rPage1["count"])
        val page1 = rPage1["files"] as List<*>
        assertEquals(2, page1.size)
        val cursor = rPage1["nextCursor"] as String?
        assertNotNull(cursor)
        val rPage2 = LocalFsTools.filesByType(tmp.toString(), category = "image", recursive = true, limit = 2, cursor = cursor)
        val page2 = rPage2["files"] as List<*>
        assertEquals(1, page2.size)
        assertNull(rPage2["nextCursor"], "No next cursor after final page")
    }

    @Test
    @DisplayName("totalSizeByType: sums bytes and respects extensions precedence over category")
    fun testTotalSizeByType(
        @TempDir tmp: Path,
    ) {
        LocalFsTools.setTestRoot(tmp)

        val d = tmp.resolve("data").apply { createDirectories() }
        val p1 = d.resolve("one.PDF").apply { writeBytes(ByteArray(5)) }
        val p2 = d.resolve("two.pdf").apply { writeBytes(ByteArray(7)) }
        val i1 = d.resolve("pic.png").apply { writeBytes(ByteArray(11)) }

        val res = LocalFsTools.totalSizeByType(d.toString(), extensions = "['PDF', ' pdf ' ]", category = "image", recursive = false)
        assertEquals(d.toString(), res["directory"])
        assertEquals(2L, res["count"], "Only PDFs should be counted due to extensions precedence")
        assertEquals(Files.size(p1) + Files.size(p2), res["bytes"])
        val matched = res["matchedExtensions"] as Set<*>
        assertEquals(setOf("pdf"), matched.toSet(), "Normalize and dedupe extensions")
        val human = res["human"] as String
        assertTrue(human.endsWith("B") || human.endsWith("KB") || human.endsWith("MB"))

        val sub = d.resolve("sub").apply { createDirectories() }
        val p3 = sub.resolve("three.pdf").apply { writeBytes(ByteArray(13)) }
        val resRec = LocalFsTools.totalSizeByType(d.toString(), extensions = "pdf", recursive = true)
        assertEquals(3L, resRec["count"])
        assertEquals(Files.size(p1) + Files.size(p2) + Files.size(p3), resRec["bytes"])
    }
}

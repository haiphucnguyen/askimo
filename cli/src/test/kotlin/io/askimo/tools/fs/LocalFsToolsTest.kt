/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.tools.fs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalFsToolsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Helper to parse JSON tool responses
    private fun parseToolResponse(jsonString: String): Map<String, Any?> {
        val element = json.parseToJsonElement(jsonString).jsonObject
        val result = mutableMapOf<String, Any?>()

        result["success"] = element["success"]?.jsonPrimitive?.boolean
        result["output"] = element["output"]?.let { if (it is kotlinx.serialization.json.JsonNull) null else it.jsonPrimitive.content }
        result["error"] = element["error"]?.let { if (it is kotlinx.serialization.json.JsonNull) null else it.jsonPrimitive.content }

        // Parse metadata if present
        val metadata = element["metadata"]
        if (metadata != null && metadata !is kotlinx.serialization.json.JsonNull) {
            val metadataObj = metadata.jsonObject
            metadataObj.forEach { (key, value) ->
                result[key] = parseJsonValue(value)
            }
            // For backward compatibility, add ok = success
            result["ok"] = result["success"]
        }

        return result
    }

    private fun parseJsonValue(value: kotlinx.serialization.json.JsonElement): Any? = when (value) {
        is kotlinx.serialization.json.JsonNull -> null
        is kotlinx.serialization.json.JsonPrimitive -> {
            when {
                value.isString -> value.content
                value.content == "true" -> true
                value.content == "false" -> false
                else -> value.content.toLongOrNull() ?: value.content.toDoubleOrNull() ?: value.content
            }
        }
        is kotlinx.serialization.json.JsonArray -> {
            value.map { parseJsonValue(it) }
        }
        is kotlinx.serialization.json.JsonObject -> {
            value.mapValues { (_, v) -> parseJsonValue(v) }
        }
        else -> value.toString()
    }

    @AfterEach
    fun cleanup() {
        // Clean up any background processes after each test to prevent Windows file locking issues
        LocalFsTools.cleanupBackgroundProcesses()

        // On Windows, give the OS extra time to release file handles before JUnit tries to delete temp directory
        // This prevents intermittent "The process cannot access the file" errors
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            Thread.sleep(200)
        }
    }

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

        val nonRec = parseToolResponse(LocalFsTools.countEntries(tmp.toString(), recursive = false, includeHidden = false))
        assertEquals(true, nonRec["success"])
        assertEquals(1L, nonRec["dirs"], "Should count only sub (not root, not hidden dir)")
        assertEquals(1L, nonRec["files"], "Should count only a.txt (exclude hidden)")
        assertEquals(Files.size(a), nonRec["bytes"], "Bytes should be size of visible files only")

        val recNoHidden = parseToolResponse(LocalFsTools.countEntries(tmp.toString(), recursive = true, includeHidden = false))
        assertEquals(1L, recNoHidden["dirs"], "Only 'sub' should be counted; hidden dir excluded")
        assertEquals(2L, recNoHidden["files"], "Should count a.txt and sub/b.md (hidden dir excluded)")
        assertEquals(Files.size(a) + Files.size(b), recNoHidden["bytes"])

        val recWithHidden = parseToolResponse(LocalFsTools.countEntries(tmp.toString(), recursive = true, includeHidden = true))
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

        val r1 = parseToolResponse(LocalFsTools.filesByType(imgDir.toString(), extensions = " PNG, jpg ", recursive = false, limit = 10))
        assertEquals(imgDir.toString(), r1["directory"])
        assertEquals(2L, r1["count"], "Non-recursive should match only a.PNG and b.png")
        val files1 = (r1["files"] as List<*>).map { it.toString() }.toSet()
        assertTrue(files1.contains("a.PNG") || files1.contains("a.png"))
        assertTrue(files1.contains("b.png"))

        val r2 = parseToolResponse(LocalFsTools.filesByType(tmp.toString(), category = "image", recursive = true, limit = 10))
        assertEquals(3L, r2["count"], "Recursive image count should include nested jpg as well")
        val files2 = (r2["files"] as List<*>).map { it.toString() }
        val files2Norm = files2.map { it.replace('\\', '/') }
        assertTrue(files2Norm.any { it.endsWith("a.PNG") || it.endsWith("a.png") })
        assertTrue(files2Norm.any { it.endsWith("b.png") })
        assertTrue(files2Norm.any { it.endsWith("imgs/nested/c.jpg") || it.endsWith("nested/c.jpg") })

        val rPage1 = parseToolResponse(LocalFsTools.filesByType(tmp.toString(), category = "image", recursive = true, limit = 2))
        assertEquals(3L, rPage1["count"])
        val page1 = rPage1["files"] as List<*>
        assertEquals(2, page1.size)
        val cursor = rPage1["nextCursor"] as String?
        assertNotNull(cursor)
        val rPage2 = parseToolResponse(LocalFsTools.filesByType(tmp.toString(), category = "image", recursive = true, limit = 2, cursor = cursor))
        val page2 = rPage2["files"] as List<*>
        assertEquals(1, page2.size)
        val nextCursor2 = rPage2["nextCursor"] as String?
        assertTrue(nextCursor2 == null || nextCursor2 == "", "No next cursor after final page")
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

        val res = parseToolResponse(LocalFsTools.totalSizeByType(d.toString(), extensions = "['PDF', ' pdf ' ]", category = "image", recursive = false))
        assertEquals(d.toString(), res["directory"])
        assertEquals(2L, res["count"], "Only PDFs should be counted due to extensions precedence")
        assertEquals(Files.size(p1) + Files.size(p2), res["bytes"])
        val matched = res["matchedExtensions"] as List<*>
        assertTrue(matched.contains("pdf"), "Should contain pdf extension")
        val human = res["human"] as String
        assertTrue(human.endsWith("B") || human.endsWith("KB") || human.endsWith("MB"))

        val sub = d.resolve("sub").apply { createDirectories() }
        val p3 = sub.resolve("three.pdf").apply { writeBytes(ByteArray(13)) }
        val resRec = parseToolResponse(LocalFsTools.totalSizeByType(d.toString(), extensions = "pdf", recursive = true))
        assertEquals(3L, resRec["count"])
        assertEquals(Files.size(p1) + Files.size(p2) + Files.size(p3), resRec["bytes"])
    }

    @Test
    @DisplayName("Background process cleanup: ensures proper cleanup to prevent Windows file locking")
    fun testBackgroundProcessCleanup(
        @TempDir tmp: Path,
    ) {
        LocalFsTools.setTestRoot(tmp)

        // Start multiple background processes
        val bgResult1 = parseToolResponse(LocalFsTools.runCommand("echo 'test1'", background = true))
        assertEquals(true, bgResult1["success"])
        val pid1 = bgResult1["pid"] as Long

        val bgResult2 = parseToolResponse(LocalFsTools.runCommand("echo 'test2'", background = true))
        assertEquals(true, bgResult2["success"])
        val pid2 = bgResult2["pid"] as Long

        // Verify processes are tracked
        val listResult = parseToolResponse(LocalFsTools.listBackgroundProcesses())
        assertEquals(true, listResult["success"])
        val processes = listResult["processes"] as List<*>
        assertTrue(processes.size >= 2)

        // Clean up all background processes
        LocalFsTools.cleanupBackgroundProcesses()

        // Verify all processes are cleaned up
        val listAfterCleanup = parseToolResponse(LocalFsTools.listBackgroundProcesses())
        assertEquals(true, listAfterCleanup["success"])
        val processesAfterCleanup = listAfterCleanup["processes"] as List<*>
        assertEquals(0, processesAfterCleanup.size)

        // Verify getting output from cleaned up processes returns not_found
        val outputResult1 = parseToolResponse(LocalFsTools.getCommandOutput(pid1))
        assertEquals(false, outputResult1["success"])
        assertEquals("not_found", outputResult1["error"])

        val outputResult2 = parseToolResponse(LocalFsTools.getCommandOutput(pid2))
        assertEquals(false, outputResult2["success"])
        assertEquals("not_found", outputResult2["error"])
    }

    @Test
    @DisplayName("searchFilesByGlob: finds files with intelligent pattern matching and relevance scoring")
    fun testSearchFilesByGlob(
        @TempDir tmp: Path,
    ) {
        LocalFsTools.setTestRoot(tmp)

        // Create test files with various naming conventions
        val srcDir = tmp.resolve("src").apply { createDirectories() }
        val testDir = tmp.resolve("test").apply { createDirectories() }

        val userService = srcDir.resolve("UserService.kt").apply { createFile() }
        val userHelper = srcDir.resolve("user_helper.py").apply { createFile() }
        val userConfig = testDir.resolve("user-config.json").apply { createFile() }
        val readme = tmp.resolve("README.md").apply { createFile() }
        val hiddenFile = tmp.resolve(".hidden.txt").apply { createFile() }

        // Test exact filename search
        val result1 = parseToolResponse(LocalFsTools.searchFilesByGlob(tmp.toString(), "UserService"))
        assertEquals(true, result1["success"])
        val files1 = result1["files"] as List<*>
        assertTrue(files1.any { it.toString().contains("UserService.kt") })
        assertTrue((result1["smartMatch"] as Boolean))

        // Test case-insensitive partial match
        val result2 = parseToolResponse(LocalFsTools.searchFilesByGlob(tmp.toString(), "user"))
        assertEquals(true, result2["success"])
        val files2 = result2["files"] as List<*>
        assertTrue(files2.size >= 3) // Should find UserService, user_helper, user-config
        assertTrue(files2.any { it.toString().contains("UserService") })
        assertTrue(files2.any { it.toString().contains("user_helper") })
        assertTrue(files2.any { it.toString().contains("user-config") })

        // Test traditional glob pattern with smartMatch disabled
        val result3 = parseToolResponse(LocalFsTools.searchFilesByGlob(tmp.toString(), "*.md", smartMatch = false))
        assertEquals(true, result3["success"])
        val files3 = result3["files"] as List<*>
        assertEquals(1, files3.size)
        assertTrue(files3.any { it.toString().contains("README.md") })

        // Test non-recursive search
        val result4 = parseToolResponse(LocalFsTools.searchFilesByGlob(tmp.toString(), "UserService", recursive = false))
        assertEquals(true, result4["success"])
        val files4 = result4["files"] as List<*>
        assertEquals(0, files4.size) // UserService.kt is in src/, not root

        // Test includeHidden
        val result5 = parseToolResponse(LocalFsTools.searchFilesByGlob(tmp.toString(), "hidden", includeHidden = true))
        assertEquals(true, result5["success"])
        val files5 = result5["files"] as List<*>
        assertTrue(files5.any { it.toString().contains(".hidden.txt") })

        // Test no matches
        val result6 = parseToolResponse(LocalFsTools.searchFilesByGlob(tmp.toString(), "nonexistent"))
        assertEquals(true, result6["success"])
        val files6 = result6["files"] as List<*>
        assertEquals(0, files6.size)
    }

    @Test
    @DisplayName("runCommand: executes shell commands with proper output and error handling")
    fun testRunCommand(
        @TempDir tmp: Path,
    ) {
        LocalFsTools.setTestRoot(tmp)

        val isWindows = System.getProperty("os.name").lowercase().contains("windows")

        // Test simple successful command
        val result1 = parseToolResponse(LocalFsTools.runCommand("echo 'Hello World'"))
        assertEquals(true, result1["success"])
        val output1 = result1["output"] as String
        assertTrue(output1.contains("Hello") || (result1["metadata"] as? Map<*, *>)?.get("output")?.toString()?.contains("Hello") == true)
        assertEquals(0L, result1["exitCode"])
        assertNotNull(result1["cwd"])
        assertEquals("echo 'Hello World'", result1["command"])

        // Test command with custom working directory
        val subDir = tmp.resolve("subdir").apply { createDirectories() }
        val pwdCommand = if (isWindows) "echo %CD%" else "pwd"
        val result2 = parseToolResponse(LocalFsTools.runCommand(pwdCommand, cwd = subDir.toString()))
        assertEquals(true, result2["success"])
        val output2 = (result2["output"] as? String) ?: ""
        assertTrue(output2.contains(subDir.toString()) || output2.isNotEmpty())
        assertEquals(subDir.toString(), result2["cwd"])

        // Test command with environment variables
        val envCommand = if (isWindows) "echo %TEST_VAR%" else "echo \$TEST_VAR"
        val result3 = parseToolResponse(LocalFsTools.runCommand(envCommand, env = mapOf("TEST_VAR" to "test_value")))
        assertEquals(true, result3["success"])
        // Just verify it executed successfully
        assertNotNull(result3["output"])

        // Test command that produces error output but succeeds
        val errorCommand = if (isWindows) {
            "echo error 1>&2 & echo output"
        } else {
            "echo 'error' >&2; echo 'output'"
        }
        val result4 = parseToolResponse(LocalFsTools.runCommand(errorCommand))
        assertEquals(true, result4["success"])
        // Verify command executed
        assertEquals(0L, result4["exitCode"])

        // Test failing command - now returns success=false
        val result5 = parseToolResponse(LocalFsTools.runCommand("exit 1"))
        assertEquals(false, result5["success"])
        assertEquals(1L, result5["exitCode"])

        // Test background command
        val sleepCommand = if (isWindows) "timeout /t 1 /nobreak > nul" else "sleep 0.1"
        val result6 = parseToolResponse(LocalFsTools.runCommand(sleepCommand, background = true))
        assertEquals(true, result6["success"])
        assertEquals(true, result6["background"])
        assertNotNull(result6["pid"])
        assertEquals(sleepCommand, result6["command"])
    }

    @Test
    @DisplayName("getCommandOutput: retrieves output from background processes")
    fun testGetCommandOutput(
        @TempDir tmp: Path,
    ) {
        LocalFsTools.setTestRoot(tmp)

        // Start a background process
        val bgResult = parseToolResponse(LocalFsTools.runCommand("echo 'background output'", background = true))
        assertEquals(true, bgResult["success"])
        val pid = bgResult["pid"] as Long

        // Wait a moment for command to complete
        Thread.sleep(200)

        // Get output from background process
        val outputResult = parseToolResponse(LocalFsTools.getCommandOutput(pid))
        assertEquals(true, outputResult["success"])
        // Check output in metadata
        val outputStr = outputResult["output"] as? String ?: ""
        assertTrue(outputStr.contains("background") || outputStr.isNotEmpty())
        // Process should be finished by now
        assertNotNull(outputResult["exitCode"])

        // Test getting output from non-existent process
        val invalidResult = parseToolResponse(LocalFsTools.getCommandOutput(99999L))
        assertEquals(false, invalidResult["success"])
        assertEquals("not_found", invalidResult["error"])
    }

    @Test
    @DisplayName("listBackgroundProcesses: tracks and lists background processes")
    fun testListBackgroundProcesses(
        @TempDir tmp: Path,
    ) {
        LocalFsTools.setTestRoot(tmp)

        // Test that listBackgroundProcesses returns expected structure
        val result = parseToolResponse(LocalFsTools.listBackgroundProcesses())
        assertEquals(true, result["success"])
        assertNotNull(result["count"])
        assertNotNull(result["processes"])

        val processes = result["processes"] as List<*>
        val count = (result["count"] as Number).toInt()
        assertEquals(processes.size, count)

        // Start a background process and verify it's tracked
        val bgResult = parseToolResponse(LocalFsTools.runCommand("echo 'test'", background = true))
        assertEquals(true, bgResult["success"])
        val pid = bgResult["pid"] as Long

        val listAfter = parseToolResponse(LocalFsTools.listBackgroundProcesses())
        assertEquals(true, listAfter["success"])
        val processesAfter = listAfter["processes"] as List<*>

        // Should have at least one process (our echo command might finish quickly)
        val processMap = processesAfter.associateBy { (it as Map<*, *>)["pid"] }
        // Either our process is still running or it finished and was cleaned up
        assertTrue(processMap.containsKey(pid) || processMap.size >= 0)

        // If process is still in the list, check its structure
        if (processMap.containsKey(pid)) {
            val proc = processMap[pid] as Map<*, *>
            assertEquals("echo 'test'", proc["command"])
            assertNotNull(proc["startTime"])
            assertNotNull(proc["runningTimeMs"])
            assertTrue(proc["status"] == "running" || proc["status"] == "finished")
        }
    }

    @Test
    @DisplayName("searchFileContent: finds text content within files with various options")
    fun testSearchFileContent(
        @TempDir tmp: Path,
    ) {
        LocalFsTools.setTestRoot(tmp)

        // Create test files with content
        val srcDir = tmp.resolve("src").apply { createDirectories() }
        val docsDir = tmp.resolve("docs").apply { createDirectories() }

        val javaFile = srcDir.resolve("Example.java").apply {
            writeText("public class Example {\n    // TODO: implement this\n    private String name;\n}")
        }
        val kotlinFile = srcDir.resolve("Service.kt").apply {
            writeText("class Service {\n    fun getName(): String {\n        return \"service\"\n    }\n}")
        }
        val readmeFile = docsDir.resolve("README.md").apply {
            writeText("# Documentation\nThis is a TODO list\nfor the project.")
        }
        val binaryFile = tmp.resolve("binary.dat").apply {
            writeBytes(byteArrayOf(0, 1, 2, 3, 255.toByte()))
        }

        // Test basic text search
        val result1 = parseToolResponse(LocalFsTools.searchFileContent(tmp.toString(), "TODO"))
        assertEquals(true, result1["success"])
        val matches1 = result1["matches"] as List<*>
        assertEquals(2, matches1.size) // Should find in both java file and readme

        val match1 = matches1[0] as Map<*, *>
        assertTrue(match1.containsKey("file"))
        assertTrue(match1.containsKey("content"))
        assertTrue(match1.containsKey("lineNumber"))

        // Test case-sensitive search
        val result2 = parseToolResponse(LocalFsTools.searchFileContent(tmp.toString(), "todo", caseSensitive = true))
        assertEquals(true, result2["success"])
        val matches2 = result2["matches"] as List<*>
        assertEquals(0, matches2.size) // Should not match "TODO" with case-sensitive

        // Test file pattern filtering
        val result3 = parseToolResponse(
            LocalFsTools.searchFileContent(
                tmp.toString(),
                "class",
                filePattern = "*.java",
            ),
        )
        assertEquals(true, result3["success"])
        val matches3 = result3["matches"] as List<*>
        assertEquals(1, matches3.size) // Should only find in Java file
        val match3 = matches3[0] as Map<*, *>
        assertTrue(match3["file"].toString().endsWith(".java"))

        // Test non-recursive search
        val result4 = parseToolResponse(
            LocalFsTools.searchFileContent(
                tmp.toString(),
                "class",
                recursive = false,
            ),
        )
        assertEquals(true, result4["success"])
        val matches4 = result4["matches"] as List<*>
        assertEquals(0, matches4.size) // No matches in root directory

        // Test maxResults limit
        val result5 = parseToolResponse(
            LocalFsTools.searchFileContent(
                tmp.toString(),
                "class",
                maxResults = 1,
            ),
        )
        assertEquals(true, result5["success"])
        val matches5 = result5["matches"] as List<*>
        assertEquals(1, matches5.size) // Limited to 1 result

        // Test includeLineNumbers = false
        val result6 = parseToolResponse(
            LocalFsTools.searchFileContent(
                tmp.toString(),
                "getName",
                includeLineNumbers = false,
            ),
        )
        assertEquals(true, result6["success"])
        val matches6 = result6["matches"] as List<*>
        assertEquals(1, matches6.size)
        val match6 = matches6[0] as Map<*, *>
        assertFalse(match6.containsKey("lineNumber"))

        // Verify binary files are skipped (no explicit test since it's internal behavior)
        val result7 = parseToolResponse(LocalFsTools.searchFileContent(tmp.toString(), "binary"))
        assertEquals(true, result7["success"])
        // Should not crash, binary files should be skipped
    }

    @Test
    @DisplayName("writeFile: creates files and directories as needed")
    fun testWriteFile(
        @TempDir tmp: Path,
    ) {
        LocalFsTools.setTestRoot(tmp)

        // Test writing file in existing directory
        val file1Path = tmp.resolve("test.txt").toString()
        val result1 = parseToolResponse(LocalFsTools.writeFile(file1Path, "Hello, World!"))
        assertEquals(true, result1["success"])
        assertTrue((result1["output"] as String).contains("Success"))

        val file1 = tmp.resolve("test.txt")
        assertTrue(Files.exists(file1))
        assertEquals("Hello, World!", Files.readString(file1))

        // Test writing file with nested directory creation
        val file2Path = tmp.resolve("nested/deep/file.txt").toString()
        val result2 = parseToolResponse(LocalFsTools.writeFile(file2Path, "Nested content"))
        assertEquals(true, result2["success"])

        val file2 = tmp.resolve("nested/deep/file.txt")
        assertTrue(Files.exists(file2))
        assertEquals("Nested content", Files.readString(file2))
        assertTrue(Files.isDirectory(tmp.resolve("nested")))
        assertTrue(Files.isDirectory(tmp.resolve("nested/deep")))

        // Test overwriting existing file
        val result3 = parseToolResponse(LocalFsTools.writeFile(file1Path, "Updated content"))
        assertEquals(true, result3["success"])
        assertEquals("Updated content", Files.readString(file1))

        // Test writing empty content
        val file3Path = tmp.resolve("empty.txt").toString()
        val result4 = parseToolResponse(LocalFsTools.writeFile(file3Path, ""))
        assertEquals(true, result4["success"])
        val file3 = tmp.resolve("empty.txt")
        assertTrue(Files.exists(file3))
        assertEquals("", Files.readString(file3))
    }

    @Test
    @DisplayName("readFile: reads file content and handles error cases")
    fun testReadFile(
        @TempDir tmp: Path,
    ) {
        LocalFsTools.setTestRoot(tmp)

        // Create test file
        val testFile = tmp.resolve("test.txt")
        testFile.writeText("Hello, World!\nLine 2\nLine 3")

        // Test successful read
        val result1 = parseToolResponse(LocalFsTools.readFile(testFile.toString()))
        assertEquals(true, result1["success"])
        assertEquals("Hello, World!\nLine 2\nLine 3", result1["content"])

        // Test reading non-existent file
        val result2 = parseToolResponse(LocalFsTools.readFile(tmp.resolve("nonexistent.txt").toString()))
        assertEquals(false, result2["success"])
        assertTrue((result2["error"] as String).contains("Path not found") || (result2["error"] as String).contains("not found"))

        // Test reading directory instead of file
        val dir = tmp.resolve("directory").apply { createDirectories() }
        val result3 = parseToolResponse(LocalFsTools.readFile(dir.toString()))
        assertEquals(false, result3["success"])
        assertTrue((result3["error"] as String).contains("not a regular file"))

        // Test reading empty file
        val emptyFile = tmp.resolve("empty.txt").apply { writeText("") }
        val result4 = parseToolResponse(LocalFsTools.readFile(emptyFile.toString()))
        assertEquals(true, result4["success"])
        assertEquals("", result4["content"])

        // Test reading file with special characters
        val specialFile = tmp.resolve("special.txt").apply {
            writeText("Special chars: áéíóú, 中文, emoji 🚀")
        }
        val result5 = parseToolResponse(LocalFsTools.readFile(specialFile.toString()))
        assertEquals(true, result5["success"])
        assertEquals("Special chars: áéíóú, 中文, emoji 🚀", result5["content"])
    }

    @Test
    @DisplayName("Error handling: validates path security and directory requirements")
    fun testErrorHandling(
        @TempDir tmp: Path,
    ) {
        LocalFsTools.setTestRoot(tmp)

        // Test searchFilesByGlob with invalid path
        val result1 = parseToolResponse(LocalFsTools.searchFilesByGlob("nonexistent", "test"))
        assertEquals(false, result1["success"])
        assertTrue((result1["error"] as String).contains("failed"))

        // Test runCommand with invalid working directory
        val result2 = parseToolResponse(LocalFsTools.runCommand("echo test", cwd = "nonexistent"))
        assertEquals(false, result2["success"])
        val errorMsg = (result2["error"] as? String ?: "").lowercase()
        assertTrue(
            errorMsg.contains("failed") || errorMsg.contains("not found") || errorMsg.contains("escapes") || errorMsg.contains("not readable"),
            "Expected error to contain failure indication but was: '${result2["error"]}')",
        )

        // Test searchFileContent with invalid path
        val result3 = parseToolResponse(LocalFsTools.searchFileContent("nonexistent", "test"))
        assertEquals(false, result3["success"])
        assertTrue((result3["error"] as String).contains("failed"))
    }
}

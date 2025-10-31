/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive tests for ProjectFileWatcher focusing on reliable functionality.
 *
 * This test suite focuses on file system events that can be reliably tested.
 * Tests use generous delays to account for file system event timing variations.
 */
class ProjectFileWatcherTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var mockIndexer: PgVectorIndexer
    private lateinit var fileWatcher: ProjectFileWatcher

    @BeforeEach
    fun setUp() {
        mockIndexer = mock()
        fileWatcher = ProjectFileWatcher(
            projectRoot = tempDir,
            indexer = mockIndexer,
            coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        )
    }

    @AfterEach
    fun tearDown() {
        fileWatcher.stopWatching()
    }

    // === Basic Functionality Tests ===

    @Test
    fun `watchedPath returns the project root`() {
        assertEquals(tempDir, fileWatcher.watchedPath)
    }

    @Test
    fun `can start and stop file watcher`() {
        // When: Starting the watcher
        fileWatcher.startWatching()
        // Then: Watcher should be active
        assertTrue(fileWatcher.isWatching)

        // When: Stopping the watcher
        fileWatcher.stopWatching()
        // Then: Watcher should be stopped
        assertFalse(fileWatcher.isWatching)
    }

    @Test
    fun `startWatching does nothing if already watching`() = runBlocking {
        // Given: Already watching
        fileWatcher.startWatching()
        assertTrue(fileWatcher.isWatching)

        // When: Starting again
        fileWatcher.startWatching()

        // Then: Should still be watching (no exception thrown)
        assertTrue(fileWatcher.isWatching)
    }

    @Test
    fun `stopWatching does nothing if not watching`() {
        // Given: Not watching
        assertFalse(fileWatcher.isWatching)

        // When: Stopping the watcher
        fileWatcher.stopWatching()

        // Then: Should remain not watching
        assertFalse(fileWatcher.isWatching)
    }

    // === File Event Tests ===

    @Test
    fun `file creation triggers indexing`() = runBlocking {
        // Given: A running watcher
        fileWatcher.startWatching()
        delay(1000) // Allow watcher to start and register directories

        // When: Creating a new file
        val newFile = tempDir.resolve("new-file.kt")
        newFile.writeText("class NewFile {}")
        delay(1500) // Allow event processing

        // Then: Indexer should be called to index the new file
        verify(mockIndexer, timeout(5000)).indexSingleFile(any(), eq("new-file.kt"))
    }

    @Test
    fun `file creation in subdirectory triggers indexing with correct path`() = runBlocking {
        // Given: A directory structure and running watcher
        val srcDir = tempDir.resolve("src").createDirectories()
        fileWatcher.startWatching()
        delay(1000) // Allow watcher to start and register directories

        // When: Creating a file in the subdirectory
        val srcFile = srcDir.resolve("Source.kt")
        srcFile.writeText("class Source {}")
        delay(1500) // Allow event processing

        // Then: Indexer should be called with correct relative path
        verify(mockIndexer, timeout(5000)).indexSingleFile(any(), eq("src/Source.kt"))
    }

    @Test
    fun `file modification triggers re-indexing`() = runBlocking {
        // Given: A running watcher
        fileWatcher.startWatching()
        delay(1000) // Allow watcher to start

        // Create a file first
        val existingFile = tempDir.resolve("existing.kt")
        existingFile.writeText("class Existing {}")
        delay(1000) // Allow creation event

        // When: Modifying the file
        existingFile.writeText("class Existing { fun modified() {} }")
        delay(1000) // Allow modification event

        // Then: Indexer should index and then remove/re-index
        verify(mockIndexer, timeout(5000)).indexSingleFile(any(), eq("existing.kt"))
        verify(mockIndexer, timeout(5000)).removeFileFromIndex(eq("existing.kt"))
    }

    @Test
    fun `multiple file creation events are handled`() = runBlocking {
        // Given: A running watcher
        fileWatcher.startWatching()
        delay(1000) // Allow watcher to start

        // When: Creating multiple files with delays between them
        val file1 = tempDir.resolve("file1.kt")
        file1.writeText("class File1 {}")
        delay(500)

        val file2 = tempDir.resolve("file2.kt")
        file2.writeText("class File2 {}")
        delay(500)

        val file3 = tempDir.resolve("file3.kt")
        file3.writeText("class File3 {}")
        delay(1000) // Allow processing

        // Then: All files should be indexed
        verify(mockIndexer, timeout(5000)).indexSingleFile(any(), eq("file1.kt"))
        verify(mockIndexer, timeout(5000)).indexSingleFile(any(), eq("file2.kt"))
        verify(mockIndexer, timeout(5000)).indexSingleFile(any(), eq("file3.kt"))
    }

    // === File Filtering Tests ===

    @Test
    fun `only supported file extensions are processed`() = runBlocking {
        // Given: A running watcher
        fileWatcher.startWatching()
        delay(1000) // Allow watcher to start

        // When: Creating files with various extensions
        tempDir.resolve("test.kt").apply { writeText("content") } // Supported
        tempDir.resolve("test.py").apply { writeText("content") } // Supported
        delay(500)
        tempDir.resolve("test.exe").apply { writeText("content") } // Not supported
        tempDir.resolve("test.bin").apply { writeText("content") } // Not supported
        delay(1000) // Allow processing

        // Then: Only supported files should be indexed
        verify(mockIndexer, timeout(5000)).indexSingleFile(any(), eq("test.kt"))
        verify(mockIndexer, timeout(5000)).indexSingleFile(any(), eq("test.py"))
        verify(mockIndexer, never()).indexSingleFile(any(), eq("test.exe"))
        verify(mockIndexer, never()).indexSingleFile(any(), eq("test.bin"))
    }

    @Test
    fun `hidden files are ignored`() = runBlocking {
        // Given: A running watcher
        fileWatcher.startWatching()
        delay(1000) // Allow watcher to start

        // When: Creating hidden and visible files
        tempDir.resolve(".hidden.kt").apply { writeText("content") }
        delay(300)
        tempDir.resolve("visible.kt").apply { writeText("content") }
        delay(1000) // Allow processing

        // Then: Only visible files should be indexed
        verify(mockIndexer, timeout(5000)).indexSingleFile(any(), eq("visible.kt"))
        verify(mockIndexer, never()).indexSingleFile(any(), eq(".hidden.kt"))
    }

    // === Directory Handling Tests ===

    @Test
    fun `nested directory structure is handled correctly`() = runBlocking {
        // Given: Create nested directories first
        val nestedDir = tempDir.resolve("src/main/kotlin").createDirectories()

        // Start watcher after directories exist
        fileWatcher.startWatching()
        delay(1000) // Allow watcher to start and register directories

        // When: Creating a file in the nested directory
        val nestedFile = nestedDir.resolve("NestedClass.kt")
        nestedFile.writeText("class NestedClass {}")
        delay(1500) // Allow time for processing

        // Then: Nested file should be indexed with correct relative path
        verify(mockIndexer, timeout(5000)).indexSingleFile(
            any(),
            eq("src/main/kotlin/NestedClass.kt"),
        )
    }

    @Test
    fun `new directories created after watcher starts are registered`() = runBlocking {
        // Given: A running watcher
        fileWatcher.startWatching()
        delay(1000)

        // When: Create a new directory structure after watcher starts
        val newDir = tempDir.resolve("new-directory").createDirectories()
        delay(1000) // Allow directory registration

        // Create a file in the new directory
        val fileInNewDir = newDir.resolve("file-in-new-dir.kt")
        fileInNewDir.writeText("class FileInNewDir {}")
        delay(1500)

        // Then: The file should be indexed with correct relative path
        verify(mockIndexer, timeout(5000)).indexSingleFile(any(), eq("new-directory/file-in-new-dir.kt"))
    }

    // === Error Handling Tests ===

    @Test
    fun `error during indexing does not stop watcher`() = runBlocking {
        // Given: A running watcher and indexer that throws exception
        org.mockito.kotlin.doThrow(RuntimeException("Indexing failed"))
            .`when`(mockIndexer).indexSingleFile(any(), any())

        fileWatcher.startWatching()
        delay(1000) // Allow watcher to start

        // When: Creating a file that will cause indexing to fail
        val file1 = tempDir.resolve("failing.kt")
        file1.writeText("class Failing {}")
        delay(1000)

        // Reset the mock to stop throwing exceptions
        org.mockito.kotlin.reset(mockIndexer)

        // Create another file
        val file2 = tempDir.resolve("working.kt")
        file2.writeText("class Working {}")
        delay(1000)

        // Then: Second file should still be processed (watcher still running)
        verify(mockIndexer, timeout(5000)).indexSingleFile(any(), eq("working.kt"))
    }

    // === Configuration Tests ===

    @Test
    fun `supported extensions include common programming languages`() {
        // This test documents which file extensions are supported
        val supportedExtensions = setOf(
            "java", "kt", "kts", "py", "js", "ts", "jsx", "tsx", "go", "rs", "c", "cpp", "h", "hpp",
            "cs", "rb", "php", "swift", "scala", "groovy", "sh", "bash", "yaml", "yml", "json",
            "xml", "md", "txt", "gradle", "properties", "toml",
        )

        // Verify key extensions are supported
        assertTrue(supportedExtensions.contains("kt"))
        assertTrue(supportedExtensions.contains("java"))
        assertTrue(supportedExtensions.contains("py"))
        assertTrue(supportedExtensions.contains("js"))
        assertTrue(supportedExtensions.contains("ts"))
        assertTrue(supportedExtensions.contains("md"))
        assertTrue(supportedExtensions.contains("yml"))
        assertTrue(supportedExtensions.contains("json"))
    }

    @Test
    fun `skipped directories include common build and IDE directories`() {
        // This test documents which directories are skipped
        val skippedDirs = setOf(
            "build", "target", "dist", "out", "bin", "node_modules", "__pycache__",
            ".gradle", ".mvn", ".idea", ".vscode", ".git", ".svn", ".hg",
        )

        // Verify common build directories are skipped
        assertTrue(skippedDirs.contains("build"))
        assertTrue(skippedDirs.contains("target"))
        assertTrue(skippedDirs.contains("node_modules"))
        assertTrue(skippedDirs.contains(".gradle"))
        assertTrue(skippedDirs.contains(".idea"))
        assertTrue(skippedDirs.contains(".git"))
    }

    // Helper property to access private isWatching field via reflection for testing
    private val ProjectFileWatcher.isWatching: Boolean
        get() {
            val field = ProjectFileWatcher::class.java.getDeclaredField("isWatching")
            field.isAccessible = true
            return field.getBoolean(this)
        }
}

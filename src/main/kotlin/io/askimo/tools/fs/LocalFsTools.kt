/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.tools.fs

import dev.langchain4j.agent.tool.Tool
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import java.util.regex.Pattern
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.use
import kotlin.io.walk
import kotlin.math.min
import kotlin.sequences.filter
import kotlin.sequences.forEach
import kotlin.text.matches
import kotlin.toString

class LocalFsTools(
    private val allowedRoot: Path = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize(),
    private val cwd: Path = Paths.get("").toAbsolutePath().normalize(),
) {
    // Track background processes
    private val backgroundProcesses = mutableMapOf<Long, BackgroundProcess>()

    private data class BackgroundProcess(
        val process: Process,
        val command: String,
        val cwd: String,
        val startTime: Long = System.currentTimeMillis(),
    )

    private fun expandHome(raw: String): Path {
        val home = System.getProperty("user.home")
        val expanded = raw.replace(Regex("^~(?=/|$)"), home)
        return Paths.get(expanded)
    }

    /** Resolve "~" and relative paths (relative to CWD), normalize, and ensure under allowedRoot. */
    private fun resolveAndGuard(raw: String): Path {
        val p0 = expandHome(raw)
        val abs = if (p0.isAbsolute) p0 else cwd.resolve(p0)
        val norm = abs.normalize().toAbsolutePath()
        require(norm.startsWith(allowedRoot)) { "Path escapes allowed root: $norm" }
        require(Files.exists(norm)) { "Path not found: $norm" }
        require(Files.isReadable(norm)) { "Path not readable: $norm" }
        return norm
    }

    private fun safeDir(raw: String): Path {
        val dir = resolveAndGuard(raw)
        require(dir.isDirectory()) { "Not a directory: $raw" }
        return dir
    }

    private fun safeFile(raw: String): Path {
        val file = resolveAndGuard(raw)
        require(file.isRegularFile()) { "Not a regular file: $raw" }
        return file
    }

    private val categoryExts: Map<String, Set<String>> =
        mapOf(
            "video" to setOf("mp4", "mkv", "mov", "avi", "wmv", "flv", "webm", "m4v"),
            "image" to setOf("jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff", "webp", "svg", "heic", "heif"),
            "audio" to setOf("mp3", "wav", "flac", "aac", "m4a", "ogg", "wma", "aiff"),
            "doc" to setOf("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "md", "rtf"),
            "archive" to setOf("zip", "tar", "gz", "tgz", "bz2", "7z", "rar"),
        )

    /**
     * Reads and returns the content of a small UTF-8 text file.
     *
     * Validates file size against ASKIMO_FILE_MAX_KB limit and rejects binary files.
     *
     * @param path The file path to read (supports ~ expansion and relative paths)
     * @return Map with success/failure status and file content or error details
     *
     * @usage
     * ```
     * readText("./README.md")
     * readText("~/Documents/notes.txt")
     * ```
     *
     * @errors
     * - "too_large": File exceeds size limit
     * - "binary": File appears to be binary
     * - "read_failed": File not found, permission denied, or other IO error
     */
    @Tool(
        """
        Read a small UTF-8 text file and return its content for summarization.
        Params: path (string).
        Limits: file size ≤ ASKIMO_FILE_MAX_KB (default 100 KB), rejects binary.
        Returns: { ok: true, text: string } or { ok: false, error, message }.
        """,
    )
    fun readText(path: String): Map<String, Any?> {
        val maxKb = System.getenv("ASKIMO_FILE_MAX_KB")?.toIntOrNull()?.coerceAtLeast(1) ?: 100
        val maxBytes = maxKb * 1024
        return try {
            val file = safeFile(path)
            val size = Files.size(file)
            if (size > maxBytes) {
                return err("too_large", "file > $maxKb KB")
            }
            val bytes = Files.readAllBytes(file)
            if (looksBinary(bytes)) {
                return err("binary", "appears binary")
            }
            val text = bytes.toString(Charsets.UTF_8)
            mapOf("ok" to true, "text" to text, "path" to file.toString(), "bytes" to size)
        } catch (e: Exception) {
            err("read_failed", "${e::class.simpleName}: ${e.message}")
        }
    }

    /**
     * Counts files and directories in a folder with optional recursion and hidden file inclusion.
     *
     * @param path Directory path to analyze
     * @param recursive Whether to search subdirectories (default: false)
     * @param includeHidden Whether to include hidden files/dirs (default: false)
     * @return Map with file/directory counts and total byte size
     *
     * @usage
     * ```
     * countEntries(".")                           // Current directory only
     * countEntries("./src", recursive = true)     // Recursive count
     * countEntries("~", includeHidden = true)     // Include hidden files
     * ```
     *
     * @errors
     * - Path validation errors (not found, not readable, outside allowed root)
     * - Permission errors are silently handled for individual files
     */
    @Tool(
        """
    Count files and directories in a folder.
    Params: path (string), recursive (optional, default false), includeHidden (optional, default false).
    Returns: { ok: true, path, files, dirs, bytes }
    """,
    )
    fun countEntries(
        path: String,
        recursive: Boolean? = false,
        includeHidden: Boolean? = false,
    ): Map<String, Any?> {
        val dir = safeDir(path)
        val rec = recursive == true
        val include = includeHidden == true

        var files = 0L
        var dirs = 0L
        var bytes = 0L

        val maxDepth = if (rec) Int.MAX_VALUE else 1
        Files.walk(dir, maxDepth).use { stream ->
            stream.forEach { p ->
                if (p == dir) return@forEach // don't count the root
                if (!include && isHidden(p)) return@forEach
                when {
                    p.isDirectory() -> dirs++
                    p.isRegularFile() -> {
                        files++
                        bytes += runCatching { Files.size(p) }.getOrDefault(0L)
                    }
                }
            }
        }

        return mapOf(
            "ok" to true,
            "path" to dir.toString(),
            "files" to files,
            "dirs" to dirs,
            "bytes" to bytes,
            "human" to humanReadable(bytes),
        )
    }

    /**
     * Lists files in a directory filtered by file type or extension with pagination.
     *
     * @param path Directory to search
     * @param category Predefined category: video|image|audio|doc|archive
     * @param extensions Custom extensions (e.g., "pdf,png" or ["kt", "java"])
     * @param recursive Search subdirectories (default: false)
     * @param limit Maximum results per page (default: 200, max: 5000)
     * @param cursor Pagination cursor for next page
     * @return Map with matching files and pagination info
     *
     * @usage
     * ```
     * filesByType(".", category = "image")
     * filesByType("./src", extensions = "kt,java", recursive = true)
     * filesByType(".", extensions = ["pdf", "docx"], limit = 50)
     * ```
     *
     * @errors
     * - "Unknown category": Invalid category name
     * - Path validation errors
     * - Requires either category OR extensions parameter
     */
    @Tool(
        """List or count files in a directory by type. Use either 'category' (video|image|audio|doc|archive) OR 'extensions' (e.g., ["pdf","png"] or "pdf,png").
       Parameters: path, category?, extensions?, recursive?(false), limit?(200), cursor?
       Returns: {count, files, nextCursor, directory}""",
    )
    fun filesByType(
        path: String,
        category: String? = null,
        extensions: String? = null,
        recursive: Boolean? = false,
        limit: Int? = 200,
        cursor: String? = null,
    ): Map<String, Any?> {
        val dir = safeDir(path)
        val extSet: Set<String> =
            when {
                extensions != null -> normalizeExtensions(extensions)
                !category.isNullOrBlank() ->
                    categoryExts[category.lowercase()] ?: error("Unknown category: $category")
                else -> error("Provide either 'category' or 'extensions'")
            }

        val max = limit?.coerceIn(1, 5_000) ?: 200
        val start = parseCursor(cursor)

        val allMatches = mutableListOf<String>()
        val stream = if (recursive == true) Files.walk(dir) else Files.list(dir)
        stream.use { s ->
            s.filter { it.isRegularFile() }.forEach { p ->
                val name = p.fileName.toString()
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in extSet) allMatches += dir.relativize(p).toString()
            }
        }

        val end = (start + max).coerceAtMost(allMatches.size)
        val page = if (start < allMatches.size) allMatches.subList(start, end) else emptyList()
        val next = if (end < allMatches.size) end.toString() else null

        return mapOf(
            "directory" to dir.toString(),
            "count" to allMatches.size,
            "files" to page,
            "nextCursor" to next,
        )
    }

    /**
     * Calculates total byte size of files filtered by type or extension.
     *
     * @param path Directory to analyze
     * @param extensions Custom extensions (takes precedence over category)
     * @param category Predefined category: video|image|audio|doc|archive
     * @param recursive Search subdirectories (default: false)
     * @return Map with count, total bytes, and human-readable size
     *
     * @usage
     * ```
     * totalSizeByType(".", category = "video")
     * totalSizeByType("./build", extensions = "jar,war")
     * totalSizeByType("~", category = "image", recursive = true)
     * ```
     *
     * @errors
     * - "Unknown category": Invalid category name
     * - Path validation errors
     * - Requires either extensions OR category parameter
     */
    @Tool(
        "Compute total byte size of files filtered by type. " +
            "Use either 'extensions' (e.g., [\"pdf\"] or \"pdf\") or 'category' (video|image|audio|doc|archive). " +
            "If both are provided, EXTENSIONS TAKE PRECEDENCE. " +
            "Params: path, extensions?, category?, recursive?(false). " +
            "Returns: {count, bytes, human, matchedExtensions, directory}",
    )
    fun totalSizeByType(
        path: String,
        extensions: String? = null,
        category: String? = null,
        recursive: Boolean? = false,
    ): Map<String, Any> {
        val dir = safeDir(path)
        val extSet: Set<String> =
            when {
                extensions != null -> normalizeExtensions(extensions)
                !category.isNullOrBlank() ->
                    categoryExts[category.lowercase(Locale.ROOT)]
                        ?: error("Unknown category: $category")
                else -> error("Provide either 'extensions' or 'category'")
            }

        var count = 0L
        var bytes = 0L
        val stream = if (recursive == true) Files.walk(dir) else Files.list(dir)
        stream.use { s ->
            s.filter { it.isRegularFile() }.forEach { p ->
                val name = p.fileName.toString()
                val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (ext in extSet) {
                    count++
                    bytes += runCatching { Files.size(p) }.getOrDefault(0L)
                }
            }
        }
        return mapOf(
            "directory" to dir.toString(),
            "count" to count,
            "bytes" to bytes,
            "human" to humanReadable(bytes),
            "matchedExtensions" to extSet,
        )
    }

    /**
     * Searches for files using glob pattern matching.
     *
     * @param path Directory to search
     * @param glob Glob pattern (e.g., "*.kt", "test/**/*.java", "Session*")
     * @param recursive Search subdirectories (default: false)
     * @param includeHidden Include hidden files (default: false)
     * @return Map with matching files list and count
     *
     * @usage
     * ```
     * searchFilesByGlob(".", "*.md")              // All markdown files
     * searchFilesByGlob(".", "Session*")          // Files starting with "Session"
     * searchFilesByGlob(".", "**\/\*.kt", recursive = true)  // All Kotlin files recursively
     *```
     *
     * @errors
     * - "search_failed": Path validation, permission, or pattern errors
     */
    @Tool(
        "Search for files in a directory using a glob pattern. " +
            "Params: path (string), glob (string), recursive (optional, default false), includeHidden (optional, default false). " +
            "Returns: {ok: true, path, glob, files, count} or {ok: false, error, message}.",
    )
    fun searchFilesByGlob(
        path: String,
        glob: String,
        recursive: Boolean? = false,
        includeHidden: Boolean? = false,
    ): Map<String, Any?> {
        return try {
            val dir = safeDir(path)
            val rec = recursive == true
            val include = includeHidden == true
            val matcher = dir.fileSystem.getPathMatcher("glob:$glob")
            val files = mutableListOf<String>()
            val stream = if (rec) Files.walk(dir) else Files.list(dir)
            stream.use { s ->
                s
                    .filter { it.isRegularFile() }
                    .forEach { p ->
                        val rel = dir.relativize(p)
                        if (matcher.matches(rel) || matcher.matches(p.fileName)) {
                            if (!include && isHidden(p)) return@forEach
                            files += rel.toString()
                        }
                    }
            }
            mapOf(
                "ok" to true,
                "path" to dir.toString(),
                "glob" to glob,
                "files" to files,
                "count" to files.size,
            )
        } catch (e: Exception) {
            err("search_failed", "${e::class.simpleName}: ${e.message}")
        }
    }

    /**
     * Executes shell commands with cross-platform compatibility and background support.
     *
     * Automatically detects platform (Windows/Unix) and uses appropriate shell.
     * Supports command chaining, custom working directory, and environment variables.
     *
     * @param command Shell command to execute (supports && || ; operators)
     * @param cwd Working directory (default: current directory)
     * @param env Additional environment variables
     * @param background Run in background and return PID (default: false)
     * @return Map with command output or background process info
     *
     * @usage
     * ```
     * runCommand("ls -la")                        // Simple command
     * runCommand("./gradlew test && npm install") // Command chaining
     * runCommand("git status", cwd = "./project") // Custom directory
     * runCommand("build.sh", background = true)   // Background execution
     * runCommand("echo $VAR", env = mapOf("VAR" to "value"))
     * ```
     *
     * @errors
     * - "run_failed": Command execution, path validation, or permission errors
     * - Returns non-zero exit codes in output for failed commands
     */
    @Tool(
        "Run shell commands in a persistent terminal, preserving environment variables, working directory, and context. " +
            "Params: command (string), cwd (optional, string), env (optional, map), background (optional, boolean). " +
            "Supports chaining commands (e.g., 'cmd1 && cmd2'). Cross-platform compatible. " +
            "Returns: {ok: true, output, error, exitCode, cwd, command} or {ok: false, error, message}.",
    )
    fun runCommand(
        command: String,
        cwd: String? = null,
        env: Map<String, String>? = null,
        background: Boolean? = false,
    ): Map<String, Any?> {
        return try {
            val workDir =
                if (cwd != null) {
                    val dir = expandHome(cwd)
                    val abs = if (dir.isAbsolute) dir else this.cwd.resolve(dir)
                    val norm = abs.normalize().toAbsolutePath()
                    require(norm.startsWith(allowedRoot)) { "Path escapes allowed root: $norm" }
                    require(Files.exists(norm) && norm.isDirectory()) { "Directory not found: $norm" }
                    norm
                } else {
                    this.cwd
                }

            // Detect platform and use appropriate shell
            val os = System.getProperty("os.name").lowercase()
            val pb =
                when {
                    os.contains("windows") -> {
                        // Windows: use cmd.exe or powershell
                        ProcessBuilder("cmd.exe", "/c", command)
                    }
                    else -> {
                        // Unix-like systems: try to find available shell
                        val shell = findAvailableShell()
                        ProcessBuilder(shell, "-c", command)
                    }
                }

            pb.directory(workDir.toFile())

            // Inherit current environment and add custom env vars if provided
            if (env != null) {
                pb.environment().putAll(env)
            }

            pb.redirectErrorStream(false)
            val process = pb.start()

            if (background == true) {
                val pid = process.pid()
                backgroundProcesses[pid] = BackgroundProcess(process, command, workDir.toString())
                return mapOf(
                    "ok" to true,
                    "background" to true,
                    "pid" to pid,
                    "cwd" to workDir.toString(),
                    "command" to command,
                )
            }

            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            mapOf(
                "ok" to true,
                "output" to output,
                "error" to error,
                "exitCode" to exitCode,
                "cwd" to workDir.toString(),
                "command" to command,
            )
        } catch (e: Exception) {
            err("run_failed", "${e::class.simpleName}: ${e.message}")
        }
    }

    /**
     * Retrieves output from a background process started by runCommand.
     *
     * @param pid Process ID returned by runCommand with background=true
     * @return Map with current output, error streams, and exit code (if finished)
     *
     * @usage
     * ```
     * val result = runCommand("long-task", background = true)
     * val pid = result["pid"] as Long
     * getCommandOutput(pid)  // Check progress/output
     * ```
     *
     * @errors
     * - "not_found": No background process with given PID
     * - "output_failed": Error reading process streams
     */
    @Tool(
        "Get output of a background command started by runCommand. " +
            "Params: pid (long). " +
            "Returns: {ok: true, output, error, exitCode} or {ok: false, error, message}.",
    )
    fun getCommandOutput(pid: Long): Map<String, Any?> {
        return try {
            val processInfo = backgroundProcesses[pid] ?: return err("not_found", "No background process with PID $pid")
            val process = processInfo.process

            // Check if the process is still running
            val isAlive = process.isAlive
            if (!isAlive) {
                // If the process has finished, remove it from the map
                backgroundProcesses.remove(pid)
            }

            // Read output and error streams
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exitCode = if (isAlive) null else process.waitFor()

            mapOf(
                "ok" to true,
                "output" to output,
                "error" to error,
                "exitCode" to exitCode,
            )
        } catch (e: Exception) {
            err("output_failed", "${e::class.simpleName}: ${e.message}")
        }
    }

    /**
     * Lists all currently tracked background processes with their status and metadata.
     *
     * Automatically cleans up finished processes from the tracking list.
     *
     * @return Map with list of background processes and their details
     *
     * @usage
     * ```
     * listBackgroundProcesses()  // Shows all running/finished processes
     * // Returns: {processes: [{pid, command, cwd, status, startTime, runningTimeMs}]}
     * ```
     *
     * @errors
     * - "list_failed": Error accessing process information
     */
    @Tool(
        "List all background processes started by runCommand. " +
            "Returns: {ok: true, processes: [{pid, command, cwd, status, startTime}]} or {ok: false, error, message}.",
    )
    fun listBackgroundProcesses(): Map<String, Any?> =
        try {
            val processes =
                backgroundProcesses.map { (pid, processInfo) ->
                    val isAlive = processInfo.process.isAlive
                    val runningTime = System.currentTimeMillis() - processInfo.startTime
                    mapOf(
                        "pid" to pid,
                        "command" to processInfo.command,
                        "cwd" to processInfo.cwd,
                        "status" to if (isAlive) "running" else "finished",
                        "startTime" to processInfo.startTime,
                        "runningTimeMs" to runningTime,
                    )
                }

            // Clean up finished processes
            backgroundProcesses.entries.removeIf { !it.value.process.isAlive }

            mapOf(
                "ok" to true,
                "processes" to processes,
                "count" to processes.size,
            )
        } catch (e: Exception) {
            err("list_failed", "${e::class.simpleName}: ${e.message}")
        }

    /**
     * Searches for text content within files across a directory tree.
     *
     * Recursively searches by default, with case-insensitive matching and binary file filtering.
     * Supports file pattern filtering and configurable result limits.
     *
     * @param path Directory to search
     * @param query Text to search for (literal string, not regex)
     * @param recursive Search subdirectories (default: true)
     * @param filePattern Glob pattern to filter files (e.g., "\*.kt", "**\/\*.java")
     * @param caseSensitive Enable case-sensitive search (default: false)
     * @param maxResults Maximum matches to return (default: 100, max: 1000)
     * @param includeLineNumbers Include line numbers in results (default: true)
     * @return Map with matching lines, files, and search metadata
     *
     * @usage
     * ```
     * searchFileContent(".", "TODO")               // Find all TODO comments
     * searchFileContent("./src", "function", filePattern = "*.kt")
     * searchFileContent(".", "MyClass", caseSensitive = true)
     * searchFileContent(".", "import", recursive = false, maxResults = 50)
     * ```
     *
     * @errors
     * - "search_content_failed": Path validation, permission, or search errors
     * - Individual file read errors are silently skipped
     * - Binary files are automatically excluded
     */
    @Tool(
        "Search for text content within files in a directory. " +
            "Params: path (string), query (string), recursive (optional, default true), " +
            "filePattern (optional, glob pattern to filter files), caseSensitive (optional, default false), " +
            "maxResults (optional, default 100), includeLineNumbers (optional, default true). " +
            "Returns: {ok: true, matches: [{file, line, lineNumber, content}], count, searchPath} or {ok: false, error, message}.",
    )
    fun searchFileContent(
        path: String,
        query: String,
        recursive: Boolean? = true,
        filePattern: String? = null,
        caseSensitive: Boolean? = false,
        maxResults: Int? = 100,
        includeLineNumbers: Boolean? = true,
    ): Map<String, Any?> {
        return try {
            val dir = safeDir(path)
            val rec = recursive != false
            val sensitive = caseSensitive == true
            val maxRes = maxResults?.coerceIn(1, 1000) ?: 100
            val includeLines = includeLineNumbers != false

            val searchRegex =
                if (sensitive) {
                    Regex.fromLiteral(query)
                } else {
                    Regex(Pattern.quote(query), RegexOption.IGNORE_CASE)
                }

            val fileGlobMatcher =
                filePattern?.let {
                    dir.fileSystem.getPathMatcher("glob:$it")
                }

            val matches = mutableListOf<Map<String, Any?>>()
            val stream = if (rec) Files.walk(dir) else Files.list(dir)

            stream.use { s ->
                s
                    .filter { it.isRegularFile() }
                    .filter { fileGlobMatcher?.matches(it.fileName) != false }
                    .forEach fileLoop@{ file ->
                        if (matches.size >= maxRes) return@fileLoop

                        try {
                            // Skip binary files
                            val bytes = Files.readAllBytes(file)
                            if (looksBinary(bytes)) return@fileLoop

                            val content = bytes.toString(Charsets.UTF_8)
                            val lines = content.split('\n')

                            lines.forEachIndexed { index, line ->
                                if (matches.size >= maxRes) return@fileLoop

                                if (searchRegex.containsMatchIn(line)) {
                                    val matchInfo =
                                        mutableMapOf<String, Any?>(
                                            "file" to dir.relativize(file).toString(),
                                            "content" to line.trim(),
                                        )

                                    if (includeLines) {
                                        matchInfo["lineNumber"] = index + 1
                                    }

                                    matches += matchInfo
                                }
                            }
                        } catch (e: Exception) {
                            // Skip files that can't be read (permissions, etc.)
                            return@fileLoop
                        }
                    }
            }

            mapOf(
                "ok" to true,
                "matches" to matches,
                "count" to matches.size,
                "searchPath" to dir.toString(),
                "query" to query,
                "caseSensitive" to sensitive,
            )
        } catch (e: Exception) {
            err("search_content_failed", "${e::class.simpleName}: ${e.message}")
        }
    }

    private fun findAvailableShell(): String {
        // Check for available shells in order of preference
        val shells = listOf("/bin/zsh", "/bin/bash", "/bin/sh")
        for (shell in shells) {
            if (Files.exists(Paths.get(shell))) {
                return shell
            }
        }
        // Fallback to system default
        return "/bin/sh"
    }

    private fun parseCursor(cursor: String?): Int = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    private fun isHidden(p: Path): Boolean =
        try {
            Files.isHidden(p) || p.fileName?.toString()?.startsWith(".") == true
        } catch (_: Exception) {
            false
        }

    private fun looksBinary(
        bytes: ByteArray,
        sample: Int = 4096,
    ): Boolean {
        val n = min(bytes.size, sample)
        var control = 0
        for (i in 0 until n) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0x00) return true
            if (b < 0x09 || (b in 0x0E..0x1F)) control++
        }
        return control > n / 10
    }

    private fun err(
        code: String,
        message: String,
    ): Map<String, Any?> = mapOf("ok" to false, "error" to code, "message" to message)

    private fun humanReadable(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB")
        var b = bytes.toDouble()
        var i = 0
        while (b >= 1024 && i < units.lastIndex) {
            b /= 1024.0
            i++
        }
        val fmt = if (i == 0) "%.0f %s" else "%.2f %s"
        return String.format(Locale.US, fmt, b, units[i])
    }

    private fun normalizeExtensions(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        var s = raw.trim()

        // Strip one layer of surrounding quotes if present
        if ((s.startsWith('"') && s.endsWith('"')) || (s.startsWith('\'') && s.endsWith('\''))) {
            s = s.substring(1, s.length - 1).trim()
        }
        // If bracketed like [pdf, "png"] or ['pdf'] or [pdf], strip the brackets
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length - 1).trim()
        }

        // Replace single quotes, then split by comma or whitespace
        s = s.replace('\'', '"')

        val tokens =
            s
                .split(Regex("[,\\s]+"))
                .map { it.trim().trim('"') }
                .filter { it.isNotBlank() }

        return tokens.map { it.lowercase().removePrefix(".") }.toSet()
    }
}

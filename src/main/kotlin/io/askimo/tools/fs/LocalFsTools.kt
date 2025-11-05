/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.tools.fs

import dev.langchain4j.agent.tool.Tool
import io.askimo.core.util.AskimoHome
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

object LocalFsTools {
    private var allowedRoot: Path = AskimoHome.userHome().toAbsolutePath().normalize()
    private var cwd: Path = Paths.get("").toAbsolutePath().normalize()
    private val backgroundProcesses = mutableMapOf<Long, BackgroundProcess>()

    private data class BackgroundProcess(
        val process: Process,
        val command: String,
        val cwd: String,
        val startTime: Long = System.currentTimeMillis(),
    )

    private fun expandHome(raw: String): Path = AskimoHome.expandTilde(raw)

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

    private val categoryExts: Map<String, Set<String>> =
        mapOf(
            "video" to setOf("mp4", "mkv", "mov", "avi", "wmv", "flv", "webm", "m4v"),
            "image" to setOf("jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff", "webp", "svg", "heic", "heif"),
            "audio" to setOf("mp3", "wav", "flac", "aac", "m4a", "ogg", "wma", "aiff"),
            "doc" to setOf("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "md", "rtf"),
            "archive" to setOf("zip", "tar", "gz", "tgz", "bz2", "7z", "rar"),
        )

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
     * Enhanced file search using flexible pattern matching for intelligent file discovery.
     *
     * @param path Directory to search
     * @param glob Search term, filename, or glob pattern - automatically expanded for better discovery
     * @param recursive Search subdirectories (default: true for better discovery)
     * @param includeHidden Include hidden files (default: false)
     * @param smartMatch Enable intelligent pattern expansion (default: true)
     * @return Map with matching files, relevance scores, and search metadata
     *
     * @usage
     * ```
     * searchFilesByGlob(".", "PgVectorIndexer")           // Find any file containing "PgVectorIndexer"
     * searchFilesByGlob(".", "SessionManager")            // Find files with "SessionManager" in name
     * searchFilesByGlob(".", "user-config")               // Find files with "user-config" pattern
     * searchFilesByGlob(".", "*.md", smartMatch = false)  // Traditional glob pattern
     * searchFilesByGlob(".", "database")                  // Find files related to "database"
     * ```
     *
     * @errors
     * - "search_failed": Path validation, permission, or pattern errors
     */
    @Tool(
        "Search for files by name/pattern with smart matching. " +
            "Params: path, glob, recursive?(true), includeHidden?(false), smartMatch?(true). " +
            "Returns: {ok, files, count, patterns, searchPath}",
    )
    fun searchFilesByGlob(
        path: String,
        glob: String,
        recursive: Boolean? = true,
        includeHidden: Boolean? = false,
        smartMatch: Boolean? = true,
    ): Map<String, Any?> {
        return try {
            val dir = safeDir(path)
            val rec = recursive != false
            val include = includeHidden == true
            val smart = smartMatch != false

            val patterns = if (smart) {
                generateIntelligentPatterns(glob)
            } else {
                listOf(glob)
            }

            val allFiles = mutableMapOf<String, Double>() // file -> relevance score

            patterns.forEachIndexed { index, pattern ->
                val matcher = try {
                    dir.fileSystem.getPathMatcher("glob:$pattern")
                } catch (e: Exception) {
                    // If pattern is invalid as glob, treat as literal filename
                    try {
                        dir.fileSystem.getPathMatcher("glob:*$pattern*")
                    } catch (e2: Exception) {
                        // Skip this pattern if it can't be processed
                        return@forEachIndexed
                    }
                }

                val stream = if (rec) Files.walk(dir) else Files.list(dir)
                stream.use { s ->
                    s
                        .filter { it != null && Files.isRegularFile(it) }
                        .forEach { file ->
                            try {
                                val rel = dir.relativize(file)
                                val fileName = file.fileName?.toString() ?: ""

                                if (fileName.isEmpty()) return@forEach
                                if (!include && isHidden(file)) return@forEach

                                val matches = when {
                                    // Exact pattern match
                                    matcher.matches(rel) || matcher.matches(file.fileName) -> true
                                    // Filename contains the search term (case insensitive)
                                    fileName.contains(glob, ignoreCase = true) -> true
                                    // Path contains the search term
                                    rel.toString().contains(glob, ignoreCase = true) -> true
                                    else -> false
                                }

                                if (matches) {
                                    val relevanceScore = calculateRelevance(fileName, rel.toString(), glob, index)
                                    val existing = allFiles[rel.toString()]
                                    if (existing == null || relevanceScore > existing) {
                                        allFiles[rel.toString()] = relevanceScore
                                    }
                                }
                            } catch (e: Exception) {
                                // Skip this file if there's an error processing it
                            }
                        }
                }
            }

            // Sort by relevance score (higher is better)
            val sortedFiles = allFiles.entries
                .sortedByDescending { it.value }
                .map { it.key }

            mapOf(
                "ok" to true,
                "path" to dir.toString(),
                "searchPath" to dir.toString(),
                "glob" to glob,
                "patterns" to patterns,
                "files" to sortedFiles,
                "count" to sortedFiles.size,
                "smartMatch" to smart,
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

            if (isAlive) {
                // Process is still running, return empty output
                mapOf(
                    "ok" to true,
                    "output" to "",
                    "error" to "",
                    "exitCode" to null,
                )
            } else {
                // Process has finished, read output and clean up
                backgroundProcesses.remove(pid)

                val output = try {
                    process.inputStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    ""
                }

                val error = try {
                    process.errorStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    ""
                }

                val exitCode = try {
                    process.waitFor()
                } catch (e: Exception) {
                    -1 // Default exit code if we can't get the real one
                }

                mapOf(
                    "ok" to true,
                    "output" to output,
                    "error" to error,
                    "exitCode" to exitCode,
                )
            }
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
    fun listBackgroundProcesses(): Map<String, Any?> = try {
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

    @Tool("Write text file to path")
    fun writeFile(
        path: String,
        content: String,
    ): String {
        val p = Paths.get(path)
        Files.createDirectories(p.toAbsolutePath().parent)
        Files.writeString(p, content)
        return "wrote:\n${p.toAbsolutePath()}"
    }

    @Tool("Read text file from path")
    fun readFile(path: String): String {
        val p = Paths.get(path)
        if (!Files.exists(p)) {
            return "Error: File not found at $path"
        }
        if (!Files.isRegularFile(p)) {
            return "Error: Path is not a regular file: $path"
        }
        return Files.readString(p)
    }

    /**
     * TEST-ONLY: Set allowedRoot and cwd for test purposes.
     */
    fun setTestRoot(root: Path) {
        allowedRoot = root.toAbsolutePath().normalize()
        cwd = root.toAbsolutePath().normalize()
    }

    /**
     * TEST-ONLY: Clean up all background processes for test cleanup.
     * This is important on Windows to prevent file handle leaks.
     */
    fun cleanupBackgroundProcesses() {
        backgroundProcesses.values.forEach { processInfo ->
            try {
                val process = processInfo.process
                if (process.isAlive) {
                    // First try graceful termination
                    process.destroy()
                    // Wait a bit for graceful shutdown
                    val terminated = process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)
                    if (!terminated) {
                        // Force kill if it doesn't terminate gracefully
                        process.destroyForcibly()
                    }
                }
                // Close all streams to release file handles
                try {
                    process.inputStream.close()
                } catch (e: Exception) { /* ignore */ }
                try {
                    process.errorStream.close()
                } catch (e: Exception) { /* ignore */ }
                try {
                    process.outputStream.close()
                } catch (e: Exception) { /* ignore */ }
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
        backgroundProcesses.clear()
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

    private fun isHidden(p: Path): Boolean = try {
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

    /**
     * Generate intelligent search patterns for better file discovery
     */
    private fun generateIntelligentPatterns(searchTerm: String): List<String> {
        val patterns = mutableListOf<String>()

        // If it's already a glob pattern, use as-is
        if (searchTerm.contains('*') || searchTerm.contains('?') || searchTerm.contains('[')) {
            patterns.add(searchTerm)
            return patterns
        }

        // Strategy 1: Exact filename with any extension
        patterns.add(searchTerm) // Exact name (could be directory)
        patterns.add("$searchTerm.*") // With any extension

        // Strategy 2: Partial matches
        patterns.add("*$searchTerm*") // Contains anywhere
        patterns.add("$searchTerm*") // Starts with
        patterns.add("*$searchTerm") // Ends with

        // Strategy 3: Case variations and common separators
        val variations = generateNameVariations(searchTerm)
        variations.forEach { variation ->
            patterns.add(variation)
            patterns.add("$variation.*")
            patterns.add("*$variation*")
        }

        // Strategy 4: Directory-based patterns
        patterns.add("**/$searchTerm") // In any subdirectory
        patterns.add("**/$searchTerm.*") // File in any subdirectory
        patterns.add("**/*$searchTerm*") // Partial match in any subdirectory

        return patterns.distinct()
    }

    /**
     * Generate naming convention variations
     */
    private fun generateNameVariations(term: String): List<String> {
        val variations = mutableListOf<String>()

        // Original term
        variations.add(term)

        // Convert CamelCase to snake_case
        if (term.matches(Regex(".*[A-Z].*"))) {
            val snakeCase = term.replace(Regex("([a-z])([A-Z])")) { match ->
                "${match.groupValues[1]}_${match.groupValues[2].lowercase()}"
            }.lowercase()
            variations.add(snakeCase)
        }

        // Convert snake_case to CamelCase
        if (term.contains('_')) {
            val camelCase = term.split('_')
                .mapIndexed { index, part ->
                    if (index == 0) {
                        part.lowercase()
                    } else {
                        part.lowercase().replaceFirstChar { it.uppercase() }
                    }
                }
                .joinToString("")
            variations.add(camelCase)

            // Also try PascalCase
            val pascalCase = term.split('_')
                .joinToString("") { part ->
                    part.lowercase().replaceFirstChar { it.uppercase() }
                }
            variations.add(pascalCase)
        }

        // Convert kebab-case variations
        if (term.contains('-')) {
            val camelCase = term.split('-')
                .mapIndexed { index, part ->
                    if (index == 0) {
                        part.lowercase()
                    } else {
                        part.lowercase().replaceFirstChar { it.uppercase() }
                    }
                }
                .joinToString("")
            variations.add(camelCase)

            val snakeCase = term.replace('-', '_')
            variations.add(snakeCase)
        }

        return variations.distinct()
    }

    /**
     * Calculate relevance score for search results
     */
    private fun calculateRelevance(
        fileName: String,
        filePath: String,
        searchTerm: String,
        patternIndex: Int,
    ): Double {
        var score = 1.0

        // Exact filename match gets highest score
        if (fileName.equals(searchTerm, ignoreCase = true) ||
            fileName.equals("$searchTerm.*".replace(".*", ""), ignoreCase = true)
        ) {
            score += 5.0
        }

        // Filename starts with search term
        if (fileName.startsWith(searchTerm, ignoreCase = true)) {
            score += 3.0
        }

        // Filename contains search term
        if (fileName.contains(searchTerm, ignoreCase = true)) {
            score += 2.0
        }

        // Earlier patterns get higher scores (more specific patterns first)
        score += (10 - patternIndex) * 0.1

        // Prefer files in root directory over deeply nested ones
        val depth = filePath.count { it == '/' }
        score += (10 - depth) * 0.05

        // Prefer certain file extensions (customizable)
        val extension = fileName.substringAfterLast('.', "").lowercase()
        when (extension) {
            "kt", "java", "py", "js", "ts", "rb" -> score += 0.5 // Source code
            "md", "txt", "rst" -> score += 0.3 // Documentation
            "json", "yml", "yaml", "xml" -> score += 0.2 // Config
        }

        return score
    }
}

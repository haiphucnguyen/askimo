package io.askimo.tools.fs

import dev.langchain4j.agent.tool.Tool
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.math.min

class LocalFsTools(
    private val allowedRoot: Path = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize(),
    private val cwd: Path = Paths.get("").toAbsolutePath().normalize(),
) {
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

    @Tool(
        """List or count files in a directory by type. Use either 'category' (video|image|audio|doc|archive) OR 'extensions' (e.g., ['pdf','png']).
                Parameters: path, category?, extensions?, recursive?(false), limit?(200), cursor?
                Returns: {count, files, nextCursor, directory}
                """,
    )
    fun filesByType(
        path: String,
        category: String? = null,
        extensions: List<String>? = null,
        recursive: Boolean? = false,
        limit: Int? = 200,
        cursor: String? = null,
    ): Map<String, Any?> {
        val dir = safeDir(path)
        val extSet: Set<String> =
            when {
                !category.isNullOrBlank() ->
                    categoryExts[category.lowercase()]
                        ?: error("Unknown category: $category")
                !extensions.isNullOrEmpty() -> extensions.map { it.lowercase().removePrefix(".") }.toSet()
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

    @Tool(
        "Compute total byte size of files filtered by type. " +
            "Use either 'extensions' (e.g., ['pdf']) or 'category' (video|image|audio|doc|archive). " +
            "If both are provided, EXTENSIONS TAKE PRECEDENCE. " +
            "Params: path, extensions?, category?, recursive?(false). " +
            "Returns: {count, bytes, human, matchedExtensions, directory}",
    )
    fun totalSizeByType(
        path: String,
        extensions: List<String>? = null,
        category: String? = null,
        recursive: Boolean? = false,
    ): Map<String, Any> {
        val dir = safeDir(path)
        val extSet: Set<String> =
            when {
                !extensions.isNullOrEmpty() ->
                    extensions
                        .map { it.trim().lowercase(Locale.ROOT).removePrefix(".") }
                        .filter { it.isNotBlank() }
                        .toSet()
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
}

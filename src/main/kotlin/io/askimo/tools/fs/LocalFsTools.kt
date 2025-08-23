package io.askimo.tools.fs

import dev.langchain4j.agent.tool.Tool
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

class LocalFsTools(
    private val allowedRoot: Path = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize(),
) {
    private fun expandHome(raw: String): Path {
        val home = System.getProperty("user.home")
        val expanded = raw.replace(Regex("^~(?=/|$)"), home)
        return Paths.get(expanded)
    }

    private fun safeDir(raw: String): Path {
        val dir = expandHome(raw).toAbsolutePath().normalize()
        require(dir.startsWith(allowedRoot)) { "Path escapes allowed root: $dir" }
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
     * Cursor format (opaque to the model):
     *   "<startIndex>" — an integer as a string, 0-based.
     */
    private fun parseCursor(cursor: String?): Int = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    @Tool(
        "List or count files in a directory by type. " +
            "Use either 'category' (video|image|audio|doc|archive) OR 'extensions' (e.g., ['pdf','png']). " +
            "Parameters: path (string), category (optional), extensions (optional list), recursive (optional, default false), " +
            "limit (optional, default 200), cursor (optional, for pagination). " +
            "Returns: {count: number, files: [names...], nextCursor: string|null, directory: string}",
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

        // Collect all matches (for accurate count), then page results.
        // If listing huge trees, consider an index later; for now, correctness first.
        val allMatches = mutableListOf<String>()
        val stream = if (recursive == true) Files.walk(dir) else Files.list(dir)
        stream.use { s ->
            s
                .filter { it.isRegularFile() }
                .forEach { p ->
                    val name = p.fileName.toString()
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in extSet) {
                        // Return names relative to the requested directory for clarity
                        allMatches += dir.relativize(p).toString()
                    }
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

        // EXTENSIONS FIRST (fix)
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
                    try {
                        bytes += Files.size(p)
                    } catch (_: Exception) {
                        // skip unreadable
                    }
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

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val fs = LocalFsTools()

            // quick tests — adjust as you like:
            println("== filesByType PDFs in ~/Downloads (recursive, first 20) ==")
            val list =
                fs.filesByType(
                    path = "~/Downloads",
                    extensions = listOf("pdf"),
                    recursive = true,
                    limit = 20,
                )
            println(list)

            println("\n== totalSizeByType PDFs in ~/Downloads (non-recursive) ==")
            val size =
                fs.totalSizeByType(
                    path = "~/Downloads",
                    extensions = listOf("pdf"),
                    recursive = false,
                )
            println(size)
        }
    }
}

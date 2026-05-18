/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Askimo
 */
package io.askimo.core.util

import java.net.URL
import java.nio.file.FileSystem
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

/**
 * Formats file size in human-readable format.
 *
 * @param bytes The file size in bytes
 * @return Formatted string (e.g., "1.5 KB", "2 MB")
 */
fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${String.format("%.1f", bytes / (1024.0 * 1024))} MB"
}

/**
 * Walks a classpath resource directory and invokes [action] for each matching file.
 *
 * Works in both exploded (IDE / `gradlew run`) and JAR layouts.
 *
 * @param resourceUrl  URL returned by `Class.getResource("/some/dir/")`, or null (no-op).
 * @param jarPath      Path inside the JAR to walk, e.g. `"/directives/"`.
 * @param extensions   File extensions to include (without the dot, e.g. `"yml"`).
 *                     Pass none to include every regular file.
 * @param action       Called for each matching [Path], in sorted order.
 */
fun walkResourceDirectory(
    resourceUrl: URL?,
    jarPath: String,
    vararg extensions: String,
    action: (Path) -> Unit,
) {
    if (resourceUrl == null) return

    fun matches(path: Path): Boolean = Files.isRegularFile(path) &&
        (extensions.isEmpty() || extensions.any { path.fileName.toString().endsWith(".$it") })

    val uri = resourceUrl.toURI()
    if (uri.scheme == "jar") {
        var ownedFs: FileSystem? = null
        val fs = try {
            FileSystems.getFileSystem(uri)
        } catch (_: FileSystemNotFoundException) {
            FileSystems.newFileSystem(uri, emptyMap<String, Any>()).also { ownedFs = it }
        }
        ownedFs.use { _ ->
            Files.walk(fs.getPath(jarPath))
                .filter { matches(it) }
                .sorted()
                .forEach(action)
        }
    } else {
        Files.walk(Path.of(uri))
            .filter { matches(it) }
            .sorted()
            .forEach(action)
    }
}

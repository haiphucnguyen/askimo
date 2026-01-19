/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.util

import androidx.compose.ui.platform.UriHandler
import io.askimo.core.i18n.LocalizationManager
import io.askimo.core.logging.logger
import java.awt.Desktop
import java.io.File
import java.net.URI

/**
 * Custom URI handler for desktop that properly handles file:// URLs.
 * This ensures that file links work correctly across different file types and paths.
 * Falls back to internal viewer if system cannot open the file.
 */
class CustomUriHandler(
    private val onShowFileViewer: ((title: String, filePath: String, content: String) -> Unit)? = null,
    private val onShowError: ((title: String, message: String) -> Unit)? = null,
) : UriHandler {
    private val log = logger<CustomUriHandler>()

    override fun openUri(uri: String) {
        try {
            when {
                uri.startsWith("file://") -> {
                    // Extract the file path from the URI
                    val fileUri = URI(uri)
                    val file = File(fileUri.path)

                    if (!file.exists()) {
                        // Show error dialog for non-existent files
                        log.warn("File does not exist: ${file.absolutePath}")
                        onShowError?.invoke(
                            "File Not Found",
                            "The file does not exist:\n\n${file.absolutePath}",
                        )
                        return
                    }

                    if (!file.isFile) {
                        log.warn("Path is not a file: ${file.absolutePath}")
                        onShowError?.invoke(
                            "Invalid File",
                            "The path is not a file:\n\n${file.absolutePath}",
                        )
                        return
                    }

                    // Try to open with system default application
                    var openedSuccessfully = false
                    if (Desktop.isDesktopSupported()) {
                        val desktop = Desktop.getDesktop()

                        if (desktop.isSupported(Desktop.Action.OPEN)) {
                            try {
                                desktop.open(file)
                                log.debug("Opened file: ${file.absolutePath}")
                                openedSuccessfully = true
                            } catch (e: Exception) {
                                log.warn("Failed to open file with system default: ${file.absolutePath}", e)
                                // Fall through to show internal viewer
                            }
                        }
                    }

                    // Fall back to internal viewer if system open failed
                    if (!openedSuccessfully) {
                        try {
                            // Check file size (limit to 10MB for internal viewer)
                            val maxSize = 10 * 1024 * 1024 // 10MB
                            if (file.length() > maxSize) {
                                onShowError?.invoke(
                                    "File Too Large",
                                    "The file is too large to view internally (${file.length() / 1024 / 1024} MB).\n\n" +
                                        "Please open it with an external application:\n${file.absolutePath}",
                                )
                                return
                            }

                            // Try to read file content
                            val content = file.readText()
                            val fileName = file.name
                            log.info("Opening file in internal viewer: ${file.absolutePath}")
                            val title = LocalizationManager.getString("file.viewer.title.with.name", fileName)
                            onShowFileViewer?.invoke(title, file.absolutePath, content)
                        } catch (e: Exception) {
                            log.error("Failed to read file: ${file.absolutePath}", e)
                            onShowError?.invoke(
                                "Cannot Read File",
                                "Unable to read the file:\n\n${file.absolutePath}\n\nError: ${e.message}",
                            )
                        }
                    }
                }
                uri.startsWith("http://") || uri.startsWith("https://") -> {
                    // Open web URLs in the default browser
                    if (Desktop.isDesktopSupported()) {
                        val desktop = Desktop.getDesktop()
                        if (desktop.isSupported(Desktop.Action.BROWSE)) {
                            desktop.browse(URI(uri))
                            log.debug("Opened URL in browser: $uri")
                        }
                    }
                }
                else -> {
                    log.warn("Unsupported URI scheme: $uri")
                }
            }
        } catch (e: Exception) {
            log.error("Failed to open URI: $uri", e)
            onShowError?.invoke(
                "Error Opening Link",
                "Failed to open:\n\n$uri\n\nError: ${e.message}",
            )
        }
    }
}

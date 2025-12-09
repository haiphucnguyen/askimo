/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.util

import io.askimo.core.logging.logger
import org.apache.tika.Tika
import org.apache.tika.exception.TikaException
import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.sax.BodyContentHandler
import java.io.File
import java.io.FileInputStream

/**
 * Utility for extracting text content from various file types.
 * Uses Apache Tika to detect actual file content types (not just extensions).
 * Supports text-based files, PDF, DOCX, and more.
 */
object FileContentExtractor {

    private val log = logger<FileContentExtractor>()

    private val tika = Tika()
    private val parser = AutoDetectParser()

    /**
     * Extract text content from a file.
     * Supports text files, PDF, DOCX, and other formats supported by Tika.
     *
     * @param file The file to extract content from
     * @return The extracted text content
     * @throws Exception if the file cannot be read or the format is unsupported
     */
    fun extractContent(file: File): String {
        val mimeType = detectMimeType(file)

        return when {
            mimeType.startsWith("application/pdf") ||
                mimeType.contains("word") ||
                mimeType.contains("wordprocessingml") ||
                mimeType.contains("msword") -> {
                extractUsingTika(file)
            }
            // Plain text files - read directly for better performance
            mimeType.startsWith("text/") ||
                mimeType in SUPPORTED_APPLICATION_TYPES -> {
                file.readText()
            }
            else -> throw UnsupportedOperationException("Cannot extract content from: $mimeType")
        }
    }

    /**
     * Extract content using Tika parser (for PDFs, DOCX, etc.).
     */
    private fun extractUsingTika(file: File): String = try {
        FileInputStream(file).use { stream ->
            val handler = BodyContentHandler(-1) // -1 = no character limit
            val metadata = Metadata()
            parser.parse(stream, handler, metadata)
            handler.toString().trim()
        }
    } catch (e: TikaException) {
        throw Exception("Failed to parse file: ${e.message}", e)
    }

    /**
     * Check if a file type is supported for content extraction.
     * Uses content-type detection (magic bytes) instead of file extension.
     *
     * @param file The file to check
     * @return true if the file type is supported, false otherwise
     */
    fun isSupported(file: File): Boolean {
        val mimeType = detectMimeType(file)
        return isSupportedMimeType(mimeType)
    }

    /**
     * Overload for backward compatibility - detects from file path.
     *
     * @param fileName The name of the file (with extension)
     * @return true if the file type is supported, false otherwise
     */
    fun isSupported(fileName: String): Boolean {
        val file = File(fileName)
        return if (file.exists()) {
            isSupported(file)
        } else {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            extension in SUPPORTED_TEXT_EXTENSIONS || extension in SUPPORTED_BINARY_EXTENSIONS
        }
    }

    /**
     * Detect MIME type using Tika (reads file content, not just extension).
     */
    private fun detectMimeType(file: File): String = try {
        tika.detect(file)
    } catch (e: Exception) {
        log.warn("Failed to detect MIME type for file: ${e.message}", e)
        "application/octet-stream"
    }

    /**
     * Check if a MIME type represents supported content.
     */
    private fun isSupportedMimeType(mimeType: String): Boolean = when {
        mimeType.startsWith("text/") -> true
        mimeType in SUPPORTED_APPLICATION_TYPES -> true
        mimeType.startsWith("application/pdf") -> true
        mimeType.contains("word") || mimeType.contains("wordprocessingml") || mimeType.contains("msword") -> true
        else -> false
    }

    /**
     * Get a user-friendly message for unsupported file types.
     */
    fun getUnsupportedMessage(file: File): String {
        val mimeType = detectMimeType(file)
        return when {
            mimeType.startsWith("image/") -> "Image OCR support coming soon"
            mimeType.contains("spreadsheet") || mimeType.contains("excel") -> "Excel support coming soon"
            mimeType.contains("presentation") || mimeType.contains("powerpoint") -> "PowerPoint support coming soon"
            else -> "Unsupported file type: $mimeType"
        }
    }

    // Application MIME types that contain text
    private val SUPPORTED_APPLICATION_TYPES = setOf(
        "application/json",
        "application/xml",
        "application/javascript",
        "application/typescript",
        "application/x-sh",
        "application/x-shellscript",
        "application/sql",
        "application/x-yaml",
        "application/x-httpd-php",
        "application/rtf",
        "application/x-tex",
    )

    // Fallback for extension-based check when file doesn't exist
    private val SUPPORTED_TEXT_EXTENSIONS = setOf(
        // Plain text
        "txt", "text",
        // Markdown
        "md", "markdown",
        // Programming languages
        "kt", "kts", "java", "py", "js", "ts", "jsx", "tsx",
        "c", "cpp", "h", "hpp", "cs", "go", "rs", "rb", "php",
        "swift", "m", "mm", "scala", "groovy", "clj", "ex", "exs",
        // Web
        "html", "htm", "css", "scss", "sass", "less",
        // Data formats
        "json", "xml", "yaml", "yml", "toml", "ini", "conf", "config",
        "csv", "tsv",
        // Shell scripts
        "sh", "bash", "zsh", "fish", "bat", "ps1",
        // Documentation
        "rst", "adoc", "tex", "rtf",
        // Other
        "sql", "log", "properties", "env",
    )

    private val SUPPORTED_BINARY_EXTENSIONS = setOf(
        "pdf",
        "docx",
        "doc",
    )
}

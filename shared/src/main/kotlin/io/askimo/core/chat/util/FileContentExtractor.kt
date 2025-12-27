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
     * Supports text files, PDF, DOCX, XLSX, PPTX, OpenDocument, emails, and other formats supported by Tika.
     *
     * @param file The file to extract content from
     * @return The extracted text content
     * @throws Exception if the file cannot be read or the format is unsupported
     */
    fun extractContent(file: File): String {
        val mimeType = detectMimeType(file)

        return when {
            // Documents
            mimeType.startsWith("application/pdf") ||
                mimeType.contains("word") ||
                mimeType.contains("wordprocessingml") ||
                mimeType.contains("msword") ||
                mimeType.contains("spreadsheet") ||
                mimeType.contains("excel") ||
                mimeType.contains("ms-excel") ||
                // Presentations
                mimeType.contains("presentation") ||
                mimeType.contains("powerpoint") ||
                mimeType.contains("ms-powerpoint") ||
                // OpenDocument formats
                mimeType.startsWith("application/vnd.oasis.opendocument") ||
                // Email formats
                mimeType.contains("message/rfc822") ||
                mimeType.contains("application/vnd.ms-outlook") ||
                // RTF
                mimeType.contains("rtf") -> {
                extractUsingTika(file)
            }
            // Plain text files (includes CSV, TSV, etc.)
            mimeType.startsWith("text/") ||
                mimeType in SUPPORTED_APPLICATION_TYPES -> {
                file.readText()
            }
            // Fallback: Extension-based check for files Tika misdetects
            // This handles .md, .gradle.kts, .gitignore, and other text files
            mimeType == "application/octet-stream" ||
                mimeType.startsWith("application/x-") -> {
                val extension = file.extension.lowercase()
                if (extension in SUPPORTED_TEXT_EXTENSIONS) {
                    file.readText()
                } else if (extension in SUPPORTED_BINARY_EXTENSIONS) {
                    extractUsingTika(file)
                } else {
                    throw UnsupportedOperationException("Cannot extract content from: $mimeType (extension: .$extension)")
                }
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
        throw Exception("Failed to parse file: ${file.path}", e)
    }

    /**
     * Check if a file type is supported for content extraction.
     * Uses content-type detection (magic bytes) with extension-based fallback.
     *
     * @param file The file to check
     * @return true if the file type is supported, false otherwise
     */
    fun isSupported(file: File): Boolean {
        val mimeType = detectMimeType(file)

        // First, check if MIME type is supported
        if (isSupportedMimeType(mimeType)) {
            return true
        }

        // Fallback: Check file extension for text files that Tika misdetects
        // This handles cases like .md, .gradle.kts, .gitignore, etc.
        if (mimeType == "application/octet-stream" ||
            mimeType.startsWith("application/x-") ||
            mimeType == "text/plain"
        ) {
            val extension = file.extension.lowercase()
            return extension in SUPPORTED_TEXT_EXTENSIONS ||
                extension in SUPPORTED_BINARY_EXTENSIONS
        }

        return false
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
        mimeType.contains("spreadsheet") || mimeType.contains("excel") || mimeType.contains("ms-excel") -> true
        mimeType.contains("presentation") || mimeType.contains("powerpoint") || mimeType.contains("ms-powerpoint") -> true
        mimeType.startsWith("application/vnd.oasis.opendocument") -> true
        mimeType.contains("message/rfc822") || mimeType.contains("application/vnd.ms-outlook") -> true
        mimeType.contains("rtf") -> true
        else -> false
    }

    /**
     * Get a user-friendly message for unsupported file types.
     */
    fun getUnsupportedMessage(file: File): String {
        val mimeType = detectMimeType(file)
        return when {
            mimeType.startsWith("image/") -> "Image OCR support coming soon"
            mimeType.startsWith("video/") -> "Video files are not supported"
            mimeType.startsWith("audio/") -> "Audio files are not supported"
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
        "rst", "adoc", "tex",
        // Other
        "sql", "log", "properties", "env",
    )

    private val SUPPORTED_BINARY_EXTENSIONS = setOf(
        // PDF
        "pdf",
        // Microsoft Office
        "docx", "doc",
        "xlsx", "xls",
        "pptx", "ppt",
        // OpenDocument
        "odt", "ods", "odp",
        // Email
        "eml", "msg",
        // Rich Text
        "rtf",
    )
}

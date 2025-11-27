/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

import java.io.BufferedWriter
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Service for exporting chat session history to various file formats.
 *
 * This service handles exporting entire chat sessions including all messages
 * and metadata using cursor-based pagination to efficiently handle large sessions.
 */
class ChatSessionExporterService(
    private val repository: ChatSessionRepository = ChatSessionRepository(),
) {
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * Export a chat session to a Markdown file.
     *
     * Uses streaming approach to write messages to file as they are loaded from pagination,
     * avoiding loading all messages into memory at once.
     *
     * @param sessionId The ID of the session to export
     * @param filename The path to the output file
     * @return Result indicating success or failure with error message
     */
    fun exportToMarkdown(sessionId: String, filename: String): Result<Unit> {
        return try {
            val session = repository.getSession(sessionId)
                ?: return Result.failure(Exception("Session not found: $sessionId"))

            val file = File(filename)
            file.parentFile?.mkdirs()

            file.bufferedWriter().use { writer ->
                writeHeader(writer, session)
                streamMessagesToFile(writer, sessionId)
                writeFooter(writer)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to export session: ${e.message}", e))
        }
    }

    /**
     * Write the header section of the markdown file.
     *
     * @param writer The buffered writer to write to
     * @param session The chat session metadata
     */
    private fun writeHeader(writer: BufferedWriter, session: ChatSession) {
        writer.appendLine("# Chat Session: ${session.title}")
        writer.appendLine()
        writer.appendLine("**Session ID**: ${session.id}")
        writer.appendLine("**Created**: ${session.createdAt.format(timestampFormatter)}")
        writer.appendLine("**Last Updated**: ${session.updatedAt.format(timestampFormatter)}")
        if (session.directiveId != null) {
            writer.appendLine("**Directive**: ${session.directiveId}")
        }
        writer.appendLine()
        writer.appendLine("---")
        writer.appendLine()
    }

    /**
     * Stream messages to file using cursor-based pagination.
     * Writes each batch of messages immediately without storing all in memory.
     *
     * @param writer The buffered writer to write to
     * @param sessionId The ID of the session
     */
    private fun streamMessagesToFile(writer: BufferedWriter, sessionId: String) {
        var cursor: LocalDateTime? = null
        val pageSize = 100 // Load 100 messages at a time
        var messageCounter = 0

        do {
            val (messages, nextCursor) = repository.getMessagesPaginated(
                sessionId = sessionId,
                limit = pageSize,
                cursor = cursor,
                direction = PaginationDirection.FORWARD,
            )

            messages.forEach { message ->
                messageCounter++
                writeMessage(writer, message, messageCounter)
            }

            cursor = nextCursor
        } while (nextCursor != null)

        totalMessageCount = messageCounter
    }

    /**
     * Write a single message to the file.
     *
     * @param writer The buffered writer to write to
     * @param message The message to write
     * @param index The message number (1-based)
     */
    private fun writeMessage(writer: BufferedWriter, message: ChatMessage, index: Int) {
        writer.appendLine("## Message $index")
        writer.appendLine("**Role**: ${message.role.value.uppercase()}")
        writer.appendLine("**Timestamp**: ${message.createdAt!!.format(timestampFormatter)}")
        writer.appendLine()
        writer.appendLine(message.content)
        writer.appendLine()
        writer.appendLine("---")
        writer.appendLine()
    }

    /**
     * Write the footer section of the markdown file.
     *
     * @param writer The buffered writer to write to
     */
    private fun writeFooter(writer: BufferedWriter) {
        val exportTime = LocalDateTime.now().format(timestampFormatter)
        writer.appendLine("[End of chat session - Total messages: $totalMessageCount]")
        writer.appendLine()
        writer.appendLine("*Exported on: $exportTime*")
    }

    private var totalMessageCount = 0
}

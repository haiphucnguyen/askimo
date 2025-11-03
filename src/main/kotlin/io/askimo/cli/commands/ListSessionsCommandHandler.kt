/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.session.ChatSession
import io.askimo.core.session.ChatSessionRepository
import io.askimo.core.util.Logger.info
import org.jline.reader.ParsedLine
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.min

class ListSessionsCommandHandler : CommandHandler {
    override val keyword = ":sessions"
    override val description = "List all chat sessions with pagination (:sessions [page])"

    private val repository = ChatSessionRepository()
    private val sessionsPerPage = 10

    override fun handle(line: ParsedLine) {
        val sessions = repository.getAllSessions()

        if (sessions.isEmpty()) {
            info("No chat sessions found.")
            info("💡 Start a new conversation to create your first session!")
            return
        }

        // Sort sessions by most recently updated first
        val sortedSessions = sessions.sortedByDescending { it.updatedAt }
        val totalPages = ceil(sortedSessions.size.toDouble() / sessionsPerPage).toInt()

        var currentPage = 1

        // Parse page number from command if provided
        val args = line.words()
        if (args.size >= 2) {
            val requestedPage = args[1].toIntOrNull()
            if (requestedPage != null && requestedPage in 1..totalPages) {
                currentPage = requestedPage
            } else if (requestedPage != null) {
                info("❌ Invalid page number. Valid range: 1-$totalPages")
                return
            } else {
                info("❌ Invalid page number format. Use: :sessions [page_number]")
                return
            }
        }

        displaySessionsPage(sortedSessions, currentPage, totalPages)
    }

    private fun displaySessionsPage(sessions: List<ChatSession>, currentPage: Int, totalPages: Int) {
        val startIndex = (currentPage - 1) * sessionsPerPage
        val endIndex = min(startIndex + sessionsPerPage, sessions.size)
        val pageSessions = sessions.subList(startIndex, endIndex)

        info("📋 Chat Sessions (Page $currentPage of $totalPages)")
        info("=".repeat(60))

        pageSessions.forEachIndexed { index, session ->
            val globalIndex = startIndex + index + 1
            val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")

            info("$globalIndex. ID: ${session.id}")
            info("   Title: ${session.title}")
            info("   Created: ${session.createdAt.format(formatter)}")
            info("   Updated: ${session.updatedAt.format(formatter)}")
            info("-".repeat(40))
        }

        info("💡 Tip: Use ':resume-session <session-id>' to resume any session")

        if (totalPages > 1) {
            val navigationHints = mutableListOf<String>()

            if (currentPage > 1) {
                navigationHints.add(":sessions ${currentPage - 1} (previous)")
            }
            if (currentPage < totalPages) {
                navigationHints.add(":sessions ${currentPage + 1} (next)")
            }

            info("📖 Navigation: ${navigationHints.joinToString(" | ")}")
            info("   Or use: :sessions <page_number> (e.g., :sessions 3)")
        }
    }
}

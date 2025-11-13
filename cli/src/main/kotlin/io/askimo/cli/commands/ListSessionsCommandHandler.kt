/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.session.ChatSessionService
import io.askimo.core.util.Logger.info
import org.jline.reader.ParsedLine
import java.time.format.DateTimeFormatter

class ListSessionsCommandHandler : CommandHandler {
    override val keyword = ":sessions"
    override val description = "List all chat sessions with pagination (:sessions [page])"

    private val sessionService = ChatSessionService()
    private val sessionsPerPage = 10

    override fun handle(line: ParsedLine) {
        // Parse page number from command if provided
        val args = line.words()
        val requestedPage = if (args.size >= 2) args[1].toIntOrNull() ?: 1 else 1

        val pagedSessions = sessionService.getSessionsPaged(requestedPage, sessionsPerPage)

        if (pagedSessions.isEmpty) {
            info("No chat sessions found.")
            info("💡 Start a new conversation to create your first session!")
            return
        }

        if (requestedPage != pagedSessions.currentPage) {
            info("❌ Invalid page number. Valid range: 1-${pagedSessions.totalPages}")
            return
        }

        displaySessionsPage(pagedSessions)
    }

    private fun displaySessionsPage(pagedSessions: io.askimo.core.session.PagedSessions) {
        val startIndex = (pagedSessions.currentPage - 1) * pagedSessions.pageSize

        info("📋 Chat Sessions (Page ${pagedSessions.currentPage} of ${pagedSessions.totalPages})")
        info("=".repeat(60))

        pagedSessions.sessions.forEachIndexed { index, session ->
            val globalIndex = startIndex + index + 1
            val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")

            info("$globalIndex. ID: ${session.id}")
            info("   Title: ${session.title}")
            info("   Created: ${session.createdAt.format(formatter)}")
            info("   Updated: ${session.updatedAt.format(formatter)}")
            info("-".repeat(40))
        }

        info("💡 Tip: Use ':resume-session <session-id>' to resume any session")

        if (pagedSessions.totalPages > 1) {
            val navigationHints = mutableListOf<String>()

            if (pagedSessions.hasPreviousPage) {
                navigationHints.add(":sessions ${pagedSessions.currentPage - 1} (previous)")
            }
            if (pagedSessions.hasNextPage) {
                navigationHints.add(":sessions ${pagedSessions.currentPage + 1} (next)")
            }

            info("📖 Navigation: ${navigationHints.joinToString(" | ")}")
            info("   Or use: :sessions <page_number> (e.g., :sessions 3)")
        }
    }
}

/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.session.ChatSessionService
import io.askimo.core.util.TimeUtil
import io.askimo.core.util.logger
import org.jline.reader.ParsedLine

class ListSessionsCommandHandler : CommandHandler {
    private val log = logger<ListSessionsCommandHandler>()
    override val keyword = ":sessions"
    override val description = "List all chat sessions with pagination (:sessions [page])"

    private val sessionService = ChatSessionService()
    private val sessionsPerPage = 10

    override fun handle(line: ParsedLine) {
        val args = line.words()
        val requestedPage = if (args.size >= 2) args[1].toIntOrNull() ?: 1 else 1

        val pagedSessions = sessionService.getSessionsPaged(requestedPage, sessionsPerPage)

        if (pagedSessions.isEmpty) {
            log.info("No chat sessions found.")
            log.info("💡 Start a new conversation to create your first session!")
            return
        }

        if (requestedPage != pagedSessions.currentPage) {
            log.info("❌ Invalid page number. Valid range: 1-${pagedSessions.totalPages}")
            return
        }

        displaySessionsPage(pagedSessions)
    }

    private fun displaySessionsPage(pagedSessions: io.askimo.core.session.PagedSessions) {
        val startIndex = (pagedSessions.currentPage - 1) * pagedSessions.pageSize

        log.info("📋 Chat Sessions (Page ${pagedSessions.currentPage} of ${pagedSessions.totalPages})")
        log.info("=".repeat(60))

        pagedSessions.sessions.forEachIndexed { index, session ->
            val globalIndex = startIndex + index + 1

            log.info("$globalIndex. ID: ${session.id}")
            log.info("   Title: ${session.title}")
            log.info("   Created: ${TimeUtil.formatDisplay(session.createdAt)}")
            log.info("   Updated: ${TimeUtil.formatDisplay(session.updatedAt)}")
            log.info("-".repeat(40))
        }

        log.info("💡 Tip: Use ':resume-session <session-id>' to resume any session")

        if (pagedSessions.totalPages > 1) {
            val navigationHints = mutableListOf<String>()

            if (pagedSessions.hasPreviousPage) {
                navigationHints.add(":sessions ${pagedSessions.currentPage - 1} (previous)")
            }
            if (pagedSessions.hasNextPage) {
                navigationHints.add(":sessions ${pagedSessions.currentPage + 1} (next)")
            }

            log.info("📖 Navigation: ${navigationHints.joinToString(" | ")}")
            log.info("   Or use: :sessions <page_number> (e.g., :sessions 3)")
        }
    }
}

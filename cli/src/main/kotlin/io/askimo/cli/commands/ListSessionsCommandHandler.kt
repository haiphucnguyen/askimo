/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.chat.domain.ChatSession
import io.askimo.core.chat.service.ChatSessionService
import io.askimo.core.context.AppContext
import io.askimo.core.db.Pageable
import io.askimo.core.logging.display
import io.askimo.core.logging.logger
import io.askimo.core.util.TimeUtil
import org.jline.reader.ParsedLine

class ListSessionsCommandHandler(private val appContext: AppContext) : CommandHandler {
    private val log = logger<ListSessionsCommandHandler>()
    override val keyword = ":sessions"
    override val description = "List all chat sessions with pagination (:sessions [page])"

    private val sessionService = ChatSessionService(appContext = appContext)
    private val sessionsPerPage = 10

    override fun handle(line: ParsedLine) {
        val args = line.words()
        val requestedPage = if (args.size >= 2) args[1].toIntOrNull() ?: 1 else 1

        val pagedSessions = sessionService.getSessionsPagedWithoutProject(requestedPage, sessionsPerPage)

        if (pagedSessions.isEmpty) {
            log.display("No chat sessions found.")
            log.display("💡 Start a new conversation to create your first session!")
            return
        }

        if (requestedPage != pagedSessions.currentPage) {
            log.display("❌ Invalid page number. Valid range: 1-${pagedSessions.totalPages}")
            return
        }

        displaySessionsPage(pagedSessions)
    }

    private fun displaySessionsPage(pagedSessions: Pageable<ChatSession>) {
        val startIndex = (pagedSessions.currentPage - 1) * pagedSessions.pageSize

        log.display("📋 Chat Sessions (Page ${pagedSessions.currentPage} of ${pagedSessions.totalPages})")
        log.display("=".repeat(60))

        pagedSessions.items.forEachIndexed { index, session ->
            val globalIndex = startIndex + index + 1

            log.display("$globalIndex. ID: ${session.id}")
            log.display("   Title: ${session.title}")
            log.display("   Created: ${TimeUtil.formatDisplay(session.createdAt)}")
            log.display("   Updated: ${TimeUtil.formatDisplay(session.updatedAt)}")
            log.display("-".repeat(40))
        }

        log.display("💡 Tip: Use ':resume-session <session-id>' to resume any session")

        if (pagedSessions.totalPages > 1) {
            val navigationHints = mutableListOf<String>()

            if (pagedSessions.hasPreviousPage) {
                navigationHints.add(":sessions ${pagedSessions.currentPage - 1} (previous)")
            }
            if (pagedSessions.hasNextPage) {
                navigationHints.add(":sessions ${pagedSessions.currentPage + 1} (next)")
            }

            log.display("📖 Navigation: ${navigationHints.joinToString(" | ")}")
            log.display("   Or use: :sessions <page_number> (e.g., :sessions 3)")
        }
    }
}

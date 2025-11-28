/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.session.ChatSessionService
import io.askimo.core.util.logger
import org.jline.reader.ParsedLine

class DeleteSessionCommandHandler : CommandHandler {
    private val log = logger<DeleteSessionCommandHandler>()
    override val keyword = ":delete-session"
    override val description = "Delete a chat session by ID (:delete-session <session-id>)"

    private val sessionService = ChatSessionService()

    override fun handle(line: ParsedLine) {
        val args = line.words()

        if (args.size < 2) {
            log.info("❌ Usage: :delete-session <session-id>")
            log.info("💡 Tip: Use ':sessions' to list all available sessions")
            return
        }

        val sessionId = args[1]

        // Check if session exists first
        val session = sessionService.getSessionById(sessionId)
        if (session == null) {
            log.info("❌ Session with ID '$sessionId' not found")
            log.info("💡 Tip: Use ':sessions' to list all available sessions")
            return
        }

        // Delete the session
        val deleted = sessionService.deleteSession(sessionId)

        if (deleted) {
            log.info("✅ Session '${session.title}' (ID: $sessionId) has been deleted successfully")
        } else {
            log.info("❌ Failed to delete session with ID '$sessionId'")
        }
    }
}

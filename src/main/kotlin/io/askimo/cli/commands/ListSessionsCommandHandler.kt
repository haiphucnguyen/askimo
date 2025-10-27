/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.session.ChatSessionRepository
import io.askimo.core.util.Logger.info
import org.jline.reader.ParsedLine
import java.time.format.DateTimeFormatter

class ListSessionsCommandHandler : CommandHandler {
    override val keyword = ":sessions"
    override val description = "List all chat sessions"

    private val repository = ChatSessionRepository()

    override fun handle(line: ParsedLine) {
        val sessions = repository.getAllSessions()

        if (sessions.isEmpty()) {
            info("No chat sessions found.")
            return
        }

        info("📋 Chat Sessions:")
        info("=".repeat(60))

        sessions.forEach { session ->
            val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
            info("ID: ${session.id}")
            info("Title: ${session.title}")
            info("Created: ${session.createdAt.format(formatter)}")
            info("Updated: ${session.updatedAt.format(formatter)}")
            info("-".repeat(40))
        }
    }
}

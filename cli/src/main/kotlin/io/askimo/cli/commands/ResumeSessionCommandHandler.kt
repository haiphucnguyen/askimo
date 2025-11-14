/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.session.ChatSessionService
import io.askimo.core.session.MessageRole
import io.askimo.core.session.Session
import io.askimo.core.util.Logger.info
import org.jline.reader.ParsedLine

class ResumeSessionCommandHandler(private val session: Session) : CommandHandler {
    override val keyword = ":resume-session"
    override val description = "Resume a chat session by ID"

    private val sessionService = ChatSessionService()

    override fun handle(line: ParsedLine) {
        val args = line.words()
        if (args.size < 2) {
            info("❌ Usage: :resume-session <session-id>")
            return
        }

        val sessionId = args[1]
        val result = sessionService.resumeSession(session, sessionId)

        if (result.success) {
            info("✅ Resumed chat session: $sessionId")
            if (result.messages.isNotEmpty()) {
                info("\n📝 All conversation history:")
                result.messages.forEach { msg ->
                    val prefix = if (msg.role == MessageRole.USER) "You" else "Assistant"
                    info("$prefix: ${msg.content}")
                    info("-".repeat(40))
                }
            }
        } else {
            info("❌ ${result.errorMessage}")
        }
    }
}

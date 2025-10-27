/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.session.Session
import io.askimo.core.session.MessageRole
import io.askimo.core.util.Logger.info
import org.jline.reader.ParsedLine

class ResumeSessionCommandHandler(private val session: Session) : CommandHandler {
    override val keyword = ":resume-session"
    override val description = "Resume a chat session by ID"

    override fun handle(line: ParsedLine) {
        val args = line.words()
        if (args.size < 2) {
            info("❌ Usage: :resume-session <session-id>")
            return
        }

        val sessionId = args[1]
        val success = session.resumeChatSession(sessionId)

        if (success) {
            info("✅ Resumed chat session: $sessionId")
            val messages = session.chatSessionRepository.getMessages(sessionId).takeLast(3)
            if (messages.isNotEmpty()) {
                info("\n📝 Recent messages:")
                messages.forEach { msg ->
                    val prefix = if (msg.role == MessageRole.USER) "You" else "Assistant"
                    val preview = msg.content.take(100)
                    info("$prefix: $preview${if (msg.content.length > 100) "..." else ""}")
                }
            }
        } else {
            info("❌ Session not found: $sessionId")
        }
    }
}

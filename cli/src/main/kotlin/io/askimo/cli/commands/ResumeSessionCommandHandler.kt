/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.session.ChatSessionService
import io.askimo.core.session.MessageRole
import io.askimo.core.session.Session
import io.askimo.core.util.logger
import org.jline.reader.ParsedLine

class ResumeSessionCommandHandler(private val session: Session) : CommandHandler {
    private val log = logger<ResumeSessionCommandHandler>()
    override val keyword = ":resume-session"
    override val description = "Resume a chat session by ID"

    private val sessionService = ChatSessionService()

    override fun handle(line: ParsedLine) {
        val args = line.words()
        if (args.size < 2) {
            log.info("❌ Usage: :resume-session <session-id>")
            return
        }

        val sessionId = args[1]
        val result = sessionService.resumeSession(session, sessionId)

        if (result.success) {
            log.info("✅ Resumed chat session: $sessionId")
            if (result.messages.isNotEmpty()) {
                log.info("\n📝 All conversation history:")
                result.messages.forEach { msg ->
                    val prefix = if (msg.role == MessageRole.USER) "You" else "Assistant"
                    log.info("$prefix: ${msg.content}")
                    log.info("-".repeat(40))
                }
            }
        } else {
            log.info("❌ ${result.errorMessage}")
        }
    }
}

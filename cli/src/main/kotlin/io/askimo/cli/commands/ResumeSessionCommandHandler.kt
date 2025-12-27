/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.cli.context.CliInteractiveContext
import io.askimo.core.chat.service.ChatSessionService
import io.askimo.core.context.ExecutionMode
import io.askimo.core.logging.display
import io.askimo.core.logging.logger
import org.jline.reader.ParsedLine

class ResumeSessionCommandHandler(private val sessionService: ChatSessionService) : CommandHandler {
    private val log = logger<ResumeSessionCommandHandler>()
    override val keyword = ":resume-session"
    override val description = "Resume a chat session by ID"

    override fun handle(line: ParsedLine) {
        val args = line.words()
        if (args.size < 2) {
            log.display("❌ Usage: :resume-session <session-id>")
            return
        }

        val sessionId = args[1]
        val session = sessionService.getSessionById(sessionId)
        if (session == null) {
            log.display("❌ No session found with ID: $sessionId")
            return
        } else {
            CliInteractiveContext.setCurrentSession(session)
            val result = sessionService.resumeSession(ExecutionMode.STATEFUL_TOOLS_MODE, sessionId)
            if (result.success) {
                log.display("✅ Resumed chat session: $sessionId")
                if (result.messages.isNotEmpty()) {
                    log.display("\n📝 All conversation history:")
                    result.messages.forEach { msg ->
                        val prefix = if (msg.isUser) "You" else "Assistant"
                        log.display("$prefix: ${msg.content}")
                        log.display("-".repeat(40))
                    }
                }
            } else {
                log.display("❌ ${result.errorMessage}")
            }
        }
    }
}

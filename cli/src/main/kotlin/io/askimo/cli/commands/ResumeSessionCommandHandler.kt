/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.chat.service.ChatSessionService
import io.askimo.core.context.AppContext
import io.askimo.core.context.MessageRole
import io.askimo.core.logging.display
import io.askimo.core.logging.logger
import org.jline.reader.ParsedLine

class ResumeSessionCommandHandler(private val appContext: AppContext) : CommandHandler {
    private val log = logger<ResumeSessionCommandHandler>()
    override val keyword = ":resume-session"
    override val description = "Resume a chat session by ID"

    private val sessionService = ChatSessionService()

    override fun handle(line: ParsedLine) {
        val args = line.words()
        if (args.size < 2) {
            log.display("❌ Usage: :resume-session <session-id>")
            return
        }

        val sessionId = args[1]
        val result = sessionService.resumeSession(appContext, sessionId)

        if (result.success) {
            log.display("✅ Resumed chat session: $sessionId")
            if (result.messages.isNotEmpty()) {
                log.display("\n📝 All conversation history:")
                result.messages.forEach { msg ->
                    val prefix = if (msg.role == MessageRole.USER) "You" else "Assistant"
                    log.display("$prefix: ${msg.content}")
                    log.display("-".repeat(40))
                }
            }
        } else {
            log.display("❌ ${result.errorMessage}")
        }
    }
}

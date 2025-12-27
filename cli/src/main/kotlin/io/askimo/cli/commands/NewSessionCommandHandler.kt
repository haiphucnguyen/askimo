/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.cli.context.CliInteractiveContext
import io.askimo.core.chat.domain.ChatSession
import io.askimo.core.chat.service.ChatSessionService
import io.askimo.core.context.ExecutionMode
import io.askimo.core.logging.display
import io.askimo.core.logging.logger
import org.jline.reader.ParsedLine

class NewSessionCommandHandler(private val chatSessionService: ChatSessionService) : CommandHandler {
    private val log = logger<NewSessionCommandHandler>()
    override val keyword = ":new-session"
    override val description = "Start a new chat session"

    override fun handle(line: ParsedLine) {
        val session = chatSessionService.createSession(
            ExecutionMode.STATEFUL_TOOLS_MODE,
            ChatSession(
                id = "",
                title = "New Chat",
            ),
        )
        CliInteractiveContext.setCurrentSession(session)
        log.display("✨ Started new chat session: ${session.id}")
    }
}

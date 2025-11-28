/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.session.Session
import io.askimo.core.util.logger
import org.jline.reader.ParsedLine

class NewSessionCommandHandler(private val session: Session) : CommandHandler {
    private val log = logger<NewSessionCommandHandler>()
    override val keyword = ":new-session"
    override val description = "Start a new chat session"

    override fun handle(line: ParsedLine) {
        session.startNewChatSession()
        log.info("✨ Started new chat session: ${session.currentChatSession?.id}")
    }
}

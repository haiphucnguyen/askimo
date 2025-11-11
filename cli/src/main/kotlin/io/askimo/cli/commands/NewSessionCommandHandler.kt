/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.session.Session
import org.jline.reader.ParsedLine

class NewSessionCommandHandler(private val session: Session) : CommandHandler {
    override val keyword = ":new-session"
    override val description = "Start a new chat session"

    override fun handle(line: ParsedLine) {
        session.startNewChatSession()
        println("✨ Started new chat session: ${session.currentChatSession?.id}")
    }
}

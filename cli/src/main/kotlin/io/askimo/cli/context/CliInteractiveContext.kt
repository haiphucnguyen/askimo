/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.context

import io.askimo.core.chat.domain.ChatSession
import io.askimo.core.logging.logger

/**
 * CLI-specific context that maintains state for CLI interactive sessions.
 * This is a singleton that persists across CLI commands within the same interactive session.
 *
 * Use this for CLI_INTERACTIVE mode only. Desktop mode manages its own session state
 * through ViewModels.
 */
object CliInteractiveContext {
    private val log = logger<CliInteractiveContext>()

    var lastResponse: String? = null
        private set

    fun setLastResponse(response: String?) {
        lastResponse = response
    }

    /**
     * Currently active chat session in CLI interactive mode.
     * This session persists across multiple user prompts until explicitly changed.
     */
    var currentChatSession: ChatSession? = null
        private set

    /**
     * Set the current chat session.
     * @param session The session to set as current, or null to clear
     */
    fun setCurrentSession(session: ChatSession?) {
        val oldSessionId = currentChatSession?.id
        currentChatSession = session
        val newSessionId = session?.id

        if (oldSessionId != newSessionId) {
            log.debug("CLI session changed from $oldSessionId to $newSessionId")
        }
    }
}

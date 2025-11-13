/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

/**
 * Result of resuming a chat session.
 */
data class ResumeSessionResult(
    val success: Boolean,
    val sessionId: String,
    val messages: List<ChatMessage> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Service for resuming chat sessions with shared logic between CLI and desktop.
 */
class SessionResumeService(
    private val session: Session,
) {
    /**
     * Resume a chat session by ID and return the result with messages.
     *
     * @param sessionId The ID of the session to resume
     * @return ResumeSessionResult containing success status, messages, and any error
     */
    fun resumeSession(sessionId: String): ResumeSessionResult {
        val success = session.resumeChatSession(sessionId)

        return if (success) {
            val messages = session.chatSessionRepository.getMessages(sessionId)
            ResumeSessionResult(
                success = true,
                sessionId = sessionId,
                messages = messages,
            )
        } else {
            ResumeSessionResult(
                success = false,
                sessionId = sessionId,
                errorMessage = "Session not found: $sessionId",
            )
        }
    }

    /**
     * Get the current session.
     */
    fun getSession(): Session = session
}

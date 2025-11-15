/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

/**
 * Defines the execution mode of the application.
 *
 * This enum is used to track how the user is running the application,
 * which can be useful for applying different business rules based on the context.
 */
enum class SessionMode {
    /**
     * Non-interactive CLI mode - single command execution with flags like --prompt or --recipe.
     */
    CLI_PROMPT,

    /**
     * Interactive CLI mode - REPL-style chat interface with continuous interaction.
     */
    CLI_INTERACTIVE,

    /**
     * Desktop application mode - GUI-based chat application.
     */
    DESKTOP,
}

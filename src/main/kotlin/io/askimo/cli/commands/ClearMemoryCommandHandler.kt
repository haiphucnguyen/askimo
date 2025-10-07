/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.session.Session
import org.jline.reader.ParsedLine

/**
 * Handles the command to clear the chat memory.
 *
 * This class provides functionality to reset the conversation history for the current
 * provider and model combination. It allows users to start fresh conversations without
 * changing their model configuration.
 */
class ClearMemoryCommandHandler(
    private val session: Session,
) : CommandHandler {
    override val keyword: String = ":clear"
    override val description: String = "Clear the current chat memory for the active provider/model."

    override fun handle(line: ParsedLine) {
        val provider = session.getActiveProvider()
        val modelName = session.params.getModel(provider)

        session.removeMemory(provider, modelName)

        println("🧹 Chat memory cleared for $provider / $modelName")
    }
}

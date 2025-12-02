/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.context.AppContext
import io.askimo.core.logging.display
import io.askimo.core.logging.logger
import org.jline.reader.ParsedLine

/**
 * Handles the command to clear the chat memory.
 *
 * This class provides functionality to reset the conversation history for the current
 * provider and model combination. It allows users to start fresh conversations without
 * changing their model configuration.
 */
class ClearMemoryCommandHandler(
    private val appContext: AppContext,
) : CommandHandler {
    private val log = logger<ClearMemoryCommandHandler>()
    override val keyword: String = ":clear"
    override val description: String = "Clear the current chat memory for the active provider/model."

    override fun handle(line: ParsedLine) {
        val provider = appContext.getActiveProvider()
        val modelName = appContext.params.getModel(provider)

        appContext.removeMemory(provider, modelName)

        log.display("🧹 Chat memory cleared for $provider / $modelName")
    }
}

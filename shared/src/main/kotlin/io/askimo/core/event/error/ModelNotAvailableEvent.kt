/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.event.error

import io.askimo.core.event.Event
import io.askimo.core.event.EventSource
import io.askimo.core.event.EventType
import io.askimo.core.providers.ModelProvider
import java.time.Instant

/**
 * Error events that require user attention.
 * These events are shown as error dialogs or notifications in the UI.
 */

/**
 * Emitted when a model is not available and cannot be used.
 * UI should display an appropriate error message to the user.
 *
 * @param provider The model provider (e.g., OLLAMA, OPENAI, GEMINI)
 * @param modelName The name of the unavailable model
 * @param isEmbedding True if this is an embedding model, false for chat model
 * @param reason Detailed reason why the model is not available
 */
data class ModelNotAvailableEvent(
    val provider: ModelProvider,
    val modelName: String,
    val isEmbedding: Boolean,
    val reason: String,
    override val timestamp: Instant = Instant.now(),
    override val source: EventSource = EventSource.SYSTEM,
) : Event {
    override val type = EventType.ERROR

    override fun getDetails() = "Model '$modelName' not available: $reason"
}

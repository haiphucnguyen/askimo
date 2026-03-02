/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import io.askimo.core.event.EventBus
import io.askimo.core.event.error.ModelNotAvailableEvent
import io.askimo.core.logging.currentFileLogger
import io.askimo.core.logging.display
import io.askimo.core.logging.displayError

private val log = currentFileLogger()

/**
 * Ensures that a locally-hosted embedding model is available before use.
 * Logs diagnostics and emits a [ModelNotAvailableEvent] if the model or provider is unreachable.
 *
 * @param provider The local provider (Ollama, Docker, LocalAI, LMStudio)
 * @param baseUrl The base URL of the local provider
 * @param modelName The embedding model name to check
 * @throws IllegalStateException if the provider is unreachable or the model is not available
 */
fun ensureLocalEmbeddingModelAvailable(
    provider: ModelProvider,
    baseUrl: String,
    modelName: String,
) {
    when (val result = LocalModelValidator.checkModelExists(provider, baseUrl, modelName)) {
        is ModelAvailabilityResult.Available -> {
            log.display("✅ ${provider.name} embedding model '$modelName' is ready")
        }

        is ModelAvailabilityResult.ProviderUnreachable -> {
            val errorMessage = """
                ❌ ${result.error}

                Please ensure ${provider.name} is running and accessible at: $baseUrl
            """.trimIndent()

            log.displayError(errorMessage)

            EventBus.post(
                ModelNotAvailableEvent(
                    provider = provider,
                    modelName = modelName,
                    isEmbedding = true,
                    reason = "${provider.name} not reachable at $baseUrl",
                ),
            )

            error("${provider.name} not reachable at $baseUrl")
        }

        is ModelAvailabilityResult.NotAvailable -> {
            val errorMessage = """
                ❌ ${result.reason}

                Please ensure the model is available in ${provider.name} at: $baseUrl
            """.trimIndent()

            log.displayError(errorMessage)

            EventBus.post(
                ModelNotAvailableEvent(
                    provider = provider,
                    modelName = modelName,
                    isEmbedding = true,
                    reason = result.reason,
                ),
            )

            error("Model '$modelName' not available in ${provider.name}")
        }
    }
}

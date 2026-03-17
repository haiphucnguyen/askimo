/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.askimo.core.event.EventBus
import io.askimo.core.event.error.AppErrorEvent
import io.askimo.core.event.error.IndexingErrorEvent
import io.askimo.core.event.error.IndexingErrorType
import io.askimo.core.event.error.ModelNotAvailableEvent
import io.askimo.core.event.error.SendMessageErrorEvent
import io.askimo.core.i18n.LocalizationManager

/**
 * Holds the state for the global error dialog triggered by application-wide error events.
 */
data class ErrorDialogState(
    val show: Boolean = false,
    val title: String = "",
    val message: String = "",
    val linkText: String? = null,
    val linkUrl: String? = null,
)

/**
 * Listens to [EventBus.errorEvents] and surfaces errors through [ErrorDialogState].
 *
 * Call this composable once at the top-level [app] scope and pass the returned
 * state (along with the dismiss callback) down to wherever the error dialog is rendered.
 *
 * @param onStateChange called whenever a new [ErrorDialogState] should be applied.
 */
@Composable
fun globalErrorHandler(onStateChange: (ErrorDialogState) -> Unit) {
    LaunchedEffect(Unit) {
        EventBus.errorEvents.collect { event ->
            when (event) {
                is IndexingErrorEvent -> {
                    when (event.errorType) {
                        IndexingErrorType.EMBEDDING_MODEL_NOT_FOUND -> {
                            val modelName = event.details["modelName"] ?: "unknown"
                            val provider = event.details["provider"] ?: "AI provider"
                            onStateChange(
                                ErrorDialogState(
                                    show = true,
                                    title = LocalizationManager.getString("error.indexing.model_not_found.title"),
                                    message = LocalizationManager.getString(
                                        "error.indexing.model_not_found.message",
                                        modelName,
                                        provider,
                                    ),
                                ),
                            )
                        }
                        IndexingErrorType.IO_ERROR -> {
                            onStateChange(
                                ErrorDialogState(
                                    show = true,
                                    title = LocalizationManager.getString("error.indexing.io_error.title"),
                                    message = event.details["message"]
                                        ?: LocalizationManager.getString("error.indexing.io_error.message"),
                                ),
                            )
                        }
                        IndexingErrorType.UNKNOWN_ERROR -> {
                            onStateChange(
                                ErrorDialogState(
                                    show = true,
                                    title = LocalizationManager.getString("error.indexing.unknown.title"),
                                    message = event.details["message"]
                                        ?: LocalizationManager.getString("error.indexing.unknown.message"),
                                ),
                            )
                        }
                    }
                }
                is ModelNotAvailableEvent -> {
                    onStateChange(
                        ErrorDialogState(
                            show = true,
                            title = if (event.isEmbedding) {
                                LocalizationManager.getString("error.model.not_available.title.embedding")
                            } else {
                                LocalizationManager.getString("error.model.not_available.title.chat")
                            },
                            message = if (event.isEmbedding) {
                                LocalizationManager.getString(
                                    "error.model.not_available.message.embedding",
                                    event.modelName,
                                )
                            } else {
                                LocalizationManager.getString(
                                    "error.model.not_available.message.chat",
                                    event.modelName,
                                )
                            },
                            linkText = if (event.isEmbedding) {
                                LocalizationManager.getString("error.model.not_available.config_link")
                            } else {
                                null
                            },
                            linkUrl = if (event.isEmbedding) {
                                LocalizationManager.getString("error.model.not_available.config_url")
                            } else {
                                null
                            },
                        ),
                    )
                }
                is SendMessageErrorEvent -> {
                    onStateChange(
                        ErrorDialogState(
                            show = true,
                            title = LocalizationManager.getString("error.send_message.title"),
                            message = event.throwable.message
                                ?: LocalizationManager.getString("error.send_message.message"),
                        ),
                    )
                }
                is AppErrorEvent -> {
                    onStateChange(
                        ErrorDialogState(
                            show = true,
                            title = event.title,
                            message = event.message,
                        ),
                    )
                }
            }
        }
    }
}

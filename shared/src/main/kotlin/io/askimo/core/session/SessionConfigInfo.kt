/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

import io.askimo.core.providers.ModelProvider

/**
 * Data class representing the current session configuration.
 *
 * This class contains the essential configuration information for a chat session,
 * excluding project-related information. It's designed to be used across different
 * modules (CLI, Desktop, etc.) without introducing circular dependencies.
 *
 * @property provider The active model provider (e.g., OPENAI, OLLAMA)
 * @property model The name of the current model being used
 * @property settingsDescription A list of human-readable strings describing the provider settings
 */
data class SessionConfigInfo(
    val provider: ModelProvider,
    val model: String,
    val settingsDescription: List<String>,
)

/**
 * Extension function to get configuration info from a Session.
 *
 * @return A SessionConfigInfo object containing the current configuration
 */
fun Session.getConfigInfo(): SessionConfigInfo {
    val provider = getActiveProvider()
    val settings = getCurrentProviderSettings()
    val model = if (hasChatService()) params.model else "(not set)"

    return SessionConfigInfo(
        provider = provider,
        model = model,
        settingsDescription = settings.describe(),
    )
}

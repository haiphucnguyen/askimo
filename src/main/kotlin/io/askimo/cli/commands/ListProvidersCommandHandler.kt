/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.providers.ProviderRegistry
import io.askimo.core.util.Logger.info
import org.jline.reader.ParsedLine

/**
 * Handles the command to list all supported model providers.
 *
 * This class retrieves and displays all registered AI model providers that are available
 * in the application. It helps users discover which providers they can choose from when
 * setting up their chat environment.
 */
class ListProvidersCommandHandler : CommandHandler {
    override val keyword: String = ":providers"

    override val description: String = "List all supported model providers"

    override fun handle(line: ParsedLine) {
        val providers = ProviderRegistry.getSupportedProviders()

        if (providers.isEmpty()) {
            info("⚠️  No model providers registered.")
            return
        }

        info("Available Model Providers:")
        providers.forEach { provider ->
            info("- ${provider.name.lowercase()}")
        }
        info("Use `:set-provider <modelName>` to choose the active model.")
    }
}

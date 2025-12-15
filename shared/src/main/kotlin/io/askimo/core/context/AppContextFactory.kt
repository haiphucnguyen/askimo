/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.context

import io.askimo.core.logging.logger
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.NoopChatClient
import io.askimo.core.providers.NoopProviderSettings
import io.askimo.core.providers.ProviderRegistry
import io.askimo.core.providers.ProviderSettings

object AppContextFactory {
    private val log = logger<AppContextFactory>()

    @Volatile
    private var cached: AppContext? = null

    /** Return the cached session if any (no I/O). */
    fun current(): AppContext? = cached

    /** Drop the cached session (e.g., after logout or user switch). */
    fun clear() {
        cached = null
    }

    /**
     * Create (or reuse) a Session.
     * - Reuses cached if params are equal to the cached session's params.
     * - Otherwise builds a new Session and caches it.
     *
     * @param params The session parameters to use
     * @param mode The execution mode (CLI_PROMPT, CLI_INTERACTIVE, or DESKTOP)
     */
    fun createAppContext(
        params: AppContextParams = AppContextConfigManager.load(),
        mode: ExecutionMode = ExecutionMode.CLI_INTERACTIVE,
    ): AppContext {
        val existing = cached
        if (existing != null && existing.params == params && existing.mode == mode) return existing

        return synchronized(this) {
            val again = cached
            if (again != null && again.params == params && again.mode == mode) return@synchronized again

            val fresh = buildAppContext(params, mode)
            cached = fresh
            fresh
        }
    }

    @Suppress("DEPRECATION") // Legacy CLI initialization - will be refactored to use session-specific clients
    private fun buildAppContext(params: AppContextParams, mode: ExecutionMode): AppContext {
        log.debug("Building session with params: {}, mode: {}", params, mode)

        val appContext = AppContext(params, mode)
        val provider = appContext.params.currentProvider
        val modelName = appContext.params.getModel(provider)

        val factory = ProviderRegistry.getFactory(provider)

        val settings: ProviderSettings =
            appContext.params.providerSettings[provider]
                ?: factory?.defaultSettings()
                ?: NoopProviderSettings

        val chatClient = if (factory != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                (factory as ChatModelFactory<ProviderSettings>)
                    .create(modelName, settings, executionMode = mode)
            } catch (e: Exception) {
                log.error("Failed to create chat service", e)
                NoopChatClient
            }
        } else {
            NoopChatClient
        }
        appContext.setChatClient(chatClient)

        return appContext
    }
}

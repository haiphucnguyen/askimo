/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

import io.askimo.core.providers.NoopChatService
import io.askimo.core.providers.NoopProviderSettings
import io.askimo.core.providers.ProviderRegistry
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.util.Logger.debug

object SessionFactory {
    @Volatile
    private var cached: Session? = null

    /** Return the cached session if any (no I/O). */
    fun current(): Session? = cached

    /** Drop the cached session (e.g., after logout or user switch). */
    fun clear() {
        cached = null
    }

    /**
     * Create (or reuse) a Session.
     * - Reuses cached if params are equal to the cached session's params and forceReload == false.
     * - Otherwise builds a new Session and caches it.
     *
     * @param params The session parameters to use
     * @param mode The execution mode (CLI_PROMPT, CLI_INTERACTIVE, or DESKTOP)
     * @param forceReload Whether to force reload the session even if params match
     */
    fun createSession(
        params: SessionParams = SessionConfigManager.load(),
        mode: SessionMode = SessionMode.CLI_INTERACTIVE,
        forceReload: Boolean = false,
    ): Session {
        if (!forceReload) {
            val existing = cached
            if (existing != null && existing.params == params && existing.mode == mode) return existing
        }

        return synchronized(this) {
            if (!forceReload) {
                val again = cached
                if (again != null && again.params == params && again.mode == mode) return@synchronized again
            }
            val fresh = buildSession(params, mode)
            cached = fresh
            fresh
        }
    }

    private fun buildSession(params: SessionParams, mode: SessionMode): Session {
        debug("Building session with params: $params, mode: $mode")

        val session = Session(params, mode)
        val provider = session.params.currentProvider
        val modelName = session.params.getModel(provider)

        val factory = ProviderRegistry.getFactory(provider)

        val settings: ProviderSettings =
            session.params.providerSettings[provider]
                ?: factory?.defaultSettings()
                ?: NoopProviderSettings

        val memory = session.getOrCreateMemory(provider, modelName, settings)

        val chatService = factory?.create(modelName, settings, memory) ?: NoopChatService
        session.setChatService(chatService)

        return session
    }
}

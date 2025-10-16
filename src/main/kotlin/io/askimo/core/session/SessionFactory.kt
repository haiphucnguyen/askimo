/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

import io.askimo.cli.Logger.log
import io.askimo.core.providers.NoopChatService
import io.askimo.core.providers.NoopProviderSettings
import io.askimo.core.providers.ProviderRegistry
import io.askimo.core.providers.ProviderSettings

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
     */
    fun createSession(
        params: SessionParams = SessionConfigManager.load(),
        forceReload: Boolean = false,
    ): Session {
        if (!forceReload) {
            val existing = cached
            if (existing != null && existing.params == params) return existing
        }

        return synchronized(this) {
            if (!forceReload) {
                val again = cached
                if (again != null && again.params == params) return@synchronized again
            }
            val fresh = buildSession(params)
            cached = fresh
            fresh
        }
    }

    /** Persist params, then (re)build and cache a fresh Session from them. */
    fun reconfigure(params: SessionParams): Session {
        SessionConfigManager.save(params)
        return createSession(params, forceReload = true)
    }

    /** Reload params from disk and rebuild the cached session. */
    fun reloadFromDisk(): Session = createSession(SessionConfigManager.load(), forceReload = true)

    private fun buildSession(params: SessionParams): Session {
        log { "Building session with params: $params" }

        val session = Session(params)
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

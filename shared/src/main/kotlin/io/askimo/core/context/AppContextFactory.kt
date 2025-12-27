/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.context

import io.askimo.core.logging.logger

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
     */
    fun createAppContext(
        params: AppContextParams = AppContextConfigManager.load(),
    ): AppContext {
        val existing = cached
        if (existing != null && existing.params == params) return existing

        return synchronized(this) {
            val again = cached
            if (again != null && again.params == params) return@synchronized again

            val fresh = buildAppContext(params)
            cached = fresh
            fresh
        }
    }

    private fun buildAppContext(params: AppContextParams): AppContext {
        log.debug("Building session with params: {}", params)
        return AppContext(params)
    }
}

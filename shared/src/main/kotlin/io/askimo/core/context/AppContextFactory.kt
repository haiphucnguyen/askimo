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

    private fun buildAppContext(params: AppContextParams, mode: ExecutionMode): AppContext {
        log.debug("Building session with params: {}, mode: {}", params, mode)
        return AppContext(params, mode)
    }
}

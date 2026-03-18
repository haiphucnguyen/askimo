/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.context

/**
 * ThreadLocal storage for passing request-scoped context to [io.askimo.core.tools.ToolProviderImpl].
 *
 * Both [projectId] and [disabledServers] are set on the same thread that calls
 * [io.askimo.core.providers.sendStreamingMessageWithCallback], which is the same thread
 * LangChain4j uses to invoke [io.askimo.core.tools.ToolProviderImpl.provideTools].
 * No cross-thread coordination needed.
 */
object ChatContext {
    private val projectIdThreadLocal = ThreadLocal<String?>()
    private val disabledServersThreadLocal = ThreadLocal<Set<String>>()

    fun setProjectId(projectId: String?) = projectIdThreadLocal.set(projectId)
    fun getProjectId(): String? = projectIdThreadLocal.get()

    fun setDisabledServers(disabledServerIds: Set<String>) = disabledServersThreadLocal.set(disabledServerIds)
    fun getDisabledServers(): Set<String> = disabledServersThreadLocal.get() ?: emptySet()

    /**
     * Clear all thread-local state. Must be called in a finally block after each request.
     */
    fun clear() {
        projectIdThreadLocal.remove()
        disabledServersThreadLocal.remove()
    }
}

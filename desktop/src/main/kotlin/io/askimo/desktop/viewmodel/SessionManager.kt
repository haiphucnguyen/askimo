/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.askimo.core.chat.service.ChatSessionService
import io.askimo.core.context.AppContext
import io.askimo.core.event.ChatEvent
import io.askimo.core.event.DatabaseEvent
import io.askimo.core.event.EventBus
import io.askimo.core.event.StreamingEvent
import io.askimo.core.logging.logger
import io.askimo.core.providers.sendStreamingMessageWithCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages multiple ChatViewModel instances with smart memory management AND streaming infrastructure.
 *
 * This manager consolidates:
 * 1. ChatViewModel lifecycle management (caching up to maxCachedViewModels)
 * 2. Streaming thread management (one thread per active session)
 * 3. Session state coordination
 *
 * Each session gets its own isolated ChatViewModel and can have at most ONE active streaming thread.
 * ViewModels are cached up to [maxCachedViewModels], and inactive ViewModels are automatically
 * cleaned up when the limit is reached.
 *
 * A ViewModel is considered "safe to remove" when:
 * 1. It's not the currently active session
 * 2. It's not waiting for an AI response (no active streaming thread)
 */
class SessionManager(
    private val chatSessionService: ChatSessionService,
    private val appContext: AppContext,
    private val scope: CoroutineScope,
) {
    private val log = logger<SessionManager>()

    companion object {
        private const val MAX_CONCURRENT_STREAMS = 20 // Match maxCachedViewModels
    }

    // Cache of ChatViewModel instances by session ID
    private val chatViewModels = mutableMapOf<String, ChatViewModel>()

    // Streaming infrastructure: sessionId -> StreamingThread
    private val activeThreads = ConcurrentHashMap<String, StreamingThread>()

    // Coroutine scope for managing streaming jobs
    private val streamingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Match the streaming capacity with ViewModel capacity
    private val maxCachedViewModels = MAX_CONCURRENT_STREAMS

    var activeSessionId by mutableStateOf<String?>(null)
        private set

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                shutdown()
            },
        )
    }

    /**
     * Represents a single streaming thread for ONE question-answer pair.
     * Thread closes automatically after completion or failure.
     */
    data class StreamingThread(
        val threadId: String,
        val sessionId: String,
        val job: Job,
        private val _chunks: MutableStateFlow<List<String>>,
        private val _isComplete: MutableStateFlow<Boolean>,
        private val _hasFailed: MutableStateFlow<Boolean>,
    ) {
        val chunks: StateFlow<List<String>> = _chunks.asStateFlow()
        val isComplete: StateFlow<Boolean> = _isComplete.asStateFlow()

        private val mutex = Mutex()

        suspend fun appendChunk(chunk: String) {
            mutex.withLock {
                _chunks.value += chunk
            }
        }

        suspend fun markComplete() {
            mutex.withLock {
                _isComplete.value = true
            }
        }

        suspend fun markFailed() {
            mutex.withLock {
                _hasFailed.value = true
            }
        }

        fun getCurrentContent(): String = _chunks.value.joinToString("")
    }

    /**
     * Send a message and start streaming in the background.
     * Creates a new thread for this question-answer pair.
     *
     * @param sessionId The session ID
     * @param userMessage The user's message
     * @param onChunkReceived Callback for each chunk (optional)
     * @return threadId if streaming started successfully, null if session already has active stream or max streams reached
     */
    fun sendMessage(
        sessionId: String,
        userMessage: String,
        onChunkReceived: (String) -> Unit = {},
    ): String? {
        // Check if this session already has an active stream
        if (activeThreads.containsKey(sessionId)) {
            log.warn("Session $sessionId already has an active stream")
            return null
        }

        // Check global stream limit
        if (activeThreads.size >= MAX_CONCURRENT_STREAMS) {
            log.warn("Max concurrent streams ($MAX_CONCURRENT_STREAMS) reached")
            return null
        }

        val threadId = "${sessionId}_${System.currentTimeMillis()}"

        val thread = StreamingThread(
            threadId = threadId,
            sessionId = sessionId,
            job = Job(),
            _chunks = MutableStateFlow(emptyList()),
            _isComplete = MutableStateFlow(false),
            _hasFailed = MutableStateFlow(false),
        )

        // Register this thread
        activeThreads[sessionId] = thread

        log.debug("Streaming thread $threadId for session $sessionId started. Active streams: ${activeThreads.size}")

        // Emit stream started event
        EventBus.post(
            StreamingEvent.StreamStarted(
                chatId = sessionId,
                threadId = threadId,
                userMessage = userMessage,
            ),
        )

        // Prepare context and save user message to DB
        val promptWithContext = appContext.prepareContextAndGetPromptForChat(userMessage, sessionId)
        EventBus.post(ChatEvent.ChatCreated(chatId = sessionId, prompt = promptWithContext))

        // Start streaming in background
        streamingScope.launch(thread.job) {
            val startTime = System.currentTimeMillis()
            try {
                // Stream the response with token-by-token callback
                val fullResponse = appContext.getChatClient().sendStreamingMessageWithCallback(promptWithContext) { token ->
                    streamingScope.launch {
                        thread.appendChunk(token)
                        val currentContent = thread.getCurrentContent()
                        onChunkReceived(currentContent)
                    }
                }

                thread.markComplete()
                appContext.saveAiResponseToSession(fullResponse, sessionId)
                EventBus.post(DatabaseEvent.SaveCompleted("Session $sessionId: Save AI Response successfully"))
                log.debug("Streaming thread $threadId completed successfully. Saved response to session $sessionId.")

                // Emit stream completed event
                EventBus.post(
                    StreamingEvent.StreamCompleted(
                        chatId = sessionId,
                        threadId = threadId,
                        fullResponse = fullResponse,
                        duration = System.currentTimeMillis() - startTime,
                    ),
                )
            } catch (e: Exception) {
                thread.markFailed()

                // Save partial response with failure marker
                val partialResponse = thread.getCurrentContent()
                val failedResponse = if (partialResponse.isNotBlank()) {
                    "$partialResponse\n\nResponse failed"
                } else {
                    "Response failed"
                }

                // Emit stream failed event
                EventBus.post(
                    StreamingEvent.StreamFailed(
                        chatId = sessionId,
                        threadId = threadId,
                        error = e.message ?: "Unknown error",
                        partialResponse = partialResponse.ifBlank { null },
                    ),
                )

                appContext.saveAiResponseToSession(failedResponse, sessionId)
                log.error("Streaming thread $threadId failed for session $sessionId: ${e.message}", e)
            } finally {
                // Thread cleanup: remove from registry
                activeThreads.remove(sessionId)
                log.debug("Thread $threadId cleaned up. Active streams: ${activeThreads.size}")
            }
        }

        return threadId
    }

    /**
     * Get an active streaming thread for a session.
     */
    fun getActiveThread(sessionId: String): StreamingThread? = activeThreads[sessionId]

    /**
     * Stop an active stream for a session.
     */
    fun stopStream(sessionId: String) {
        val thread = activeThreads[sessionId]
        if (thread != null) {
            log.info("Stopping stream for session $sessionId (thread ${thread.threadId})")
            thread.job.cancel()
            activeThreads.remove(sessionId)
        } else {
            log.warn("No active stream found for session $sessionId")
        }
    }

    /**
     * Get or create a ChatViewModel for a session.
     * Automatically cleans up inactive ViewModels when the cache limit is reached.
     *
     * @param sessionId The session ID
     * @return The ChatViewModel for this session
     */
    fun getOrCreateChatViewModel(sessionId: String): ChatViewModel {
        // If already cached, return it
        chatViewModels[sessionId]?.let { return it }

        // Check if we need to clean up before creating new one
        if (chatViewModels.size >= maxCachedViewModels) {
            cleanupInactiveViewModels()
        }

        // Create new ViewModel (no longer needs ChatSessionManager)
        val viewModel = ChatViewModel(
            sessionManager = this, // Pass SessionManager instead
            scope = scope,
            chatSessionService = chatSessionService,
            appContext = appContext,
        )

        chatViewModels[sessionId] = viewModel
        log.debug("Created new ChatViewModel for session: $sessionId (total cached: ${chatViewModels.size})")
        return viewModel
    }

    /**
     * Switch to a different session.
     * No cancellation needed - each ViewModel manages its own state independently.
     *
     * @param sessionId The session ID to switch to
     */
    fun switchToSession(sessionId: String) {
        activeSessionId = sessionId
        val viewModel = getOrCreateChatViewModel(sessionId)
        viewModel.resumeSession(sessionId)
    }

    /**
     * Clean up inactive ViewModels that are safe to remove.
     * Priority order:
     * 1. Remove inactive ViewModels (not active, not streaming)
     * 2. If all are active or streaming, remove the oldest non-active ViewModel
     */
    private fun cleanupInactiveViewModels() {
        // Find ViewModels that are safe to remove (not active, not streaming)
        val inactiveViewModels = chatViewModels.filter { (sessionId, _) ->
            sessionId != activeSessionId && !activeThreads.containsKey(sessionId)
        }

        if (inactiveViewModels.isEmpty()) {
            // All ViewModels are either active or streaming
            // Remove the oldest one (first in map, excluding active session)
            val oldestSession = chatViewModels.keys
                .firstOrNull { it != activeSessionId }

            if (oldestSession != null) {
                chatViewModels[oldestSession]?.cleanup()
                chatViewModels.remove(oldestSession)
                log.warn("Removed oldest ViewModel (at capacity): $oldestSession")
            }
        } else {
            // Remove one inactive ViewModel (first one found)
            val (sessionId, viewModel) = inactiveViewModels.entries.first()
            viewModel.cleanup()
            chatViewModels.remove(sessionId)
            log.debug("Removed inactive ViewModel: $sessionId (total cached: ${chatViewModels.size})")
        }
    }

    /**
     * Shutdown all streaming threads and clean up resources.
     */
    private fun shutdown() {
        log.info("Shutting down SessionManager. Cancelling ${activeThreads.size} active streams.")
        activeThreads.values.forEach { it.job.cancel() }
        activeThreads.clear()
        chatViewModels.values.forEach { it.cleanup() }
        chatViewModels.clear()
    }

    /**
     * Explicitly close a session and clean up its ViewModel.
     * This should be called when the user deletes a session.
     *
     * @param sessionId The session ID to close
     */
    fun closeSession(sessionId: String) {
        // 1. Stop any active streaming thread first
        stopStream(sessionId)

        // 2. Clean up the ViewModel
        chatViewModels[sessionId]?.cleanup()
        chatViewModels.remove(sessionId)
        log.info("Closed session: $sessionId (total cached: ${chatViewModels.size})")

        // 3. If closing the active session, clear the active session ID
        if (activeSessionId == sessionId) {
            activeSessionId = null
        }
    }

    /**
     * Get statistics about cached ViewModels for debugging/monitoring.
     *
     * @return Statistics about the ViewModel cache
     */
    fun getStats(): ViewModelCacheStats {
        val totalCached = chatViewModels.size
        val activeCount = chatViewModels.count { (_, vm) -> vm.isLoading }
        val inactiveCount = totalCached - activeCount

        return ViewModelCacheStats(
            totalCached = totalCached,
            activeCount = activeCount,
            inactiveCount = inactiveCount,
            maxCapacity = maxCachedViewModels,
        )
    }
}

/**
 * Statistics about the ViewModel cache.
 */
data class ViewModelCacheStats(
    val totalCached: Int,
    val activeCount: Int,
    val inactiveCount: Int,
    val maxCapacity: Int,
)

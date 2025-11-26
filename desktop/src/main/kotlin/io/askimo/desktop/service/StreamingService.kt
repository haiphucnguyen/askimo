/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.service

import io.askimo.core.providers.sendStreamingMessageWithCallback
import io.askimo.core.session.Session
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
 * Service for managing background streaming of AI responses.
 *
 * Architecture:
 * - Each chatId has ONE thread pool thread for ONE question-answer pair
 * - Thread lifecycle: Question asked → AI responds → Save to DB → Close thread
 * - No resources kept after completion/failure
 * - User must wait for completion before asking next question in same chat
 * - Multiple chats can run simultaneously, each in their own thread
 *
 * Key features:
 * - Thread pool with configurable concurrent stream limit (default: 10)
 * - One thread per question (not per chat)
 * - In-memory buffering of streaming chunks
 * - Automatic database persistence when stream completes
 * - Thread auto-closes after completion/failure
 * - No resource leaks - all resources freed immediately
 */
class StreamingService(
    private val session: Session,
) {
    companion object {
        // Maximum number of concurrent streams (configurable via constant)
        private const val MAX_CONCURRENT_STREAMS = 10
    }

    // Coroutine scope with supervisor job to handle individual stream failures
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Active threads mapped by unique threadId (chatId + timestamp)
    // Each thread represents ONE question-answer pair
    private val activeThreads = ConcurrentHashMap<String, StreamingThread>()

    // Map chatId to its current active threadId (only one active question per chat)
    private val chatToThreadMap = ConcurrentHashMap<String, String>()

    // Map to keep completed chatId→threadId mappings for tracking
    // This map is NOT cleared when thread completes, so we can track which chats had streaming
    private val completedThreads = ConcurrentHashMap<String, String>()

    /**
     * Represents a single thread for ONE question-answer pair.
     * Thread closes automatically after completion or failure.
     */
    data class StreamingThread(
        val threadId: String,        // Unique thread ID for this Q&A
        val chatId: String,          // Which chat this belongs to
        val job: Job,
        private val _chunks: MutableStateFlow<List<String>>,
        private val _isComplete: MutableStateFlow<Boolean>,
        private val _hasFailed: MutableStateFlow<Boolean>,
    ) {
        val chunks: StateFlow<List<String>> = _chunks.asStateFlow()
        val isComplete: StateFlow<Boolean> = _isComplete.asStateFlow()
        val hasFailed: StateFlow<Boolean> = _hasFailed.asStateFlow()

        private val mutex = Mutex()

        suspend fun appendChunk(chunk: String) {
            mutex.withLock {
                _chunks.value = _chunks.value + chunk
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
     * Start a new thread for ONE question-answer pair.
     * Each question gets a unique thread that closes after completion.
     *
     * @param chatId The chat ID where this question is asked
     * @param userMessage The user's message (already saved to DB)
     * @param onChunkReceived Callback invoked for each chunk (for UI updates)
     * @return threadId if started successfully, null if max concurrent streams reached or chat already has active question
     */
    fun startStream(
        chatId: String,
        userMessage: String,
        onChunkReceived: (String) -> Unit,
    ): String? {
        // Check if this chat already has an active question (user must wait for completion)
        val existingThreadId = chatToThreadMap[chatId]
        if (existingThreadId != null) {
            // Chat already has an active question - user must wait
            return null
        }

        // Check concurrent stream limit
        if (activeThreads.size >= MAX_CONCURRENT_STREAMS) {
            return null
        }

        // Generate unique thread ID for this Q&A pair
        val threadId = "${chatId}_${System.currentTimeMillis()}"

        // Create streaming thread for this ONE question
        val thread = StreamingThread(
            threadId = threadId,
            chatId = chatId,
            job = Job(),
            _chunks = MutableStateFlow(emptyList()),
            _isComplete = MutableStateFlow(false),
            _hasFailed = MutableStateFlow(false),
        )

        // Register this thread
        activeThreads[threadId] = thread
        chatToThreadMap[chatId] = threadId

        // Clear old completed thread entry since we're starting a new question
        completedThreads.remove(chatId)

        // IMPORTANT: Save user message to DB BEFORE starting async streaming
        // Use thread-safe method that saves to SPECIFIC chatId, not currentChatSession
        // This ensures true thread isolation - chatId is locked to this thread
        val promptWithContext = session.prepareContextAndGetPromptForChat(userMessage, chatId)

        // Start streaming in background thread
        serviceScope.launch(thread.job) {
            try {
                // Prompt is already prepared above (user message saved to DB)

                // Stream the response with token-by-token callback
                val fullResponse = session.getChatService().sendStreamingMessageWithCallback(promptWithContext) { token ->
                    // Append chunk to in-memory buffer
                    serviceScope.launch {
                        thread.appendChunk(token)
                        // Notify UI if callback provided
                        onChunkReceived(thread.getCurrentContent())
                    }
                }

                // Mark as complete
                thread.markComplete()

                // Save to database - use the specific chat ID
                session.saveAiResponseToSession(fullResponse, chatId)
                session.lastResponse = fullResponse

            } catch (e: Exception) {
                // Mark as failed
                thread.markFailed()

                // Save partial response with failure marker
                val partialResponse = thread.getCurrentContent()
                val failedResponse = if (partialResponse.isNotBlank()) {
                    "$partialResponse\n\nResponse failed"
                } else {
                    "Response failed"
                }

                // Save to database - use the specific chat ID
                try {
                    session.saveAiResponseToSession(failedResponse, chatId)
                } catch (saveException: Exception) {
                    println("Failed to save error response: ${saveException.message}")
                }
            } finally {
                // IMPORTANT: Close this thread - free all resources
                // No matter success or failure, thread closes after ONE Q&A
                activeThreads.remove(threadId)
                chatToThreadMap.remove(chatId)

                // Keep the completed chatId→threadId mapping for tracking
                // This allows us to know which chats had streaming that completed
                completedThreads[chatId] = threadId

                // Thread is now closed, resources freed
                // User can ask next question in this chat
            }
        }

        return threadId
    }

    /**
     * Get the active thread for a specific threadId.
     *
     * @param threadId The unique thread ID
     * @return StreamingThread if thread is active, null otherwise
     */
    fun getActiveThread(threadId: String): StreamingThread? = activeThreads[threadId]

    /**
     * Get the active thread for a chatId (if any question is being answered).
     *
     * @param chatId The chat ID
     * @return StreamingThread if chat has active question, null otherwise
     */
    fun getActiveThreadForChat(chatId: String): StreamingThread? {
        val threadId = chatToThreadMap[chatId] ?: return null
        return activeThreads[threadId]
    }

    /**
     * Check if a chat currently has an active question being answered.
     *
     * @param chatId The chat ID
     * @return true if chat has active question
     */
    fun isStreaming(chatId: String): Boolean = chatToThreadMap.containsKey(chatId)

    /**
     * Get the last completed threadId for a chatId.
     * This is useful for tracking which chats had completed streaming.
     *
     * @param chatId The chat ID
     * @return The last completed threadId, or null if no completed thread
     */
    fun getLastCompletedThreadId(chatId: String): String? {
        return completedThreads[chatId]
    }

    /**
     * Stop an active thread (user clicked stop button).
     * Discards all buffered chunks and does NOT save to database.
     *
     * @param chatId The chat ID to stop
     */
    fun stopStream(chatId: String) {
        val threadId = chatToThreadMap[chatId]
        if (threadId != null) {
            val thread = activeThreads[threadId]
            if (thread != null) {
                // Cancel the job
                thread.job.cancel()

                // Remove from active threads (discards all chunks, closes thread)
                activeThreads.remove(threadId)
                chatToThreadMap.remove(chatId)
            }
        }
    }

    /**
     * Get all currently active chat IDs.
     */
    fun getActiveStreamingChatIds(): Set<String> = chatToThreadMap.keys.toSet()

    /**
     * Shutdown the service and cancel all active threads.
     */
    fun shutdown() {
        activeThreads.values.forEach { thread ->
            thread.job.cancel()
        }
        activeThreads.clear()
        chatToThreadMap.clear()
    }
}


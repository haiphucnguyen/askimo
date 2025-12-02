/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.chat

import io.askimo.core.context.AppContext
import io.askimo.core.event.ChatEvent
import io.askimo.core.event.DatabaseEvent
import io.askimo.core.event.EventBus
import io.askimo.core.event.StreamingEvent
import io.askimo.core.i18n.LocalizationManager
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
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing chat interactions in the desktop application.
 *
 * This service provides a bridge between the UI and the core chat functionality,
 * handling session management and message streaming without JLine dependencies.
 *
 * @param appContext The singleton Session instance injected by DI
 */
class ChatSessionManager(
    private val appContext: AppContext,
) {
    private val log = logger<ChatSessionManager>()

    companion object {
        private const val MAX_CONCURRENT_STREAMS = 10
    }

    // Coroutine scope for managing streaming jobs
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Thread registry: threadId -> StreamingThread
    private val activeThreads = ConcurrentHashMap<String, StreamingThread>()

    // Map: chatId -> threadId (to enforce ONE active thread per chat)
    private val chatToThreadMap = ConcurrentHashMap<String, String>()

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                shutdown()
            },
        )
    }

    /**
     * Represents a single thread for ONE question-answer pair.
     * Thread closes automatically after completion or failure.
     */
    data class StreamingThread(
        val threadId: String,
        val chatId: String,
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
     *
     * Creates a new thread for this question-answer pair.
     * Thread closes automatically after completion.
     *
     * @param userMessage The message from the user
     * @param chatId The chat ID for this message
     * @return threadId if streaming started successfully, null if chat already has active question or max concurrent streams reached
     */
    fun sendMessage(
        userMessage: String,
        chatId: String,
        onChunkReceived: (String) -> Unit = {},
    ): String? = startStream(chatId, userMessage, onChunkReceived)

    /**
     * Start streaming for a chat.
     * Creates a new thread for this question-answer pair.
     *
     * @param chatId The chat session ID
     * @param userMessage The user's message
     * @param onChunkReceived Callback for each chunk (optional)
     * @return threadId if started successfully, null if chat already has active question or max streams reached
     */
    private fun startStream(
        chatId: String,
        userMessage: String,
        onChunkReceived: (String) -> Unit,
    ): String? {
        val existingThreadId = chatToThreadMap[chatId]
        if (existingThreadId != null) {
            return null
        }

        if (activeThreads.size >= MAX_CONCURRENT_STREAMS) {
            return null
        }

        val threadId = "${chatId}_${System.currentTimeMillis()}"

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

        log.debug("Streaming thread $threadId assigned to chat $chatId started. Active threads: ${activeThreads.size}")

        // Emit stream started event
        EventBus.post(
            StreamingEvent.StreamStarted(
                chatId = chatId,
                threadId = threadId,
                userMessage = userMessage,
            ),
        )

        // IMPORTANT: Save user message to DB BEFORE starting async streaming
        // Use thread-safe method that saves to SPECIFIC chatId, not currentChatSession
        // This ensures true thread isolation - chatId is locked to this thread
        val promptWithContext = appContext.prepareContextAndGetPromptForChat(userMessage, chatId)
        EventBus.post(ChatEvent.ChatCreated(chatId = chatId, prompt = promptWithContext))

        // Start streaming in background thread
        serviceScope.launch(thread.job) {
            val startTime = System.currentTimeMillis()
            try {
                // Stream the response with token-by-token callback
                val fullResponse = appContext.getChatClient().sendStreamingMessageWithCallback(promptWithContext) { token ->
                    serviceScope.launch {
                        thread.appendChunk(token)
                        val currentContent = thread.getCurrentContent()
                        onChunkReceived(currentContent)
                    }
                }

                thread.markComplete()
                appContext.saveAiResponseToSession(fullResponse, chatId)
                EventBus.post(DatabaseEvent.SaveCompleted("Chat $chatId, Save AI Response successfully"))
                log.debug("Streaming thread $threadId completed successfully. Saved response to Chat $chatId.")

                // Emit stream completed event
                EventBus.post(
                    StreamingEvent.StreamCompleted(
                        chatId = chatId,
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
                        chatId = chatId,
                        threadId = threadId,
                        error = e.message ?: "Unknown error",
                        partialResponse = partialResponse.ifBlank { null },
                    ),
                )

                appContext.saveAiResponseToSession(failedResponse, chatId)
                log.error("Streaming thread $threadId failed for chat $chatId: ${e.message}", e)
            } finally {
                // Thread cleanup: remove from registry
                activeThreads.remove(threadId)
                chatToThreadMap.remove(chatId)
                log.debug("Thread $threadId cleaned up. Active threads: ${activeThreads.size}")
            }
        }

        return threadId
    }

    /**
     * Get an active thread by threadId.
     */
    fun getActiveThread(threadId: String): StreamingThread? = activeThreads[threadId]

    /**
     * Get the active thread for a chatId.
     */
    fun getActiveThreadForChat(chatId: String): StreamingThread? {
        val threadId = chatToThreadMap[chatId] ?: return null
        return activeThreads[threadId]
    }

    /**
     * Stop an active stream for a chat.
     */
    fun stopStream(chatId: String) {
        val threadId = chatToThreadMap[chatId]
        if (threadId != null) {
            log.info("Stopping stream for chat $chatId (thread $threadId)")
            val thread = activeThreads[threadId]
            thread?.job?.cancel()
            activeThreads.remove(threadId)
            chatToThreadMap.remove(chatId)
        } else {
            log.warn("No active stream found for chat $chatId")
        }
    }

    /**
     * Shutdown all streaming threads.
     */
    fun shutdown() {
        log.info("Shutting down ChatService. Cancelling ${activeThreads.size} active threads.")
        activeThreads.values.forEach { it.job.cancel() }
        activeThreads.clear()
        chatToThreadMap.clear()
    }

    /**
     * Clear the conversation memory.
     */
    fun clearMemory() {
        val provider = appContext.getActiveProvider()
        val modelName = appContext.params.getModel(provider)
        appContext.removeMemory(provider, modelName)
    }

    /**
     * Set the language directive based on user's locale selection.
     * This constructs a comprehensive instruction for the AI to communicate in the specified language,
     * with a fallback to English if the language is not supported by the AI.
     *
     * @param locale The user's selected locale (e.g., Locale.JAPANESE, Locale.ENGLISH)
     */
    fun setLanguageDirective(locale: Locale) {
        appContext.systemDirective = buildLanguageDirective(locale)
    }

    /**
     * Build a language directive instruction based on the locale.
     * Uses LocalizationManager to access localized templates.
     * Includes fallback to English if the AI doesn't support the target language.
     *
     * @param locale The target locale
     * @return A complete language directive with fallback instructions
     */
    private fun buildLanguageDirective(locale: Locale): String {
        // Temporarily set locale to get the correct translations
        val previousLocale = Locale.getDefault()
        LocalizationManager.setLocale(locale)

        try {
            val languageCode = locale.language

            // For English, use simplified template without fallback
            return if (languageCode == "en") {
                LocalizationManager.getString("language.directive.english.only")
            } else {
                // Get the language display name
                val languageDisplayName = LocalizationManager.getString("language.name.display")

                // Get templates and format with language name
                val instruction = LocalizationManager.getString(
                    "language.directive.instruction",
                    languageDisplayName,
                    languageDisplayName,
                    languageDisplayName,
                    languageDisplayName,
                )

                val fallback = LocalizationManager.getString(
                    "language.directive.fallback",
                    languageDisplayName,
                    languageDisplayName,
                    languageDisplayName,
                )

                instruction + fallback
            }
        } finally {
            // Restore previous locale if it was different
            if (previousLocale != locale) {
                LocalizationManager.setLocale(previousLocale)
            }
        }
    }
}

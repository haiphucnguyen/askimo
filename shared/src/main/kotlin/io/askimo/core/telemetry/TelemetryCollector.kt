/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.telemetry

import dev.langchain4j.model.output.TokenUsage
import io.askimo.core.logging.logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Collects telemetry data for RAG operations and LLM calls.
 * All data stays local - no external reporting.
 *
 * Thread-safe for concurrent access.
 */
class TelemetryCollector {
    private val log = logger<TelemetryCollector>()

    // Reactive state for UI
    private val _metricsFlow = MutableStateFlow(TelemetryMetrics.empty())
    val metricsFlow: StateFlow<TelemetryMetrics> = _metricsFlow.asStateFlow()

    // RAG Classification Metrics
    private val ragClassificationTotal = AtomicInteger(0)
    private val ragTriggered = AtomicInteger(0)
    private val ragSkipped = AtomicInteger(0)
    private val ragClassificationTotalTime = AtomicLong(0L)

    // RAG Retrieval Metrics
    private val ragRetrievalTotal = AtomicInteger(0)
    private val ragRetrievalTotalTime = AtomicLong(0L)
    private val ragChunksRetrievedTotal = AtomicInteger(0)

    // LLM Call Metrics (per provider)
    private val llmCallsByProvider = ConcurrentHashMap<String, AtomicInteger>()
    private val llmTokensByProvider = ConcurrentHashMap<String, AtomicLong>()
    private val llmDurationByProvider = ConcurrentHashMap<String, AtomicLong>()
    private val llmErrorsByProvider = ConcurrentHashMap<String, AtomicInteger>()

    init {
        // Load persisted telemetry data on initialization
        loadPersistedData()
    }

    /**
     * Loads persisted telemetry data and restores counters.
     */
    private fun loadPersistedData() {
        val loaded = TelemetryPersistenceManager.load()

        // Restore RAG metrics
        ragClassificationTotal.set(loaded.ragClassificationTotal)
        ragTriggered.set(loaded.ragTriggered)
        ragSkipped.set(loaded.ragSkipped)
        ragClassificationTotalTime.set(loaded.ragAvgClassificationTimeMs * loaded.ragClassificationTotal)

        ragRetrievalTotal.set(loaded.ragRetrievalTotal)
        ragRetrievalTotalTime.set(loaded.ragAvgRetrievalTimeMs * loaded.ragRetrievalTotal)
        ragChunksRetrievedTotal.set((loaded.ragAvgChunksRetrieved * loaded.ragRetrievalTotal).toInt())

        // Restore LLM metrics
        loaded.llmCallsByProvider.forEach { (key, calls) ->
            llmCallsByProvider[key] = AtomicInteger(calls)
        }
        loaded.llmTokensByProvider.forEach { (key, tokens) ->
            llmTokensByProvider[key] = AtomicLong(tokens)
        }
        loaded.llmAvgDurationMsByProvider.forEach { (key, avgDuration) ->
            val calls = loaded.llmCallsByProvider[key] ?: 0
            if (calls > 0) {
                llmDurationByProvider[key] = AtomicLong(avgDuration * calls)
            }
        }
        loaded.llmErrorsByProvider.forEach { (key, errors) ->
            llmErrorsByProvider[key] = AtomicInteger(errors)
        }

        // Update the flow with loaded data
        updateMetricsFlow()

        if (loaded != TelemetryMetrics.empty()) {
            log.debug("Restored telemetry: ${loaded.ragClassificationTotal} classifications, ${loaded.totalTokensUsed} tokens")
        }
    }

    /**
     * Records a RAG classification decision.
     */
    fun recordRAGClassification(triggered: Boolean, durationMs: Long) {
        ragClassificationTotal.incrementAndGet()
        ragClassificationTotalTime.addAndGet(durationMs)

        if (triggered) {
            ragTriggered.incrementAndGet()
            log.debug("RAG triggered (total: ${ragTriggered.get()}/${ragClassificationTotal.get()})")
        } else {
            ragSkipped.incrementAndGet()
            log.debug("RAG skipped (total: ${ragSkipped.get()}/${ragClassificationTotal.get()})")
        }
    }

    /**
     * Records a RAG retrieval operation.
     */
    fun recordRAGRetrieval(chunksRetrieved: Int, durationMs: Long) {
        ragRetrievalTotal.incrementAndGet()
        ragRetrievalTotalTime.addAndGet(durationMs)
        ragChunksRetrievedTotal.addAndGet(chunksRetrieved)

        log.debug("RAG retrieval: $chunksRetrieved chunks in ${durationMs}ms")
    }

    /**
     * Records an LLM call (from LangChain4J listener).
     */
    fun recordLLMCall(
        provider: String,
        model: String,
        tokenUsage: TokenUsage?,
        durationMs: Long,
    ) {
        val key = "$provider:$model"

        llmCallsByProvider.getOrPut(key) { AtomicInteger(0) }.incrementAndGet()
        llmDurationByProvider.getOrPut(key) { AtomicLong(0L) }.addAndGet(durationMs)

        tokenUsage?.let {
            llmTokensByProvider.getOrPut(key) { AtomicLong(0L) }.addAndGet(it.totalTokenCount().toLong())
        }

        log.debug("LLM call to $key: ${tokenUsage?.totalTokenCount() ?: 0} tokens in ${durationMs}ms")
        updateMetricsFlow()
    }

    /**
     * Records an LLM error.
     */
    fun recordLLMError(provider: String, model: String, error: Throwable) {
        val key = "$provider:$model"
        llmErrorsByProvider.getOrPut(key) { AtomicInteger(0) }.incrementAndGet()
        log.warn("LLM error for $key: ${error.message}")
    }

    /**
     * Gets current metrics snapshot.
     */
    fun getMetrics(): TelemetryMetrics {
        val classificationCount = ragClassificationTotal.get()
        val retrievalCount = ragRetrievalTotal.get()

        return TelemetryMetrics(
            // RAG Classification
            ragClassificationTotal = classificationCount,
            ragTriggered = ragTriggered.get(),
            ragSkipped = ragSkipped.get(),
            ragTriggeredPercent = if (classificationCount > 0) {
                (ragTriggered.get() * 100.0 / classificationCount)
            } else {
                0.0
            },
            ragAvgClassificationTimeMs = if (classificationCount > 0) {
                ragClassificationTotalTime.get() / classificationCount
            } else {
                0L
            },
            // RAG Retrieval
            ragRetrievalTotal = retrievalCount,
            ragAvgRetrievalTimeMs = if (retrievalCount > 0) {
                ragRetrievalTotalTime.get() / retrievalCount
            } else {
                0L
            },
            ragAvgChunksRetrieved = if (retrievalCount > 0) {
                ragChunksRetrievedTotal.get().toDouble() / retrievalCount
            } else {
                0.0
            },
            // LLM Calls
            llmCallsByProvider = llmCallsByProvider.mapValues { it.value.get() },
            llmTokensByProvider = llmTokensByProvider.mapValues { it.value.get() },
            llmAvgDurationMsByProvider = llmDurationByProvider.mapValues { (key, totalDuration) ->
                val calls = llmCallsByProvider[key]?.get() ?: 0
                if (calls > 0) totalDuration.get() / calls else 0L
            },
            llmErrorsByProvider = llmErrorsByProvider.mapValues { it.value.get() },
        )
    }

    /**
     * Updates the reactive state flow with current metrics and persists to disk.
     */
    private fun updateMetricsFlow() {
        val currentMetrics = getMetrics()
        _metricsFlow.value = currentMetrics

        // Persist to disk (async, non-blocking)
        TelemetryPersistenceManager.save(currentMetrics)
    }

    /**
     * Resets all metrics (useful for testing or per-session tracking).
     */
    fun reset() {
        ragClassificationTotal.set(0)
        ragTriggered.set(0)
        ragSkipped.set(0)
        ragClassificationTotalTime.set(0L)

        ragRetrievalTotal.set(0)
        ragRetrievalTotalTime.set(0L)
        ragChunksRetrievedTotal.set(0)

        llmCallsByProvider.clear()
        llmTokensByProvider.clear()
        llmDurationByProvider.clear()
        llmErrorsByProvider.clear()

        log.info("Telemetry metrics reset")

        // Update flow and delete persisted file
        updateMetricsFlow()
        TelemetryPersistenceManager.delete()
    }
}

/**
 * Snapshot of telemetry metrics at a point in time.
 */
@Serializable
data class TelemetryMetrics(
    // RAG Classification
    val ragClassificationTotal: Int,
    val ragTriggered: Int,
    val ragSkipped: Int,
    val ragTriggeredPercent: Double,
    val ragAvgClassificationTimeMs: Long,

    // RAG Retrieval
    val ragRetrievalTotal: Int,
    val ragAvgRetrievalTimeMs: Long,
    val ragAvgChunksRetrieved: Double,

    // LLM Calls
    val llmCallsByProvider: Map<String, Int>,
    val llmTokensByProvider: Map<String, Long>,
    val llmAvgDurationMsByProvider: Map<String, Long>,
    val llmErrorsByProvider: Map<String, Int>,
) {
    val totalTokensUsed: Long
        get() = llmTokensByProvider.values.sum()

    companion object {
        fun empty() = TelemetryMetrics(
            ragClassificationTotal = 0,
            ragTriggered = 0,
            ragSkipped = 0,
            ragTriggeredPercent = 0.0,
            ragAvgClassificationTimeMs = 0L,
            ragRetrievalTotal = 0,
            ragAvgRetrievalTimeMs = 0L,
            ragAvgChunksRetrieved = 0.0,
            llmCallsByProvider = emptyMap(),
            llmTokensByProvider = emptyMap(),
            llmAvgDurationMsByProvider = emptyMap(),
            llmErrorsByProvider = emptyMap(),
        )
    }
}

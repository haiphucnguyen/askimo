/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.plan

import io.askimo.core.event.Event
import io.askimo.core.event.EventSource
import io.askimo.core.event.EventType
import java.time.Instant

/**
 * Events emitted by [PlanExecutionListener] for each step in a running plan.
 *
 * All variants are [EventType.INTERNAL] so they flow through [io.askimo.core.event.EventBus.internalEvents].
 *
 * @param planId      The id of the [io.askimo.core.plan.domain.PlanDef] being executed.
 * @param stepName    The name / outputKey of the step agent (matches the YAML step id).
 * @param executionId The [io.askimo.core.plan.domain.PlanExecution] record id, for correlation.
 */
sealed class PlanStepEvent : Event {
    abstract val planId: String
    abstract val stepName: String
    abstract val executionId: String

    override val source: EventSource get() = EventSource.SYSTEM

    /**
     * Fired immediately before an agent step is invoked.
     *
     * @param inputs Subset of the scope state passed as inputs to this step.
     */
    data class Started(
        override val planId: String,
        override val stepName: String,
        override val executionId: String,
        val inputs: Map<String, Any> = emptyMap(),
        override val timestamp: Instant = Instant.now(),
    ) : PlanStepEvent() {
        override val type = EventType.INTERNAL
        override fun getDetails() = "[$planId] Step '$stepName' started"
    }

    /**
     * Fired after a step agent returns successfully.
     *
     * @param output The raw output object returned by the agent (usually a String).
     * @param durationMs Wall-clock time for this step in milliseconds.
     */
    data class Completed(
        override val planId: String,
        override val stepName: String,
        override val executionId: String,
        val output: Any?,
        val durationMs: Long = 0L,
        override val timestamp: Instant = Instant.now(),
    ) : PlanStepEvent() {
        override val type = EventType.INTERNAL
        override fun getDetails() = "[$planId] Step '$stepName' completed in ${durationMs}ms"
    }

    /**
     * Fired when an agent step throws an exception.
     *
     * @param error The exception that caused the step to fail.
     * @param durationMs Wall-clock time until failure in milliseconds.
     */
    data class Failed(
        override val planId: String,
        override val stepName: String,
        override val executionId: String,
        val error: Throwable,
        val durationMs: Long = 0L,
        override val timestamp: Instant = Instant.now(),
    ) : PlanStepEvent() {
        override val type = EventType.INTERNAL
        override fun getDetails() = "[$planId] Step '$stepName' failed after ${durationMs}ms: ${error.message}"
    }
}

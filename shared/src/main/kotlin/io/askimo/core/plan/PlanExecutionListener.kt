/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.plan

import dev.langchain4j.agentic.observability.AgentInvocationError
import dev.langchain4j.agentic.observability.AgentListener
import dev.langchain4j.agentic.observability.AgentRequest
import dev.langchain4j.agentic.observability.AgentResponse
import io.askimo.core.event.EventBus
import io.askimo.core.logging.logger
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Observability listener wired into [PlanExecutor].
 *
 * - Publishes [PlanStepEvent] on [EventBus.internalEvents] so the developer event log
 *   panel and any future progress UI can react in real time.
 * - Measures wall-clock duration per step.
 * - Collects completed step outputs in [stepOutputs] (ordered by completion time)
 *   so [PlanExecutor] can read the final result after execution completes.
 *
 * [inheritedBySubagents] = true means a single instance attached to the root agent
 * covers all nested step agents automatically.
 */
internal class PlanExecutionListener(
    private val planId: String,
    private val executionId: String,
) : AgentListener {

    private val log = logger<PlanExecutionListener>()

    /** nanoTime at step start, keyed by "agentId:stepName". */
    private val startTimes = ConcurrentHashMap<String, Long>()

    /**
     * Outputs of completed steps, in completion order.
     * Key = stepName (agent name / outputKey), value = output string.
     * Used by [PlanExecutor] to derive the final plan output.
     */
    val stepOutputs = CopyOnWriteArrayList<Pair<String, String>>()

    override fun inheritedBySubagents(): Boolean = true

    override fun beforeAgentInvocation(agentRequest: AgentRequest) {
        val stepName = agentRequest.agentName()
        startTimes[key(agentRequest.agentId(), stepName)] = System.nanoTime()

        val inputSummary = agentRequest.inputs()
            ?.entries?.take(5)
            ?.joinToString { (k, v) -> "$k=${truncate(v.toString())}" }
            ?: ""
        log.debug("[{}] ▶ step '{}' starting | inputs: {}", planId, stepName, inputSummary)

        post(
            PlanStepEvent.Started(
                planId = planId,
                stepName = stepName,
                executionId = executionId,
                inputs = agentRequest.inputs() ?: emptyMap(),
            ),
        )
    }

    override fun afterAgentInvocation(agentResponse: AgentResponse) {
        val stepName = agentResponse.agentName()
        val durationMs = elapsed(startTimes.remove(key(agentResponse.agentId(), stepName)))
        val output = agentResponse.output()
        val outputStr = output?.toString() ?: ""
        log.debug("[{}] ✔ step '{}' done in {}ms | output: {}", planId, stepName, durationMs, truncate(outputStr))

        // Record non-blank outputs so the executor can find the final result.
        if (outputStr.isNotBlank()) {
            stepOutputs.add(stepName to outputStr)
        }

        post(
            PlanStepEvent.Completed(
                planId = planId,
                stepName = stepName,
                executionId = executionId,
                output = output,
                durationMs = durationMs,
            ),
        )
    }

    override fun onAgentInvocationError(err: AgentInvocationError) {
        val stepName = err.agentName()
        val durationMs = elapsed(startTimes.remove(key(err.agentId(), stepName)))
        log.error("[{}] ✖ step '{}' failed in {}ms: {}", planId, stepName, durationMs, err.error().message, err.error())

        post(
            PlanStepEvent.Failed(
                planId = planId,
                stepName = stepName,
                executionId = executionId,
                error = err.error(),
                durationMs = durationMs,
            ),
        )
    }

    private fun key(agentId: String?, name: String) = "${agentId ?: "?"}:$name"

    private fun elapsed(startNs: Long?) = if (startNs != null) (System.nanoTime() - startNs) / 1_000_000L else 0L

    private fun truncate(s: String, max: Int = 200) = if (s.length <= max) s else "${s.take(max)}…"

    private fun post(event: PlanStepEvent) = runBlocking { EventBus.post(event) }
}

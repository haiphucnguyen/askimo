/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.plan

import dev.langchain4j.agentic.AgenticServices
import dev.langchain4j.agentic.UntypedAgent
import dev.langchain4j.model.chat.ChatModel
import io.askimo.core.logging.logger
import io.askimo.core.plan.domain.PlanDef
import io.askimo.core.plan.domain.PlanStep
import io.askimo.core.plan.domain.WorkflowNode
import java.util.concurrent.Executors

/**
 * Executes a [PlanDef] against a [ChatModel] using the LangChain4j Agentic API.
 *
 * ## How it works
 *
 * 1. Caller provides user-supplied input values (matching [PlanDef.inputs]).
 * 2. The executor walks the [WorkflowNode] tree and maps each node to the
 *    corresponding `AgenticServices` builder:
 *    - [WorkflowNode.Step]        → `AgenticServices.agentBuilder()` (one `UntypedAgent` per step)
 *    - [WorkflowNode.Sequence]    → `AgenticServices.sequenceBuilder()`
 *    - [WorkflowNode.Parallel]    → `AgenticServices.parallelBuilder()`
 *    - [WorkflowNode.Conditional] → `AgenticServices.conditionalBuilder()`
 * 3. Inputs are seeded into the shared `AgenticScope` via `UntypedAgent.invoke(Map)`.
 *    Each step reads what it needs from scope (prior outputs resolve `{{key}}` in the
 *    LangChain4j user-message template) and writes its own result back under `outputKey = stepId`.
 * 4. The final output is read from scope using the last step's id (or plan id as fallback).
 *
 * ## Tools
 * Each step receives a [PlanToolProvider] populated with its *effective* tool list:
 * - If [PlanStep.tools] is non-empty it is used as-is (step-level override).
 * - Otherwise [PlanDef.tools] is used as the plan-wide default.
 * - A step with no tools at either level runs without any tools attached.
 *
 * Only built-in [ToolRegistry] tools are resolved at this layer.
 * MCP tools are skipped until a plan-aware MCP wiring layer is added.
 */
class PlanExecutor(private val chatModel: ChatModel) {

    private val log = logger<PlanExecutor>()

/**
     * Executes the plan and returns the final AI-generated result as a string.
     *
     * @param plan        The parsed plan definition.
     * @param inputs      User-provided values keyed by [PlanInput.key].
     * @param executionId Optional [PlanExecution] record id — passed to [PlanExecutionListener]
     *                    so every [PlanStepEvent] is correlated back to the DB record.
     * @return The final output string produced by the last step in the workflow.
     */
    fun execute(plan: PlanDef, inputs: Map<String, String>, executionId: String = ""): String {
        validateInputs(plan, inputs)

        log.debug("Executing plan '{}' (execution={}) with inputs: {}", plan.id, executionId, inputs.keys)

        val scopeInputs: Map<String, Any> = inputs.toMap()

        // Build the root agent with the observability listener attached.
        // Because inheritedBySubagents() = true, every nested step agent automatically
        // inherits the same listener — no per-step wiring needed.
        val listener = PlanExecutionListener(planId = plan.id, executionId = executionId)
        val rootAgent: UntypedAgent = buildAgent(plan.workflow, plan.steps, plan, listener)

        // rootAgent.invoke() returns the root composite agent's result, which is null
        // for Sequence/Parallel wrappers — they don't expose a top-level output key.
        // Instead, we derive the final output from the listener's ordered step outputs:
        //   1. Try the last step id in the workflow (the "intended" final output).
        //   2. Fall back to the last completed step's output (handles parallel endings).
        rootAgent.invoke(scopeInputs)

        val lastStepId = lastLeafStepId(plan.workflow)
        val output = listener.stepOutputs.lastOrNull { (stepName, _) -> stepName == lastStepId }?.second
            ?: listener.stepOutputs.lastOrNull()?.second
            ?: ""

        logExecutionSummary(plan, output)

        return output
    }

    private fun validateInputs(plan: PlanDef, inputs: Map<String, String>) {
        val missing = plan.inputs
            .filter { it.required && inputs[it.key].isNullOrBlank() }
            .map { it.key }

        require(missing.isEmpty()) {
            "Plan '${plan.id}' is missing required inputs: ${missing.joinToString()}"
        }
    }

    private fun logExecutionSummary(plan: PlanDef, output: String) {
        if (output.isBlank()) {
            log.warn(
                "[{}] Plan completed but result is blank. " +
                    "Verify that the last workflow step returned a non-empty response.",
                plan.id,
            )
        } else {
            log.debug("[{}] Plan completed. Output length: {} chars", plan.id, output.length)
        }
    }

    /**
     * Returns the step id of the last leaf [WorkflowNode.Step] in execution order.
     * For a Sequence this is the last node's leaf; for a Parallel/Conditional it's the
     * last child's leaf. This is the step whose output is considered the plan's final result.
     */
    private fun lastLeafStepId(node: WorkflowNode): String? = when (node) {
        is WorkflowNode.Step -> node.stepId
        is WorkflowNode.Sequence -> lastLeafStepId(node.nodes.last())
        is WorkflowNode.Parallel -> node.nodes.mapNotNull { lastLeafStepId(it) }.lastOrNull()
        is WorkflowNode.Conditional -> lastLeafStepId(node.node)
    }

    /**
     * Recursively maps a [WorkflowNode] to a built [UntypedAgent].
     * [listener] is threaded through and attached to every leaf step agent.
     * Since [PlanExecutionListener.inheritedBySubagents] = true, attaching it on the
     * root is sufficient — but we attach it to every leaf for explicit clarity.
     */
    private fun buildAgent(
        node: WorkflowNode,
        steps: Map<String, PlanStep>,
        plan: PlanDef,
        listener: PlanExecutionListener? = null,
    ): UntypedAgent = when (node) {
        is WorkflowNode.Step -> buildStepAgent(node, steps, plan, listener)
        is WorkflowNode.Sequence -> buildSequenceAgent(node, steps, plan, listener)
        is WorkflowNode.Parallel -> buildParallelAgent(node, steps, plan, listener)
        is WorkflowNode.Conditional -> buildConditionalAgent(node, steps, plan, listener)
    }

    /**
     * The step's `message` is the user prompt template; `{{variable}}` placeholders
     * are resolved by LangChain4j from the shared `AgenticScope`.
     * The step result is stored in scope under the step's own `id` as `outputKey`.
     *
     * Effective tools = [PlanStep.tools] if non-empty, else [PlanDef.tools].
     * An empty effective list means no tool provider is attached.
     */
    private fun buildStepAgent(
        node: WorkflowNode.Step,
        steps: Map<String, PlanStep>,
        plan: PlanDef,
        listener: PlanExecutionListener? = null,
    ): UntypedAgent {
        val step = steps[node.stepId]
            ?: error("Plan references unknown step '${node.stepId}'")

        val builder = AgenticServices.agentBuilder()
            .chatModel(chatModel)
            .name(step.id)
            .outputKey(step.id)
            .userMessage(step.message)

        step.system?.let { builder.systemMessage(it) }

        listener?.let { builder.listener(it) }

        // Resolve effective tools: step-level overrides plan-level default.
        val effectiveTools = step.tools.ifEmpty { plan.tools }
        if (effectiveTools.isNotEmpty()) {
            log.debug("[{}] step '{}' tools: {}", plan.id, step.id, effectiveTools)
            builder.toolProvider(PlanToolProvider(effectiveTools))
        }

        return builder.build()
    }

    /**
     * Maps [WorkflowNode.Sequence] → `AgenticServices.sequenceBuilder()`.
     * Sub-agents execute one-by-one; each result is available in scope for the next.
     */
    private fun buildSequenceAgent(
        node: WorkflowNode.Sequence,
        steps: Map<String, PlanStep>,
        plan: PlanDef,
        listener: PlanExecutionListener? = null,
    ): UntypedAgent {
        val subAgents = node.nodes.map { buildAgent(it, steps, plan, listener) }.toTypedArray()

        return AgenticServices.sequenceBuilder()
            .subAgents(*subAgents)
            .build()
    }

    /**
     * Maps [WorkflowNode.Parallel] → `AgenticServices.parallelBuilder()`.
     * Sub-agents run concurrently; results are all written to scope before any dependent step.
     */
    private fun buildParallelAgent(
        node: WorkflowNode.Parallel,
        steps: Map<String, PlanStep>,
        plan: PlanDef,
        listener: PlanExecutionListener? = null,
    ): UntypedAgent {
        val subAgents = node.nodes.map { buildAgent(it, steps, plan, listener) }.toTypedArray()

        return AgenticServices.parallelBuilder()
            .subAgents(*subAgents)
            .outputKey(node.outputKey)
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build()
    }

    /**
     * Maps [WorkflowNode.Conditional] → `AgenticServices.conditionalBuilder()`.
     * The child agent only runs when the condition expression evaluates to `true` against the scope.
     */
    private fun buildConditionalAgent(
        node: WorkflowNode.Conditional,
        steps: Map<String, PlanStep>,
        plan: PlanDef,
        listener: PlanExecutionListener? = null,
    ): UntypedAgent {
        val childAgent = buildAgent(node.node, steps, plan, listener)
        val condition = node.condition

        return AgenticServices.conditionalBuilder()
            .subAgents(
                condition,
                { scope -> PlanConditionEvaluator.evaluate(condition, scope) },
                childAgent,
            )
            .build()
    }
}

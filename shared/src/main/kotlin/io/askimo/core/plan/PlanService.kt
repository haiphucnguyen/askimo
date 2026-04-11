/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.plan

import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import io.askimo.core.context.AppContext
import io.askimo.core.db.DatabaseManager
import io.askimo.core.logging.logger
import io.askimo.core.plan.domain.PlanDef
import io.askimo.core.plan.domain.PlanExecution
import io.askimo.core.plan.domain.PlanExecutionStatus
import io.askimo.core.plan.repository.PlanDefRepository
import io.askimo.core.plan.repository.PlanExecutionRepository
import java.time.LocalDateTime
import java.util.UUID

/**
 * Orchestrates the full lifecycle of a plan run.
 *
 * The service does NOT stream — it blocks until the whole plan finishes.
 * The streaming message will be supported in the future
 */
class PlanService(
    private val planDefRepository: PlanDefRepository = PlanDefRepository(),
    private val planExecutionRepository: PlanExecutionRepository = DatabaseManager.getInstance().getPlanExecutionRepository(),
    private val appContext: AppContext,
) {

    private val log = logger<PlanService>()

    /** Returns all available plans (built-ins + user plans), sorted by name. */
    fun getPlans(): List<PlanDef> = planDefRepository.getAll()

    /**
     * Returns the raw YAML string for a user plan by id, or null if not found.
     * Used by the YAML editor to pre-fill content when editing an existing plan.
     */
    fun loadYaml(id: String): String? = planDefRepository.loadYaml(id)

    /**
     * Saves a user plan from raw YAML.
     * Validates the YAML before writing to disk.
     *
     * @return [Result.success] with the parsed [PlanDef], or [Result.failure] with a validation error.
     */
    fun savePlan(yaml: String): Result<PlanDef> {
        val error = PlanYamlParser.validate(yaml)
        if (error != null) return Result.failure(IllegalArgumentException(error))
        return runCatching { planDefRepository.save(yaml) }
    }

    /**
     * Loads the YAML for [id] (user plan or built-in) and returns it with the id
     * suffix "-copy" so the duplicate can be saved as a new user plan.
     *
     * Returns null if the original plan YAML cannot be found.
     */
    fun loadYamlForDuplicate(id: String): String? {
        val yaml = planDefRepository.loadYamlForDuplicate(id) ?: return null
        return yaml
            // Give the copy a unique id so it doesn't shadow the original built-in
            .replace(Regex("(?m)^(id:\\s*)$id(\\s*)$"), "$1$id-copy$2")
            // Remove built_in: true so the duplicate is treated as a user plan
            .replace(Regex("(?m)^built_in:\\s*true\\s*\\n?"), "")
    }

    /**
     * Deletes a user plan by id. Built-in plans are silently skipped.
     *
     * @return `true` if the file was deleted, `false` if no user plan with that id existed.
     */
    fun deletePlan(id: String): Boolean {
        val plan = planDefRepository.findById(id)
        if (plan?.builtIn == true) {
            log.warn("Attempted to delete built-in plan '{}' — ignored", id)
            return false
        }
        return planDefRepository.delete(id)
    }

    /** Returns all execution records for a specific plan, newest first. */
    fun getExecutions(planId: String): List<PlanExecution> = planExecutionRepository.findByPlanId(planId)

    /**
     * Runs a plan synchronously and returns the final AI result.
     *
     * @param planId  Id of the plan to run.
     * @param inputs  User-supplied key→value pairs matching [PlanDef.inputs].
     * @return [Result.success] with the AI output string, or [Result.failure] with the error.
     */
    fun run(planId: String, inputs: Map<String, String>): Result<PlanRunResult> {
        val plan = planDefRepository.findById(planId)
            ?: return Result.failure(IllegalArgumentException("Plan not found: '$planId'"))

        val execution = planExecutionRepository.create(
            PlanExecution(
                id = UUID.randomUUID().toString(),
                planId = plan.id,
                planName = plan.name,
                inputs = inputs,
                status = PlanExecutionStatus.IDLE,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            ),
        )

        planExecutionRepository.updateStatus(execution.id, PlanExecutionStatus.RUNNING)
        log.info("Starting plan '{}' (execution={})", plan.id, execution.id)

        val chatModel = runCatching { appContext.createPlanChatModel() }.getOrElse { e ->
            val msg = "Failed to create chat model for plan '${plan.id}': ${e.message}"
            log.error(msg, e)
            planExecutionRepository.updateStatus(execution.id, PlanExecutionStatus.FAILED, msg)
            return Result.failure(IllegalStateException(msg, e))
        }

        val executor = PlanExecutor(chatModel)

        return runCatching {
            executor.execute(plan, inputs, execution.id)
        }.fold(
            onSuccess = { output ->
                planExecutionRepository.update(
                    execution.copy(
                        status = PlanExecutionStatus.COMPLETED,
                        output = output.takeIf { it.isNotBlank() },
                    ),
                )
                log.info("Plan '{}' (execution={}) completed successfully", plan.id, execution.id)
                Result.success(PlanRunResult(executionId = execution.id, output = output))
            },
            onFailure = { e ->
                val msg = e.message ?: e.javaClass.simpleName
                planExecutionRepository.updateStatus(execution.id, PlanExecutionStatus.FAILED, msg)
                log.error("Plan '{}' (execution={}) failed: {}", plan.id, execution.id, msg, e)
                Result.failure(e)
            },
        )
    }

    /** Deletes a single execution record. */
    fun deleteExecution(executionId: String) = planExecutionRepository.delete(executionId)

    /**
     * Runs a follow-up question against the result of a previous plan execution.
     *
     * The prior plan output is injected as context so the model can refine or extend it
     * without re-running the full multi-step workflow. The [PlanExecution] record's
     * [output] field is updated in place with the new answer, and [runCount] is incremented.
     *
     * @param executionId  The id of the previous [PlanExecution] whose output is the context.
     * @param followUpText The user's follow-up instruction (e.g. "Make it shorter").
     * @return [Result.success] with the new [PlanRunResult], or [Result.failure] with the error.
     */
    fun runFollowUp(executionId: String, followUpText: String): Result<PlanRunResult> {
        val execution = planExecutionRepository.findById(executionId)
            ?: return Result.failure(IllegalArgumentException("Execution not found: '$executionId'"))

        val priorOutput = execution.output?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("No prior output to follow up on"))

        val chatModel = runCatching { appContext.createPlanChatModel() }.getOrElse { e ->
            val msg = "Failed to create chat model for follow-up on execution '$executionId': ${e.message}"
            log.error(msg, e)
            return Result.failure(IllegalStateException(msg, e))
        }

        log.info("Running follow-up on execution '{}': {}", executionId, followUpText.take(80))

        val systemMessage = "You are continuing work on a previously generated result. " +
            "The user wants to refine or extend it. Reply with the complete updated result only."

        val userMessage = "Previous result:\n\n$priorOutput\n\n---\n\nUser request: $followUpText"

        return runCatching {
            val response = chatModel.chat(
                SystemMessage.from(systemMessage),
                UserMessage.from(userMessage),
            )
            response.aiMessage().text()
        }.fold(
            onSuccess = { newOutput ->
                val updated = planExecutionRepository.update(
                    execution.copy(
                        output = newOutput.takeIf { it.isNotBlank() } ?: priorOutput,
                        runCount = execution.runCount + 1,
                        status = PlanExecutionStatus.COMPLETED,
                    ),
                )
                log.info("Follow-up on execution '{}' completed, run #{}", executionId, updated.runCount)
                Result.success(PlanRunResult(executionId = executionId, output = newOutput))
            },
            onFailure = { e ->
                log.error("Follow-up on execution '{}' failed: {}", executionId, e.message, e)
                Result.failure(e)
            },
        )
    }
}

/**
 * Returned on a successful [PlanService.run] call.
 *
 * @param executionId The id of the [PlanExecution] record created for this run.
 * @param output      The final AI-generated text produced by the last workflow step.
 */
data class PlanRunResult(
    val executionId: String,
    val output: String,
)

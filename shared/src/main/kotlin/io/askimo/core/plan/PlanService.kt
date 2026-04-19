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

    /**
     * Generates Askimo plan YAML from a plain-English [description] using the active chat model.
     *
     * The model is instructed to output only valid YAML — no markdown fences, no explanation.
     * The result is stripped of any accidental code fences before being returned so it can be
     * fed directly into [PlanYamlParser.validate] and the editor.
     *
     * @param description A plain-English description of what the plan should do.
     * @return The generated YAML string, ready for the editor.
     * @throws Exception if the model call fails or returns blank output.
     */
    fun generateYamlFromPrompt(description: String): String {
        val chatModel = runCatching { appContext.createPlanChatModel() }.getOrElse { e ->
            throw IllegalStateException("Failed to create chat model: ${e.message}", e)
        }

        val systemPrompt = """
            You are an Askimo plan YAML generator.
            Given a plain-English description of a workflow, output ONLY valid Askimo plan YAML.
            Rules:
            - Output raw YAML only. No markdown fences, no explanation, no extra text.
            - Use kebab-case for the plan id derived from the name.
            - Every step id must be unique and referenced correctly in the workflow.
            - The workflow is optional; omit it for simple sequential plans (steps run top-to-bottom automatically).
            - Use {{stepId}} to reference a prior step's output in a subsequent step's message.
            - Use {{inputKey}} to reference an input value — e.g. {{topic}}, {{preferences}}. NEVER use {{inputs.topic}} or any prefix.
            - Supported input types: text, multiline, toggle, number.
            - Supported workflow node types: sequence, parallel, conditional, step.
            - ALWAYS wrap message and system values in double quotes. Escape any double quotes inside with \".
            - Use 'key' (not 'id') for input fields. ALWAYS include 'label' for every input — it is shown as the field caption in the UI.
            - Use 'hint' (not 'placeholder') for input hint text — it appears as grey placeholder text inside the field.
            - Every input should also have 'required: true' unless it is genuinely optional.
            - The 'icon' field MUST be a single emoji character (e.g. 💡 📊 ✍️ 🔍). Never use a text name like "lightbulb".
            Schema fields: id, name, description, icon, inputs[], steps{id,system?,message}, workflow?
        """.trimIndent()

        val response = chatModel.chat(
            SystemMessage.from(systemPrompt),
            UserMessage.from(description),
        )

        val raw = response.aiMessage().text().trim()
        check(raw.isNotBlank()) { "Model returned empty response" }

        // Strip accidental markdown code fences (```yaml ... ``` or ``` ... ```)
        val cleaned = raw
            .removePrefix("```yaml").removePrefix("```")
            .removeSuffix("```")
            .trim()

        // Replace text icon names with their emoji equivalents in case the model ignored the rule
        return replaceTextIconWithEmoji(cleaned)
    }

    /**
     * Replaces common text icon names produced by AI (e.g. `icon: "lightbulb"`) with their
     * emoji equivalents. Unrecognised names are replaced with a generic 📋 fallback.
     */
    private fun replaceTextIconWithEmoji(yaml: String): String {
        val iconNames = mapOf(
            "lightbulb" to "💡", "bulb" to "💡",
            "chart" to "📊", "bar_chart" to "📊", "graph" to "📊",
            "pencil" to "✍️", "edit" to "✍️", "write" to "✍️", "writing" to "✍️",
            "search" to "🔍", "magnify" to "🔍", "magnifying_glass" to "🔍",
            "clipboard" to "📋", "checklist" to "📋", "notepad" to "📋",
            "rocket" to "🚀", "launch" to "🚀",
            "star" to "⭐", "flag" to "🚩",
            "gear" to "⚙️", "settings" to "⚙️", "cog" to "⚙️",
            "book" to "📚", "books" to "📚", "document" to "📄", "file" to "📄",
            "email" to "📧", "mail" to "📧", "envelope" to "📧",
            "calendar" to "📅", "clock" to "🕐", "time" to "🕐",
            "trophy" to "🏆", "award" to "🏆",
            "brain" to "🧠", "idea" to "💡",
            "person" to "👤", "people" to "👥", "team" to "👥",
            "money" to "💰", "dollar" to "💰", "finance" to "💰",
            "computer" to "💻", "code" to "💻", "laptop" to "💻",
            "phone" to "📱", "mobile" to "📱",
            "camera" to "📷", "image" to "🖼️", "photo" to "📷",
            "map" to "🗺️", "location" to "📍", "pin" to "📍",
            "lock" to "🔒", "key" to "🔑", "security" to "🔒",
            "heart" to "❤️", "love" to "❤️",
            "fire" to "🔥", "lightning" to "⚡", "thunder" to "⚡",
            "cloud" to "☁️", "sun" to "☀️", "moon" to "🌙",
            "recycle" to "♻️", "refresh" to "🔄", "loop" to "🔄",
            "warning" to "⚠️", "alert" to "⚠️", "danger" to "⚠️",
            "check" to "✅", "checkmark" to "✅", "done" to "✅",
            "cross" to "❌", "x" to "❌", "no" to "❌",
            "info" to "ℹ️", "information" to "ℹ️",
            "question" to "❓", "help" to "❓",
            "chat" to "💬", "message" to "💬", "comment" to "💬",
            "link" to "🔗", "chain" to "🔗",
            "download" to "⬇️", "upload" to "⬆️",
            "play" to "▶️", "pause" to "⏸️", "stop" to "⏹️",
        )
        // Match: icon: "some-text-name" or icon: some-text-name (no emoji unicode ranges)
        return yaml.replace(Regex("""(?m)^(\s*icon\s*:\s*)"?([A-Za-z_\-]+)"?\s*$""")) { match ->
            val indent = match.groupValues[1]
            val name = match.groupValues[2].lowercase().replace("-", "_")
            val emoji = iconNames[name] ?: iconNames.entries
                .firstOrNull { name.contains(it.key) }?.value
                ?: "📋"
            "${indent}\"$emoji\""
        }
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

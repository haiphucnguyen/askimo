/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.ui.plan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.askimo.core.event.EventBus
import io.askimo.core.logging.logger
import io.askimo.core.plan.PlanRunResult
import io.askimo.core.plan.PlanService
import io.askimo.core.plan.PlanStepEvent
import io.askimo.core.plan.PlanYamlParser
import io.askimo.core.plan.domain.PlanDef
import io.askimo.core.plan.domain.PlanExecution
import io.askimo.ui.common.preferences.ApplicationPreferences
import io.askimo.ui.plan.PlanExportService.ExportFormat
import io.askimo.ui.plan.PlanExportService.ExportMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel for the Plans gallery and execution flow.
 *
 * Lifecycle:
 * 1. [loadPlans] — populates [plans] list on the gallery screen.
 * 2. [selectPlan] — sets [selectedPlan] and pre-fills [inputValues] with defaults.
 * 3. [runPlan]   — validates required inputs, runs via [PlanService], sets [runResult].
 * 4. [clearResult] — resets to the gallery / re-run state.
 * 5. [startNewPlan] / [startEditPlan] — opens the YAML editor.
 * 6. [savePlan] — validates + persists the YAML, reloads gallery.
 *
 * Per-plan state caching: while a plan is running, or has a pending result that hasn't
 * been persisted to history yet, the run state (inputs, result, error, step progress) is
 * cached in [planStateCache] keyed by plan ID. This lets the user navigate to another plan
 * and come back without losing in-flight or just-completed state. The cache entry is evicted
 * as soon as the run finishes (success or failure) so the user always sees fresh history on
 * the next visit.
 */
class PlansViewModel(
    private val scope: CoroutineScope,
    private val planService: PlanService,
) {

    private val log = logger<PlansViewModel>()

    var plans by mutableStateOf<List<PlanDef>>(emptyList())
        private set

    var isLoadingPlans by mutableStateOf(false)
        private set

    var plansError by mutableStateOf<String?>(null)
        private set

    /** Which tab is selected in the gallery: true = Built-in, false = My Plans only. */
    var galleryShowAll by mutableStateOf(true)
        private set

    fun selectGalleryTab(showAll: Boolean) {
        galleryShowAll = showAll
    }

    var selectedPlan by mutableStateOf<PlanDef?>(null)
        private set

    /** Mutable map of input key → current text value, pre-filled from PlanInput.default. */
    var inputValues by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var inputErrors by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var isRunning by mutableStateOf(false)
        private set

    /** Populated on successful run; null if no run has completed yet. */
    var runResult by mutableStateOf<PlanRunResult?>(null)
        private set

    var runError by mutableStateOf<String?>(null)
        private set

    var exportError by mutableStateOf<String?>(null)
        private set

    /** Text the user has typed in the follow-up input box. */
    var followUpText by mutableStateOf("")
        private set

    /** True while a follow-up request is in flight. */
    var isFollowingUp by mutableStateOf(false)
        private set

    /** Error message from the most recent follow-up attempt; null on success. */
    var followUpError by mutableStateOf<String?>(null)
        private set

    /**
     * A previously-completed result that the user has "pinned" for comparison
     * against the current run. Null when nothing is pinned.
     */
    var pinnedResult by mutableStateOf<PlanRunResult?>(null)
        private set

    /** Pin the given [PlanExecution]'s output for side-by-side comparison. */
    fun pinExecution(execution: PlanExecution) {
        val output = execution.output?.takeIf { it.isNotBlank() } ?: return
        pinnedResult = PlanRunResult(executionId = execution.id, output = output)
    }

    /** Remove the pinned comparison result. */
    fun unpinResult() {
        pinnedResult = null
    }

    /**
     * Live agentic step progress for the current (or most recent) run.
     * Each entry is a [PlanStepEvent] received from [EventBus.internalEvents].
     * Cleared when a new run starts via [runPlan].
     */
    var stepProgress by mutableStateOf<List<PlanStepEvent>>(emptyList())
        private set

    /** Tracks the executionId of the currently active run for event filtering. */
    private var activeExecutionId: String? = null

    var executions by mutableStateOf<List<PlanExecution>>(emptyList())
        private set

    /** null = creating a new plan; non-null = editing an existing user plan by id. */
    var editingPlanId by mutableStateOf<String?>(null)
        private set

    /** Raw YAML text in the editor text field. */
    var editorYaml by mutableStateOf("")
        private set

    /** Validation error from [PlanYamlParser.validate]; null means the YAML is valid. */
    var editorValidationError by mutableStateOf<String?>(null)
        private set

    /** True while [savePlan] is running. */
    var isSaving by mutableStateOf(false)
        private set

    /** Populated on save error (network/IO); cleared when the user edits again. */
    var saveError by mutableStateOf<String?>(null)
        private set

    /**
     * Snapshot of the mutable run state for a plan that was navigated away from
     * while a run was in progress or had just completed.
     *
     * Entries are evicted once the run finishes so subsequent visits start fresh
     * and the user sees the latest history instead.
     */
    private data class PlanStateSnapshot(
        val inputValues: Map<String, String>,
        val runResult: PlanRunResult?,
        val runError: String?,
        val stepProgress: List<PlanStepEvent>,
        val activeExecutionId: String?,
        val isRunning: Boolean,
    )

    private val planStateCache = mutableMapOf<String, PlanStateSnapshot>()

    fun loadPlans() {
        scope.launch {
            isLoadingPlans = true
            plansError = null
            runCatching {
                withContext(Dispatchers.IO) { planService.getPlans() }
            }.fold(
                onSuccess = { plans = it },
                onFailure = {
                    plansError = it.message
                    log.error("Failed to load plans", it)
                },
            )
            isLoadingPlans = false
        }
    }

    fun selectPlan(plan: PlanDef) {
        // Persist current plan's live state before switching (only while running)
        val previousPlan = selectedPlan
        if (previousPlan != null && previousPlan.id != plan.id && isRunning) {
            planStateCache[previousPlan.id] = PlanStateSnapshot(
                inputValues = inputValues,
                runResult = runResult,
                runError = runError,
                stepProgress = stepProgress,
                activeExecutionId = activeExecutionId,
                isRunning = true,
            )
        }

        selectedPlan = plan
        inputErrors = emptyMap()
        exportError = null

        // Restore cached state if available (e.g. navigated away while running)
        val cached = planStateCache[plan.id]
        if (cached != null) {
            inputValues = cached.inputValues
            runResult = cached.runResult
            runError = cached.runError
            stepProgress = cached.stepProgress
            activeExecutionId = cached.activeExecutionId
        } else {
            val defaults = plan.inputs.associate { it.key to it.default }
            val persisted = ApplicationPreferences.getPlanInputs(plan.id)
            inputValues = defaults + persisted
            runResult = null
            runError = null
            stepProgress = emptyList()
            activeExecutionId = null
        }

        loadExecutionsForPlan(plan.id)
    }

    fun clearSelection() {
        selectedPlan = null
        inputValues = emptyMap()
        inputErrors = emptyMap()
        runResult = null
        runError = null
    }

    fun updateInput(key: String, value: String) {
        val sanitized = value.replace("\u0000", "")
        inputValues = inputValues + (key to sanitized)
        // Live validation for number type
        val inputDef = selectedPlan?.inputs?.find { it.key == key }
        val newErrors = inputErrors.toMutableMap()
        if (inputDef?.type == "number" && sanitized.isNotBlank() && sanitized.toDoubleOrNull() == null) {
            newErrors[key] = "${inputDef.label} must be a number"
        } else {
            newErrors.remove(key)
        }
        inputErrors = newErrors
        selectedPlan?.id?.let { ApplicationPreferences.setPlanInputs(it, inputValues) }
    }

    fun runPlan() {
        val plan = selectedPlan ?: return

        // Validate required inputs + type coercion
        val errors = mutableMapOf<String, String>()
        plan.inputs.forEach { input ->
            val v = inputValues[input.key].orEmpty()
            if (input.required && v.isBlank()) {
                errors[input.key] = "${input.label} is required"
            } else if (input.type == "number" && v.isNotBlank() && v.toDoubleOrNull() == null) {
                errors[input.key] = "${input.label} must be a number"
            }
        }
        if (errors.isNotEmpty()) {
            inputErrors = errors
            return
        }

        scope.launch {
            isRunning = true
            runError = null
            stepProgress = emptyList()
            activeExecutionId = null

            // Subscribe to step events for this run — filter by executionId once we have it.
            // We open the collector before starting execution so we never miss the first event.
            val progressJob = launch {
                EventBus.internalEvents
                    .filterIsInstance<PlanStepEvent>()
                    .collect { event ->
                        val execId = activeExecutionId
                        // Accept events that match our active execution, or before we know it yet
                        // (first Started event tells us the executionId)
                        if (execId == null || event.executionId == execId) {
                            if (execId == null) activeExecutionId = event.executionId
                            stepProgress = stepProgress + event
                        }
                    }
            }

            runCatching {
                withContext(Dispatchers.IO) { planService.run(plan.id, inputValues) }
            }.fold(
                onSuccess = { result ->
                    result.fold(
                        onSuccess = { runResult = it },
                        onFailure = { runError = it.message ?: "Plan failed" },
                    )
                },
                onFailure = {
                    runError = it.message ?: "Unexpected error"
                    log.error("Plan run failed for '${plan.id}'", it)
                },
            )

            progressJob.cancel()
            isRunning = false

            // The result is now persisted to history; the next visit to this plan
            // should start fresh so the user sees the updated execution list.
            planStateCache.remove(plan.id)

            loadExecutionsForPlan(plan.id)
        }
    }

    fun clearResult() {
        runResult = null
        runError = null
        stepProgress = emptyList()
        activeExecutionId = null
        pinnedResult = null
        followUpText = ""
        followUpError = null
        selectedPlan?.id?.let { planStateCache.remove(it) }
    }

    /** Updates the follow-up input text. */
    fun updateFollowUpText(text: String) {
        followUpText = text
        followUpError = null
    }

    /**
     * Sends [followUpText] as a follow-up to the current [runResult].
     *
     * The previous result is used as context; the AI response replaces [runResult]
     * in the UI and updates the persisted [PlanExecution] output in place.
     * The execution's [runCount] is incremented so history reflects the iteration.
     */
    fun runFollowUp() {
        val executionId = runResult?.executionId ?: return
        val text = followUpText.trim()
        if (text.isBlank()) return

        scope.launch {
            isFollowingUp = true
            followUpError = null

            runCatching {
                withContext(Dispatchers.IO) { planService.runFollowUp(executionId, text) }
            }.fold(
                onSuccess = { result ->
                    result.fold(
                        onSuccess = { newResult ->
                            runResult = newResult
                            followUpText = ""
                            selectedPlan?.id?.let { loadExecutionsForPlan(it) }
                        },
                        onFailure = { followUpError = it.message ?: "Follow-up failed" },
                    )
                },
                onFailure = {
                    followUpError = it.message ?: "Unexpected error"
                    log.error("Follow-up failed for execution '$executionId'", it)
                },
            )

            isFollowingUp = false
        }
    }

    /**
     * Exports the current run result to a file.
     */
    fun exportPlan(
        targetFile: File,
        mode: ExportMode,
        format: ExportFormat,
        onDone: (File?) -> Unit,
    ) {
        val plan = selectedPlan ?: return
        val result = runResult ?: return
        scope.launch {
            val exported = runCatching {
                withContext(Dispatchers.IO) {
                    PlanExportService.export(
                        plan = plan,
                        inputValues = inputValues,
                        stepProgress = stepProgress,
                        result = result,
                        mode = mode,
                        format = format,
                        targetFile = targetFile,
                    )
                }
                targetFile
            }.getOrElse { e ->
                log.error("Export failed for plan '${plan.id}'", e)
                exportError = e.message ?: "Export failed"
                null
            }
            onDone(exported)
        }
    }

    fun deletePlan(planId: String) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { planService.deletePlan(planId) }
            }
            planStateCache.remove(planId)
            loadPlans()
        }
    }

    fun deleteExecution(executionId: String) {
        val planId = selectedPlan?.id ?: return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { planService.deleteExecution(executionId) }
            }
            loadExecutionsForPlan(planId)
        }
    }

    /**
     * Restores the input values from a previous [PlanExecution] into the input form,
     * merging with the plan's defaults so any keys not present in the execution are kept.
     * Also restores the result or error so the main view reflects what that run produced.
     */
    fun restoreInputs(execution: PlanExecution) {
        val plan = selectedPlan ?: return
        val defaults = plan.inputs.associate { it.key to it.default }
        inputValues = defaults + execution.inputs
        inputErrors = emptyMap()
        stepProgress = emptyList()
        activeExecutionId = null
        val output = execution.output?.takeIf { it.isNotBlank() }
        when {
            output != null -> {
                runResult = PlanRunResult(executionId = execution.id, output = output)
                runError = null
            }

            execution.errorMessage != null -> {
                runResult = null
                runError = execution.errorMessage
            }

            else -> {
                runResult = null
                runError = null
            }
        }
    }

    /** Opens the editor to create a brand-new plan with a starter template. */
    fun startNewPlan() {
        editingPlanId = null
        editorYaml = STARTER_TEMPLATE
        editorValidationError = PlanYamlParser.validate(STARTER_TEMPLATE)
        saveError = null
    }

    /** Opens the editor to edit an existing user plan (loaded from its YAML on disk). */
    fun startEditPlan(planId: String) {
        scope.launch {
            val yaml = withContext(Dispatchers.IO) {
                runCatching { planService.loadYaml(planId) }.getOrNull() ?: ""
            }
            editingPlanId = planId
            editorYaml = yaml
            editorValidationError = if (yaml.isBlank()) "Plan YAML not found" else PlanYamlParser.validate(yaml)
            saveError = null
        }
    }

    /**
     * Loads the YAML for [planId] (works for both built-ins and user plans),
     * rewrites the id to `<id>-copy`, and opens the YAML editor pre-filled so
     * the user can customise and save a new plan based on the original.
     */
    fun startDuplicatePlan(planId: String) {
        scope.launch {
            val yaml = withContext(Dispatchers.IO) {
                runCatching { planService.loadYamlForDuplicate(planId) }.getOrNull() ?: ""
            }
            editingPlanId = null // treat as new plan (no existing id to overwrite)
            editorYaml = yaml
            editorValidationError = if (yaml.isBlank()) "Plan YAML not found" else PlanYamlParser.validate(yaml)
            saveError = null
        }
    }

    /** Updates the editor text and runs live validation. */
    fun updateEditorYaml(yaml: String) {
        editorYaml = yaml
        editorValidationError = PlanYamlParser.validate(yaml)
        saveError = null
    }

    /**
     * Validates and saves the current [editorYaml].
     * On success calls [onSuccess] (typically navigate back to gallery).
     */
    fun savePlan(onSuccess: () -> Unit) {
        if (editorValidationError != null) return
        scope.launch {
            isSaving = true
            saveError = null
            runCatching {
                withContext(Dispatchers.IO) { planService.savePlan(editorYaml) }
            }.fold(
                onSuccess = { result ->
                    result.fold(
                        onSuccess = {
                            loadPlans()
                            onSuccess()
                        },
                        onFailure = { saveError = it.message ?: "Save failed" },
                    )
                },
                onFailure = {
                    saveError = it.message ?: "Unexpected error"
                    log.error("Failed to save plan YAML", it)
                },
            )
            isSaving = false
        }
    }

    /** Discards editor state (called on Cancel). */
    fun cancelEdit() {
        editingPlanId = null
        editorYaml = ""
        editorValidationError = null
        saveError = null
    }

    private fun loadExecutionsForPlan(planId: String) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { planService.getExecutions(planId) }
            }.onSuccess { executions = it }
        }
    }

    companion object {
        /** Minimal valid YAML shown when the user creates a new plan. */
        val STARTER_TEMPLATE = """
            |id: my-plan
            |name: My Plan
            |description: What this plan does
            |inputs:
            |  - key: topic
            |    label: Topic
            |    required: true
            |steps:
            |  - id: analyse
            |    message: "Analyse {{topic}} and provide a summary."
        """.trimMargin()
    }
}

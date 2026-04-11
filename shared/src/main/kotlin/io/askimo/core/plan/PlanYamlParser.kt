/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.plan

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.askimo.core.plan.domain.PlanDef
import io.askimo.core.plan.domain.PlanInput
import io.askimo.core.plan.domain.PlanStep
import io.askimo.core.plan.domain.WorkflowNode
import java.io.File
import java.io.InputStream
import java.nio.file.Path

/**
 * Parses a YAML file into a [PlanDef].
 *
 * Supports two styles for the `steps` block:
 *
 * ### Style 1 — Inline list (simple sequential plans)
 * Steps are declared as an ordered list. The parser infers a sequential workflow
 * from declaration order — no `workflow` block needed.
 * ```yaml
 * id: report
 * name: Report
 * steps:
 *   - id: research
 *     message: "Research {{topic}}"
 *   - id: write
 *     message: "Write report. Research: {{research}}"
 * ```
 *
 * ### Style 2 — Explicit map + workflow block (complex plans)
 * Steps are declared as a keyed map and a `workflow` block controls execution topology.
 * ```yaml
 * id: travel-planner
 * name: Travel Planner
 * steps:
 *   assess:
 *     message: "Assess {{destination}}"
 *   itinerary:
 *     message: "Create itinerary"
 *   flights:
 *     message: "Find flights"
 * workflow:
 *   type: sequence
 *   nodes:
 *     - type: step
 *       stepId: assess
 *     - type: parallel
 *       outputKey: research
 *       nodes:
 *         - type: step
 *           stepId: itinerary
 *         - type: conditional
 *           condition: "include_flights == true"
 *           node:
 *             type: step
 *             stepId: flights
 * ```
 *
 * ### Rules
 * - No `workflow` block → infer sequential workflow from step declaration order
 * - `workflow` block present → use it; list order is ignored
 */
object PlanYamlParser {

    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    fun parse(yaml: String): PlanDef = toModel(mapper.readValue(yaml, PlanYaml::class.java))

    fun parse(file: File): PlanDef = toModel(mapper.readValue(file, PlanYaml::class.java))

    fun parse(path: Path): PlanDef = parse(path.toFile())

    fun parse(stream: InputStream): PlanDef = toModel(mapper.readValue(stream, PlanYaml::class.java))

    /** Validates YAML without fully converting — returns error message or null if valid. */
    fun validate(yaml: String): String? = runCatching {
        val raw = mapper.readValue(yaml, PlanYaml::class.java)
        val errors = mutableListOf<String>()

        if (raw.id.isBlank()) errors += "Missing required field: id"
        if (raw.name.isBlank()) errors += "Missing required field: name"
        if (raw.steps.isEmpty()) errors += "Plan must have at least one step"

        // Validate workflow step references
        raw.workflow?.let { validateWorkflowRefs(it, raw.steps.keys, errors) }

        errors.joinToString("; ").takeIf { it.isNotBlank() }
    }.getOrElse { e -> "Invalid YAML: ${e.message}" }

    private fun toModel(raw: PlanYaml): PlanDef {
        val steps = raw.steps.mapValues { (id, s) ->
            PlanStep(
                id = id,
                system = s.system,
                message = s.message,
                tools = s.tools,
            )
        }

        // If no workflow is declared, default to a sequence over all steps in declaration order
        val workflow = raw.workflow ?: defaultWorkflow(steps.keys.toList())

        return PlanDef(
            id = raw.id,
            name = raw.name,
            icon = raw.icon,
            description = raw.description,
            inputs = raw.inputs.map { i ->
                PlanInput(
                    key = i.key,
                    label = i.label,
                    type = i.type,
                    options = i.options,
                    default = i.default,
                    required = i.required,
                    hint = i.hint,
                )
            },
            tools = raw.tools,
            steps = steps,
            workflow = workflow,
            builtIn = raw.builtIn,
        )
    }

    /** Fallback: sequence over all steps in declaration order. */
    private fun defaultWorkflow(stepIds: List<String>): WorkflowNode = when {
        stepIds.size == 1 -> WorkflowNode.Step(stepIds.first())
        else -> WorkflowNode.Sequence(stepIds.map { WorkflowNode.Step(it) })
    }

    private fun validateWorkflowRefs(
        node: WorkflowNode,
        knownStepIds: Set<String>,
        errors: MutableList<String>,
    ) {
        when (node) {
            is WorkflowNode.Step ->
                if (node.stepId !in knownStepIds) {
                    errors += "Workflow references unknown step: '${node.stepId}'"
                }

            is WorkflowNode.Sequence -> node.nodes.forEach { validateWorkflowRefs(it, knownStepIds, errors) }

            is WorkflowNode.Parallel -> node.nodes.forEach { validateWorkflowRefs(it, knownStepIds, errors) }

            is WorkflowNode.Conditional -> validateWorkflowRefs(node.node, knownStepIds, errors)
        }
    }
}

// ── Raw YAML DTOs ─────────────────────────────────────────────────────────────
// These mirror the YAML structure 1:1 and are only used internally by the parser.

@JsonIgnoreProperties(ignoreUnknown = true)
private data class PlanYaml(
    val id: String = "",
    val name: String = "",
    val icon: String = "",
    val description: String = "",
    val inputs: List<PlanInputYaml> = emptyList(),
    val tools: List<String> = emptyList(),
    @JsonDeserialize(using = StepsDeserializer::class)
    val steps: Map<String, PlanStepYaml> = emptyMap(),
    val workflow: WorkflowNode? = null,
    @param:JsonProperty("built_in")
    val builtIn: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class PlanInputYaml(
    val key: String = "",
    val label: String = "",
    val type: String = "text",
    val options: List<String> = emptyList(),
    val default: String = "",
    val required: Boolean = false,
    val hint: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class PlanStepYaml(
    val id: String = "",
    val system: String? = null,
    val message: String = "",
    val tools: List<String> = emptyList(),
)

/**
 * Deserializes the `steps` field from either:
 * - **List style**: `[{id, message, ...}, ...]` — inline sequential, order preserved
 * - **Map style**: `{stepId: {message, ...}, ...}` — explicit keys, order preserved
 *
 * Both forms are normalised into a `LinkedHashMap<String, PlanStepYaml>` so the
 * rest of the parser is style-agnostic.
 */
private class StepsDeserializer : StdDeserializer<Map<String, PlanStepYaml>>(Map::class.java) {

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Map<String, PlanStepYaml> {
        val result = LinkedHashMap<String, PlanStepYaml>()
        val codec = p.codec as ObjectMapper

        return when (p.currentToken) {
            // List style: - id: research\n  message: "..."
            JsonToken.START_ARRAY -> {
                val steps = codec.readValue(p, codec.typeFactory.constructCollectionType(List::class.java, PlanStepYaml::class.java))
                    as List<PlanStepYaml>
                steps.associateByTo(result) { it.id }
                result
            }

            // Map style: research:\n  message: "..."
            JsonToken.START_OBJECT -> {
                val raw = codec.readValue(
                    p,
                    codec.typeFactory.constructMapType(
                        LinkedHashMap::class.java,
                        String::class.java,
                        PlanStepYaml::class.java,
                    ),
                ) as LinkedHashMap<String, PlanStepYaml>
                raw
            }

            else -> result
        }
    }
}

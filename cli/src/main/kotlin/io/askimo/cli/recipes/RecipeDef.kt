/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.recipes

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
/**
 * Defines a recipe that can be executed by Askimo.
 *
 * Instances are typically loaded from YAML (see templates), then consumed by the
 * recipe registry/executor to render prompts and optionally run post actions.
 *
 * @property name Unique identifier for the recipe.
 * @property version Schema version of the recipe definition (default is 3).
 * @property description Optional human‑readable description of what the recipe does.
 * @property allowedTools Whitelist of tool names that the recipe is allowed to use. If empty, ALL tools are allowed.
 * @property vars Map of variable placeholders to tool calls used to compute their values.
 * @property system The system prompt provided to the chat model.
 * @property userTemplate The user prompt template rendered with resolved variables.
 * @property postActions Actions to run after the model response is received.
 * @property defaults Default values for variables when not otherwise provided.
 */
data class RecipeDef(
    val name: String,
    val version: Int = 3,
    val description: String? = null,
    val allowedTools: List<String> = emptyList(), // empty ⇒ unrestricted
    val vars: Map<String, VarCall> = emptyMap(),
    val system: String,
    val userTemplate: String,
    val postActions: List<PostAction> = emptyList(),
    val defaults: Map<String, String> = emptyMap(),
)

/**
 * Describes a tool invocation used to compute a variable or perform an action.
 *
 * The arguments may be any JSON/YAML compatible structure (list, map, or string).
 *
 * @property tool Name of the tool to call.
 * @property args Arguments for the tool. Can be a List, Map, String, or null.
 */
data class VarCall(
    val tool: String,
    val args: Any? = null,
)

/**
 * Post-execution action that can be conditionally triggered after a recipe runs.
 *
 * Note: The field is named `when_` in Kotlin because `when` is a reserved keyword.
 * In YAML it is written as `when` and mapped manually as needed.
 *
 * @property when_ Optional condition expression controlling whether the action runs.
 * @property call The tool invocation to execute when the condition is met.
 */
data class PostAction(
    val when_: String? = null, // named 'when' in YAML; map it manually if needed
    val call: VarCall,
)

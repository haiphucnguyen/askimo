/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.plan.domain

/**
 * Declares a user-facing input variable for a [PlanDef].
 *
 * The [key] is used as the placeholder name in step prompts — e.g. a key of
 * `destination` is referenced in YAML as `{{destination}}`.
 *
 * The [type] drives the UI control rendered in the Plan Executor input panel.
 */
data class PlanInput(
    /** Variable name — referenced in prompts as `{{key}}`. */
    val key: String,

    /** Human-readable label shown in the input panel. */
    val label: String,

    /**
     * UI control type.
     * Supported values: `text`, `select`, `toggle`, `number`, `multiline`.
     */
    val type: String = "text",

    /** Options for `type: select`. Empty for all other types. */
    val options: List<String> = emptyList(),

    /** Pre-filled value shown before the user edits. */
    val default: String = "",

    /** Whether the plan refuses to run if this input is blank. */
    val required: Boolean = false,

    /** Optional helper text shown below the input field. */
    val hint: String = "",
)

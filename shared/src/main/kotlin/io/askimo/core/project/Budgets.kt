/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

/**
 * Guardrail limits for an edit session
 */
data class Budgets(
    val maxFiles: Int = 3,
    val maxChangedLines: Int = 300,
    val allowDirty: Boolean = false,
    val applyDirect: Boolean = false,
)

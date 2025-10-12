package io.askimo.core.project

/**
 * Guardrail limits for an edit session
 */
data class Budgets(
    val maxFiles: Int = 3,
    val maxChangedLines: Int = 300,
    val allowDirty: Boolean = false,
    val applyDirect: Boolean = false // set true if you add a --write flag to skip the confirm prompt
)
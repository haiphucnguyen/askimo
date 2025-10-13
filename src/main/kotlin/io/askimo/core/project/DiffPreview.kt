/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

object DiffPreview {
    fun print(summary: DiffInspector.Summary) {
        println("📝 Preview: ${summary.changedFiles} file(s), +${summary.totalAdded} / -${summary.totalRemoved} lines")
    }

    /** Simple pass-through; you can colorize later. */
    fun printUnified(diff: String) {
        println("----- BEGIN DIFF -----")
        println(diff)
        println("------ END DIFF ------")
    }
}

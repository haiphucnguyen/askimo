/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import io.askimo.core.util.Logger.info

object DiffPreview {
    fun print(summary: DiffInspector.Summary) {
        info("📝 Preview: ${summary.changedFiles} file(s), +${summary.totalAdded} / -${summary.totalRemoved} lines")
    }

    /** Simple pass-through; you can colorize later. */
    fun printUnified(diff: String) {
        info("----- BEGIN DIFF -----")
        info(diff)
        info("------ END DIFF ------")
    }
}

/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.util

import java.io.File

object Git {
    /** True if there are unstaged/unstashed changes. */
    fun isDirty(root: String): Boolean =
        runCatching {
            val out = execOut(root, "git", "status", "--porcelain")
            out.isNotBlank()
        }.getOrElse { true } // if git fails, be conservative

    /** Short SHA of HEAD, or "nohead" on failure. */
    fun headShort(root: String): String =
        runCatching {
            val out = execOut(root, "git", "rev-parse", "--short", "HEAD").trim()
            if (out.isBlank()) "nohead" else out
        }.getOrElse { "nohead" }

    private fun execOut(
        dir: String,
        vararg cmd: String,
    ): String {
        val p =
            ProcessBuilder(*cmd)
                .directory(File(dir))
                .redirectErrorStream(true)
                .start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out
    }
}

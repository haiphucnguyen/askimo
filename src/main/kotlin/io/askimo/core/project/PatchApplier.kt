/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import io.askimo.core.util.Logger.info
import java.io.File

class PatchApplier {
    fun apply(
        projectRoot: String,
        tempBranch: String,
        unifiedDiff: String,
    ): Boolean {
        val root = File(projectRoot)

        // create temp branch from current HEAD
        if (!exec(root, "git", "checkout", "-b", tempBranch)) return false

        // apply the patch via stdin using 3-way merge; fix whitespace automatically
        val proc =
            ProcessBuilder("git", "apply", "--3way", "--whitespace=fix")
                .directory(root)
                .redirectErrorStream(true)
                .start()

        proc.outputStream.bufferedWriter().use { it.write(unifiedDiff) }
        val output = proc.inputStream.bufferedReader().readText()
        val ok = proc.waitFor() == 0
        if (!ok) {
            info(output)
            // rollback temp branch
            exec(root, "git", "reset", "--hard")
            exec(root, "git", "checkout", "-")
            exec(root, "git", "branch", "-D", tempBranch)
            return false
        }

        // stage & commit
        if (!exec(root, "git", "add", "-A")) return false
        if (!exec(root, "git", "commit", "-m", "feat(askimo): apply AI-proposed patch")) return false

        return true
    }

    private fun exec(
        dir: File,
        vararg cmd: String,
    ): Boolean {
        val p =
            ProcessBuilder(*cmd)
                .directory(dir)
                .redirectErrorStream(true)
                .start()
        val out = p.inputStream.bufferedReader().readText()
        val success = p.waitFor() == 0
        if (!success) info(out)
        return success
    }
}

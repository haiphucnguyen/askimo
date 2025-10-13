/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import io.askimo.core.util.Git
import io.askimo.core.util.TimeUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class EditFlow(
    private val diffGen: DiffGenerator,
    private val patchApplier: PatchApplier,
    private val budgets: Budgets = Budgets(),
) {
    fun run(
        instruction: String,
        meta: ProjectMeta,
    ) {
        // Collect files mentioned
        val sources = resolveFiles(meta, instruction)
        if (sources.isEmpty()) {
            println("ℹ️  No eligible files to edit under project root.")
            return
        }

        val header =
            EnvHeaderV0(
                project = EnvProject(meta.id, ".", ".", meta.createdAt),
                constraints = EnvConstraints(budgets.maxFiles, budgets.maxChangedLines, budgets.allowDirty),
                targetHints = sources.map { it.path },
                policy = EnvPolicy(editScope = "prefer-docs-only", noBuildFileEdits = true, allowBinaryEdits = false),
            )

        val req = DiffRequest(header, instruction, sources)
        val diff =
            try {
                diffGen.generateDiff(req)
            } catch (e: Exception) {
                println("❌ Model error: ${e.message}")
                return
            }
        if (diff.isBlank()) {
            println("ℹ️  No changes proposed.")
            return
        }

        val sum = DiffInspector.summarize(diff)
        if (sum.changedFiles > budgets.maxFiles || sum.totalChanged > budgets.maxChangedLines) {
            println(
                "⛔ Exceeds budget: ${sum.changedFiles} files, ${sum.totalChanged} lines (max ${budgets.maxFiles}/${budgets.maxChangedLines}).",
            )
            return
        }
        if (DiffInspector.containsBlockedPaths(diff)) {
            println("⛔ Diff touches guarded files (build/lock/CI).")
            return
        }

        DiffPreview.print(sum)
        DiffPreview.printUnified(diff)

        print("Apply changes on a temp branch? [y/N]: ")
        val yes = readlnOrNull()?.trim()?.lowercase() in setOf("y", "yes")
        if (!yes) {
            println("🛑 Aborted.")
            return
        }

        if (!budgets.allowDirty && Git.isDirty(meta.root)) {
            println("⛔ Working tree dirty. Commit/stash or allow dirty.")
            return
        }

        val branch = "askimo/change-${TimeUtil.shortStamp()}"
        val backupPath =
            Paths
                .get(meta.root, ".askimo", "patches")
                .resolve("${TimeUtil.shortStamp()}-${Git.headShort(meta.root)}.patch")
        try {
            Files.createDirectories(backupPath.parent)
            Files.writeString(backupPath, diff)
        } catch (_: Exception) {
        }

        val ok = patchApplier.apply(meta.root, branch, diff)
        if (ok) {
            println("✅ Applied on temp branch $branch")
            println("🧷 Backup patch: ${backupPath.toAbsolutePath()}")
        } else {
            println("❌ Patch failed; see backup: ${backupPath.toAbsolutePath()}")
        }
    }

    private fun resolveFiles(
        meta: ProjectMeta,
        instruction: String,
    ): List<SourceFile> {
        val absPaths =
            EditIntentDetector
                .detect(instruction, true)
                .targetPaths
                .map { p -> if (Paths.get(p).isAbsolute) p else Paths.get(meta.root).resolve(p).toString() }
                .distinct()

        val files = mutableListOf<SourceFile>()
        for (absStr in absPaths) {
            val abs = Paths.get(absStr).toAbsolutePath().normalize()
            if (!PathGuards.isUnderRoot(abs, meta.root)) {
                println("⛔ Outside root: $abs")
                continue
            }
            if (!Files.isRegularFile(abs)) {
                println("⚠️ Not a file: $abs")
                continue
            }
            if (PathGuards.isBlocked(abs)) {
                println("⛔ Guarded file: $abs")
                continue
            }
            val text = Files.readString(abs)
            files += SourceFile(rootRel(meta.root, abs), detectEol(text), text)
        }
        return files
    }

    private fun rootRel(
        root: String,
        abs: Path,
    ): String =
        Paths
            .get(root)
            .relativize(abs)
            .toString()
            .replace('\\', '/')
}

/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import io.askimo.core.util.Git
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.info
import io.askimo.core.util.TimeUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class CodingAssistant(
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
            info("ℹ️  No eligible files to edit under project root.")
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
                info("❌ Model error: ${e.message}")
                debug(e)
                return
            }
        if (diff.isBlank()) {
            info("ℹ️  No changes proposed.")
            return
        }

        val sum = DiffInspector.summarize(diff)
        if (sum.changedFiles > budgets.maxFiles || sum.totalChanged > budgets.maxChangedLines) {
            info(
                "⛔ Exceeds budget: ${sum.changedFiles} files, ${sum.totalChanged} lines (max ${budgets.maxFiles}/${budgets.maxChangedLines}).",
            )
            return
        }
        if (DiffInspector.containsBlockedPaths(diff)) {
            info("⛔ Diff touches guarded files (build/lock/CI).")
            return
        }

        DiffPreview.print(sum)
        DiffPreview.printUnified(diff)

        print("Apply changes on a temp branch? [y/N]: ")
        val yes = readlnOrNull()?.trim()?.lowercase() in setOf("y", "yes")
        if (!yes) {
            info("🛑 Aborted.")
            return
        }

        if (!budgets.allowDirty && Git.isDirty(meta.root)) {
            info("⛔ Working tree dirty. Commit/stash or allow dirty.")
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
        } catch (e: Exception) {
            debug(e)
        }

        val ok = patchApplier.apply(meta.root, branch, diff)
        if (ok) {
            info("✅ Applied on temp branch $branch")
            info("🧷 Backup patch: ${backupPath.toAbsolutePath()}")
        } else {
            info("❌ Patch failed; see backup: ${backupPath.toAbsolutePath()}")
        }
    }

    private fun resolveFiles(
        meta: ProjectMeta,
        instruction: String,
    ): List<SourceFile> {
        val filePathRegex = Regex("""[A-Za-z0-9_\-./]+(?:\.[A-Za-z0-9_\-]+)""")
        val absPaths =
            filePathRegex
                .findAll(instruction)
                .map { it.value }
                .filter { path ->
                    val hasExtension = path.contains('.')
                    val hasPathSeparators = path.contains('/') || path.contains('\\')
                    val notCommand = !path.startsWith(":")
                    val commonExtensions = listOf(".kt", ".java", ".js", ".ts", ".py", ".rb", ".cpp", ".h", ".cs", ".go", ".rs", ".php", ".swift", ".scala")
                    val hasCodeExtension = commonExtensions.any { ext -> path.endsWith(ext, ignoreCase = true) }

                    hasExtension && (hasPathSeparators || hasCodeExtension) && notCommand
                }
                .map { p ->
                    if (Paths.get(p).isAbsolute) p
                    else Paths.get(meta.root).resolve(p).toString()
                }
                .distinct()
                .toList()

        val files = mutableListOf<SourceFile>()
        for (absStr in absPaths) {
            val abs = Paths.get(absStr).toAbsolutePath().normalize()
            if (!PathGuards.isUnderRoot(abs, meta.root)) {
                info("⛔ Outside root: $abs")
                continue
            }
            if (!Files.isRegularFile(abs)) {
                info("⚠️ Not a file: $abs")
                continue
            }
            if (PathGuards.isBlocked(abs)) {
                info("⛔ Guarded file: $abs")
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

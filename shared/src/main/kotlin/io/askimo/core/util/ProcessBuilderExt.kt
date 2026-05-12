/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.util

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Wrapper around ProcessBuilder that automatically resolves executable paths
 * and enriches the child process PATH so binaries installed via nvm, Homebrew,
 * or user-level npm are found even when the app was launched outside a login shell.
 *
 * This is particularly important on macOS when running apps from ~/Applications,
 * as they don't inherit the shell's PATH environment variable.
 *
 * Cross-platform PATH enrichment strategy:
 * - macOS / Linux: ask the user's login shell (`$SHELL -l -c "echo $PATH"`) to
 *   resolve all shell-profile additions (nvm, pyenv, Homebrew, etc.), then
 *   append a set of well-known static fallback directories.
 * - Windows: append common Node / npm global bin locations under APPDATA and Program Files.
 */
class ProcessBuilderExt(vararg command: String) {
    constructor(command: List<String>) : this(*command.toTypedArray())

    private val processBuilder: ProcessBuilder

    init {
        val resolvedCommand = resolveCommand(command.toList())
        processBuilder = ProcessBuilder(resolvedCommand)
        enrichPath(processBuilder.environment())
    }

    // ── PATH enrichment ───────────────────────────────────────────────────────

    private fun enrichPath(env: MutableMap<String, String>) {
        val separator = File.pathSeparator
        val extra = if (isWindows()) windowsExtraPaths() else unixExtraPaths()
        val shellPath = if (!isWindows()) resolveShellPath() else null
        val existing = (shellPath ?: env["PATH"] ?: "").split(separator)
        val merged = (existing + extra)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(separator)
        env["PATH"] = merged
    }

    /**
     * Asks the user's login shell for its PATH.
     * Covers nvm, Homebrew, pyenv, and any other shell-profile additions.
     * Returns null on failure or timeout.
     */
    private fun resolveShellPath(): String? = runCatching {
        val shell = System.getenv("SHELL") ?: "/bin/zsh"
        val proc = ProcessBuilder(shell, "-l", "-c", "echo \$PATH")
            .redirectErrorStream(true)
            .start()
        val path = proc.inputStream.bufferedReader().readText().trim()
        val ok = proc.waitFor(5, TimeUnit.SECONDS)
        path.takeIf { ok && it.isNotBlank() }
    }.getOrNull()

    /** Static extra paths on macOS / Linux. */
    private fun unixExtraPaths(): List<String> {
        val home = System.getProperty("user.home")
        val nvmBase = File("$home/.nvm/versions/node")
        val nvmBins = if (nvmBase.isDirectory) {
            nvmBase.listFiles()
                ?.sortedDescending() // newest version first
                ?.map { "${it.absolutePath}/bin" }
                ?: emptyList()
        } else {
            emptyList()
        }

        return nvmBins + listOf(
            "/usr/local/bin",
            "/opt/homebrew/bin", // Apple Silicon Homebrew
            "/opt/homebrew/sbin",
            "/usr/bin",
            "/bin",
            "/opt/local/bin", // MacPorts
            "/usr/local/lib/node_modules/.bin",
            "/usr/lib/node_modules/.bin",
            "$home/.npm-global/bin",
            "$home/.local/bin",
            "$home/bin",
        )
    }

    /** Static extra paths on Windows. */
    private fun windowsExtraPaths(): List<String> {
        val home = System.getProperty("user.home")
        val appData = System.getenv("APPDATA") ?: "$home\\AppData\\Roaming"
        val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
        val programFilesX86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
        return listOf(
            "$appData\\npm",
            "$home\\AppData\\Local\\Programs\\node",
            "$programFiles\\nodejs",
            "$programFilesX86\\nodejs",
            "C:\\Program Files\\nodejs",
        )
    }

    /**
     * Delegates to the underlying ProcessBuilder.
     */
    fun redirectErrorStream(redirect: Boolean): ProcessBuilderExt {
        processBuilder.redirectErrorStream(redirect)
        return this
    }

    /**
     * Delegates to the underlying ProcessBuilder.
     */
    fun directory(directory: File?): ProcessBuilderExt {
        processBuilder.directory(directory)
        return this
    }

    /**
     * Delegates to the underlying ProcessBuilder.
     */
    fun environment(): MutableMap<String, String> = processBuilder.environment()

    /**
     * Delegates to the underlying ProcessBuilder.
     */
    fun command(): MutableList<String> = processBuilder.command()

    /**
     * Delegates to the underlying ProcessBuilder.
     */
    fun command(vararg command: String): ProcessBuilderExt {
        processBuilder.command(resolveCommand(command.toList()))
        return this
    }

    /**
     * Delegates to the underlying ProcessBuilder.
     */
    fun command(command: List<String>): ProcessBuilderExt {
        processBuilder.command(resolveCommand(command))
        return this
    }

    /**
     * Starts the process.
     */
    fun start(): Process = processBuilder.start()

    companion object {
        fun resolveCommand(command: List<String>): List<String> {
            if (command.isEmpty()) return command
            val resolved = findExecutable(command[0])
            return listOf(resolved) + command.drop(1)
        }

        private fun findExecutable(executableName: String): String {
            // Already an absolute path
            File(executableName).let {
                if (it.isAbsolute && it.exists() && it.canExecute()) return executableName
            }

            val windowsExtensions = if (isWindows()) listOf(".exe", ".cmd", ".bat", ".com") else listOf("")

            val windowsBasePaths = if (isWindows()) {
                listOf(
                    System.getenv("ProgramFiles"),
                    System.getenv("ProgramFiles(x86)"),
                    System.getenv("LOCALAPPDATA"),
                    (System.getenv("windir") ?: "C:\\Windows") + "\\System32",
                )
            } else {
                emptyList()
            }

            val commonPaths = listOf(
                "/usr/local/bin",
                "/opt/homebrew/bin",
                "/usr/bin",
                "/bin",
                "/opt/local/bin",
                System.getProperty("user.home") + "/.local/bin",
            )

            val allPaths = if (isWindows()) {
                (windowsBasePaths + commonPaths).flatMap { base ->
                    windowsExtensions.map { ext -> "$base\\$executableName$ext" }
                }
            } else {
                commonPaths.map { "$it/$executableName" }
            }

            allPaths.firstOrNull { File(it).let { f -> f.exists() && !f.isDirectory } }
                ?.let { return it }

            // Shell fallback
            val resolvedPath = resolveViaShell(executableName)

            if (resolvedPath != null && isWindows()) {
                for (ext in listOf(".exe", ".cmd", ".bat", ".com", "")) {
                    val candidate = if (ext.isEmpty()) {
                        resolvedPath
                    } else if (resolvedPath.endsWith(ext, ignoreCase = true)) {
                        resolvedPath
                    } else {
                        val dotIndex = resolvedPath.lastIndexOf('.')
                        val lastSlash = maxOf(resolvedPath.lastIndexOf('\\'), resolvedPath.lastIndexOf('/'))
                        if (dotIndex > lastSlash) {
                            resolvedPath.substring(0, dotIndex) + ext
                        } else {
                            resolvedPath + ext
                        }
                    }
                    val f = File(candidate)
                    if (f.exists() && !f.isDirectory) return candidate
                }
            }

            return resolvedPath ?: executableName
        }

        private fun resolveViaShell(executableName: String): String? = try {
            val command = if (isWindows()) {
                listOf("cmd.exe", "/c", "where", executableName)
            } else {
                listOf("/bin/sh", "-c", "which $executableName")
            }
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (process.exitValue() == 0 && output.isNotBlank()) {
                output.lines().firstOrNull()?.trim()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

        private fun isWindows(): Boolean = System.getProperty("os.name", "").lowercase().contains("windows")
    }
}

/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.util

import java.io.File

/**
 * Wrapper around ProcessBuilder that automatically resolves executable paths.
 *
 * This is particularly important on macOS when running apps from ~/Applications,
 * as they don't inherit the shell's PATH environment variable.
 *
 * Usage:
 * ```
 * // Instead of ProcessBuilder("ollama", "list")
 * ProcessBuilderExt("ollama", "list").start()
 * ```
 */
class ProcessBuilderExt(vararg command: String) {
    constructor(command: List<String>) : this(*command.toTypedArray())

    private val processBuilder: ProcessBuilder

    init {
        val resolvedCommand = resolveCommand(command.toList())
        processBuilder = ProcessBuilder(resolvedCommand)
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
        val resolvedCommand = resolveCommand(command.toList())
        processBuilder.command(resolvedCommand)
        return this
    }

    /**
     * Delegates to the underlying ProcessBuilder.
     */
    fun command(command: List<String>): ProcessBuilderExt {
        val resolvedCommand = resolveCommand(command)
        processBuilder.command(resolvedCommand)
        return this
    }

    /**
     * Starts the process.
     */
    fun start(): Process = processBuilder.start()

    companion object {
        /**
         * Resolves the executable in the command list to its full path.
         */
        private fun resolveCommand(command: List<String>): List<String> {
            if (command.isEmpty()) return command

            val executable = command[0]
            val resolvedExecutable = findExecutable(executable)

            return listOf(resolvedExecutable) + command.drop(1)
        }

        /**
         * Finds the full path to an executable.
         *
         * @param executableName The name of the executable to find
         * @return The full path to the executable, or the original name if not found
         */
        private fun findExecutable(executableName: String): String {
            File(executableName).let {
                if (it.isAbsolute && it.exists() && it.canExecute()) {
                    return executableName
                }
            }

            // On Windows, check multiple extensions for batch files and executables
            val windowsExtensions = if (isWindows()) {
                listOf(".exe", ".cmd", ".bat", ".com")
            } else {
                listOf("")
            }

            // On Windows, check common program paths
            val windowsBasePaths =
                if (isWindows()) {
                    listOf(
                        System.getenv("ProgramFiles"),
                        System.getenv("ProgramFiles(x86)"),
                        System.getenv("LOCALAPPDATA"),
                        System.getenv("windir") + "\\System32",
                    )
                } else {
                    emptyList()
                }

            // Common installation directories
            val commonPaths =
                listOf(
                    "/usr/local/bin",
                    "/opt/homebrew/bin", // macOS Homebrew on Apple Silicon
                    "/usr/bin",
                    "/bin",
                    "/opt/local/bin", // macOS MacPorts
                    System.getProperty("user.home") + "/.local/bin", // User local installations
                )

            val allPaths =
                if (isWindows()) {
                    windowsBasePaths.flatMap { basePath ->
                        windowsExtensions.map { ext ->
                            "$basePath\\$executableName$ext"
                        }
                    } + commonPaths.flatMap { basePath ->
                        windowsExtensions.map { ext ->
                            "$basePath\\$executableName$ext"
                        }
                    }
                } else {
                    commonPaths.map { "$it/$executableName" }
                }

            // Try common installation paths first
            allPaths.firstOrNull { path ->
                File(path).let { it.exists() && !it.isDirectory }
            }?.let { return it }

            // Fallback: resolve via shell/command line
            val resolvedPath = resolveViaShell(executableName)

            if (resolvedPath != null && isWindows()) {
                // On Windows, verify the resolved path with proper extensions
                val extensionsToCheck = listOf(".exe", ".cmd", ".bat", ".com", "")

                for (ext in extensionsToCheck) {
                    val pathToCheck = if (ext.isEmpty()) {
                        resolvedPath
                    } else if (resolvedPath.endsWith(ext, ignoreCase = true)) {
                        resolvedPath
                    } else {
                        // Try adding the extension
                        val dotIndex = resolvedPath.lastIndexOf('.')
                        val lastSlash = maxOf(resolvedPath.lastIndexOf('\\'), resolvedPath.lastIndexOf('/'))
                        if (dotIndex > lastSlash) {
                            // Has an extension, replace it
                            resolvedPath.substring(0, dotIndex) + ext
                        } else {
                            // No extension, just append
                            resolvedPath + ext
                        }
                    }

                    val file = File(pathToCheck)
                    if (file.exists() && !file.isDirectory) {
                        return pathToCheck
                    }
                }
            }

            return resolvedPath ?: executableName
        }

        /**
         * Attempts to resolve an executable path using the system shell.
         */
        private fun resolveViaShell(executableName: String): String? = try {
            val command =
                if (isWindows()) {
                    listOf("cmd.exe", "/c", "where", executableName)
                } else {
                    listOf("/bin/sh", "-c", "which $executableName")
                }

            val process =
                ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()

            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            if (process.exitValue() == 0 && output.isNotBlank()) {
                // On Windows, 'where' can return multiple paths; take the first one
                output.lines().firstOrNull()?.trim()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

        /**
         * Checks if the current platform is Windows.
         */
        private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("windows")
    }
}

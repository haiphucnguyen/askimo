/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.util

import java.io.File

/**
 * Utility for resolving executable paths across platforms.
 *
 * This is particularly important on:
 * - macOS: Apps from ~/Applications don't inherit shell's PATH
 * - Windows: Executables need proper extensions (.exe, .cmd, .bat)
 */
object ExecutableResolver {

    /**
     * Resolves the executable in the command list to its full path.
     *
     * @param command The command list where the first element is the executable
     * @return A new command list with the resolved executable path
     */
    fun resolveCommand(command: List<String>): List<String> {
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
    fun findExecutable(executableName: String): String {
        // If already absolute and exists, return as-is
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
        val windowsBasePaths = if (isWindows()) {
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
        val commonPaths = listOf(
            "/usr/local/bin",
            "/opt/homebrew/bin", // macOS Homebrew on Apple Silicon
            "/usr/bin",
            "/bin",
            "/opt/local/bin", // macOS MacPorts
            System.getProperty("user.home") + "/.local/bin", // User local installations
        )

        val allPaths = if (isWindows()) {
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
        val command = if (isWindows()) {
            listOf("cmd.exe", "/c", "where", executableName)
        } else {
            listOf("/bin/sh", "-c", "which $executableName")
        }

        val process = ProcessBuilder(command)
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
    fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("windows")
}

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
        val resolvedCommand = ExecutableResolver.resolveCommand(command.toList())
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
        val resolvedCommand = ExecutableResolver.resolveCommand(command.toList())
        processBuilder.command(resolvedCommand)
        return this
    }

    /**
     * Delegates to the underlying ProcessBuilder.
     */
    fun command(command: List<String>): ProcessBuilderExt {
        val resolvedCommand = ExecutableResolver.resolveCommand(command)
        processBuilder.command(resolvedCommand)
        return this
    }

    /**
     * Starts the process.
     */
    fun start(): Process = processBuilder.start()
}

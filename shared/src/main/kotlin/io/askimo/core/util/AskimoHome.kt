/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.util

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Centralized resolution of Askimo home-related paths.
 *
 * Override base directory with env var ASKIMO_HOME; if unset, defaults to `${user.home}/.askimo`.
 * Paths are computed on demand so tests that override `user.home` still work.
 *
 * For testing: use `withTestBase()` to temporarily override the base directory without
 * affecting system properties or the actual askimo installation.
 */
object AskimoHome {
    // Thread-local override for testing - doesn't affect other threads or the main application
    private val testBaseOverride = ThreadLocal<Path?>()

    /** Returns the (possibly overridden) base Askimo directory. */
    fun base(): Path {
        // Check for test override first
        testBaseOverride.get()?.let { return it }

        val override = System.getenv("ASKIMO_HOME")?.trim()?.takeIf { it.isNotEmpty() }
        val userHome = System.getProperty("user.home")
        val base = override?.let { Paths.get(it) } ?: Paths.get(userHome).resolve(".askimo")
        return base.toAbsolutePath().normalize()
    }

    /**
     * For testing: temporarily override the base directory for the current thread.
     * Use in try-finally or with `use` pattern to ensure cleanup.
     */
    fun withTestBase(testBase: Path): TestBaseScope {
        testBaseOverride.set(testBase.toAbsolutePath().normalize())
        return TestBaseScope()
    }

    /**
     * Scope object for managing test base directory cleanup.
     */
    class TestBaseScope : AutoCloseable {
        override fun close() {
            testBaseOverride.remove()
        }
    }

    fun userHome(): Path = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize()

    fun recipesDir(): Path = base().resolve("recipes")

    fun projectsDir(): Path = base().resolve("projects")

    fun historyFile(): Path = base().resolve("history")

    fun sessionFile(): Path = base().resolve("session")

    fun encryptionKeyFile(): Path = base().resolve(".key")

    /** Expand "~" or "~/foo" to the current user home directory. */
    fun expandTilde(raw: String): Path = when {
        raw == "~" -> userHome()
        raw.startsWith("~/") -> userHome().resolve(raw.removePrefix("~/"))
        else -> Paths.get(raw)
    }.toAbsolutePath().normalize()
}

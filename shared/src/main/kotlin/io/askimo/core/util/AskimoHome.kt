/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.util

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Profile type for Askimo home directory.
 * PERSONAL is the default — used when the app runs standalone with user-provided API keys.
 * TEAM is activated when the user signs in to an Askimo team server.
 */
enum class AppProfile(val dirName: String) {
    PERSONAL("personal"),
    TEAM("team"),
}

/**
 * Centralized resolution of Askimo home-related paths.
 *
 * Directory structure (Option B — single base, profile subdirectories):
 *   ~/.askimo/
 *     personal/    ← PERSONAL profile data (default)
 *     team/        ← TEAM profile data (when signed in to a server)
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

    /**
     * The currently active profile. Defaults to PERSONAL.
     * Switch via [switchProfile] when the user logs in/out of a team.
     */
    @Volatile
    var activeProfile: AppProfile = AppProfile.PERSONAL
        private set

    /**
     * Switches the active profile. Should be called when user logs in/out of a team server.
     * All subsequent [base], [projectsDir], [recipesDir], etc. calls will resolve
     * to the new profile's subdirectory.
     */
    fun switchProfile(profile: AppProfile) {
        activeProfile = profile
    }

    /** Returns the base Askimo directory (e.g., ~/.askimo) — does NOT include profile subdir. */
    fun rootBase(): Path {
        testBaseOverride.get()?.let { return it }

        val override = System.getenv("ASKIMO_HOME")?.trim()?.takeIf { it.isNotEmpty() }
        val userHome = System.getProperty("user.home")
        val base = override?.let { Paths.get(it) } ?: Paths.get(userHome).resolve(".askimo")
        return base.toAbsolutePath().normalize()
    }

    /**
     * Returns the active profile's home directory (e.g., ~/.askimo/personal).
     * All app data paths resolve under this directory.
     */
    fun base(): Path = rootBase().resolve(activeProfile.dirName).also {
        it.toFile().mkdirs()
    }

    /**
     * Returns the home directory for a specific profile, regardless of which one is active.
     * Useful when reading data from the non-active profile (e.g., during migration checks).
     */
    fun profileBase(profile: AppProfile): Path = rootBase().resolve(profile.dirName).also {
        it.toFile().mkdirs()
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

    fun sessionFile(): Path = base().resolve("session")

    fun encryptionKeyFile(): Path = base().resolve(".key")

    /** Expand "~" or "~/foo" to the current user home directory. */
    fun expandTilde(raw: String): Path = when {
        raw == "~" -> userHome()
        raw.startsWith("~/") -> userHome().resolve(raw.removePrefix("~/"))
        else -> Paths.get(raw)
    }.toAbsolutePath().normalize()
}

/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.ui.util

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files

/**
 * Installs (or refreshes) a symlink at `~/.askimo/bin/askimo` pointing to the
 * `askimo` launcher bundled inside the running Desktop .app bundle.
 *
 * When the Desktop app is distributed it carries a thin CLI jar + the bundled JVM.
 * A platform-specific launcher script (`Contents/MacOS/askimo` on macOS,
 * `bin/askimo` on Linux) lets users run the CLI directly from the terminal.
 *
 * This function creates a stable, version-independent symlink at startup so the
 * launcher is always available on `$PATH` regardless of where the app is installed.
 *
 * If the app is run from Gradle (development mode, no .app bundle), the function
 * silently skips installation.
 */
object CliLauncherInstaller {

    private val log = LoggerFactory.getLogger(CliLauncherInstaller::class.java)

    /** Install or refresh the CLI symlink. Safe to call on every launch. */
    fun install() {
        try {
            val launcher = findBundledLauncher() ?: return // not a bundled app
            installSymlink(launcher)
        } catch (e: Exception) {
            // Non-fatal — the user can still launch the CLI from the app directly
            log.warn("⚠️  Could not install askimo CLI symlink: ${e.message}")
        }
    }

    // ── private helpers ──────────────────────────────────────────────────────

    /**
     * Resolves the bundled `askimo` launcher for the current platform.
     *
     * macOS: `<bundle>/Contents/MacOS/askimo`
     * Linux: `<distDir>/bin/askimo`
     * Windows: `<distDir>\bin\askimo.bat`
     */
    private fun findBundledLauncher(): File? {
        val os = System.getProperty("os.name").lowercase()

        // The JVM's java.home points inside the bundled runtime, e.g.
        //   macOS: Contents/runtime/Contents/Home
        //   Linux: <distDir>/runtime
        val javaHome = File(System.getProperty("java.home"))

        return when {
            os.contains("mac") -> {
                // Walk up: java.home → Contents/Home → Contents → runtime → Contents
                //   Contents/MacOS/askimo
                val contentsDir = javaHome.resolve("../../..").canonicalFile // Contents/
                val launcher = contentsDir.resolve("MacOS/askimo")
                if (launcher.exists()) launcher else null
            }

            os.contains("win") -> {
                val runtimeDir = javaHome.parentFile // <distDir>/runtime
                val launcher = runtimeDir?.resolve("../bin/askimo.bat")?.canonicalFile
                if (launcher?.exists() == true) launcher else null
            }

            else -> {
                val runtimeDir = javaHome.parentFile // <distDir>/runtime
                val launcher = runtimeDir?.resolve("../bin/askimo")?.canonicalFile
                if (launcher?.exists() == true) launcher else null
            }
        }
    }

    /**
     * Creates `~/.askimo/bin/askimo` → [launcher] symlink.
     * Replaces any stale symlink or outdated file silently.
     */
    private fun installSymlink(launcher: File) {
        val binDir = File(System.getProperty("user.home"), ".askimo/bin")
        binDir.mkdirs()

        val symlink = binDir.resolve("askimo").toPath()
        val target = launcher.toPath()

        // No-op if symlink already points to the right target
        if (Files.isSymbolicLink(symlink) && Files.readSymbolicLink(symlink) == target) {
            return
        }

        // Remove stale entry
        Files.deleteIfExists(symlink)
        Files.createSymbolicLink(symlink, target)
        log.info("✅ CLI launcher symlink installed: $symlink → $target")
    }
}

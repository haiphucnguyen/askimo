/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.service

import io.askimo.core.VersionInfo
import io.askimo.core.logging.logger
import io.askimo.core.service.UpdateChecker
import io.askimo.core.service.UpdateInfo
import java.io.File

/**
 * Installation method detected for the CLI application.
 */
enum class InstallMethod {
    HOMEBREW,
    SCOOP,
    CHOCOLATEY,
    MANUAL,
    UNKNOWN,
}

/**
 * CLI-specific service to check for application updates.
 * Detects the installation method and provides appropriate update instructions.
 */
class CliUpdateService {
    private val log = logger<CliUpdateService>()
    private val updateChecker = UpdateChecker(userAgent = "Askimo-CLI/${VersionInfo.version}")

    /**
     * Detect how the CLI was installed based on the executable path.
     */
    fun detectInstallMethod(): InstallMethod {
        val executable = try {
            ProcessHandle.current().info().command().orElse("")
        } catch (e: Exception) {
            log.debug("Unable to detect installation method: ${e.message}")
            return InstallMethod.UNKNOWN
        }

        return when {
            executable.contains("/Cellar/") || executable.contains("/homebrew/") ||
                executable.contains("/opt/homebrew/") -> InstallMethod.HOMEBREW
            executable.contains("\\scoop\\") -> InstallMethod.SCOOP
            executable.contains("\\chocolatey\\") || executable.contains("\\ProgramData\\chocolatey\\") ->
                InstallMethod.CHOCOLATEY
            File(executable).exists() && !executable.contains("build") -> InstallMethod.MANUAL
            else -> InstallMethod.UNKNOWN
        }
    }

    /**
     * Check for updates and display a notification if a new version is available.
     */
    fun checkAndNotifyUpdate() {
        val updateInfo = updateChecker.checkForUpdates() ?: return

        if (updateInfo.isNewVersion) {
            printUpdateNotification(updateInfo, detectInstallMethod())
        }
    }

    /**
     * Print an update notification to the console with installation-specific instructions.
     */
    private fun printUpdateNotification(updateInfo: UpdateInfo, method: InstallMethod) {
        val (updateCommand, additionalInfo) = getUpdateInstructions(method)

        println()
        println("╔═══════════════════════════════════════════════════════════╗")
        println("║  🎉 Update Available!                                     ║")
        println("╠═══════════════════════════════════════════════════════════╣")
        println("║  Current version: ${updateInfo.currentVersion.padEnd(41)}║")
        println("║  Latest version:  ${updateInfo.latestVersion.padEnd(41)}║")
        println("║  Release date:    ${updateInfo.releaseDate.padEnd(41)}║")
        println("║                                                           ║")
        println("║  To update:                                               ║")

        // Split long commands into multiple lines if needed
        if (updateCommand.length > 55) {
            val lines = updateCommand.chunked(55)
            lines.forEach { line ->
                println("║  ${line.padEnd(55)}║")
            }
        } else {
            println("║  ${updateCommand.padEnd(55)}║")
        }

        if (additionalInfo.isNotEmpty()) {
            println("║                                                           ║")
            println("║  ${additionalInfo.take(55).padEnd(55)}║")
        }

        println("╚═══════════════════════════════════════════════════════════╝")
        println()
    }

    /**
     * Get platform-specific update instructions.
     */
    private fun getUpdateInstructions(method: InstallMethod): Pair<String, String> = when (method) {
        InstallMethod.HOMEBREW ->
            "brew update && brew upgrade askimo" to ""
        InstallMethod.SCOOP ->
            "scoop update askimo" to ""
        InstallMethod.CHOCOLATEY ->
            "choco upgrade askimo" to ""
        InstallMethod.MANUAL ->
            "Download from GitHub:" to "https://github.com/haiphucnguyen/askimo/releases"
        InstallMethod.UNKNOWN ->
            "Update using your package manager" to "or download from GitHub releases"
    }
}

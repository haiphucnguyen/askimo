/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.service

import io.askimo.core.VersionInfo
import io.askimo.core.event.EventBus
import io.askimo.core.event.UpdateAvailableEvent
import io.askimo.core.logging.logger
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@kotlinx.serialization.Serializable
data class GitHubRelease(
    val tag_name: String,
    val name: String,
    val published_at: String,
    val html_url: String,
    val body: String,
)

data class ReleaseInfo(
    val version: String,
    val name: String,
    val releaseDate: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val isNewVersion: Boolean,
)

/**
 * Service to check for application updates from GitHub releases.
 */
class UpdateService {
    private val log = logger<UpdateService>()
    private val githubApiUrl = "https://api.github.com/repos/haiphucnguyen/askimo/releases/latest"
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Track the last version for which we emitted an event to prevent duplicates
    private var lastEmittedVersion: String? = null

    /**
     * Check for updates from GitHub releases.
     * Returns null if the check fails or if there's an error.
     */
    fun checkForUpdates(): ReleaseInfo? = try {
        log.debug("Checking for updates from: $githubApiUrl")

        val request = HttpRequest.newBuilder()
            .uri(URI.create(githubApiUrl))
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "Askimo-Desktop/${VersionInfo.version}")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() == 200) {
            parseReleaseInfo(response.body())
        } else {
            log.warn("Failed to check for updates. Status code: ${response.statusCode()}")
            null
        }
    } catch (e: Exception) {
        log.error("Error checking for updates", e)
        null
    }

    private fun parseReleaseInfo(jsonResponse: String): ReleaseInfo? = try {
        val release = json.decodeFromString<GitHubRelease>(jsonResponse)
        val latestVersion = release.tag_name.removePrefix("v")
        val currentVersion = VersionInfo.version

        log.debug("Current version: $currentVersion, Latest version: $latestVersion")

        val isNewVersion = isNewerVersion(latestVersion, currentVersion)

        if (isNewVersion && lastEmittedVersion != latestVersion) {
            log.info("New version available: $latestVersion (current: $currentVersion)")
            EventBus.post(
                UpdateAvailableEvent(
                    currentVersion = currentVersion,
                    latestVersion = latestVersion,
                    releaseNotes = release.body,
                    downloadUrl = release.html_url,
                ),
            )
            lastEmittedVersion = latestVersion
        }

        ReleaseInfo(
            version = latestVersion,
            name = release.name,
            releaseDate = formatReleaseDate(release.published_at),
            downloadUrl = release.html_url,
            releaseNotes = release.body,
            isNewVersion = isNewVersion,
        )
    } catch (e: Exception) {
        log.error("Error parsing release info", e)
        null
    }

    private fun formatReleaseDate(isoDate: String): String = try {
        isoDate.substringBefore('T')
    } catch (e: Exception) {
        isoDate
    }

    /**
     * Compare two semantic version strings.
     * Returns true if the latest version is newer than current.
     */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        if (current == "unknown") return true // Always show updates if version is unknown

        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val latestPart = latestParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }

            if (latestPart > currentPart) return true
            if (latestPart < currentPart) return false
        }
        return false
    }

    fun getCurrentVersion(): String = VersionInfo.version
}

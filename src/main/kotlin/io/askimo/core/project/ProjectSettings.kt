/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Represents a registered Askimo project.
 *
 * Each entry stores the human-friendly project name and the absolute
 * path to the project's root directory. Instances are serialized to
 * the projects.json configuration file under the user's home directory.
 */
@Serializable
data class ProjectEntry(
    val name: String,
    val dir: String,
)

/**
 * On-disk schema of ~/.askimo/projects.json.
 *
 * Internal data transfer object used only for persistence. It includes
 * a file format version and the list of known projects.
 */
@Serializable
private data class ProjectConfigFile(
    val version: Int = 1,
    val projects: MutableList<ProjectEntry> = mutableListOf(),
)

/**
 * Persists and retrieves Askimo projects.
 *
 * ProjectStore reads and writes a JSON file at ~/.askimo/projects.json
 * using kotlinx.serialization. It provides simple CRUD helpers to list
 * projects, fetch by name (case-insensitive), and upsert entries.
 * The store creates the configuration directory if it does not exist.
 */
object ProjectStore {
    private val configDir: Path =
        Paths.get(System.getProperty("user.home"), ".askimo")
    private val configFile: Path = configDir.resolve("projects.json")
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

    fun list(): List<ProjectEntry> = load().projects

    fun get(name: String): ProjectEntry? = load().projects.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun upsert(entry: ProjectEntry) {
        val cfg = load()
        val existingIdx = cfg.projects.indexOfFirst { it.name.equals(entry.name, true) }
        if (existingIdx >= 0) cfg.projects[existingIdx] = entry else cfg.projects += entry
        save(cfg)
    }

    fun delete(name: String): Boolean {
        val cfg = load()
        val removed = cfg.projects.removeIf { it.name.equals(name, ignoreCase = true) }
        if (removed) save(cfg)
        return removed
    }

    private fun load(): ProjectConfigFile {
        if (!Files.exists(configFile)) return ProjectConfigFile()
        val text = Files.readString(configFile)
        return runCatching { json.decodeFromString<ProjectConfigFile>(text) }
            .getOrElse { ProjectConfigFile() }
    }

    private fun save(cfg: ProjectConfigFile) {
        if (!Files.exists(configDir)) Files.createDirectories(configDir)
        Files.writeString(configFile, json.encodeToString(cfg))
    }
}

/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Base sealed class for all knowledge source configurations.
 * Uses polymorphic serialization to handle different source types.
 *
 * Each subclass represents a specific knowledge source type with its own
 * resource identifier format and configuration options.
 */
@Serializable
sealed class KnowledgeSourceConfig {
    abstract val resourceIdentifiers: List<String>
    abstract val config: Map<String, String>
}

/**
 * Configuration for local folder knowledge sources.
 * Folders are watched for changes.
 *
 * @property resourceIdentifiers Folder paths (e.g., "/path/to/folder")
 * @property config Configuration options:
 *   - watchForChanges: "true" or "false"
 *   - fileExtensions: ".kt,.java,.md" (comma-separated)
 *   - excludePatterns: "node_modules,build,.git" (comma-separated)
 */
@Serializable
@SerialName("local_folders")
data class LocalFoldersKnowledgeSourceConfig(
    override val resourceIdentifiers: List<String>,
    override val config: Map<String, String> = emptyMap(),
) : KnowledgeSourceConfig()

/**
 * Configuration for individual files as knowledge sources.
 * Unlike folders, files are NOT watched for changes.
 *
 * @property resourceIdentifiers File paths (e.g., "/path/to/document.pdf")
 * @property config Configuration options (currently unused)
 */
@Serializable
@SerialName("local_files")
data class LocalFilesKnowledgeSourceConfig(
    override val resourceIdentifiers: List<String>,
    override val config: Map<String, String> = emptyMap(),
) : KnowledgeSourceConfig()

/**
 * Wrapper for the new indexed_paths JSON format.
 * Contains version for future migrations and list of knowledge sources.
 */
@Serializable
data class IndexedPathsData(
    val version: Int = 1,
    val sources: List<KnowledgeSourceConfig>,
)

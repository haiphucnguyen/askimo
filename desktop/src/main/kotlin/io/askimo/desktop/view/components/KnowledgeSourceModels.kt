/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.ui.graphics.vector.ImageVector
import io.askimo.core.chat.domain.KnowledgeSourceConfig
import io.askimo.core.chat.domain.LocalFilesKnowledgeSourceConfig
import io.askimo.core.chat.domain.LocalFoldersKnowledgeSourceConfig
import io.askimo.core.chat.domain.UrlKnowledgeSourceConfig
import java.io.File
import java.util.UUID

/**
 * UI representation of a knowledge source item
 */
sealed class KnowledgeSourceItem {
    abstract val id: String
    abstract val displayName: String
    abstract val icon: ImageVector
    abstract val isValid: Boolean
    abstract val typeLabel: String

    data class Folder(
        override val id: String,
        val path: String,
        override val isValid: Boolean,
    ) : KnowledgeSourceItem() {
        override val displayName = path
        override val icon = Icons.Default.Folder
        override val typeLabel = "Folder (watched)"
    }

    data class File(
        override val id: String,
        val path: String,
        override val isValid: Boolean,
    ) : KnowledgeSourceItem() {
        override val displayName = path
        override val icon = Icons.AutoMirrored.Filled.InsertDriveFile
        override val typeLabel = "File (static)"
    }

    data class Url(
        override val id: String,
        val url: String,
        override val isValid: Boolean,
    ) : KnowledgeSourceItem() {
        override val displayName = url
        override val icon = Icons.Default.Language
        override val typeLabel = "URL (web content)"
    }
}

/**
 * Enum representing available knowledge source types
 */
enum class KnowledgeSourceType(
    val displayName: String,
    val icon: ImageVector,
) {
    FOLDER("Folder (watched for changes)", Icons.Default.Folder),
    FILE("Files (static)", Icons.AutoMirrored.Filled.InsertDriveFile),
    URL("URL (web content)", Icons.Default.Language),
}

/**
 * Parse KnowledgeSourceConfig into UI items
 */
fun parseKnowledgeSourceConfigs(configs: List<KnowledgeSourceConfig>): List<KnowledgeSourceItem> = configs.flatMap { config ->
    when (config) {
        is LocalFoldersKnowledgeSourceConfig -> {
            config.resourceIdentifiers.map {
                KnowledgeSourceItem.Folder(
                    id = UUID.randomUUID().toString(),
                    path = it,
                    isValid = validateFolder(it),
                )
            }
        }
        is LocalFilesKnowledgeSourceConfig -> {
            config.resourceIdentifiers.map {
                KnowledgeSourceItem.File(
                    id = UUID.randomUUID().toString(),
                    path = it,
                    isValid = validateFile(it),
                )
            }
        }
        is UrlKnowledgeSourceConfig -> {
            config.resourceIdentifiers.map {
                KnowledgeSourceItem.Url(
                    id = UUID.randomUUID().toString(),
                    url = it,
                    isValid = validateUrl(it),
                )
            }
        }
    }
}

/**
 * Build KnowledgeSourceConfig list from UI items
 */
fun buildKnowledgeSourceConfigs(sources: List<KnowledgeSourceItem>): List<KnowledgeSourceConfig> {
    val folders = sources.filterIsInstance<KnowledgeSourceItem.Folder>().map { it.path }
    val files = sources.filterIsInstance<KnowledgeSourceItem.File>().map { it.path }
    val urls = sources.filterIsInstance<KnowledgeSourceItem.Url>().map { it.url }

    val configs = mutableListOf<KnowledgeSourceConfig>()

    if (folders.isNotEmpty()) {
        configs.add(LocalFoldersKnowledgeSourceConfig(resourceIdentifiers = folders))
    }

    if (files.isNotEmpty()) {
        configs.add(LocalFilesKnowledgeSourceConfig(resourceIdentifiers = files))
    }

    if (urls.isNotEmpty()) {
        configs.add(UrlKnowledgeSourceConfig(resourceIdentifiers = urls))
    }

    return configs
}

/**
 * Validate if a folder path is valid
 */
fun validateFolder(path: String): Boolean {
    val file = File(path)
    return file.exists() && file.isDirectory && file.canRead()
}

/**
 * Validate if a file path is valid
 */
fun validateFile(path: String): Boolean {
    val file = File(path)
    return file.exists() && file.isFile && file.canRead()
}

/**
 * Validate if a URL is valid
 */
fun validateUrl(url: String): Boolean = try {
    val uri = java.net.URI(url)
    uri.scheme in listOf("http", "https") && uri.host != null
} catch (_: Exception) {
    false
}

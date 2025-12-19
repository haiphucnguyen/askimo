/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Table schema for indexed files metadata.
 * Tracks which files have been indexed and their modification times.
 */
object IndexedFilesTable : Table("indexed_files") {
    val path = varchar("path", 1024) // Relative path from project root
    val lastModified = long("last_modified") // File last modified timestamp in millis
    val indexedAt = datetime("indexed_at") // When this file was indexed
    val size = long("size") // File size in bytes
    val checksum = varchar("checksum", 64).nullable() // Optional SHA-256 checksum for extra verification

    override val primaryKey = PrimaryKey(path)
}

/**
 * Table schema for index metadata.
 * Stores overall index state and configuration.
 */
object IndexMetadataTable : Table("index_metadata") {
    val key = varchar("key", 64) // Metadata key (e.g., "status", "version", "last_indexed_at")
    val value = text("value") // Metadata value (stored as string, parsed as needed)

    override val primaryKey = PrimaryKey(key)
}

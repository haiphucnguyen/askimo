/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.util

import org.slf4j.LoggerFactory
import java.io.File

/**
 * Migrates the legacy flat ~/.askimo/ structure to the new profile-based structure:
 *
 *   Before:                       After:
 *   ~/.askimo/                    ~/.askimo/
 *     database/                     personal/
 *     projects/                       database/
 *     recipes/                        projects/
 *     session                         recipes/
 *     .key                            session
 *     .encrypted-keys                 .key
 *     logs/                           .encrypted-keys
 *     backups/                        logs/
 *     model-capabilities-cache.json   backups/
 *     ...                             model-capabilities-cache.json
 *                                     ...
 *                                   team/   ← empty, created on first team login
 *
 * Safe to call on every startup — skips if already migrated (marker file present).
 */
object AskimoHomeMigration {

    private val log = LoggerFactory.getLogger(AskimoHomeMigration::class.java)

    private const val MIGRATION_MARKER = ".migrated_profiles_v1"

    /**
     * Known legacy entries to move into personal/ subdirectory.
     * If any new top-level dirs/files are added in future, add them here.
     */
    private val LEGACY_ENTRIES = listOf(
        "database",
        "projects",
        "recipes",
        "session",
        ".key",
        ".encrypted-keys",
        "logs",
        "backups",
        "model-capabilities-cache.json",
        "avatars",
        "mcp",
    )

    /**
     * Runs the migration from the legacy flat structure to the new profile-based structure.
     * Must be called early on startup, before [AskimoHome.base] is first accessed.
     *
     * @param rootBase The root ~/.askimo directory (from [AskimoHome.rootBase])
     */
    fun migrate(rootBase: File) {
        val migrationMarker = File(rootBase, MIGRATION_MARKER)

        if (migrationMarker.exists()) {
            log.debug("Profile migration already completed, skipping.")
            return
        }

        // If personal/ already exists, migration was done manually or in a newer install
        val personalDir = File(rootBase, AppProfile.PERSONAL.dirName)
        if (personalDir.exists()) {
            log.debug("personal/ dir already exists, writing marker and skipping migration.")
            migrationMarker.writeText("migrated_at=${System.currentTimeMillis()}")
            return
        }

        log.info("Migrating ~/.askimo to profile-based structure...")

        try {
            personalDir.mkdirs()

            var movedCount = 0
            LEGACY_ENTRIES.forEach { entry ->
                val source = File(rootBase, entry)
                val destination = File(personalDir, entry)
                if (source.exists() && !destination.exists()) {
                    val success = source.renameTo(destination)
                    if (success) {
                        log.info("  Migrated: $entry → personal/$entry")
                        movedCount++
                    } else {
                        log.warn("  Could not move: $entry (will be re-created in personal/)")
                    }
                }
            }

            migrationMarker.writeText("migrated_at=${System.currentTimeMillis()}\nmoved=$movedCount")
            log.info("Migration completed. $movedCount entries moved to personal/.")
        } catch (e: Exception) {
            // Do NOT throw — app must still start even if migration fails
            log.error("Migration failed — original files are untouched, app will continue.", e)
        }
    }
}

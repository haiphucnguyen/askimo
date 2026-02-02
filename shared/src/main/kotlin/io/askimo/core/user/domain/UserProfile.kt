/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.user.domain

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

/**
 * User profile data class representing the user's personal information
 * used for AI personalization.
 */
data class UserProfile(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val preferredTitle: String? = null, // Mr., Ms., Dr., etc.
    val occupation: String? = null,
    val location: String? = null,
    val timezone: String? = null,
    val bio: String? = null,
    val interests: List<String> = emptyList(),
    val preferences: Map<String, String> = emptyMap(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)

/**
 * Database table for user profiles.
 * Stores a single user profile (one row expected).
 */
object UserProfilesTable : Table("user_profiles") {
    val id = varchar("id", 36)
    val name = varchar("name", 255).nullable()
    val email = varchar("email", 255).nullable()
    val preferredTitle = varchar("preferred_title", 50).nullable()
    val occupation = varchar("occupation", 255).nullable()
    val location = varchar("location", 255).nullable()
    val timezone = varchar("timezone", 100).nullable()
    val bio = text("bio").nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Database table for user interests (many-to-many relationship).
 */
object UserInterestsTable : Table("user_interests") {
    val id = varchar("id", 36)
    val profileId = varchar("profile_id", 36).references(UserProfilesTable.id)
    val interest = varchar("interest", 255)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Database table for user preferences (key-value pairs).
 */
object UserPreferencesTable : Table("user_preferences") {
    val id = varchar("id", 36)
    val profileId = varchar("profile_id", 36).references(UserProfilesTable.id)
    val key = varchar("key", 255)
    val value = text("value")
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        // Ensure unique key per profile
        uniqueIndex(profileId, key)
    }
}

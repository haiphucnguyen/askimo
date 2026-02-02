/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.user.repository

import io.askimo.core.db.AbstractSQLiteRepository
import io.askimo.core.db.DatabaseManager
import io.askimo.core.user.domain.UserInterestsTable
import io.askimo.core.user.domain.UserPreferencesTable
import io.askimo.core.user.domain.UserProfile
import io.askimo.core.user.domain.UserProfilesTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

/**
 * Extension function to map an Exposed ResultRow to a UserProfile object.
 */
private fun ResultRow.toUserProfile(): UserProfile = UserProfile(
    id = this[UserProfilesTable.id],
    name = this[UserProfilesTable.name],
    email = this[UserProfilesTable.email],
    preferredTitle = this[UserProfilesTable.preferredTitle],
    occupation = this[UserProfilesTable.occupation],
    location = this[UserProfilesTable.location],
    timezone = this[UserProfilesTable.timezone],
    bio = this[UserProfilesTable.bio],
    createdAt = this[UserProfilesTable.createdAt],
    updatedAt = this[UserProfilesTable.updatedAt],
)

/**
 * Repository for managing user profiles.
 * Note: This system assumes a single user profile (single-user application).
 */
class UserProfileRepository internal constructor(
    databaseManager: DatabaseManager = DatabaseManager.getInstance(),
) : AbstractSQLiteRepository(databaseManager) {

    companion object {
        private const val DEFAULT_PROFILE_ID = "default"
    }

    /**
     * Get the user profile. Creates a default profile if none exists.
     *
     * @return The user profile
     */
    fun getProfile(): UserProfile = transaction(database) {
        val profileRow = UserProfilesTable
            .selectAll()
            .where { UserProfilesTable.id eq DEFAULT_PROFILE_ID }
            .singleOrNull()

        if (profileRow == null) {
            // Create default profile
            val defaultProfile = UserProfile(
                id = DEFAULT_PROFILE_ID,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )
            saveProfile(defaultProfile)
            defaultProfile
        } else {
            val profile = profileRow.toUserProfile()

            // Load interests
            val interests = UserInterestsTable
                .selectAll()
                .where { UserInterestsTable.profileId eq profile.id }
                .map { it[UserInterestsTable.interest] }

            // Load preferences
            val preferences = UserPreferencesTable
                .selectAll()
                .where { UserPreferencesTable.profileId eq profile.id }
                .associate { it[UserPreferencesTable.key] to it[UserPreferencesTable.value] }

            profile.copy(
                interests = interests,
                preferences = preferences,
            )
        }
    }

    /**
     * Save or update the user profile.
     *
     * @param profile The profile to save
     * @return The saved profile
     */
    fun saveProfile(profile: UserProfile): UserProfile = transaction(database) {
        val profileToSave = profile.copy(
            id = DEFAULT_PROFILE_ID,
            updatedAt = LocalDateTime.now(),
        )

        // Check if profile exists
        val exists = UserProfilesTable
            .selectAll()
            .where { UserProfilesTable.id eq DEFAULT_PROFILE_ID }
            .singleOrNull() != null

        if (exists) {
            // Update existing profile
            UserProfilesTable.update({ UserProfilesTable.id eq DEFAULT_PROFILE_ID }) {
                it[name] = profileToSave.name
                it[email] = profileToSave.email
                it[preferredTitle] = profileToSave.preferredTitle
                it[occupation] = profileToSave.occupation
                it[location] = profileToSave.location
                it[timezone] = profileToSave.timezone
                it[bio] = profileToSave.bio
                it[updatedAt] = profileToSave.updatedAt
            }
        } else {
            // Insert new profile
            UserProfilesTable.insert {
                it[id] = profileToSave.id
                it[name] = profileToSave.name
                it[email] = profileToSave.email
                it[preferredTitle] = profileToSave.preferredTitle
                it[occupation] = profileToSave.occupation
                it[location] = profileToSave.location
                it[timezone] = profileToSave.timezone
                it[bio] = profileToSave.bio
                it[createdAt] = profileToSave.createdAt
                it[updatedAt] = profileToSave.updatedAt
            }
        }

        // Update interests
        saveInterests(profileToSave.id, profileToSave.interests)

        // Update preferences
        savePreferences(profileToSave.id, profileToSave.preferences)

        profileToSave
    }

    /**
     * Update specific fields of the profile without affecting others.
     *
     * @param updates Map of field names to new values
     * @return The updated profile
     */
    fun updateProfile(updates: Map<String, Any?>): UserProfile = transaction(database) {
        val currentProfile = getProfile()

        val updatedProfile = currentProfile.copy(
            name = updates["name"] as? String ?: currentProfile.name,
            email = updates["email"] as? String ?: currentProfile.email,
            preferredTitle = updates["preferredTitle"] as? String ?: currentProfile.preferredTitle,
            occupation = updates["occupation"] as? String ?: currentProfile.occupation,
            location = updates["location"] as? String ?: currentProfile.location,
            timezone = updates["timezone"] as? String ?: currentProfile.timezone,
            bio = updates["bio"] as? String ?: currentProfile.bio,
            interests = (updates["interests"] as? List<*>)?.filterIsInstance<String>() ?: currentProfile.interests,
            preferences = (updates["preferences"] as? Map<*, *>)?.entries?.associate {
                it.key.toString() to it.value.toString()
            } ?: currentProfile.preferences,
        )

        saveProfile(updatedProfile)
    }

    /**
     * Clear all profile data (reset to default).
     */
    fun clearProfile(): Unit = transaction(database) {
        UserInterestsTable.deleteWhere { profileId eq DEFAULT_PROFILE_ID }
        UserPreferencesTable.deleteWhere { profileId eq DEFAULT_PROFILE_ID }
        UserProfilesTable.deleteWhere { id eq DEFAULT_PROFILE_ID }
    }

    /**
     * Get personalization context string for AI prompts.
     * Returns null if no meaningful personalization data exists.
     *
     * @return Formatted personalization context or null
     */
    fun getPersonalizationContext(): String? {
        val profile = getProfile()

        val contextParts = mutableListOf<String>()

        profile.name?.let { contextParts.add("User's name: $it") }
        profile.preferredTitle?.let { contextParts.add("Preferred title: $it") }
        profile.occupation?.let { contextParts.add("Occupation: $it") }
        profile.location?.let { contextParts.add("Location: $it") }

        if (profile.interests.isNotEmpty()) {
            contextParts.add("Interests: ${profile.interests.joinToString(", ")}")
        }

        profile.bio?.let { contextParts.add("About: $it") }

        return if (contextParts.isEmpty()) null else contextParts.joinToString(". ")
    }

    /**
     * Save interests for a profile.
     */
    private fun saveInterests(profileId: String, interests: List<String>) {
        // Delete existing interests
        UserInterestsTable.deleteWhere { UserInterestsTable.profileId eq profileId }

        // Insert new interests
        interests.forEach { interest ->
            UserInterestsTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[UserInterestsTable.profileId] = profileId
                it[UserInterestsTable.interest] = interest
                it[createdAt] = LocalDateTime.now()
            }
        }
    }

    /**
     * Save preferences for a profile.
     */
    private fun savePreferences(profileId: String, preferences: Map<String, String>) {
        // Delete existing preferences
        UserPreferencesTable.deleteWhere { UserPreferencesTable.profileId eq profileId }

        // Insert new preferences
        preferences.forEach { (key, value) ->
            UserPreferencesTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[UserPreferencesTable.profileId] = profileId
                it[UserPreferencesTable.key] = key
                it[UserPreferencesTable.value] = value
                it[createdAt] = LocalDateTime.now()
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }

    /**
     * Get a specific preference value.
     *
     * @param key The preference key
     * @return The preference value or null if not found
     */
    fun getPreference(key: String): String? = transaction(database) {
        UserPreferencesTable
            .selectAll()
            .where {
                (UserPreferencesTable.profileId eq DEFAULT_PROFILE_ID) and
                    (UserPreferencesTable.key eq key)
            }
            .singleOrNull()
            ?.get(UserPreferencesTable.value)
    }

    /**
     * Set a specific preference value.
     *
     * @param key The preference key
     * @param value The preference value
     */
    fun setPreference(key: String, value: String): Unit = transaction(database) {
        val exists = UserPreferencesTable
            .selectAll()
            .where {
                (UserPreferencesTable.profileId eq DEFAULT_PROFILE_ID) and
                    (UserPreferencesTable.key eq key)
            }
            .singleOrNull() != null

        if (exists) {
            UserPreferencesTable.update({
                (UserPreferencesTable.profileId eq DEFAULT_PROFILE_ID) and
                    (UserPreferencesTable.key eq key)
            }) {
                it[UserPreferencesTable.value] = value
                it[updatedAt] = LocalDateTime.now()
            }
        } else {
            UserPreferencesTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[profileId] = DEFAULT_PROFILE_ID
                it[UserPreferencesTable.key] = key
                it[UserPreferencesTable.value] = value
                it[createdAt] = LocalDateTime.now()
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }
}

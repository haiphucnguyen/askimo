/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.service

import io.askimo.core.logging.logger
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Service for managing user and AI avatar images.
 * Stores avatars in ~/.askimo/avatars/ directory.
 */
class AvatarService {
    private val log = logger<AvatarService>()

    companion object {
        private val ASKIMO_HOME = File(System.getProperty("user.home"), ".askimo")
        private val AVATARS_DIR = File(ASKIMO_HOME, "avatars")
        private val USER_AVATAR_DIR = File(AVATARS_DIR, "user")
        private val AI_AVATAR_DIR = File(AVATARS_DIR, "ai")

        private const val DEFAULT_USER_AVATAR = "user-avatar.png"
        private const val DEFAULT_AI_AVATAR = "ai-avatar.png"
    }

    init {
        // Ensure avatar directories exist
        USER_AVATAR_DIR.mkdirs()
        AI_AVATAR_DIR.mkdirs()
    }

    /**
     * Save user avatar from the selected file path.
     * Copies the file to ~/.askimo/avatars/user/
     *
     * @param sourcePath Path to the source image file
     * @return Path to the saved avatar file, or null if failed
     */
    fun saveUserAvatar(sourcePath: String): String? {
        return try {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                log.error("Source avatar file does not exist: $sourcePath")
                return null
            }

            // Validate file is an image
            if (!isValidImageFile(sourceFile)) {
                log.error("Invalid image file: $sourcePath")
                return null
            }

            val extension = sourceFile.extension
            val destFile = File(USER_AVATAR_DIR, DEFAULT_USER_AVATAR.replace(".png", ".$extension"))

            // Copy file to avatar directory
            Files.copy(
                sourceFile.toPath(),
                destFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )

            log.info("User avatar saved to: ${destFile.absolutePath}")
            destFile.absolutePath
        } catch (e: Exception) {
            log.error("Failed to save user avatar", e)
            null
        }
    }

    /**
     * Save AI avatar from the selected file path.
     * Copies the file to ~/.askimo/avatars/ai/
     *
     * @param sourcePath Path to the source image file
     * @return Path to the saved avatar file, or null if failed
     */
    fun saveAIAvatar(sourcePath: String): String? {
        return try {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                log.error("Source avatar file does not exist: $sourcePath")
                return null
            }

            // Validate file is an image
            if (!isValidImageFile(sourceFile)) {
                log.error("Invalid image file: $sourcePath")
                return null
            }

            val extension = sourceFile.extension
            val destFile = File(AI_AVATAR_DIR, DEFAULT_AI_AVATAR.replace(".png", ".$extension"))

            // Copy file to avatar directory
            Files.copy(
                sourceFile.toPath(),
                destFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )

            log.info("AI avatar saved to: ${destFile.absolutePath}")
            destFile.absolutePath
        } catch (e: Exception) {
            log.error("Failed to save AI avatar", e)
            null
        }
    }

    /**
     * Remove user avatar file.
     */
    fun removeUserAvatar(): Boolean {
        return try {
            val files = USER_AVATAR_DIR.listFiles() ?: return true
            files.forEach { it.delete() }
            log.info("User avatar removed")
            true
        } catch (e: Exception) {
            log.error("Failed to remove user avatar", e)
            false
        }
    }

    /**
     * Remove AI avatar file.
     */
    fun removeAIAvatar(): Boolean {
        return try {
            val files = AI_AVATAR_DIR.listFiles() ?: return true
            files.forEach { it.delete() }
            log.info("AI avatar removed")
            true
        } catch (e: Exception) {
            log.error("Failed to remove AI avatar", e)
            false
        }
    }

    /**
     * Get the current user avatar path, or null if not set.
     */
    fun getUserAvatarPath(): String? {
        val files = USER_AVATAR_DIR.listFiles() ?: return null
        return files.firstOrNull { it.isFile && isValidImageFile(it) }?.absolutePath
    }

    /**
     * Get the current AI avatar path, or null if not set.
     */
    fun getAIAvatarPath(): String? {
        val files = AI_AVATAR_DIR.listFiles() ?: return null
        return files.firstOrNull { it.isFile && isValidImageFile(it) }?.absolutePath
    }

    /**
     * Validate if the file is a supported image format.
     */
    private fun isValidImageFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in setOf("png", "jpg", "jpeg", "gif", "bmp")
    }
}

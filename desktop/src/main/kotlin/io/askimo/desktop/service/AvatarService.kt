/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.service

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.askimo.core.logging.logger
import io.askimo.core.util.AskimoHome
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.jetbrains.skia.Image as SkiaImage

/**
 * Service for managing user and AI avatar images.
 * Stores avatars in ~/.askimo/<profile>/avatars/ directory.
 *
 * Caches decoded [BitmapPainter] instances so the PNG/JPEG bytes are decoded only once.
 * Call [invalidateAiAvatarCache] / [invalidateUserAvatarCache] when an avatar is saved or removed.
 */
class AvatarService {
    private val log = logger<AvatarService>()

    companion object {
        private val AVATARS_DIR get() = AskimoHome.base().resolve("avatars").toFile()
        private val USER_AVATAR_DIR get() = File(AVATARS_DIR, "user")
        private val AI_AVATAR_DIR get() = File(AVATARS_DIR, "ai")

        private const val DEFAULT_USER_AVATAR = "user-avatar.png"
        private const val DEFAULT_AI_AVATAR = "ai-avatar.png"

        /** Built-in fallback — decoded once for the entire app lifetime. */
        val fallbackAiAvatarPainter: BitmapPainter? by lazy {
            AvatarService::class.java
                .getResourceAsStream("/images/askimo_logo_64.png")
                ?.readBytes()
                ?.let { bytes -> BitmapPainter(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()) }
        }
    }

    /** Cached painter for the user-set AI avatar (null = not yet loaded or no file). */
    @Volatile
    private var cachedAiAvatarPainter: BitmapPainter? = null

    /** Cached painter for the user avatar (null = not yet loaded or no file). */
    @Volatile
    private var cachedUserAvatarPainter: BitmapPainter? = null

    /** The path that [cachedUserAvatarPainter] was decoded from — used to detect path changes. */
    @Volatile
    private var cachedUserAvatarPath: String? = null

    init {
        USER_AVATAR_DIR.mkdirs()
        AI_AVATAR_DIR.mkdirs()
    }

    // ── Path resolution ──────────────────────────────────────────────────────

    /**
     * Returns the on-disk path of the user-set AI avatar, or null if none exists.
     */
    fun getAIAvatarPath(): String? {
        val files = AI_AVATAR_DIR.listFiles() ?: return null
        return files.firstOrNull { it.isFile && isValidImageFile(it) }?.absolutePath
    }

    /**
     * Returns the on-disk path of the user avatar, or null if none exists.
     */
    fun getUserAvatarPath(): String? {
        val files = USER_AVATAR_DIR.listFiles() ?: return null
        return files.firstOrNull { it.isFile && isValidImageFile(it) }?.absolutePath
    }

    // ── Painter cache ────────────────────────────────────────────────────────

    /**
     * Returns a [BitmapPainter] for the AI avatar, falling back to the built-in
     * [fallbackAiAvatarPainter] if no user-set avatar exists.
     * The result is cached — decoded only on first call (or after [invalidateAiAvatarCache]).
     */
    fun getAiAvatarPainter(): BitmapPainter? {
        cachedAiAvatarPainter?.let { return it }

        val path = getAIAvatarPath() ?: return fallbackAiAvatarPainter

        return try {
            val painter = BitmapPainter(
                SkiaImage.makeFromEncoded(File(path).readBytes()).toComposeImageBitmap(),
            )
            cachedAiAvatarPainter = painter
            painter
        } catch (e: Exception) {
            log.warn("Failed to decode AI avatar at $path, using fallback", e)
            fallbackAiAvatarPainter
        }
    }

    /**
     * Returns a [BitmapPainter] for the user avatar stored at [avatarPath], or null if the
     * path is null or the file does not exist.
     * The result is cached by path — decoded only on first call for a given path
     * (or after [invalidateUserAvatarCache]).
     *
     * Pass the path from [UserProfile.preferences]["avatarPath"].
     */
    fun getUserAvatarPainter(avatarPath: String?): BitmapPainter? {
        if (avatarPath == null) return null

        // Return cached painter if path hasn't changed
        if (avatarPath == cachedUserAvatarPath) return cachedUserAvatarPainter

        val file = File(avatarPath)
        if (!file.exists()) return null

        return try {
            val painter = BitmapPainter(
                SkiaImage.makeFromEncoded(file.readBytes()).toComposeImageBitmap(),
            )
            cachedUserAvatarPainter = painter
            cachedUserAvatarPath = avatarPath
            painter
        } catch (e: Exception) {
            log.warn("Failed to decode user avatar at $avatarPath", e)
            null
        }
    }

    /** Clears the cached AI avatar painter so the next call to [getAiAvatarPainter] re-decodes. */
    fun invalidateAiAvatarCache() {
        cachedAiAvatarPainter = null
    }

    /** Clears the cached user avatar painter so the next call to [getUserAvatarPainter] re-decodes. */
    fun invalidateUserAvatarCache() {
        cachedUserAvatarPainter = null
        cachedUserAvatarPath = null
    }

    // ── Save / remove ────────────────────────────────────────────────────────

    /**
     * Copies the image at [sourcePath] to the user avatar directory and invalidates the cache.
     * @return The saved file path, or null on failure.
     */
    fun saveUserAvatar(sourcePath: String): String? = try {
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) {
            log.error("Source avatar file does not exist: $sourcePath")
            null
        } else if (!isValidImageFile(sourceFile)) {
            log.error("Invalid image file: $sourcePath")
            null
        } else {
            val destFile = File(USER_AVATAR_DIR, DEFAULT_USER_AVATAR.replace(".png", ".${sourceFile.extension}"))
            Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            invalidateUserAvatarCache()
            log.info("User avatar saved to: ${destFile.absolutePath}")
            destFile.absolutePath
        }
    } catch (e: Exception) {
        log.error("Failed to save user avatar", e)
        null
    }

    /**
     * Copies the image at [sourcePath] to the AI avatar directory and invalidates the cache.
     * @return The saved file path, or null on failure.
     */
    fun saveAIAvatar(sourcePath: String): String? = try {
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) {
            log.error("Source avatar file does not exist: $sourcePath")
            null
        } else if (!isValidImageFile(sourceFile)) {
            log.error("Invalid image file: $sourcePath")
            null
        } else {
            val destFile = File(AI_AVATAR_DIR, DEFAULT_AI_AVATAR.replace(".png", ".${sourceFile.extension}"))
            Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            invalidateAiAvatarCache()
            log.info("AI avatar saved to: ${destFile.absolutePath}")
            destFile.absolutePath
        }
    } catch (e: Exception) {
        log.error("Failed to save AI avatar", e)
        null
    }

    fun removeUserAvatar(): Boolean = try {
        USER_AVATAR_DIR.listFiles()?.forEach { it.delete() }
        invalidateUserAvatarCache()
        log.info("User avatar removed")
        true
    } catch (e: Exception) {
        log.error("Failed to remove user avatar", e)
        false
    }

    fun removeAIAvatar(): Boolean = try {
        AI_AVATAR_DIR.listFiles()?.forEach { it.delete() }
        invalidateAiAvatarCache()
        log.info("AI avatar removed")
        true
    } catch (e: Exception) {
        log.error("Failed to remove AI avatar", e)
        false
    }

    private fun isValidImageFile(file: File): Boolean = file.extension.lowercase() in setOf("png", "jpg", "jpeg", "gif", "bmp")
}

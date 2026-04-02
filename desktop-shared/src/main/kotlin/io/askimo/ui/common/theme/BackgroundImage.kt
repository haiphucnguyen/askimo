/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.ui.common.theme

/**
 * Represents a background image selection — either no image, one of the bundled
 * presets, or a custom image loaded from an arbitrary file path on disk.
 *
 * A semi-transparent overlay (theme surface color @ ~0.55 alpha) is rendered on top
 * so all UI text and surfaces remain readable regardless of the chosen background.
 */
sealed class BackgroundImage {
    abstract val displayName: String
    abstract val description: String

    /** No background image — plain theme background colour is used. */
    object None : BackgroundImage() {
        override val displayName = "None"
        override val description = "No background image"
    }

    /**
     * A JPEG bundled inside the app under `images/backgrounds/`.
     * [resourcePath] is the classpath-relative path (no leading slash).
     */
    data class Preset(
        val id: String,
        override val displayName: String,
        override val description: String,
        val resourcePath: String,
    ) : BackgroundImage()

    /**
     * A user-supplied image managed by [io.askimo.ui.service.BackgroundImageService].
     * [filePath] points to the **copy** inside `~/.askimo/personal/backgrounds/`,
     * not the original file the user picked from their filesystem.
     */
    data class Custom(
        val filePath: String,
        override val displayName: String = "Custom",
        override val description: String = "User-selected image",
    ) : BackgroundImage()

    companion object {
        /**
         * All bundled preset backgrounds, in display order.
         * This is the full declared list; use [availablePresets] at runtime
         * to get only those whose resource file is actually present on the classpath.
         */
        val presets: List<Preset> = listOf(
            Preset(
                id = "FOREST_MIST",
                displayName = "Forest Mist",
                description = "Soft morning mist through tall trees",
                resourcePath = "images/backgrounds/bg_forest_mist.jpg",
            ),
            Preset(
                id = "OCEAN_HORIZON",
                displayName = "Ocean Horizon",
                description = "Calm deep-blue sea at golden hour",
                resourcePath = "images/backgrounds/bg_ocean_horizon.jpg",
            ),
            Preset(
                id = "CHERRY_BLOSSOM",
                displayName = "Cherry Blossom",
                description = "Delicate pink petals in spring light",
                resourcePath = "images/backgrounds/bg_cherry_blossom.jpg",
            ),
            Preset(
                id = "MOUNTAIN_SNOW",
                displayName = "Mountain Snow",
                description = "Crisp white peaks under a clear sky",
                resourcePath = "images/backgrounds/bg_mountain_snow.jpg",
            ),
            Preset(
                id = "CITY_NIGHT",
                displayName = "City Night",
                description = "City lights glowing through the dark",
                resourcePath = "images/backgrounds/bg_city_night.jpg",
            ),
            Preset(
                id = "PARCHMENT",
                displayName = "Parchment",
                description = "Warm paper texture for a cosy feel",
                resourcePath = "images/backgrounds/bg_parchment.jpg",
            ),
        )

        /**
         * Subset of [presets] whose image resource actually exists on the classpath.
         * Presets whose JPEG file has not been bundled are silently excluded so the
         * UI never shows an option the user cannot load.
         */
        val availablePresets: List<Preset> by lazy {
            presets.filter { preset ->
                Thread.currentThread().contextClassLoader
                    ?.getResource(preset.resourcePath) != null ||
                    object {}.javaClass.getResource("/${preset.resourcePath}") != null
            }
        }

        /**
         * Deserialise from the string stored in Java Preferences.
         * Format:
         *  - `"NONE"` → [None]
         *  - `"PRESET:<id>"` → matching [Preset] (falls back to [None] if not found)
         *  - `"CUSTOM:<absolute-path>"` → [Custom]
         */
        fun fromPrefsString(value: String?): BackgroundImage {
            if (value.isNullOrBlank() || value == "NONE") return None
            if (value.startsWith("PRESET:")) {
                val id = value.removePrefix("PRESET:")
                return presets.find { it.id == id } ?: None
            }
            if (value.startsWith("CUSTOM:")) {
                val path = value.removePrefix("CUSTOM:")
                return Custom(filePath = path)
            }
            // Legacy enum name fallback (FOREST_MIST, etc.)
            return presets.find { it.id == value } ?: None
        }
    }

    /** Serialise to the string that is stored in Java Preferences. */
    fun toPrefsString(): String = when (this) {
        is None -> "NONE"
        is Preset -> "PRESET:$id"
        is Custom -> "CUSTOM:$filePath"
    }
}

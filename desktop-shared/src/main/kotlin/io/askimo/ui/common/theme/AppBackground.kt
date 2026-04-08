/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.ui.common.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File
import org.jetbrains.skia.Image as SkiaImage

/**
 * Composition local that is `true` when a background image is active.
 * Components can read this to make themselves semi-transparent.
 */
val LocalBackgroundActive = compositionLocalOf { false }

/**
 * Wraps [content] with an optional full-app background image.
 *
 * When [backgroundImage] is [BackgroundImage.None] this composable is a transparent
 * pass-through — no extra layers are drawn.
 *
 * When an image is selected two layers sit between the photo and the app content:
 * 1. The image fills the entire available space with [ContentScale.Crop].
 * 2. A theme-aware semi-transparent overlay is drawn on top:
 *    - **Dark themes** ([useDarkMode] = `true`): a dark/black overlay at
 *      [DARK_OVERLAY_ALPHA] — white text (the default on dark schemes) pops
 *      clearly against the dimmed photo.
 *    - **Light themes** ([useDarkMode] = `false`): a light/white overlay at
 *      [LIGHT_OVERLAY_ALPHA] — dark text (the default on light schemes) remains
 *      crisp without the image looking washed out.
 *
 * Background images are intentionally independent of the active [ThemeMode].
 *
 * @param useDarkMode Pass `true` for dark/night colour schemes (DARK, NORD, INDIGO,
 *   SYSTEM-in-dark) and `false` for all light colour schemes. This drives which
 *   overlay colour is chosen so text always contrasts well with the photo.
 */
@Composable
fun appBackground(
    backgroundImage: BackgroundImage,
    useDarkMode: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (backgroundImage is BackgroundImage.None) {
        Box(modifier = modifier) { content() }
        return
    }

    val painter = remember(backgroundImage) {
        try {
            val bytes: ByteArray? = when (backgroundImage) {
                is BackgroundImage.Preset -> {
                    // Bundled classpath resource — try context classloader first, then
                    // fallback via anonymous object (for modules that depend on this one).
                    Thread.currentThread().contextClassLoader
                        ?.getResourceAsStream(backgroundImage.resourcePath)
                        ?.readBytes()
                        ?: object {}.javaClass
                            .getResourceAsStream("/${backgroundImage.resourcePath}")
                            ?.readBytes()
                }
                is BackgroundImage.Custom -> {
                    val file = File(backgroundImage.filePath)
                    if (file.exists()) file.readBytes() else null
                }
            }
            if (bytes != null) {
                BitmapPainter(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap())
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    // Choose overlay colour and alpha based on whether the active theme is dark or light.
    // Dark theme  → dark overlay so white text stands out against the bright photo areas.
    // Light theme → light overlay so dark text stands out without the image overpowering the UI.
    val overlayColor = if (useDarkMode) {
        Color.Black.copy(alpha = DARK_OVERLAY_ALPHA)
    } else {
        Color.White.copy(alpha = LIGHT_OVERLAY_ALPHA)
    }

    Box(modifier = modifier) {
        // Layer 1: background image
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Layer 2: theme-aware overlay so UI stays readable regardless of photo brightness
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayColor),
            )
        }

        // Layer 3: actual app content — tell children a background image is active
        CompositionLocalProvider(LocalBackgroundActive provides (painter != null)) {
            content()
        }
    }
}

/**
 * Overlay alpha for **dark** themes.
 * A moderately dark veil dims bright photo areas so white text (used in dark
 * colour schemes) always has sufficient contrast.
 */
private const val DARK_OVERLAY_ALPHA = 0.60f

/**
 * Overlay alpha for **light** themes.
 * A lighter wash keeps the image visible while giving dark text (used in light
 * colour schemes) a clean, bright surface to sit against.
 */
private const val LIGHT_OVERLAY_ALPHA = 0.65f

/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.askimo.core.logging.currentFileLogger
import org.jetbrains.skia.Image
import java.io.File

private val log = currentFileLogger()

/**
 * Asynchronously load and display an image from a file path.
 *
 * @param imagePath Path to the image file
 * @param contentDescription Description for accessibility
 * @param modifier Modifier for the Image composable
 * @param contentScale How to scale the image content
 */
@Composable
fun asyncImage(
    imagePath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var imageBitmap by remember(imagePath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(imagePath) {
        try {
            val file = File(imagePath)
            if (file.exists()) {
                val bytes = file.readBytes()
                imageBitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
            } else {
                log.error("Avatar file does not exist: $imagePath")
            }
        } catch (e: Exception) {
            log.error("Failed to load avatar: ${e.message}", e)
        }
    }

    imageBitmap?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}

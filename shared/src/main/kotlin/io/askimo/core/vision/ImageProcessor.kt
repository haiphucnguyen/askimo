/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.vision

import io.askimo.core.logging.logger
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.max
import kotlin.math.min

object ImageProcessor {

    private val log = logger<ImageProcessor>()

    // ===== Tunables (safe for GPT-4o vision) =====
    const val DEFAULT_MAX_WIDTH = 1024
    const val DEFAULT_MAX_HEIGHT = 1024
    const val DEFAULT_JPEG_QUALITY = 0.80f
    private const val MIN_WIDTH = 640
    private const val MAX_TARGET_BYTES = 120_000
    private const val MIN_JPEG_QUALITY = 0.65f

    /**
     * Process an image to reduce token usage and keep vision accuracy.
     * - Resize with aspect ratio
     * - Normalize to JPEG
     * - Handle PNG alpha correctly
     * - Compression ladder with size guard
     */
    fun process(
        imageBytes: ByteArray,
        mimeType: String,
        maxWidth: Int = DEFAULT_MAX_WIDTH,
        maxHeight: Int = DEFAULT_MAX_HEIGHT,
        jpegQuality: Float = DEFAULT_JPEG_QUALITY,
    ): ProcessedImage {
        try {
            val originalImage = ImageIO.read(ByteArrayInputStream(imageBytes))
                ?: return fallback(imageBytes, mimeType)

            val originalWidth = originalImage.width
            val originalHeight = originalImage.height

            val (newWidth, newHeight) =
                calculateDimensions(originalWidth, originalHeight, maxWidth, maxHeight)

            val resizedImage =
                if (newWidth != originalWidth || newHeight != originalHeight) {
                    resizeImage(originalImage, newWidth, newHeight)
                } else {
                    normalizeImage(originalImage)
                }

            var quality = jpegQuality
            var compressed: ByteArray

            do {
                compressed = compressJpeg(resizedImage, quality)
                quality -= 0.05f
            } while (
                compressed.size > MAX_TARGET_BYTES &&
                quality >= MIN_JPEG_QUALITY
            )

            val savedBytes = imageBytes.size - compressed.size
            val savedPercent =
                ((1.0 - compressed.size.toDouble() / imageBytes.size) * 100).toInt()

            log.debug(
                "Image processed: ${originalWidth}x$originalHeight → " +
                    "${resizedImage.width}x${resizedImage.height}, " +
                    "size ${imageBytes.size} → ${compressed.size} bytes " +
                    "(-$savedBytes, $savedPercent%)",
            )

            return ProcessedImage(
                bytes = compressed,
                mimeType = "image/jpeg",
                originalSize = imageBytes.size,
                processedSize = compressed.size,
                wasResized = resizedImage.width != originalWidth ||
                    resizedImage.height != originalHeight,
                wasCompressed = compressed.size < imageBytes.size,
                originalWidth = originalWidth,
                originalHeight = originalHeight,
                processedWidth = resizedImage.width,
                processedHeight = resizedImage.height,
            )
        } catch (e: Exception) {
            log.warn("Image processing failed, using original bytes", e)
            return fallback(imageBytes, mimeType)
        }
    }

    // ===== Helpers =====

    private fun fallback(bytes: ByteArray, mimeType: String) = ProcessedImage(
        bytes = bytes,
        mimeType = mimeType,
        originalSize = bytes.size,
        processedSize = bytes.size,
        wasResized = false,
        wasCompressed = false,
    )

    private fun calculateDimensions(
        width: Int,
        height: Int,
        maxWidth: Int,
        maxHeight: Int,
    ): Pair<Int, Int> {
        if (width <= maxWidth && height <= maxHeight) {
            return width to height
        }

        val ratio = min(
            maxWidth.toDouble() / width,
            maxHeight.toDouble() / height,
        )

        val newWidth = max((width * ratio).toInt(), MIN_WIDTH)
        val newHeight = max((height * ratio).toInt(), 1)

        return newWidth to newHeight
    }

    private fun normalizeImage(original: BufferedImage): BufferedImage {
        if (original.type == BufferedImage.TYPE_INT_RGB) {
            return original
        }

        val img = BufferedImage(
            original.width,
            original.height,
            BufferedImage.TYPE_INT_RGB,
        )

        val g = img.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, img.width, img.height)
        g.drawImage(original, 0, 0, null)
        g.dispose()

        return img
    }

    private fun resizeImage(
        original: BufferedImage,
        targetWidth: Int,
        targetHeight: Int,
    ): BufferedImage {
        val resized = BufferedImage(
            targetWidth,
            targetHeight,
            BufferedImage.TYPE_INT_RGB,
        )

        val g = resized.createGraphics()
        try {
            g.color = Color.WHITE
            g.fillRect(0, 0, targetWidth, targetHeight)

            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC,
            )
            g.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY,
            )
            g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON,
            )

            g.drawImage(original, 0, 0, targetWidth, targetHeight, null)
        } finally {
            g.dispose()
        }

        return resized
    }

    private fun compressJpeg(
        image: BufferedImage,
        quality: Float,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val params = writer.defaultWriteParam

        params.compressionMode = ImageWriteParam.MODE_EXPLICIT
        params.compressionQuality = quality

        MemoryCacheImageOutputStream(out).use {
            writer.output = it
            writer.write(null, IIOImage(image, null, null), params)
        }

        writer.dispose()
        return out.toByteArray()
    }

    /**
     * Conservative upper-bound estimate for GPT-4o vision tokens.
     * Intentionally overestimates to avoid TPM failures.
     */
    fun estimateMaxVisionTokens(width: Int, height: Int): Int = (width * height) / 750
}

// ===== Result DTO =====

data class ProcessedImage(
    val bytes: ByteArray,
    val mimeType: String,
    val originalSize: Int,
    val processedSize: Int,
    val wasResized: Boolean,
    val wasCompressed: Boolean,
    val originalWidth: Int? = null,
    val originalHeight: Int? = null,
    val processedWidth: Int? = null,
    val processedHeight: Int? = null,
) {
    val compressionRatio: Double
        get() = if (originalSize > 0) {
            processedSize.toDouble() / originalSize
        } else {
            1.0
        }

    val savedBytes: Int
        get() = originalSize - processedSize
}

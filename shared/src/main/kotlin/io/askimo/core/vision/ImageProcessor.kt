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
    const val DEFAULT_MAX_WIDTH = 2048 // Increased for better detail preservation
    const val DEFAULT_MAX_HEIGHT = 2048 // Increased for better detail preservation
    const val DEFAULT_JPEG_QUALITY = 0.90f // Higher initial quality
    private const val MIN_WIDTH = 640
    private const val MAX_TARGET_BYTES = 500_000 // 500KB - better quality budget
    private const val MIN_JPEG_QUALITY = 0.75f // Higher quality floor
    private const val QUALITY_STEP = 0.03f // Finer quality adjustments
    private const val MAX_SCALE_DOWN_RATIO = 0.5 // Never scale down more than 50% for detail preservation

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

            // Adaptive compression: prioritize quality over file size
            var quality = jpegQuality
            var compressed: ByteArray
            var attempts = 0
            val maxAttempts = 15

            do {
                compressed = compressJpeg(resizedImage, quality)
                attempts++

                // Accept result if within reasonable size or quality floor reached
                if (compressed.size <= MAX_TARGET_BYTES || quality <= MIN_JPEG_QUALITY) {
                    break
                }

                quality -= QUALITY_STEP
            } while (attempts < maxAttempts)

            val savedBytes = imageBytes.size - compressed.size
            val savedPercent =
                ((1.0 - compressed.size.toDouble() / imageBytes.size) * 100).toInt()

            // Warn if compression is too aggressive
            if (savedPercent > 90) {
                log.warn(
                    "Aggressive compression detected ($savedPercent% reduction). " +
                        "Consider using higher quality images or reducing resolution before upload. " +
                        "Final quality: ${"%.2f".format(quality)}",
                )
            }

            log.debug(
                "Image processed: ${originalWidth}x$originalHeight → " +
                    "${resizedImage.width}x${resizedImage.height}, " +
                    "size ${imageBytes.size} → ${compressed.size} bytes " +
                    "(-$savedBytes, $savedPercent%), quality=${"%.2f".format(quality)}",
            )

            // Save to temp file for debugging if debug logging is enabled
            if (log.isDebugEnabled) {
                try {
                    val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "askimo-images")
                    tempDir.mkdirs()
                    val timestamp = java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"),
                    )
                    val debugFile = java.io.File(tempDir, "processed_${timestamp}_${newWidth}x${newHeight}_q${String.format("%.2f", quality)}.jpg")
                    debugFile.writeBytes(compressed)
                    log.debug("Saved processed image to: ${debugFile.absolutePath}")
                } catch (e: Exception) {
                    log.debug("Failed to save debug image: ${e.message}")
                }
            }

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
        // Don't downscale if already within limits
        if (width <= maxWidth && height <= maxHeight) {
            return width to height
        }

        // Calculate normal scaling ratio
        val ratio = min(
            maxWidth.toDouble() / width,
            maxHeight.toDouble() / height,
        )

        // Smart scaling: preserve detail by never scaling down more than 50%
        // This prevents excessive quality loss on high-resolution images
        val safeRatio = max(ratio, MAX_SCALE_DOWN_RATIO)

        val newWidth = max((width * safeRatio).toInt(), MIN_WIDTH)
        val newHeight = max((height * safeRatio).toInt(), 1)

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
        params.compressionQuality = quality.coerceIn(0f, 1f)

        // Enable progressive JPEG for better quality perception at same file size
        params.progressiveMode = ImageWriteParam.MODE_DEFAULT

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

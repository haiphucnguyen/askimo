/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.askimo.core.logging.currentFileLogger
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import java.awt.AlphaComposite
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.awt.Color as AwtColor

private val log = currentFileLogger()

/**
 * Renders a LaTeX formula as an image using JLaTeXMath.
 */
@Composable
fun latexFormula(
    latex: String,
    modifier: Modifier = Modifier,
    fontSize: Float = 48f,
) {
    val textColor = LocalContentColor.current

    val formulaImage = remember(latex, fontSize, textColor) {
        renderLatexToImage(latex, fontSize, textColor)
    }

    if (formulaImage != null) {
        Image(
            bitmap = formulaImage,
            contentDescription = "LaTeX formula: $latex",
            modifier = modifier
                .height(70.dp)
                .padding(vertical = 4.dp),
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
        )
    } else {
        Text(
            text = latex,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = modifier,
        )
    }
}

// Synchronization lock for thread-safe rendering
private val renderLock = Any()

/**
 * Render LaTeX formula as an image using JLaTeXMath.
 */
private fun renderLatexToImage(
    latex: String,
    fontSize: Float,
    textColor: Color,
): ImageBitmap? = synchronized(renderLock) {
    try {
        val red = (textColor.red * 255).toInt().coerceIn(0, 255)
        val green = (textColor.green * 255).toInt().coerceIn(0, 255)
        val blue = (textColor.blue * 255).toInt().coerceIn(0, 255)
        val awtColor = AwtColor(red, green, blue)

        // Normalize the LaTeX string to prevent encoding issues
        val normalizedLatex = latex.trim()

        // Create formula and set default color
        val formula = TeXFormula(normalizedLatex)

        // Use TeXFormula's setColor to set the default foreground color for ALL elements
        try {
            val setColorMethod = TeXFormula::class.java.getDeclaredMethod("setColor", AwtColor::class.java)
            setColorMethod.isAccessible = true
            setColorMethod.invoke(formula, awtColor)
        } catch (e: Exception) {
            log.warn("Could not use setColor method: ${e.message}")
        }

        val icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, fontSize)

        // Ensure icon has valid dimensions
        if (icon.iconWidth <= 0 || icon.iconHeight <= 0) {
            log.error("Invalid icon dimensions: ${icon.iconWidth}x${icon.iconHeight} for latex: $normalizedLatex")
            return@synchronized null
        }

        val bufferedImage = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
        val g2 = bufferedImage.createGraphics()

        try {
            // Set transparent background
            g2.composite = AlphaComposite.getInstance(AlphaComposite.CLEAR)
            g2.fillRect(0, 0, icon.iconWidth, icon.iconHeight)
            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER)

            // Enable anti-aliasing for better quality
            g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON,
            )
            g2.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
            )
            g2.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY,
            )
            g2.setRenderingHint(
                RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON,
            )
            g2.setRenderingHint(
                RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE,
            )

            // Paint the icon - the color should now be applied
            icon.paintIcon(null, g2, 0, 0)
        } finally {
            g2.dispose()
        }

        // Convert BufferedImage to Compose ImageBitmap via Skia
        val outputStream = ByteArrayOutputStream()
        val writeSuccess = ImageIO.write(bufferedImage, "PNG", outputStream)

        if (!writeSuccess) {
            log.error("Failed to write PNG for latex: $normalizedLatex")
            return@synchronized null
        }

        val bytes = outputStream.toByteArray()

        if (bytes.isEmpty()) {
            log.error("Empty PNG bytes for latex: $normalizedLatex")
            return@synchronized null
        }

        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (e: Exception) {
        log.error("Failed to render LaTeX: $latex - ${e.message}", e)
        null
    }
}

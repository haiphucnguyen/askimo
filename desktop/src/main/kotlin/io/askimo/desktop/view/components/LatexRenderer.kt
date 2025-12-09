/* SPDX-License-Identifier: Apache-2.0
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
import io.askimo.core.logging.logger
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import java.awt.AlphaComposite
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.awt.Color as AwtColor

private val log = logger("LatexRenderer")

/**
 * Renders a LaTeX formula as an image using JLaTeXMath.
 */
@Composable
fun latexFormula(
    latex: String,
    modifier: Modifier = Modifier,
    fontSize: Float = 32f,
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
                .height(50.dp)
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

/**
 * Render LaTeX formula as an image using JLaTeXMath.
 */
private fun renderLatexToImage(
    latex: String,
    fontSize: Float,
    textColor: Color,
): ImageBitmap? = try {
    val red = (textColor.red * 255).toInt().coerceIn(0, 255)
    val green = (textColor.green * 255).toInt().coerceIn(0, 255)
    val blue = (textColor.blue * 255).toInt().coerceIn(0, 255)
    val awtColor = AwtColor(red, green, blue)

    // Create formula without color wrapping
    val formula = TeXFormula(latex)
    val icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, fontSize)

    // Set the foreground color on the icon using reflection to access internal box
    try {
        val boxField = icon.javaClass.getDeclaredField("box")
        boxField.isAccessible = true
        val box = boxField.get(icon)
        val foregroundField = box.javaClass.superclass.getDeclaredField("foreground")
        foregroundField.isAccessible = true
        foregroundField.set(box, awtColor)
    } catch (e: Exception) {
        log.error("Warning: Could not set LaTeX foreground color: ${e.message}", e)
    }

    val bufferedImage = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
    val g2 = bufferedImage.createGraphics()

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

    // Paint the icon
    icon.paintIcon(null, g2, 0, 0)
    g2.dispose()

    // Convert BufferedImage to Compose ImageBitmap via Skia
    val outputStream = ByteArrayOutputStream()
    ImageIO.write(bufferedImage, "PNG", outputStream)
    val bytes = outputStream.toByteArray()
    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
} catch (e: Exception) {
    log.error("Failed to render LaTeX: $latex - ${e.message}", e)
    null
}

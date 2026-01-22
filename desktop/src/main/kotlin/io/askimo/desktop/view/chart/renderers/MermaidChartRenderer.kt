/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.chart.renderers

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import io.askimo.core.logging.currentFileLogger
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.service.MermaidCliNotAvailableException
import io.askimo.desktop.service.MermaidSvgService
import io.askimo.tools.chart.MermaidChartData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.URI
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import org.jetbrains.skia.Image as SkiaImage

private val log = currentFileLogger()

/**
 * Cache for rendered Mermaid diagrams.
 * Uses memory cache for fast access and temp directory for persistence across renders.
 */
private object DiagramCache {
    private val memoryCache = mutableMapOf<String, ByteArray>()

    private val cacheDir = File(
        System.getProperty("java.io.tmpdir"),
        "askimo-diagram-cache",
    ).apply {
        mkdirs()
    }

    fun getCacheKey(diagram: String, theme: String, backgroundColor: String): String {
        val content = "$diagram|$theme|$backgroundColor"
        return content.hashCode().toString()
    }

    fun get(key: String): ByteArray? {
        memoryCache[key]?.let {
            log.debug("✅ Cache HIT (memory) for key: {}", key)
            return it
        }

        // Check disk cache in temp
        val cacheFile = File(cacheDir, "$key.png")
        return if (cacheFile.exists()) {
            try {
                cacheFile.readBytes().also {
                    memoryCache[key] = it // Store in memory for faster access
                    log.debug("✅ Cache HIT (disk) for key: {}, size: {} bytes", key, it.size)
                }
            } catch (e: Exception) {
                log.warn("Failed to read cache file: {}", e.message)
                null
            }
        } else {
            log.debug("❌ Cache MISS for key: {}", key)
            null
        }
    }

    fun put(key: String, data: ByteArray) {
        memoryCache[key] = data
        log.debug("📝 Cached in memory: key={}, size={} bytes", key, data.size)

        // Store in temp directory
        try {
            val cacheFile = File(cacheDir, "$key.png")
            cacheFile.writeBytes(data)
            log.debug("💾 Cached to disk: {}, size={} bytes", cacheFile.absolutePath, data.size)
        } catch (e: Exception) {
            log.warn("Failed to write cache file: {}", e.message)
        }
    }

    fun clearMemoryCache() {
        val size = memoryCache.size
        memoryCache.clear()
        log.debug("🗑️ Cleared memory cache ({} entries)", size)
    }
}

/**
 * Renders Mermaid diagrams using local Mermaid CLI to generate PNG.
 *
 * This renderer uses the locally installed mermaid-cli to convert Mermaid diagrams
 * to PNG images, ensuring privacy by keeping all data local and working offline.
 */
@Composable
fun mermaidChart(
    data: MermaidChartData,
    modifier: Modifier = Modifier,
) {
    val mermaidService = remember { MermaidSvgService() }
    var imageData by remember { mutableStateOf<ByteArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isMermaidCliAvailable by remember { mutableStateOf<Boolean?>(null) }
    var zoomLevel by remember { mutableStateOf(0.5f) }
    var showFullScreenDialog by remember { mutableStateOf(false) }
    var sanitizedDiagramCode by remember { mutableStateOf("") }

    // Detect if we're in dark mode based on background luminance
    val isDarkMode = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Get the actual surface color from the app theme to match backgrounds perfectly
    val surfaceColor = MaterialTheme.colorScheme.surface
    val backgroundColor = remember(surfaceColor) {
        val red = (surfaceColor.red * 255).toInt()
        val green = (surfaceColor.green * 255).toInt()
        val blue = (surfaceColor.blue * 255).toInt()
        "#%02x%02x%02x".format(red, green, blue)
    }

    // Load diagram - check cache first, then CLI availability if needed
    LaunchedEffect(data.diagram, isDarkMode, backgroundColor) {
        isLoading = true
        error = null

        val theme = if (data.theme.isNotEmpty() && data.theme.lowercase() != "default") {
            // User specified a theme, use it
            data.theme.lowercase()
        } else if (isDarkMode) {
            "dark"
        } else {
            "default"
        }

        // Check cache first - fastest path
        val sanitizedDiagram = sanitizeMermaidDiagram(data.diagram)
        sanitizedDiagramCode = sanitizedDiagram
        log.debug("Sanitized diagram:\n{}", sanitizedDiagram)
        val cacheKey = DiagramCache.getCacheKey(sanitizedDiagram, theme, backgroundColor)
        val cachedImage = DiagramCache.get(cacheKey)

        if (cachedImage != null) {
            log.debug("✅ Loaded diagram from cache ({} bytes)", cachedImage.size)
            imageData = cachedImage
            error = null
            isLoading = false
            isMermaidCliAvailable = true
        } else {
            log.debug("Cache miss, checking Mermaid CLI availability...")

            withContext(Dispatchers.IO) {
                isMermaidCliAvailable = mermaidService.isMermaidCliAvailable()
            }

            log.debug("Mermaid CLI available: {}", isMermaidCliAvailable)

            if (isMermaidCliAvailable == true) {
                log.debug("Converting diagram to PNG...")
                try {
                    val rendered = mermaidService.convertToPng(sanitizedDiagram, theme, backgroundColor)
                    imageData = rendered
                    error = null

                    // Store in cache for next time
                    DiagramCache.put(cacheKey, rendered)
                    log.debug("✅ Successfully rendered and cached diagram ({} bytes)", rendered.size)
                } catch (_: MermaidCliNotAvailableException) {
                    log.warn("Mermaid CLI became unavailable during conversion")
                    error = "mermaid_cli_not_available"
                    isMermaidCliAvailable = false
                } catch (e: Exception) {
                    log.error("Failed to convert diagram", e)
                    error = "Failed to render diagram: ${e.message}"
                }
            }

            isLoading = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        log.debug(
            "Rendering UI - CLI: {}, isLoading: {}, error: {}, imageData: {}",
            isMermaidCliAvailable,
            isLoading,
            error,
            imageData?.size,
        )
        when {
            // Still checking if CLI is available
            isMermaidCliAvailable == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Checking Mermaid CLI...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // CLI is not available - show setup instructions
            isMermaidCliAvailable == false -> {
                log.debug("Showing setup instructions (CLI not available)")
                mermaidSetupInstructions(
                    diagram = data.diagram,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Loading PNG (CLI is available, converting diagram)
            isLoading && isMermaidCliAvailable == true -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource("mermaid.rendering.progress"), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Error rendering (CLI is available but conversion failed)
            error != null && error != "mermaid_cli_not_available" && isMermaidCliAvailable == true -> {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource("mermaid.error.rendering.title"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource("mermaid.error.rendering.raw.label"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = data.diagram,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp).background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                            ).padding(8.dp),
                        )
                    }
                }
            }

            // Success - render PNG (CLI is available and conversion succeeded)
            imageData != null && isMermaidCliAvailable == true -> {
                log.debug("Rendering PNG image")
                diagramViewer(
                    imageData = imageData!!,
                    sanitizedDiagram = sanitizedDiagramCode,
                    zoomLevel = zoomLevel,
                    onZoomIn = { zoomLevel = (zoomLevel + 0.1f).coerceAtMost(3f) },
                    onZoomOut = { zoomLevel = (zoomLevel - 0.1f).coerceAtLeast(0.5f) },
                    onResetZoom = { zoomLevel = 1f },
                    onFullScreen = { showFullScreenDialog = true },
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                )

                // Full-screen dialog
                if (showFullScreenDialog && imageData != null) {
                    fullScreenDiagramDialog(
                        imageData = imageData!!,
                        sanitizedDiagram = sanitizedDiagramCode,
                        onDismiss = { showFullScreenDialog = false },
                    )
                }
            }

            // Fallback - should not normally reach here
            else -> {
                log.warn(
                    "Unexpected state - CLI: {}, isLoading: {}, error: {}, imageData: {}",
                    isMermaidCliAvailable,
                    isLoading,
                    error,
                    imageData?.size,
                )
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource("mermaid.error.unexpected.state"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * Composable that displays setup instructions when Mermaid CLI is not available.
 */
@Composable
private fun mermaidSetupInstructions(
    diagram: String,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val setupLink = stringResource("mermaid.setup.instructions.link")

    Column(
        modifier = modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource("mermaid.setup.required.title"),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource("mermaid.setup.privacy.note"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Setup instructions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                )
                .padding(16.dp),
        ) {
            Text(
                text = stringResource("mermaid.setup.instructions.title"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource("mermaid.setup.instructions.text"),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = setupLink,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                    )
                    .padding(12.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    try {
                        Desktop.getDesktop().browse(URI(setupLink))
                    } catch (_: Exception) {
                        // Ignore browser open failures
                    }
                },
            ) {
                Text(stringResource("mermaid.setup.button.open.guide"))
            }

            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(diagram))
                },
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource("mermaid.setup.button.copy.diagram"))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Show raw diagram
        Text(
            text = stringResource("mermaid.diagram.source.label"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = diagram,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                )
                .padding(12.dp),
        )
    }
}

/**
 * Shows a file chooser dialog and saves the image data as PNG.
 */
private fun downloadDiagramAsPng(imageData: ByteArray, log: org.slf4j.Logger) {
    val fileChooser = JFileChooser()
    fileChooser.dialogTitle = "Save Diagram"
    fileChooser.fileFilter = FileNameExtensionFilter("PNG Image", "png")
    fileChooser.selectedFile = File("mermaid-diagram.png")

    val result = fileChooser.showSaveDialog(null)
    if (result == JFileChooser.APPROVE_OPTION) {
        try {
            var file = fileChooser.selectedFile
            // Ensure .png extension
            if (!file.name.endsWith(".png", ignoreCase = true)) {
                file = File(file.absolutePath + ".png")
            }
            file.writeBytes(imageData)
            log.debug("Diagram saved to: {}", file.absolutePath)
        } catch (e: Exception) {
            log.error("Failed to save diagram", e)
        }
    }
}

/**
 * Reusable diagram viewer with zoom controls and download button.
 */
@Composable
private fun diagramViewer(
    imageData: ByteArray,
    sanitizedDiagram: String,
    zoomLevel: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onFullScreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    var showCopyFeedback by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Row(modifier = modifier) {
        // Diagram
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            diagramImage(
                imageData = imageData,
                zoomLevel = zoomLevel,
                modifier = Modifier.fillMaxSize(),
            )

            // Copy feedback
            if (showCopyFeedback) {
                Text(
                    text = stringResource("mermaid.feedback.copied"),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        // Zoom controls
        Column(
            modifier = Modifier
                .padding(start = if (onFullScreen != null) 8.dp else 16.dp)
                .width(if (onFullScreen != null) 48.dp else 56.dp),
            verticalArrangement = if (onFullScreen != null) Arrangement.Top else Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Fullscreen button (only in inline view)
            if (onFullScreen != null) {
                IconButton(
                    onClick = onFullScreen,
                    modifier = Modifier
                        .size(32.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(Icons.Default.Fullscreen, stringResource("mermaid.button.expand"))
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Copy diagram code button
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(sanitizedDiagram))
                    showCopyFeedback = true
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(2000)
                        showCopyFeedback = false
                    }
                },
                modifier = Modifier
                    .size(if (onFullScreen != null) 32.dp else 48.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    stringResource("mermaid.button.copy.code"),
                    modifier = if (onFullScreen != null) Modifier else Modifier.size(32.dp),
                )
            }

            Spacer(modifier = Modifier.height(if (onFullScreen != null) 8.dp else 16.dp))

            // Download button
            IconButton(
                onClick = { downloadDiagramAsPng(imageData, log) },
                modifier = Modifier
                    .size(if (onFullScreen != null) 32.dp else 48.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    Icons.Default.Download,
                    stringResource("mermaid.button.download"),
                    modifier = if (onFullScreen != null) Modifier else Modifier.size(32.dp),
                )
            }

            Spacer(modifier = Modifier.height(if (onFullScreen != null) 8.dp else 16.dp))

            // Zoom in
            IconButton(
                onClick = onZoomIn,
                modifier = Modifier
                    .size(if (onFullScreen != null) 32.dp else 48.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    Icons.Default.Add,
                    stringResource("mermaid.button.zoom.in"),
                    modifier = if (onFullScreen != null) Modifier else Modifier.size(32.dp),
                )
            }

            Spacer(modifier = Modifier.height(if (onFullScreen != null) 8.dp else 16.dp))

            // Reset zoom
            IconButton(
                onClick = onResetZoom,
                modifier = Modifier
                    .size(if (onFullScreen != null) 32.dp else 48.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    Icons.Default.Refresh,
                    stringResource("mermaid.button.reset.zoom"),
                    modifier = if (onFullScreen != null) Modifier else Modifier.size(32.dp),
                )
            }

            Spacer(modifier = Modifier.height(if (onFullScreen != null) 8.dp else 16.dp))

            // Zoom out
            IconButton(
                onClick = onZoomOut,
                modifier = Modifier
                    .size(if (onFullScreen != null) 32.dp else 48.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    Icons.Default.Remove,
                    stringResource("mermaid.button.zoom.out"),
                    modifier = if (onFullScreen != null) Modifier else Modifier.size(32.dp),
                )
            }
        }
    }
}

/**
 * Composable that renders PNG image data with zoom support and scrolling for large images.
 */
@Composable
private fun diagramImage(
    imageData: ByteArray,
    zoomLevel: Float,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = remember(imageData) {
        try {
            SkiaImage.makeFromEncoded(imageData).toComposeImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    if (imageBitmap != null) {
        val scaledWidth = (imageBitmap.width * zoomLevel).dp
        val scaledHeight = (imageBitmap.height * zoomLevel).dp

        val verticalScrollState = rememberScrollState()
        val horizontalScrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()

        Box(modifier = modifier) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
                    .horizontalScroll(horizontalScrollState)
                    .padding(end = 12.dp, bottom = 12.dp)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val newHorizontalScroll = (horizontalScrollState.value - dragAmount.x).coerceIn(
                                    0f,
                                    horizontalScrollState.maxValue.toFloat(),
                                )
                                val newVerticalScroll = (verticalScrollState.value - dragAmount.y).coerceIn(
                                    0f,
                                    verticalScrollState.maxValue.toFloat(),
                                )
                                horizontalScrollState.scrollTo(newHorizontalScroll.toInt())
                                verticalScrollState.scrollTo(newVerticalScroll.toInt())
                            }
                        }
                    },
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Mermaid Diagram",
                    modifier = Modifier
                        .width(scaledWidth)
                        .height(scaledHeight),
                )
            }

            // Vertical scrollbar
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd),
                adapter = rememberScrollbarAdapter(verticalScrollState),
                style = ScrollbarStyle(
                    minimalHeight = 16.dp,
                    thickness = 8.dp,
                    shape = MaterialTheme.shapes.small,
                    hoverDurationMillis = 300,
                    unhoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    hoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                ),
            )

            // Horizontal scrollbar
            HorizontalScrollbar(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                adapter = rememberScrollbarAdapter(horizontalScrollState),
                style = ScrollbarStyle(
                    minimalHeight = 16.dp,
                    thickness = 8.dp,
                    shape = MaterialTheme.shapes.small,
                    hoverDurationMillis = 300,
                    unhoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    hoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                ),
            )
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource("mermaid.error.failed.load.image"),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Full-screen dialog to display diagram at larger size with zoom controls.
 */
@Composable
private fun fullScreenDiagramDialog(
    imageData: ByteArray,
    sanitizedDiagram: String,
    onDismiss: () -> Unit,
) {
    var dialogZoomLevel by remember { mutableStateOf(0.5f) }

    Window(
        onCloseRequest = onDismiss,
        state = rememberWindowState(
            width = 1200.dp,
            height = 800.dp,
            position = WindowPosition(Alignment.Center),
        ),
        title = "Diagram Viewer",
        resizable = true,
        alwaysOnTop = true, // Makes it modal-like
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Close button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(48.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource("mermaid.button.close"),
                        modifier = Modifier.size(32.dp),
                    )
                }

                // Diagram with zoom controls
                diagramViewer(
                    imageData = imageData,
                    sanitizedDiagram = sanitizedDiagram,
                    zoomLevel = dialogZoomLevel,
                    onZoomIn = { dialogZoomLevel = (dialogZoomLevel + 0.2f).coerceAtMost(5f) },
                    onZoomOut = { dialogZoomLevel = (dialogZoomLevel - 0.2f).coerceAtLeast(0.5f) },
                    onResetZoom = { dialogZoomLevel = 1f },
                    onFullScreen = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 80.dp, end = 16.dp, bottom = 16.dp, start = 16.dp),
                )
            }
        }
    }
}

/**
 * Sanitizes Mermaid diagram syntax to fix common issues that AI might generate.
 * This is defensive programming - we fix common mistakes rather than showing errors.
 */
internal fun sanitizeMermaidDiagram(diagram: String): String {
    // ========================================
    // STEP 1: Initial Normalization
    // ========================================

    // First, normalize escape sequences (handles diagrams from JSON with literal \n)
    var sanitized = diagram
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\r", "\r")
        .trim()

    // Remove code fence markers if they were accidentally included
    sanitized = sanitized.replace(Regex("""^```mermaid\s*\n?"""), "")
    sanitized = sanitized.replace(Regex("""\n?```\s*$"""), "")
    sanitized = sanitized.trim()

    // ========================================
    // STEP 2: Validation - Detect Invalid Multi-Diagram
    // ========================================

    val diagramTypes = listOf(
        "flowchart", "sequenceDiagram", "classDiagram", "stateDiagram",
        "erDiagram", "gantt", "xychart-beta", "journey", "pie", "gitGraph",
    )

    val hasMultipleDiagramTypes = diagramTypes.count { type ->
        sanitized.contains(Regex("""\b$type\b"""))
    } > 1

    if (hasMultipleDiagramTypes) {
        return """flowchart TD
    A["Invalid Diagram: Multiple diagram types detected"]
    B["This diagram contains nested or multiple diagram types"]
    C["Please use only one diagram type per code block"]
    A --> B
    B --> C
    style A fill:#ffcccc,stroke:#cc0000,stroke-width:2px
    style B fill:#fff4cc,stroke:#cc9900,stroke-width:2px
    style C fill:#ccf4ff,stroke:#0099cc,stroke-width:2px"""
    }

    // ========================================
    // STEP 3: Flowchart/Graph Fixes
    // ========================================

    // Fix subgraph/node name conflicts
    val subgraphNames = mutableSetOf<String>()
    val subgraphRegex = Regex("""subgraph\s+(\w+)(?:\[.*?\])?""")
    subgraphRegex.findAll(sanitized).forEach { match ->
        subgraphNames.add(match.groupValues[1])
    }

    if (subgraphNames.isNotEmpty()) {
        val nodeRegex = Regex("""(\b(?:${subgraphNames.joinToString("|")})\b)(\[.+?\])""")
        sanitized = nodeRegex.replace(sanitized) { match ->
            val nodeName = match.groupValues[1]
            val nodeLabel = match.groupValues[2]
            "${nodeName}_Node$nodeLabel"
        }

        subgraphNames.forEach { name ->
            sanitized = sanitized.replace(
                Regex("""(?<!subgraph\s)\b($name)\b(?!\[)(?=\s*(?:-->|---|-\.-|==>|~~>|\|))"""),
            ) { "${it.value}_Node" }

            sanitized = sanitized.replace(
                Regex("""((?:-->|---|-\.-|==>|~~>|\|)\s+)($name)\b(?!\[)"""),
            ) { "${it.groupValues[1]}${it.groupValues[2]}_Node" }
        }
    }

    // Remove incomplete style statements
    sanitized = sanitized.lines()
        .filter { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("style ")) {
                !trimmed.matches(Regex("""style\s+\w+\s+.*\d$""")) ||
                    trimmed.matches(Regex("""style\s+\w+\s+.*\d+px\s*$"""))
            } else {
                true
            }
        }
        .joinToString("\n")

    // Fix node labels with special characters
    sanitized = sanitized.replace(
        Regex("""(\w+)\[([^\]"]+[()'"&<>]+[^\]"]*)\]"""),
    ) { matchResult ->
        val nodeId = matchResult.groupValues[1]
        val label = matchResult.groupValues[2]
        val escapedLabel = label.replace("\"", "#quot;")
        "$nodeId[\"$escapedLabel\"]"
    }

    // ========================================
    // STEP 4: XYChart-Beta Fixes (ALL TOGETHER)
    // ========================================

    if (sanitized.contains("xychart-beta")) {
        // Fix title without quotes
        sanitized = sanitized.replace(
            Regex("""(?m)^\s*title\s+(?!")(.+?)$"""),
        ) { matchResult ->
            val titleText = matchResult.groupValues[1].trim()
            if (titleText.startsWith("\"") && titleText.endsWith("\"")) {
                matchResult.value
            } else {
                "    title \"$titleText\""
            }
        }

        // Fix incorrect axis keywords: xaxis → x-axis, yaxis → y-axis
        sanitized = sanitized.replace(Regex("""(?m)^\s*xaxis\s+"""), "    x-axis ")
        sanitized = sanitized.replace(Regex("""(?m)^\s*yaxis\s+"""), "    y-axis ")

        // Fix y-axis label without quotes
        sanitized = sanitized.replace(
            Regex("""(?m)^\s*y-axis\s+(?!")([A-Za-z]\S*(?:\s+\S+)*)\s+(\d+\s*-->)"""),
        ) { matchResult ->
            val label = matchResult.groupValues[1].trim()
            val range = matchResult.groupValues[2]
            if (label.startsWith("\"") && label.endsWith("\"")) {
                matchResult.value
            } else {
                "    y-axis \"$label\" $range"
            }
        }

        // Replace invalid 'values' line with proper x-axis data format
        sanitized = sanitized.replace(
            Regex("""(?m)^\s*values\s+(.+?)$"""),
        ) { matchResult ->
            val values = matchResult.groupValues[1].trim()
            "    x-axis [$values]"
        }

        // Replace invalid 'data' keyword section with proper line format
        if (sanitized.contains(Regex("""(?m)^\s*data\s*$"""))) {
            val dataSection = Regex("""(?m)^\s*data\s*$\n((?:\s+"[^"]+"\s+\d+\s+\d+\n?)+)""").find(sanitized)
            if (dataSection != null) {
                val dataLines = dataSection.groupValues[1].trim().lines()
                val lineStatements = dataLines.map { line ->
                    val match = Regex("""\s*"([^"]+)"\s+(\d+)\s+(\d+)""").find(line)
                    if (match != null) {
                        val label = match.groupValues[1]
                        val x = match.groupValues[2]
                        val y = match.groupValues[3]
                        "    line [$x, $y] : \"$label\""
                    } else {
                        null
                    }
                }.filterNotNull()

                sanitized = sanitized.replace(
                    Regex("""(?m)^\s*data\s*$\n(?:\s+"[^"]+"\s+\d+\s+\d+\n?)+"""),
                    lineStatements.joinToString("\n") + "\n",
                )
            }
        }

        // Replace simple 'data' line with proper 'line' format
        sanitized = sanitized.replace(
            Regex("""(?m)^\s*data\s+(.+?)$"""),
        ) { matchResult ->
            val values = matchResult.groupValues[1].trim()
            "    line [$values]"
        }
    }

    // ========================================
    // STEP 5: ER Diagram Fixes
    // ========================================

    if (sanitized.contains(Regex("""^erDiagram\b""", RegexOption.MULTILINE))) {
        // Fix attribute syntax: convert "* id : integer" to "int id PK"
        // and "name : string" to "string name"
        val lines = sanitized.lines().toMutableList()
        var inEntity = false
        var entityIndent = ""

        for (i in lines.indices) {
            val line = lines[i]
            val trimmed = line.trim()

            // Detect entity definition start (entity name followed by {)
            if (trimmed.matches(Regex("""^\w+\s*\{"""))) {
                inEntity = true
                entityIndent = line.takeWhile { it.isWhitespace() }
                continue
            }

            // Detect entity definition end
            if (inEntity && trimmed == "}") {
                inEntity = false
                continue
            }

            // Fix attribute lines inside entity
            if (inEntity && trimmed.isNotEmpty()) {
                // Pattern: "* attribute : type" or "attribute : type"
                val attrMatch = Regex("""^(\*?)\s*(\w+)\s*:\s*(\w+)\s*(.*)$""").find(trimmed)
                if (attrMatch != null) {
                    val isPK = attrMatch.groupValues[1] == "*"
                    val attrName = attrMatch.groupValues[2]
                    val attrType = attrMatch.groupValues[3]
                    val rest = attrMatch.groupValues[4].trim()

                    // Convert type names to Mermaid-compatible format
                    val mermaidType = when (attrType.lowercase()) {
                        "integer", "int" -> "int"
                        "string", "varchar", "text" -> "string"
                        "decimal", "float", "double", "number" -> "decimal"
                        "date", "datetime", "timestamp" -> "date"
                        "boolean", "bool" -> "boolean"
                        else -> attrType
                    }

                    // Build the corrected attribute line
                    val pkMarker = if (isPK) " PK" else ""
                    val fkMarker = if (rest.contains("FK", ignoreCase = true)) " FK" else ""
                    lines[i] = "$entityIndent    $mermaidType $attrName$pkMarker$fkMarker"
                }
            }
        }

        sanitized = lines.joinToString("\n")
    }

    // ========================================
    // STEP 6: Journey/Pie/Gantt Title Fixes
    // ========================================

    if (sanitized.contains(Regex("""^(journey|pie|gantt)\b""", RegexOption.MULTILINE))) {
        // Remove quotes from titles - these diagram types don't use quoted titles
        sanitized = sanitized.replace(
            Regex("""(?m)^\s*title\s+"([^"]+)"\s*$"""),
        ) { matchResult ->
            val titleText = matchResult.groupValues[1].trim()
            "    title $titleText"
        }
    }

    // ========================================
    // STEP 7: Pie Chart Fixes
    // ========================================

    if (sanitized.contains(Regex("""^pie\b""", RegexOption.MULTILINE))) {
        // Ensure proper spacing in data format
        sanitized = sanitized.replace(
            Regex("""(?m)^\s*"([^"]+)"\s*:\s*(\d+)\s*$"""),
        ) { matchResult ->
            val label = matchResult.groupValues[1]
            val value = matchResult.groupValues[2]
            "    \"$label\" : $value"
        }
    }

    // ========================================
    // STEP 8: RequirementDiagram Fixes
    // ========================================

    if (sanitized.contains(Regex("""^requirementDiagram\b""", RegexOption.MULTILINE))) {
        // Remove title - not supported
        sanitized = sanitized.replace(Regex("""(?m)^\s*title\s+.+?$\n?"""), "")

        // Convert simple syntax to proper structure with braces
        sanitized = sanitized.replace(
            Regex("""(?m)^\s*requirement\s+(\w+)\s*$"""),
        ) { match ->
            val name = match.groupValues[1]
            "    requirement $name {\n    }"
        }

        sanitized = sanitized.replace(
            Regex("""(?m)^\s*element\s+(\w+)\s*$"""),
        ) { match ->
            val name = match.groupValues[1]
            "    element $name {\n    }"
        }

        // Handle angle bracket syntax
        sanitized = sanitized.replace(
            Regex("""requirement\s+(\w+)\s*<[^>]+>"""),
        ) { match ->
            val name = match.groupValues[1]
            "requirement $name {\n    }"
        }

        sanitized = sanitized.replace(
            Regex("""element\s+(\w+)\s*<[^>]+>"""),
        ) { match ->
            val name = match.groupValues[1]
            "element $name {\n    }"
        }
    }

    // ========================================
    // STEP 9: Treemap Fixes
    // ========================================

    if (sanitized.contains(Regex("""^treemap\b""", RegexOption.MULTILINE))) {
        // Fix missing closing quotes
        sanitized = sanitized.replace(
            Regex("""(?m)^\s*"([^"]+:\s*\d+)\s*$"""),
        ) { match ->
            val content = match.groupValues[1]
            val indent = match.value.takeWhile { it.isWhitespace() }
            "$indent\"$content\""
        }

        // Scale up small values to improve readability
        val values = mutableListOf<Int>()
        Regex(""""[^"]+:\s*(\d+)"""").findAll(sanitized).forEach { match ->
            values.add(match.groupValues[1].toInt())
        }

        if (values.isNotEmpty() && values.maxOrNull()!! < 1000) {
            val scaleFactor = 10
            sanitized = sanitized.replace(
                Regex(""""([^"]+):\s*(\d+)""""),
            ) { match ->
                val label = match.groupValues[1]
                val value = match.groupValues[2].toInt()
                "\"$label: ${value * scaleFactor}\""
            }
        }
    }

    // ========================================
    // STEP 10: Architecture-Beta Fixes
    // ========================================

    if (sanitized.contains(Regex("""^architecture-beta\b""", RegexOption.MULTILINE))) {
        // Add default icon if missing
        sanitized = sanitized.replace(
            Regex("""(?m)^\s*service\s+([a-zA-Z0-9_-]+)\[([^\]]+)\]\s*$"""),
        ) { match ->
            val serviceName = match.groupValues[1]
            val label = match.groupValues[2]
            val indent = match.value.takeWhile { it.isWhitespace() }
            "$indent service $serviceName(server)[$label]"
        }

        // Add position indicators to connections
        sanitized = sanitized.replace(
            Regex("""(?m)^\s*([a-zA-Z0-9_-]+)\s+-->\s+([a-zA-Z0-9_-]+)\s*$"""),
        ) { match ->
            val source = match.groupValues[1]
            val target = match.groupValues[2]
            val indent = match.value.takeWhile { it.isWhitespace() }
            "$indent$source:R --> L:$target"
        }
    }

    // ========================================
    // STEP 11: Sankey Diagram Fixes
    // ========================================

    if (sanitized.contains(Regex("""^sankeyDiagram\b""", RegexOption.MULTILINE))) {
        // Fix keyword
        sanitized = sanitized.replace("sankeyDiagram", "sankey-beta")

        // Fix data format from nested arrays
        sanitized = sanitized.replace(
            Regex("""data\s+\[\[(.*?)\]\]""", RegexOption.DOT_MATCHES_ALL),
        ) { matchResult ->
            val dataContent = matchResult.groupValues[1]
            val entries = Regex("""\[\\?"([^"\\]+)\\?",\s*\\?"([^"\\]+)\\?",\s*(\d+)\]""")
                .findAll(dataContent)
                .map { "${it.groupValues[1]},${it.groupValues[2]},${it.groupValues[3]}" }
                .joinToString("\n    ")
            if (entries.isNotEmpty()) {
                entries
            } else {
                matchResult.value
            }
        }
    }

    return sanitized
}

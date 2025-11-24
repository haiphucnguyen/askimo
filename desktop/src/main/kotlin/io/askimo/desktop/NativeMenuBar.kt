/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop

import androidx.compose.ui.window.FrameWindowScope
import java.awt.Desktop
import java.awt.Menu
import java.awt.MenuBar
import java.awt.MenuItem
import java.awt.event.ActionListener
import java.net.URI

/**
 * Native menu bar handler that provides OS-specific menu implementations.
 * - macOS: Uses system menu bar at the top of screen
 * - Windows/Linux: Uses AWT MenuBar with native look and feel
 */
object NativeMenuBar {
    fun setup(
        frameWindowScope: FrameWindowScope,
        onShowAbout: () -> Unit,
    ) {
        val window = frameWindowScope.window
        val isMac = System.getProperty("os.name").contains("Mac", ignoreCase = true)

        // Setup AWT menu bar for all platforms (includes Documentation)
        setupAWTMenuBar(window, onShowAbout)

        // On macOS, also register the About handler for the app menu
        if (isMac) {
            setupMacAboutHandler(onShowAbout)
        }
    }

    private fun setupMacAboutHandler(onShowAbout: () -> Unit) {
        try {
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()

                // Set About handler (appears in app menu on macOS)
                if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
                    desktop.setAboutHandler { onShowAbout() }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupAWTMenuBar(
        window: java.awt.Window,
        onShowAbout: () -> Unit,
    ) {
        if (window is java.awt.Frame) {
            val menuBar = MenuBar()

            // Help Menu
            val helpMenu = Menu("Help")

            val docsItem = MenuItem("Documentation")
            docsItem.addActionListener(
                ActionListener {
                    try {
                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().browse(URI("https://askimo.chat/docs/"))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
            )
            helpMenu.add(docsItem)

            helpMenu.addSeparator()

            val aboutItem = MenuItem("About Askimo")
            aboutItem.addActionListener(
                ActionListener {
                    onShowAbout()
                },
            )
            helpMenu.add(aboutItem)

            menuBar.add(helpMenu)

            // Set the native menu bar
            window.menuBar = menuBar
        }
    }
}

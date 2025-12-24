/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.components

import androidx.compose.ui.window.FrameWindowScope
import io.askimo.core.i18n.LocalizationManager
import io.askimo.desktop.util.Platform
import java.awt.Desktop
import java.awt.Frame
import java.awt.Menu
import java.awt.MenuBar
import java.awt.MenuItem
import java.awt.MenuShortcut
import java.awt.Window
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
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
        onNewChat: () -> Unit,
        onShowSettings: () -> Unit,
        onShowEventLog: () -> Unit,
        onCheckForUpdates: () -> Unit,
    ) {
        val window = frameWindowScope.window

        // Setup AWT menu bar for all platforms (includes Documentation)
        setupAWTMenuBar(window, onShowAbout, onNewChat, onShowSettings, onShowEventLog, onCheckForUpdates)

        // On macOS, also register the About handler for the app menu
        if (Platform.isMac) {
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
        window: Window,
        onShowAbout: () -> Unit,
        onNewChat: () -> Unit,
        onShowSettings: () -> Unit,
        onShowEventLog: () -> Unit,
        onCheckForUpdates: () -> Unit,
    ) {
        if (window is Frame) {
            val menuBar = MenuBar()

            // File Menu
            val fileMenu = Menu(LocalizationManager.getString("menu.file"))

            val newChatItem = MenuItem(
                LocalizationManager.getString("chat.new"),
                MenuShortcut(KeyEvent.VK_N),
            )
            newChatItem.addActionListener(
                ActionListener {
                    onNewChat()
                },
            )
            fileMenu.add(newChatItem)

            fileMenu.addSeparator()

            val settingsItem = MenuItem(
                LocalizationManager.getString("settings.title"),
                MenuShortcut(KeyEvent.VK_COMMA),
            )
            settingsItem.addActionListener(
                ActionListener {
                    onShowSettings()
                },
            )
            fileMenu.add(settingsItem)

            menuBar.add(fileMenu)

            // Help Menu
            val helpMenu = Menu(LocalizationManager.getString("menu.help"))

            val docsItem = MenuItem(LocalizationManager.getString("menu.documentation"))
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

            // Star on GitHub
            val starGitHubText = LocalizationManager.getString("menu.help.star.github")
            // On Windows, replace emoji with Unicode star character that renders better in AWT
            val starGitHubDisplayText = if (Platform.isWindows) {
                starGitHubText.replace("⭐", "★")
            } else {
                starGitHubText
            }
            val starGitHubItem = MenuItem(starGitHubDisplayText)
            starGitHubItem.addActionListener(
                ActionListener {
                    try {
                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().browse(URI("https://github.com/haiphucnguyen/askimo"))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
            )
            helpMenu.add(starGitHubItem)

            helpMenu.addSeparator()

            // Check for Updates
            val checkUpdatesItem = MenuItem(LocalizationManager.getString("menu.help.check.updates"))
            checkUpdatesItem.addActionListener(
                ActionListener {
                    onCheckForUpdates()
                },
            )
            helpMenu.add(checkUpdatesItem)

            // Event Log (Developer Tools)
            val eventLogItem = MenuItem(LocalizationManager.getString("menu.eventlog"))
            eventLogItem.addActionListener(
                ActionListener {
                    onShowEventLog()
                },
            )
            helpMenu.add(eventLogItem)

            helpMenu.addSeparator()

            val aboutItem = MenuItem(LocalizationManager.getString("menu.about"))
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

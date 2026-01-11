/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.components

import androidx.compose.ui.window.FrameWindowScope
import io.askimo.core.i18n.LocalizationManager
import io.askimo.desktop.preferences.ThemePreferences
import io.askimo.desktop.theme.ThemeMode
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
    private var updateSidebarMenuItem: ((Boolean) -> Unit)? = null

    fun updateSidebarMenuLabel(isSidebarExpanded: Boolean) {
        updateSidebarMenuItem?.invoke(isSidebarExpanded)
    }

    fun setup(
        frameWindowScope: FrameWindowScope,
        onShowAbout: () -> Unit,
        onNewChat: () -> Unit,
        onNewProject: () -> Unit,
        onSearchInSessions: () -> Unit,
        onShowSettings: () -> Unit,
        onShowEventLog: () -> Unit,
        onCheckForUpdates: () -> Unit,
        onEnterFullScreen: () -> Unit,
        onNavigateToSessions: () -> Unit,
        onToggleSidebar: () -> Unit,
    ) {
        val window = frameWindowScope.window

        // Setup AWT menu bar for all platforms (includes Documentation)
        setupAWTMenuBar(window, onShowAbout, onNewChat, onNewProject, onSearchInSessions, onShowSettings, onShowEventLog, onCheckForUpdates, onEnterFullScreen, onNavigateToSessions, onToggleSidebar)

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
        onNewProject: () -> Unit,
        onSearchInSessions: () -> Unit,
        onShowSettings: () -> Unit,
        onShowEventLog: () -> Unit,
        onCheckForUpdates: () -> Unit,
        onEnterFullScreen: () -> Unit,
        onNavigateToSessions: () -> Unit,
        onToggleSidebar: () -> Unit,
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

            val newProjectItem = MenuItem(
                LocalizationManager.getString("menu.new.project"),
                MenuShortcut(KeyEvent.VK_N, true), // Shift+Ctrl+N (or Shift+Cmd+N on Mac)
            )
            newProjectItem.addActionListener(
                ActionListener {
                    onNewProject()
                },
            )
            fileMenu.add(newProjectItem)

            fileMenu.addSeparator()

            val searchSessionsItem = MenuItem(
                LocalizationManager.getString("menu.search.sessions"),
                MenuShortcut(KeyEvent.VK_F, true), // Shift+Ctrl+F (or Shift+Cmd+F on Mac)
            )
            searchSessionsItem.addActionListener(
                ActionListener {
                    onSearchInSessions()
                },
            )
            fileMenu.add(searchSessionsItem)

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

            val viewMenu = Menu(LocalizationManager.getString("menu.view"))

            val appearanceMenu = Menu(LocalizationManager.getString("menu.view.appearance"))

            val systemThemeItem = MenuItem("")
            val lightThemeItem = MenuItem("")
            val darkThemeItem = MenuItem("")

            // Helper function to update all theme menu items
            fun updateThemeMenuItems() {
                val currentTheme = ThemePreferences.themeMode.value
                systemThemeItem.label = (if (currentTheme == ThemeMode.SYSTEM) "✓ " else "  ") +
                    LocalizationManager.getString("menu.view.appearance.system")
                lightThemeItem.label = (if (currentTheme == ThemeMode.LIGHT) "✓ " else "  ") +
                    LocalizationManager.getString("menu.view.appearance.light")
                darkThemeItem.label = (if (currentTheme == ThemeMode.DARK) "✓ " else "  ") +
                    LocalizationManager.getString("menu.view.appearance.dark")
            }

            // Initialize labels
            updateThemeMenuItems()

            systemThemeItem.addActionListener(
                ActionListener {
                    ThemePreferences.setThemeMode(ThemeMode.SYSTEM)
                    updateThemeMenuItems()
                },
            )
            appearanceMenu.add(systemThemeItem)

            lightThemeItem.addActionListener(
                ActionListener {
                    ThemePreferences.setThemeMode(ThemeMode.LIGHT)
                    updateThemeMenuItems()
                },
            )
            appearanceMenu.add(lightThemeItem)

            darkThemeItem.addActionListener(
                ActionListener {
                    ThemePreferences.setThemeMode(ThemeMode.DARK)
                    updateThemeMenuItems()
                },
            )
            appearanceMenu.add(darkThemeItem)

            viewMenu.add(appearanceMenu)

            // Separator after Appearance group
            viewMenu.addSeparator()

            // Session View
            val sessionViewItem = MenuItem(
                LocalizationManager.getString("menu.view.session"),
                MenuShortcut(KeyEvent.VK_E), // Ctrl+E (or Cmd+E on Mac)
            )
            sessionViewItem.addActionListener(
                ActionListener {
                    onNavigateToSessions()
                },
            )
            viewMenu.add(sessionViewItem)

            viewMenu.addSeparator()

            val toggleSidebarItem = MenuItem("")

            val updateSidebarMenuItemFunc: (Boolean) -> Unit = { isSidebarExpanded ->
                toggleSidebarItem.label = if (isSidebarExpanded) {
                    LocalizationManager.getString("menu.view.hide.sidebar")
                } else {
                    LocalizationManager.getString("menu.view.show.sidebar")
                }
            }

            updateSidebarMenuItem = updateSidebarMenuItemFunc

            updateSidebarMenuItemFunc(true)

            toggleSidebarItem.addActionListener(
                ActionListener {
                    onToggleSidebar()
                },
            )
            viewMenu.add(toggleSidebarItem)

            val fullScreenItem = MenuItem(
                LocalizationManager.getString("menu.view.fullscreen"),
                MenuShortcut(KeyEvent.VK_F, true), // Ctrl+Cmd+F on Mac, Ctrl+F on others
            )
            fullScreenItem.addActionListener(
                ActionListener {
                    onEnterFullScreen()
                },
            )
            viewMenu.add(fullScreenItem)

            menuBar.add(viewMenu)

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

            // Release Notes
            val releaseNotesItem = MenuItem(LocalizationManager.getString("menu.help.release.notes"))
            releaseNotesItem.addActionListener(
                ActionListener {
                    try {
                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().browse(URI("https://askimo.chat/docs/changelogs/"))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
            )
            helpMenu.add(releaseNotesItem)

            // Star on GitHub
            val starGitHubText = LocalizationManager.getString("menu.help.star.github")
            // On Windows, replace emoji with Unicode star character that renders better in AWT
            val starGitHubDisplayText = if (Platform.isWindows) {
                starGitHubText.replace("⭐", "")
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

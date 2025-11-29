/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.di

import io.askimo.core.di.coreModule
import io.askimo.desktop.service.ChatService
import io.askimo.desktop.service.SystemResourceMonitor
import io.askimo.desktop.viewmodel.ChatViewModel
import io.askimo.desktop.viewmodel.SessionsViewModel
import io.askimo.desktop.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module

/**
 * Koin module for desktop application dependencies.
 * This module provides desktop-specific dependencies like ViewModels and Services.
 */
val desktopModule = module {
    single { ChatService() }
    single { SystemResourceMonitor() }

    // ViewModels - factory scope so each request gets a new instance with its own scope
    factory { (scope: CoroutineScope) ->
        ChatViewModel(
            chatService = get(),
            scope = scope,
            repository = get(),
        )
    }

    factory { (scope: CoroutineScope) ->
        SessionsViewModel(scope = scope)
    }

    factory { (scope: CoroutineScope) ->
        SettingsViewModel(scope = scope)
    }
}

/**
 * All modules for the desktop application.
 */
val allDesktopModules = listOf(coreModule, desktopModule)

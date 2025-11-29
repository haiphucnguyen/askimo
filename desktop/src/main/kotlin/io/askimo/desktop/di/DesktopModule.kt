/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.di

import io.askimo.core.directive.ChatDirectiveRepository
import io.askimo.core.directive.ChatDirectiveService
import io.askimo.core.session.ChatSessionExporterService
import io.askimo.core.session.ChatSessionRepository
import io.askimo.core.session.ChatSessionService
import io.askimo.core.session.Session
import io.askimo.core.session.SessionFactory
import io.askimo.core.session.SessionMode
import io.askimo.desktop.service.ChatService
import io.askimo.desktop.service.SystemResourceMonitor
import io.askimo.desktop.viewmodel.ChatViewModel
import io.askimo.desktop.viewmodel.SessionsViewModel
import io.askimo.desktop.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module

/**
 * Koin module for desktop application dependencies.
 * This module provides both core and desktop-specific dependencies.
 */
val desktopModule = module {
    single<Session> { SessionFactory.createSession(mode = SessionMode.DESKTOP) }

    single { ChatSessionRepository() }
    single { ChatDirectiveRepository() }

    single { ChatSessionService(repository = get()) }
    single { ChatSessionExporterService(repository = get()) }
    single { ChatDirectiveService(repository = get()) }

    single { ChatService(session = get()) }
    single { SystemResourceMonitor() }

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
        SettingsViewModel(scope = scope, session = get())
    }
}

/**
 * All modules for the desktop application.
 */
val allDesktopModules = listOf(desktopModule)

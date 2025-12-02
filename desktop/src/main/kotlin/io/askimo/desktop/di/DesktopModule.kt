/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.di

import io.askimo.core.chat.repository.ChatDirectiveRepository
import io.askimo.core.chat.repository.ChatFolderRepository
import io.askimo.core.chat.repository.ChatMessageRepository
import io.askimo.core.chat.repository.ChatSessionRepository
import io.askimo.core.chat.repository.ConversationSummaryRepository
import io.askimo.core.chat.service.ChatDirectiveService
import io.askimo.core.chat.service.ChatSessionExporterService
import io.askimo.core.chat.service.ChatSessionService
import io.askimo.core.context.AppContext
import io.askimo.core.context.AppContextFactory
import io.askimo.core.context.ExecutionMode
import io.askimo.desktop.chat.ChatSessionManager
import io.askimo.desktop.monitoring.SystemResourceMonitor
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
    single<AppContext> { AppContextFactory.createAppContext(mode = ExecutionMode.DESKTOP) }

    // Repository layer - each repository manages one table and auto-initializes on construction
    single { ChatSessionRepository() }
    single { ChatMessageRepository() }
    single { ChatFolderRepository() }
    single { ConversationSummaryRepository() }
    single { ChatDirectiveRepository() }

    // Service layer - coordinates between repositories
    single {
        ChatSessionService(
            sessionRepository = get(),
            messageRepository = get(),
            summaryRepository = get(),
            folderRepository = get(),
        )
    }
    single {
        ChatSessionExporterService(
            sessionRepository = get(),
            messageRepository = get(),
        )
    }
    single { ChatDirectiveService(repository = get()) }

    single { ChatSessionManager(appContext = get()) }
    single { SystemResourceMonitor() }

    factory { (scope: CoroutineScope) ->
        ChatViewModel(
            chatSessionManager = get(),
            scope = scope,
            repository = get<ChatSessionService>(),
            appContext = get(),
        )
    }

    factory { (scope: CoroutineScope) ->
        SessionsViewModel(scope = scope)
    }

    factory { (scope: CoroutineScope) ->
        SettingsViewModel(scope = scope, appContext = get())
    }
}

/**
 * All modules for the desktop application.
 */
val allDesktopModules = listOf(desktopModule)

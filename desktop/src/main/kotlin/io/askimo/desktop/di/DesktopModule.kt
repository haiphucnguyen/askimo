/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.di
import io.askimo.core.chat.service.ChatDirectiveService
import io.askimo.core.chat.service.ChatSessionExporterService
import io.askimo.core.chat.service.ChatSessionService
import io.askimo.core.context.AppContext
import io.askimo.core.context.AppContextFactory
import io.askimo.core.context.ExecutionMode
import io.askimo.core.db.DatabaseManager
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

    single { DatabaseManager.getInstance() }

    single { get<DatabaseManager>().getChatSessionRepository() }
    single { get<DatabaseManager>().getChatMessageRepository() }
    single { get<DatabaseManager>().getChatFolderRepository() }
    single { get<DatabaseManager>().getConversationSummaryRepository() }
    single { get<DatabaseManager>().getChatDirectiveRepository() }

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

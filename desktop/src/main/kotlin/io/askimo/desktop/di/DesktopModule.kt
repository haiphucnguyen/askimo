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
import io.askimo.desktop.monitoring.SystemResourceMonitor
import io.askimo.desktop.service.UpdateService
import io.askimo.desktop.viewmodel.SessionManager
import io.askimo.desktop.viewmodel.SessionsViewModel
import io.askimo.desktop.viewmodel.SettingsViewModel
import io.askimo.desktop.viewmodel.UpdateViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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
    single { get<DatabaseManager>().getChatDirectiveRepository() }

    single {
        ChatSessionService(
            sessionRepository = get(),
            messageRepository = get(),
            folderRepository = get(),
            appContext = get(),
        )
    }
    single {
        ChatSessionExporterService(
            sessionRepository = get(),
            messageRepository = get(),
        )
    }
    single { ChatDirectiveService(repository = get()) }

    single { SystemResourceMonitor() }

    // SessionManager - manages multiple ChatViewModel instances AND streaming infrastructure
    single {
        SessionManager(
            chatSessionService = get(),
            appContext = get(),
            scope = CoroutineScope(kotlinx.coroutines.Dispatchers.Default + SupervisorJob()),
        )
    }

    factory { (scope: CoroutineScope, sessionManager: SessionManager?, onCreateNewSession: (() -> String)?) ->
        SessionsViewModel(
            scope = scope,
            sessionService = get(),
            sessionManager = sessionManager,
            onCreateNewSession = onCreateNewSession,
        )
    }

    factory { (scope: CoroutineScope) ->
        SettingsViewModel(scope = scope, appContext = get())
    }

    single { UpdateService() }
    factory { (scope: CoroutineScope) ->
        UpdateViewModel(
            scope = scope,
            updateService = get(),
        )
    }
}

/**
 * All modules for the desktop application.
 */
val allDesktopModules = listOf(desktopModule)

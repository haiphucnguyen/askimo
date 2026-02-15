/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.di
import io.askimo.core.chat.service.ChatDirectiveService
import io.askimo.core.chat.service.ChatSessionExporterService
import io.askimo.core.chat.service.ChatSessionService
import io.askimo.core.context.AppContext
import io.askimo.core.db.DatabaseManager
import io.askimo.core.mcp.ProjectMcpInstanceService
import io.askimo.desktop.common.monitoring.SystemResourceMonitor
import io.askimo.desktop.project.ProjectViewModel
import io.askimo.desktop.project.ProjectsViewModel
import io.askimo.desktop.service.UpdateService
import io.askimo.desktop.session.SessionManager
import io.askimo.desktop.session.SessionsViewModel
import io.askimo.desktop.session.command.DeleteSessionFromProjectCommand
import io.askimo.desktop.settings.SettingsViewModel
import io.askimo.desktop.shell.UpdateViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * Koin module for desktop application dependencies.
 */
val desktopModule = module {
    single<AppContext> { AppContext.getInstance() }

    single { DatabaseManager.getInstance() }

    single { get<DatabaseManager>().getChatSessionRepository() }
    single { get<DatabaseManager>().getChatMessageRepository() }
    single { get<DatabaseManager>().getChatDirectiveRepository() }
    single { get<DatabaseManager>().getProjectRepository() }

    single {
        ChatSessionService(
            sessionRepository = get(),
            messageRepository = get(),
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

    single { ProjectMcpInstanceService() }

    single { SystemResourceMonitor() }

    single {
        SessionManager(
            chatSessionService = get(),
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
        )
    }

    factory { (scope: CoroutineScope, sessionManager: SessionManager, onCreateNewSession: () -> String, onRenameComplete: () -> Unit) ->
        SessionsViewModel(
            scope = scope,
            sessionService = get(),
            sessionManager = sessionManager,
            onCreateNewSession = onCreateNewSession,
            onRenameComplete = onRenameComplete,
        )
    }

    factory { (scope: CoroutineScope) ->
        ProjectsViewModel(scope = scope)
    }

    factory { (scope: CoroutineScope, projectId: String) ->
        ProjectViewModel(scope = scope, projectId = projectId)
    }

    factory { (scope: CoroutineScope) ->
        SettingsViewModel(scope = scope, appContext = get())
    }

    // Commands
    factory { (scope: CoroutineScope) ->
        DeleteSessionFromProjectCommand(
            chatSessionRepository = get(),
            scope = scope,
        )
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
val allDesktopModules = listOf(
    desktopRagModule,
    desktopModule,
)

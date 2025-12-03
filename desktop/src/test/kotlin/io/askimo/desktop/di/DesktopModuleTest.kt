/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.di

import io.askimo.core.chat.service.ChatDirectiveService
import io.askimo.core.chat.service.ChatSessionExporterService
import io.askimo.core.chat.service.ChatSessionService
import io.askimo.core.context.AppContext
import io.askimo.desktop.di.allDesktopModules
import io.askimo.desktop.monitoring.SystemResourceMonitor
import io.askimo.desktop.viewmodel.SessionManager
import io.askimo.desktop.viewmodel.SessionsViewModel
import io.askimo.desktop.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.test.KoinTest
import kotlin.test.assertNotNull

/**
 * Test to verify all Koin modules are properly configured.
 *
 * This test ensures:
 * - All dependencies can be resolved
 * - No circular dependencies exist
 * - All services are registered correctly
 *
 * This prevents runtime errors like "No definition found for type X"
 */
class DesktopModuleTest : KoinTest {

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `verify all services can be instantiated`() {
        val koin = startKoin {
            modules(allDesktopModules)
        }.koin

        assertNotNull(koin.get<AppContext>())
        assertNotNull(koin.get<SystemResourceMonitor>())
        assertNotNull(koin.get<SessionManager>())
        assertNotNull(koin.get<ChatSessionService>())
        assertNotNull(koin.get<ChatSessionExporterService>())
        assertNotNull(koin.get<ChatDirectiveService>())
    }

    @Test
    fun `verify ViewModels can be instantiated with parameters`() {
        val koin = startKoin {
            modules(allDesktopModules)
        }.koin

        val scope = CoroutineScope(Dispatchers.Default)
        val sessionManager = koin.get<SessionManager>()

        assertNotNull(sessionManager)

        assertNotNull(
            koin.get<SessionsViewModel> {
                parametersOf(
                    scope,
                    sessionManager,
                    { "test-session-id" },
                )
            },
        )
        assertNotNull(koin.get<SettingsViewModel> { parametersOf(scope) })
    }
}

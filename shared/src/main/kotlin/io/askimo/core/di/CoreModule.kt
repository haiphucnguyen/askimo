/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.di

import io.askimo.core.directive.ChatDirectiveRepository
import io.askimo.core.directive.ChatDirectiveService
import io.askimo.core.session.ChatSessionExporterService
import io.askimo.core.session.ChatSessionRepository
import io.askimo.core.session.ChatSessionService
import org.koin.dsl.module

/**
 * Koin module for core dependencies.
 * This module provides shared dependencies like repositories and services.
 */
val coreModule = module {
    // Singleton repositories - only one instance throughout the app lifecycle
    single { ChatSessionRepository() }
    single { ChatDirectiveRepository() }

    // Singleton services - use the injected repositories
    single { ChatSessionService(repository = get()) }
    single { ChatSessionExporterService(repository = get()) }
    single { ChatDirectiveService(repository = get()) }
}

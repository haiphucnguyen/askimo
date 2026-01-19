/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.di

import io.askimo.core.rag.ProjectIndexer
import org.koin.dsl.module

/**
 * Koin module for RAG (Retrieval-Augmented Generation) components in the desktop application.
 */
val desktopRagModule = module {
    single(createdAtStart = true) {
        ProjectIndexer(
            appContext = get(),
            projectRepository = get(),
        )
    }
}

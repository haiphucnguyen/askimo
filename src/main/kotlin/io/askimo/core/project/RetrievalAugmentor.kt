/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import dev.langchain4j.model.input.PromptTemplate
import dev.langchain4j.rag.DefaultRetrievalAugmentor
import dev.langchain4j.rag.content.injector.DefaultContentInjector

fun buildRetrievalAugmentor(retriever: PgVectorContentRetriever) =
    DefaultRetrievalAugmentor
        .builder()
        .contentRetriever(retriever)
        .contentInjector(
            DefaultContentInjector
                .builder()
                .promptTemplate(
                    PromptTemplate.from(
                        """
                        You are grounded by the following retrieved context. Prefer it over general knowledge.
                        If the answer is not present, say so.

                        === Retrieved Context ===
                        {{contents}}
                        === End Context ===

                        {{userMessage}}
                        """.trimIndent(),
                    ),
                ).build(),
        ).build()

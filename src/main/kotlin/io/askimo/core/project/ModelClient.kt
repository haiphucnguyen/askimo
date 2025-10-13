/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

interface ModelClient {
    fun proposeUnifiedDiff(request: DiffRequest): String
}

package io.askimo.core.project

interface ModelClient {

    fun proposeUnifiedDiff(request: DiffRequest): String
}
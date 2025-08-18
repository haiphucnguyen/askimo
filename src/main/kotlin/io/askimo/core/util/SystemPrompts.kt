package io.askimo.core.util

object SystemPrompts {
    val markdownDefaults =
        listOf(
            "By default, always respond in GitHub-Flavored Markdown.",
            "Never return HTML unless explicitly asked.",
        )

    fun systemMessage(vararg extras: String) = (markdownDefaults + extras).joinToString("\n\n")
}

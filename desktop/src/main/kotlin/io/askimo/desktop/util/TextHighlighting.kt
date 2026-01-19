/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * Highlights all occurrences of a search query in text.
 *
 * @param text The text to highlight
 * @param query The search query to highlight (case-insensitive)
 * @param highlightColor The color to use for highlighting
 * @param isActiveResult Whether this message is the currently active search result
 * @param activeHighlightColor The color to use for the active result (only used if isActiveResult is true)
 * @return AnnotatedString with highlighted matches
 */
fun highlightSearchText(
    text: String,
    query: String,
    highlightColor: Color,
    isActiveResult: Boolean = false,
    activeHighlightColor: Color = highlightColor,
): AnnotatedString {
    if (query.isBlank()) {
        return AnnotatedString(text)
    }

    return buildAnnotatedString {
        var currentIndex = 0
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()

        // Use active color if this is the active result, otherwise use normal color
        val bgColor = if (isActiveResult) activeHighlightColor else highlightColor

        while (currentIndex < text.length) {
            val matchIndex = lowerText.indexOf(lowerQuery, currentIndex)

            if (matchIndex == -1) {
                // No more matches, append the rest of the text
                append(text.substring(currentIndex))
                break
            }

            // Append text before the match
            if (matchIndex > currentIndex) {
                append(text.substring(currentIndex, matchIndex))
            }

            // Append the highlighted match
            withStyle(
                style = SpanStyle(
                    background = bgColor,
                    color = Color.Black,
                ),
            ) {
                append(text.substring(matchIndex, matchIndex + query.length))
            }

            currentIndex = matchIndex + query.length
        }
    }
}

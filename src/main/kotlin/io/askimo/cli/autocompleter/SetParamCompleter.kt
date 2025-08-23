/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.autocompleter

import io.askimo.core.session.ParamKey
import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine

class SetParamCompleter : Completer {
    override fun complete(
        reader: LineReader,
        line: ParsedLine,
        candidates: MutableList<Candidate>,
    ) {
        val words = line.words()
        if (words.isEmpty()) return

        if (words[0] != ":setparam") return

        when (words.size) {
            2 -> {
                // User typed ":setparam <partial>"
                ParamKey.all().forEach { param ->
                    candidates.add(
                        Candidate(
                            param.key,
                            param.key,
                            null,
                            "${param.type} – ${param.description}",
                            null,
                            null,
                            true,
                        ),
                    )
                }
            }
            3 -> {
                // User typed ":setparam param <value>" — maybe suggest bool values
                val param = ParamKey.fromInput(words[1])
                param?.suggestions?.forEach {
                    candidates.add(Candidate(it))
                }
            }
        }
    }
}

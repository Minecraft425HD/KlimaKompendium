package de.klimakompendium.domain.search

import de.klimakompendium.domain.model.SignalCode

/**
 * Suche in Signal-/Fühlerkürzeln: nach Kürzel ("SIW"), Klemme ("U1", "B3", "NO7") oder Text ("Außenluft", "discharge").
 * Tippfehler- und Buchstabendreher-tolerant für Kürzel ab 3 Zeichen: "SWI" findet SIW.
 */
object SignalSearch {
    private class Entry(val s: SignalCode, val order: Int) {
        val codeC = TextNormalizer.compact(s.code)
        val terminalC = TextNormalizer.compact(s.terminal)
        val haystack = TextNormalizer.normalize("${s.title} ${s.original} ${s.note} ${s.series} ${s.board} ${s.type}")
        val words = haystack.split(' ').filter { it.isNotEmpty() }
    }

    fun search(all: List<SignalCode>, query: String, limit: Int = 300): List<SignalCode> {
        val qc = TextNormalizer.compact(query)
        if (qc.isEmpty()) return all.take(limit)
        val tokens = TextNormalizer.words(query)
        val entries = all.mapIndexed { i, s -> Entry(s, i) }

        fun rank(scored: List<Pair<Entry, Int>>) = scored.sortedWith(compareByDescending<Pair<Entry, Int>> { it.second }.thenBy { it.first.order })

        // 1) Exakt/Präfix auf Kürzel oder Klemme sowie Textsuche
        val strong = entries.mapNotNull { e ->
            val score = when {
                e.codeC == qc -> 1000
                e.terminalC == qc -> 900
                e.codeC.startsWith(qc) -> 800
                tokens.isNotEmpty() && tokens.all { t -> matchesToken(e, t) } -> 500
                else -> 0
            }
            if (score > 0) e to score else null
        }
        if (strong.any { it.second >= 800 }) return rank(strong).take(limit).map { it.first.s }

        // 2) Nur wenn es keinen exakten Treffer gibt: Tippfehler/Buchstabendreher ("SWI" → SIW)
        val fuzzy = if (qc.length >= 3) entries.mapNotNull { e ->
            if (TextNormalizer.distance(qc, e.codeC, 1) <= 1) e to 600 else null
        } else emptyList()
        return rank(strong + fuzzy.filter { f -> strong.none { it.first === f.first } }).take(limit).map { it.first.s }
    }

    private fun matchesToken(e: Entry, t: String): Boolean =
        if (t.length >= 3) e.haystack.contains(t) else e.words.any { it.startsWith(t) } || e.codeC.startsWith(t)
}

package de.klimakompendium.domain.search

import de.klimakompendium.domain.model.FaultCode

/**
 * Suche in Störungscodes: nach Code ("AL33", "33", "pec sw1"), nach Text (DE/EN) oder Hersteller/Serie.
 * Eine reine Zahl ("33") trifft die Codes mit dieser Nummer (AL33).
 */
object FaultCodeSearch {
    private class Entry(val code: FaultCode, val order: Int) {
        val codeC = TextNormalizer.compact(code.code)
        val number: String? = Regex("\\d+").find(code.code)?.value?.trimStart('0')?.ifEmpty { "0" }
        val haystack = TextNormalizer.normalize("${code.title} ${code.cause} ${code.series} ${code.manufacturer}")
        val words = haystack.split(' ').filter { it.isNotEmpty() }
    }

    fun search(all: List<FaultCode>, query: String, limit: Int = 300): List<FaultCode> {
        val qc = TextNormalizer.compact(query)
        if (qc.isEmpty()) return all.take(limit)
        val tokens = TextNormalizer.words(query)
        val digitsOnly = qc.all { it.isDigit() }
        val qNumber = if (digitsOnly) qc.trimStart('0').ifEmpty { "0" } else null

        return all.withIndex().mapNotNull { (i, c) ->
            val e = Entry(c, i)
            val score = when {
                e.codeC == qc -> 1000
                qNumber != null && e.number == qNumber && e.codeC.startsWith("al") -> 900
                digitsOnly -> 0
                e.codeC.startsWith(qc) -> 800
                tokens.isNotEmpty() && tokens.all { t -> matchesToken(e, t) } -> 500
                else -> 0
            }
            if (score > 0) e to score else null
        }.sortedWith(compareByDescending<Pair<Entry, Int>> { it.second }.thenBy { it.first.order })
            .take(limit).map { it.first.code }
    }

    private fun matchesToken(e: Entry, t: String): Boolean =
        if (t.length >= 3) e.haystack.contains(t) else e.words.any { it.startsWith(t) } || e.codeC.startsWith(t)
}

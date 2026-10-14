package de.klimakompendium.domain.search

import java.text.Normalizer

/** Vereinheitlicht Text für die tippfehlertolerante Suche (Umlaute, Akzente, Groß/Klein, Sonderzeichen). */
object TextNormalizer {
    private val diacritics = Regex("\\p{Mn}+")

    /** Kleinbuchstaben, ohne Akzente; ä→a, ö→o, ü→u, ß→ss; Δ→d; µ→u. Wortgrenzen bleiben als Leerzeichen. */
    fun normalize(input: String): String {
        val replaced = input.lowercase()
            .replace("ß", "ss").replace("δ", "d").replace("µ", "u").replace("μ", "u").replace("φ", "f")
        val decomposed = Normalizer.normalize(replaced, Normalizer.Form.NFD).replace(diacritics, "")
        val sb = StringBuilder(decomposed.length)
        for (ch in decomposed) sb.append(if (ch.isLetterOrDigit()) ch else ' ')
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }

    /** Wie [normalize], aber ohne Leerzeichen: "R-410 A" → "r410a". */
    fun compact(input: String): String = normalize(input).replace(" ", "")

    fun words(input: String): List<String> = normalize(input).split(' ').filter { it.isNotEmpty() }

    /** Damerau-Levenshtein (optimal string alignment) mit frühem Abbruch bei Überschreiten von [max]. */
    fun distance(a: String, b: String, max: Int = Int.MAX_VALUE): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            var rowMin = Int.MAX_VALUE
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, d[i - 2][j - 2] + 1)
                d[i][j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
        }
        return d[a.length][b.length]
    }
}

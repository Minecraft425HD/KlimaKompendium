package de.klimakompendium.domain.search

import de.klimakompendium.domain.model.Term

data class TermHit(val term: Term, val score: Int)

/**
 * Sofortsuche über Abkürzung, vollständigen Begriff, DE/EN/IT-Bezeichnungen und Bedeutung.
 * Der Index liegt komplett im Speicher (einige hundert Einträge) → Antwortzeit im Millisekundenbereich.
 *
 * Bewertung (höher = besser):
 *  1000 Abkürzung exakt · 800 Abkürzung beginnt mit Eingabe · 700 Bezeichnung exakt ·
 *  600 Wortanfang · 400 enthält · 300–200 Tippfehler (Damerau-Levenshtein) · 150 Treffer in Bedeutung/Kontext.
 */
class TermIndex(terms: List<Term>) {
    internal class Entry(val term: Term) {
        val abbr = TextNormalizer.compact(term.abbreviation)
        val names: List<String> = listOf(term.fullTerm, term.termDe, term.termEn, term.termIt)
            .filter { it.isNotBlank() }.map { TextNormalizer.compact(it) }.distinct()
        val words: List<String> = listOf(term.abbreviation, term.fullTerm, term.termDe, term.termEn, term.termIt)
            .flatMap { TextNormalizer.words(it) }.distinct()
        val body: String = TextNormalizer.normalize(term.meaning + " " + term.context + " " + term.category.label)
    }

    internal val entries: List<Entry> = terms.map { Entry(it) }

    fun search(query: String, limit: Int = 100): List<TermHit> {
        val q = TextNormalizer.compact(query)
        if (q.isEmpty()) return emptyList()
        val tokens = TextNormalizer.words(query)
        return entries.asSequence()
            .mapNotNull { e -> score(e, q, tokens).takeIf { it > 0 }?.let { TermHit(e.term, it + if (e.term.isFavorite) 20 else 0) } }
            .sortedWith(compareByDescending<TermHit> { it.score }.thenBy { it.term.abbreviation.lowercase() })
            .take(limit).toList()
    }

    private fun score(e: Entry, q: String, tokens: List<String>): Int {
        var best = 0
        if (e.abbr == q) best = 1000
        else if (e.abbr.startsWith(q)) best = 800 - minOf(100, e.abbr.length - q.length)
        if (best >= 800) return best

        if (e.names.any { it == q }) best = maxOf(best, 700)

        if (tokens.size > 1) {
            // Mehrwort-Suche: jedes Wort muss irgendwo (Wortanfang oder knapper Tippfehler) vorkommen.
            val ok = tokens.all { t -> e.words.any { w -> w.startsWith(t) } || e.body.contains(t) }
            if (ok) best = maxOf(best, 550)
            else if (tokens.all { t -> e.words.any { w -> w.startsWith(t) || fuzzyWord(t, w) } }) best = maxOf(best, 280)
            return best
        }

        if (e.words.any { it.startsWith(q) }) best = maxOf(best, 600)
        else if (q.length >= 3 && e.names.any { it.contains(q) }) best = maxOf(best, 400)

        if (best >= 400) return best

        if (q.length >= 3) {
            val abbrDist = TextNormalizer.distance(q, e.abbr, 2)
            if (abbrDist <= allowedErrors(q.length)) best = maxOf(best, 300 - 40 * abbrDist)
            if (e.words.any { fuzzyWord(q, it) }) best = maxOf(best, 250)
            if (best == 0 && e.body.contains(q)) best = 150
        }
        return best
    }

    private fun allowedErrors(len: Int) = when {
        len <= 3 -> 1
        len <= 6 -> 1
        else -> 2
    }

    /** Tippfehler gegenüber ganzem Wort oder gegenüber dem gleich langen Wortanfang ("verdampfng" ~ "verdampfung"). */
    private fun fuzzyWord(q: String, w: String): Boolean {
        if (q.length < 4 || w.length < 3) return false
        val allowed = allowedErrors(q.length)
        if (TextNormalizer.distance(q, w, allowed) <= allowed) return true
        if (w.length > q.length && TextNormalizer.distance(q, w.substring(0, q.length), allowed) <= allowed) return true
        return false
    }
}

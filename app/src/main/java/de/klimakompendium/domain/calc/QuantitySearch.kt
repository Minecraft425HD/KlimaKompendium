package de.klimakompendium.domain.calc

import de.klimakompendium.domain.search.TextNormalizer

/** Eine Größe samt der Messwerte, die zur Berechnung noch fehlen (leer = sofort berechenbar). */
data class Availability(val quantity: Quantity, val missing: List<Measure>) {
    val ready get() = missing.isEmpty()
}

object QuantitySearch {
    /** Findet Größen über Name, Stichwörter und Kategorie ("Heißgas", "SH", "Unterkuehlung"). */
    fun search(query: String, all: List<Quantity> = Quantities.all, limit: Int = 6): List<Quantity> {
        val tokens = TextNormalizer.words(query)
        if (tokens.isEmpty()) return emptyList()
        return all.mapNotNull { q ->
            val words = TextNormalizer.words(q.name + " " + q.keywords.joinToString(" ") + " " + q.category.label)
            score(tokens, words)?.let { q to it }
        }.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    /** Summe der Wort-Treffer; null, sobald ein Suchwort nirgends passt (alle Wörter müssen passen). */
    private fun score(tokens: List<String>, words: List<String>): Int? {
        var total = 0
        for (t in tokens) {
            total += when {
                words.any { it == t } -> 10
                t.length >= 3 && words.any { it.startsWith(t) } -> 6
                t.length >= 4 && words.any { it.contains(t) } -> 3
                else -> return null
            }
        }
        return total
    }

    /**
     * Teilt alle Größen anhand der vorhandenen Messwerte ein.
     * Ergebnis: zuerst sofort berechenbare, dann solche, bei denen höchstens [maxMissing] Pflichtwerte fehlen.
     */
    fun availability(selected: Set<Measure>, all: List<Quantity> = Quantities.all, maxMissing: Int = 1): List<Availability> =
        all.map { q -> Availability(q, q.needs.filter { it !in selected }) }
            .filter { it.missing.size <= maxMissing && (it.ready || it.quantity.needs.any { n -> n in selected }) }
            .sortedWith(compareBy({ it.missing.size }, { it.quantity.category.ordinal }))
}

package de.klimakompendium.domain.search

import de.klimakompendium.domain.model.Plant

data class PlantFilter(
    val query: String = "",
    val refrigerant: String? = null,
    val manufacturer: String? = null,
    val customer: String? = null,
) {
    val isActive get() = query.isNotBlank() || refrigerant != null || manufacturer != null || customer != null
}

/** Suche und Filter für Anlagen (Name, Kunde, Standort, Controller, Kältemittel, Hersteller, Seriennummer). */
object PlantSearch {
    fun filter(plants: List<Plant>, f: PlantFilter): List<Plant> {
        // Roh-Tokens (an Leerzeichen getrennt) → "192.168.1.2" bleibt ein Suchbegriff statt vier Ziffern.
        val tokens = f.query.trim().split(Regex("\\s+")).map { TextNormalizer.compact(it) }.filter { it.isNotEmpty() }
        return plants.filter { p ->
            (f.refrigerant == null || p.refrigerant.equals(f.refrigerant, true)) &&
                (f.manufacturer == null || p.manufacturer.equals(f.manufacturer, true)) &&
                (f.customer == null || p.customer.equals(f.customer, true)) &&
                matches(p, tokens)
        }
    }

    /** @param tokens bereits mit [TextNormalizer.compact] normalisierte Suchbegriffe */
    fun matches(p: Plant, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return true
        val haystack = listOf(
            p.name, p.customer, p.location, p.refrigerant, p.manufacturer, p.serialNumber, p.controllerType, p.notes,
        ) + p.controllers.flatMap { listOf(it.label, it.model, it.address) }
        val norm = haystack.map { TextNormalizer.normalize(it) }
        val compactAll = haystack.map { TextNormalizer.compact(it) }
        val words = norm.flatMap { it.split(' ') }.filter { it.isNotEmpty() }
        return tokens.all { t ->
            compactAll.any { it.contains(t) } ||
                (t.length >= 4 && words.any { TextNormalizer.distance(t, it, 1) <= 1 })
        }
    }
}

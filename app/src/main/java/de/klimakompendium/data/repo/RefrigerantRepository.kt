package de.klimakompendium.data.repo

import de.klimakompendium.domain.model.Refrigerant

/** Kältemittel-Stammdaten und p-T-Tabellen (aus assets/refrigerants.json, im Speicher gehalten). */
class RefrigerantRepository(val all: List<Refrigerant>) {
    fun byId(id: String): Refrigerant? = all.firstOrNull { it.id.equals(id.trim(), true) }

    /** Findet "R-410A", "r410a", "R 410 A" etc. */
    fun findLoose(name: String): Refrigerant? {
        val key = name.filter { it.isLetterOrDigit() }
        return all.firstOrNull { it.id.filter { c -> c.isLetterOrDigit() }.equals(key, true) }
    }

    val withPtData: List<Refrigerant> get() = all.filter { it.hasPtData }
}

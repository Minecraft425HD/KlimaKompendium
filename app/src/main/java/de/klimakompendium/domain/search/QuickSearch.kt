package de.klimakompendium.domain.search

import de.klimakompendium.domain.calc.Quantity
import de.klimakompendium.domain.calc.QuantitySearch
import de.klimakompendium.domain.convert.Measurement
import de.klimakompendium.domain.convert.MeasurementParser
import de.klimakompendium.domain.model.FaultCode
import de.klimakompendium.domain.model.Plant
import de.klimakompendium.domain.model.Refrigerant
import de.klimakompendium.domain.model.SignalCode

data class QuickResult(
    val measurement: Measurement?,
    val terms: List<TermHit>,
    val refrigerants: List<Refrigerant>,
    val plants: List<Plant>,
    val faults: List<FaultCode> = emptyList(),
    val calcs: List<Quantity> = emptyList(),
    val signals: List<SignalCode> = emptyList(),
) {
    val isEmpty get() = measurement == null && terms.isEmpty() && refrigerants.isEmpty() && plants.isEmpty() && faults.isEmpty() && calcs.isEmpty() && signals.isEmpty()
}

/** Zentrale Schnellsuche der Startseite: Begriffe, Kältemittel, Anlagen und Messwert-Umrechnung in einem Zug. */
object QuickSearch {
    fun run(
        query: String,
        index: TermIndex,
        plants: List<Plant>,
        refrigerants: List<Refrigerant>,
        faultCodes: List<FaultCode> = emptyList(),
        signalCodes: List<SignalCode> = emptyList(),
        termLimit: Int = 8,
    ): QuickResult {
        val q = TextNormalizer.compact(query)
        if (q.isEmpty()) return QuickResult(null, emptyList(), emptyList(), emptyList())
        val measurement = MeasurementParser.parse(query)
        val terms = if (measurement != null) emptyList() else index.search(query, termLimit)
        val refs = if (measurement != null) emptyList() else refrigerants.filter {
            val id = TextNormalizer.compact(it.id)
            id.startsWith(q) || (q.length >= 3 && id.contains(q)) || TextNormalizer.compact(it.type) == q
        }
        val matchedPlants = if (measurement != null) emptyList() else PlantSearch.filter(plants, PlantFilter(query = query))
        val faults = if (measurement != null) emptyList() else FaultCodeSearch.search(faultCodes, query, limit = 5)
        val calcs = if (measurement != null) emptyList() else QuantitySearch.search(query, limit = 3)
        val signals = if (measurement != null) emptyList() else SignalSearch.search(signalCodes, query, limit = 4)
        return QuickResult(measurement, terms, refs, matchedPlants, faults, calcs, signals)
    }
}

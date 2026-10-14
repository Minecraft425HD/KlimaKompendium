package de.klimakompendium.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.klimakompendium.AppContainer
import de.klimakompendium.domain.convert.Measurement
import de.klimakompendium.domain.convert.UnitConverter
import de.klimakompendium.domain.hvac.Saturation
import de.klimakompendium.domain.model.Plant
import de.klimakompendium.domain.model.SaturationPhase
import de.klimakompendium.domain.model.Term
import de.klimakompendium.domain.search.QuickResult
import de.klimakompendium.domain.search.QuickSearch
import de.klimakompendium.domain.search.TermIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val query: String = "",
    val result: QuickResult? = null,
    /** Fertig formatierte Umrechnungszeilen zur Messwert-Eingabe. */
    val measurementLines: List<String> = emptyList(),
    val recentPlants: List<Plant> = emptyList(),
    val favorites: List<Term> = emptyList(),
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<HomeState> = combine(
        query, c.terms.terms, c.plants.plants, c.preferences.settings,
    ) { q, terms, plants, settings ->
        val result = QuickSearch.run(q, TermIndex(terms), plants, c.refrigerants.all)
        HomeState(
            query = q,
            result = result.takeIf { q.isNotBlank() },
            measurementLines = result.measurement?.let { describe(it, settings.lastRefrigerant, settings.pressureReference) } ?: emptyList(),
            recentPlants = plants.filter { it.lastOpenedAt != null }.sortedByDescending { it.lastOpenedAt }.take(5),
            favorites = terms.filter { it.isFavorite },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun onQuery(q: String) { query.value = q }

    fun toggleFavorite(t: Term) { viewModelScope.launch { c.terms.setFavorite(t.id, !t.isFavorite) } }

    private fun describe(
        m: Measurement, refrigerantId: String, ref: de.klimakompendium.domain.convert.PressureReference,
    ): List<String> = when (m) {
        is Measurement.Pressure -> {
            val lines = UnitConverter.allPressures(m.value, m.unit).filterKeys { it != m.unit }
                .map { (u, v) -> "${UnitConverter.format(v, 3)} ${u.symbol}" }.toMutableList()
            // Sättigungstemperatur für das zuletzt genutzte Kältemittel (Bezug laut Einstellung)
            val r = c.refrigerants.byId(refrigerantId)
            if (r != null && r.hasPtData) {
                val abs = UnitConverter.toAbsoluteBar(m.value, m.unit, ref)
                val dew = Saturation.temperatureC(r, abs, SaturationPhase.DEW)
                val bub = Saturation.temperatureC(r, abs, SaturationPhase.BUBBLE)
                val refLabel = if (ref == de.klimakompendium.domain.convert.PressureReference.GAUGE) "Überdruck" else "absolut"
                if (dew != null) {
                    lines += "${r.name} bei ${UnitConverter.format(m.value, 2)} ${m.unit.symbol} ($refLabel): " +
                        if (bub != null && kotlin.math.abs(bub - dew) >= 0.5)
                            "Tau ${UnitConverter.format(dew, 1)} °C · Siede ${UnitConverter.format(bub, 1)} °C"
                        else "${UnitConverter.format(dew, 1)} °C Sättigung"
                }
            }
            lines
        }
        is Measurement.Temperature -> UnitConverter.allTemperatures(m.value, m.unit).filterKeys { it != m.unit }
            .map { (u, v) -> "${UnitConverter.format(v, 2)} ${u.symbol}" }
    }

}

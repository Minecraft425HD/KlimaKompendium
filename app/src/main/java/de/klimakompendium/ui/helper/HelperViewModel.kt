package de.klimakompendium.ui.helper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.klimakompendium.AppContainer
import de.klimakompendium.data.prefs.Settings
import de.klimakompendium.domain.convert.PressureReference
import de.klimakompendium.domain.model.MeasurementKind
import de.klimakompendium.domain.model.Refrigerant
import de.klimakompendium.domain.model.Term
import de.klimakompendium.domain.search.TermIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExplainState(val query: String = "", val terms: List<Term> = emptyList())

class HelperViewModel(private val c: AppContainer) : ViewModel() {
    val refrigerants: List<Refrigerant> = c.refrigerants.all
    val settings: StateFlow<Settings> = c.preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val query = MutableStateFlow("")

    /** Nur Begriffe mit Druck-/Temperaturbezug; ohne Eingabe alle, sortiert nach Messgröße. */
    val explain: StateFlow<ExplainState> = combine(query, c.terms.terms) { q, all ->
        val measurable = all.filter { it.kind != MeasurementKind.NONE }
        val list = if (q.isBlank()) measurable.sortedWith(compareBy({ it.kind }, { it.abbreviation.lowercase() }))
        else TermIndex(measurable).search(q, 60).map { it.term }
        ExplainState(q, list)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExplainState())

    fun setQuery(q: String) { query.value = q }
    fun setRefrigerant(id: String) { viewModelScope.launch { c.preferences.setLastRefrigerant(id) } }
    fun setReference(ref: PressureReference) { viewModelScope.launch { c.preferences.setPressureReference(ref) } }
}

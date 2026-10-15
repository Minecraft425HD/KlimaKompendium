package de.klimakompendium.ui.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.klimakompendium.AppContainer
import de.klimakompendium.domain.model.FaultCode
import de.klimakompendium.domain.model.Guide
import de.klimakompendium.domain.model.Manufacturer
import de.klimakompendium.domain.model.SignalCode
import de.klimakompendium.domain.search.SignalSearch
import de.klimakompendium.domain.search.FaultCodeSearch
import de.klimakompendium.domain.search.TextNormalizer
import de.klimakompendium.ui.util.SearchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FaultsState(
    val query: String = "",
    val series: String? = null,
    val all: Int = 0,
    val codes: List<FaultCode> = emptyList(),
    /** Anzeigename "Hersteller · Serie" aller vorhandenen Gruppen für den Filter. */
    val groups: List<String> = emptyList(),
)

data class SignalsState(
    val query: String = "",
    val series: String? = null,
    val type: String? = null,
    val all: Int = 0,
    val signals: List<SignalCode> = emptyList(),
    val seriesOptions: List<String> = emptyList(),
    val typeOptions: List<String> = emptyList(),
)

class ServiceViewModel(private val c: AppContainer, initialQuery: String) : ViewModel() {
    val manufacturers: List<Manufacturer> = c.service.data.manufacturers
    val guideCategories: List<String> = c.service.guideCategories

    /** Suchtexte synchron im Snapshot-Zustand (siehe [SearchState]). */
    val faultSearch = SearchState(initialQuery)
    val guideSearch = SearchState(initialQuery)
    val signalSearch = SearchState(initialQuery)
    private val signalSeries = MutableStateFlow<String?>(null)
    private val signalType = MutableStateFlow<String?>(null)
    private val faultGroup = MutableStateFlow<String?>(null)
    private val guideCategory = MutableStateFlow<String?>(null)

    val faults: StateFlow<FaultsState> = combine(faultSearch.flow, faultGroup, c.faultCodes.codes) { q, group, all ->
        val groups = all.map { "${it.manufacturer} · ${it.series}" }.distinct()
        val scoped = if (group == null) all else all.filter { "${it.manufacturer} · ${it.series}" == group }
        FaultsState(q, group, all.size, FaultCodeSearch.search(scoped, q), groups)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FaultsState(query = initialQuery))

    val signals: StateFlow<SignalsState> = combine(signalSearch.flow, signalSeries, signalType) { q, series, type ->
        val all = c.signals
        val scoped = all.filter { (series == null || "${it.manufacturer} ${it.series}" == series) && (type == null || it.type == type) }
        SignalsState(
            q, series, type, all.size, SignalSearch.search(scoped, q),
            all.map { "${it.manufacturer} ${it.series}" }.distinct(), all.map { it.type }.distinct(),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SignalsState(query = initialQuery))

    val guides: StateFlow<List<Guide>> = combine(guideSearch.flow, guideCategory) { q, cat ->
        val tokens = TextNormalizer.words(q)
        c.service.data.guides.filter { g ->
            (cat == null || g.category == cat) &&
                (tokens.isEmpty() || TextNormalizer.normalize("${g.title} ${g.appliesTo} ${g.category} ${g.steps.joinToString(" ")} ${g.notes}")
                    .let { hay -> tokens.all { hay.contains(it) } })
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), c.service.data.guides)

    val guideState: StateFlow<Pair<String, String?>> = combine(guideSearch.flow, guideCategory) { q, cat -> q to cat }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialQuery to null)

    fun setFaultQuery(q: String) { faultSearch.text = q }
    fun setFaultGroup(g: String?) { faultGroup.value = g }
    fun setSignalQuery(q: String) { signalSearch.text = q }
    fun setSignalSeries(v: String?) { signalSeries.value = v }
    fun setSignalType(v: String?) { signalType.value = v }
    fun setGuideQuery(q: String) { guideSearch.text = q }
    fun setGuideCategory(cat: String?) { guideCategory.value = cat }

    fun deleteFault(id: Long) { viewModelScope.launch { c.faultCodes.deleteCustom(id) } }
    fun hasFaultCodes(manufacturer: String) = faults.value.groups.any { it.startsWith(manufacturer, ignoreCase = true) }
}

class FaultEditViewModel(private val c: AppContainer, private val id: Long) : ViewModel() {
    val existing: StateFlow<FaultCode?> = if (id == 0L) MutableStateFlow(null)
    else c.faultCodes.observe(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val knownManufacturers: List<String> = (c.service.data.manufacturers.map { it.name }).distinct()

    fun save(code: FaultCode, onSaved: () -> Unit) { viewModelScope.launch { c.faultCodes.saveCustom(code); onSaved() } }
}

package de.klimakompendium.ui.terms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.klimakompendium.AppContainer
import de.klimakompendium.domain.model.MeasurementKind
import de.klimakompendium.domain.model.Refrigerant
import de.klimakompendium.domain.model.Term
import de.klimakompendium.domain.model.TermCategory
import de.klimakompendium.domain.model.TermLanguage
import de.klimakompendium.domain.search.TermIndex
import de.klimakompendium.ui.util.SearchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TermsState(
    val query: String = "",
    val language: TermLanguage? = null,
    val category: TermCategory? = null,
    val favoritesOnly: Boolean = false,
    val terms: List<Term> = emptyList(),
    val total: Int = 0,
)

class TermsViewModel(private val c: AppContainer) : ViewModel() {
    private data class Filter(val lang: TermLanguage?, val cat: TermCategory?, val fav: Boolean)

    /** Suchtext synchron im Snapshot-Zustand (siehe [SearchState]). */
    val search = SearchState()
    private val filter = MutableStateFlow(Filter(null, null, false))

    val state: StateFlow<TermsState> = combine(search.flow, filter, c.terms.terms) { q, f, all ->
        var list: List<Term> = if (q.isBlank()) all else TermIndex(all).search(q, 200).map { it.term }
        if (f.lang != null) list = list.filter { it.language == f.lang }
        if (f.cat != null) list = list.filter { it.category == f.cat }
        if (f.fav) list = list.filter { it.isFavorite }
        TermsState(q, f.lang, f.cat, f.fav, list, all.size)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TermsState())

    fun setQuery(q: String) { search.text = q }
    fun setLanguage(l: TermLanguage?) = filter.update { it.copy(lang = l) }
    fun setCategory(cat: TermCategory?) = filter.update { it.copy(cat = cat) }
    fun toggleFavoritesOnly() = filter.update { it.copy(fav = !it.fav) }
    fun toggleFavorite(t: Term) { viewModelScope.launch { c.terms.setFavorite(t.id, !t.isFavorite) } }

    private inline fun MutableStateFlow<Filter>.update(f: (Filter) -> Filter) { value = f(value) }
}

class TermDetailViewModel(private val c: AppContainer, private val id: Long) : ViewModel() {
    val term: StateFlow<Term?> = c.terms.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Zum Begriff passendes Kältemittel (z. B. Begriff "R32" → Kältemittel R32) für den Link zur p-T-Tabelle. */
    val refrigerant: StateFlow<Refrigerant?> = c.terms.observe(id)
        .map { t -> t?.takeIf { it.category == TermCategory.REFRIGERANT }?.let { c.refrigerants.findLoose(it.abbreviation) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleFavorite() { viewModelScope.launch { term.value?.let { c.terms.setFavorite(it.id, !it.isFavorite) } } }
    fun delete(onDone: () -> Unit) { viewModelScope.launch { c.terms.deleteCustom(id); onDone() } }
}

class TermEditViewModel(private val c: AppContainer, private val id: Long) : ViewModel() {
    val existing: StateFlow<Term?> = if (id == 0L) MutableStateFlow(null)
    else c.terms.observe(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun save(term: Term, onSaved: (Long) -> Unit) {
        viewModelScope.launch { onSaved(c.terms.saveCustom(term)) }
    }

    companion object {
        val kinds = MeasurementKind.entries
    }
}

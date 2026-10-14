package de.klimakompendium.ui.plants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.klimakompendium.AppContainer
import de.klimakompendium.data.security.SecretCipherException
import de.klimakompendium.domain.model.Controller
import de.klimakompendium.domain.model.PasswordChange
import de.klimakompendium.domain.model.Plant
import de.klimakompendium.domain.model.Refrigerant
import de.klimakompendium.domain.model.Term
import de.klimakompendium.domain.model.TermCategory
import de.klimakompendium.domain.search.PlantFilter
import de.klimakompendium.domain.search.PlantSearch
import de.klimakompendium.domain.search.TermIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlantsState(
    val filter: PlantFilter = PlantFilter(),
    val plants: List<Plant> = emptyList(),
    val total: Int = 0,
    val refrigerants: List<String> = emptyList(),
    val manufacturers: List<String> = emptyList(),
    val customers: List<String> = emptyList(),
)

class PlantsViewModel(c: AppContainer) : ViewModel() {
    private val filter = MutableStateFlow(PlantFilter())

    val state: StateFlow<PlantsState> = combine(filter, c.plants.plants) { f, all ->
        fun distinct(sel: (Plant) -> String) = all.map(sel).filter { it.isNotBlank() }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
        PlantsState(
            filter = f, plants = PlantSearch.filter(all, f), total = all.size,
            refrigerants = distinct { it.refrigerant }, manufacturers = distinct { it.manufacturer }, customers = distinct { it.customer },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlantsState())

    fun setQuery(q: String) { filter.value = filter.value.copy(query = q) }
    fun setRefrigerant(v: String?) { filter.value = filter.value.copy(refrigerant = v) }
    fun setManufacturer(v: String?) { filter.value = filter.value.copy(manufacturer = v) }
    fun setCustomer(v: String?) { filter.value = filter.value.copy(customer = v) }
    fun clearFilters() { filter.value = PlantFilter() }
}

/** Vorschläge, die beim Öffnen einer Anlage automatisch eingeblendet werden. */
data class PlantSuggestions(val refrigerant: Refrigerant?, val refrigerantTerm: Term?)

sealed interface PlantEvent {
    data class Message(val text: String) : PlantEvent
    data class CopyPassword(val password: String) : PlantEvent
    data object Deleted : PlantEvent
}

class PlantDetailViewModel(private val c: AppContainer, private val plantId: Long) : ViewModel() {
    val plant: StateFlow<Plant?> = c.plants.observe(plantId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val suggestions: StateFlow<PlantSuggestions> = combine(c.plants.observe(plantId), c.terms.terms) { p, terms ->
        val ref = p?.refrigerant?.takeIf { it.isNotBlank() }?.let { c.refrigerants.findLoose(it) }
        val term = ref?.let { r -> TermIndex(terms.filter { it.category == TermCategory.REFRIGERANT }).search(r.name, 1).firstOrNull()?.term }
        PlantSuggestions(ref, term)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlantSuggestions(null, null))

    /** Angezeigte (entschlüsselte) Passwörter je Controller-ID. Verschwinden automatisch wieder. */
    private val _revealed = MutableStateFlow<Map<Long, String>>(emptyMap())
    val revealed: StateFlow<Map<Long, String>> = _revealed

    private val _events = MutableSharedFlow<PlantEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlantEvent> = _events

    private val hideJobs = mutableMapOf<Long, Job>()

    init { viewModelScope.launch { c.plants.markOpened(plantId) } }

    fun reveal(controllerId: Long) {
        viewModelScope.launch {
            val pw = decrypt(controllerId) ?: return@launch
            _revealed.value = _revealed.value + (controllerId to pw)
            val seconds = c.preferences.settings.first().passwordVisibleSeconds
            hideJobs[controllerId]?.cancel()
            hideJobs[controllerId] = launch { delay(seconds * 1_000L); hide(controllerId) }
        }
    }

    fun hide(controllerId: Long) {
        hideJobs.remove(controllerId)?.cancel()
        _revealed.value = _revealed.value - controllerId
    }

    fun copyPassword(controllerId: Long) {
        viewModelScope.launch { decrypt(controllerId)?.let { _events.emit(PlantEvent.CopyPassword(it)) } }
    }

    private suspend fun decrypt(controllerId: Long): String? = try {
        c.plants.revealPassword(controllerId)
    } catch (e: SecretCipherException) {
        // Meldung ohne Details – niemals Klartext oder Chiffrat loggen
        _events.emit(PlantEvent.Message("Passwort kann nicht entschlüsselt werden (Gerätschlüssel nicht mehr vorhanden). Bitte neu eintragen."))
        null
    }

    fun deleteController(id: Long) { viewModelScope.launch { hide(id); c.plants.deleteController(id) } }

    fun deletePlant() { viewModelScope.launch { c.plants.deletePlant(plantId); _events.emit(PlantEvent.Deleted) } }

    fun addPhotos(uris: List<String>) {
        viewModelScope.launch {
            val ok = uris.count { c.plants.addPhoto(plantId, it) }
            if (ok < uris.size) _events.emit(PlantEvent.Message("${uris.size - ok} Foto(s) konnten nicht übernommen werden."))
        }
    }

    fun removePhoto(id: Long) { viewModelScope.launch { c.plants.removePhoto(id) } }

    override fun onCleared() { _revealed.value = emptyMap() }
}

class PlantEditViewModel(private val c: AppContainer, private val id: Long) : ViewModel() {
    val existing: StateFlow<Plant?> = if (id == 0L) MutableStateFlow(null) else c.plants.observe(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val refrigerantNames: List<String> = c.refrigerants.all.map { it.name }
    val plants: StateFlow<List<Plant>> = c.plants.plants.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun save(plant: Plant, onSaved: (Long) -> Unit) { viewModelScope.launch { onSaved(c.plants.savePlant(plant)) } }
}

class ControllerEditViewModel(private val c: AppContainer, private val plantId: Long, private val controllerId: Long) : ViewModel() {
    val existing: StateFlow<Controller?> = c.plants.observe(plantId)
        .map { p -> p?.controllers?.firstOrNull { it.id == controllerId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun save(controller: Controller, password: PasswordChange, onSaved: () -> Unit) {
        viewModelScope.launch { c.plants.saveController(controller, password); onSaved() }
    }
}

package de.klimakompendium.ui.calc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.klimakompendium.AppContainer
import de.klimakompendium.data.prefs.Settings
import de.klimakompendium.domain.convert.PressureReference
import de.klimakompendium.domain.model.Refrigerant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CalcViewModel(private val c: AppContainer) : ViewModel() {
    val refrigerants: List<Refrigerant> = c.refrigerants.withPtData
    val settings: StateFlow<Settings> = c.preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    fun setRefrigerant(id: String) { viewModelScope.launch { c.preferences.setLastRefrigerant(id) } }
    fun setReference(ref: PressureReference) { viewModelScope.launch { c.preferences.setPressureReference(ref) } }
}

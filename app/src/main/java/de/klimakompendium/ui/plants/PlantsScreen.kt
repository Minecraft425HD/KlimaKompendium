package de.klimakompendium.ui.plants

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.klimakompendium.ui.components.CenteredContent
import de.klimakompendium.ui.components.EmptyState
import de.klimakompendium.ui.components.FilterDropdown
import de.klimakompendium.ui.components.SearchField
import de.klimakompendium.ui.home.PlantCard
import de.klimakompendium.ui.util.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantsScreen(onOpenPlant: (Long) -> Unit, onNewPlant: () -> Unit) {
    val vm = appViewModel { PlantsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Anlagen & Controller") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewPlant, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Neue Anlage") })
        },
    ) { padding ->
        CenteredContent(Modifier.padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { SearchField(vm.search.text, vm::setQuery, "Anlage, Kunde, Standort, Controller …") }
                item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterDropdown("Kältemittel", s.refrigerants, s.filter.refrigerant, { it }, vm::setRefrigerant)
                        FilterDropdown("Hersteller", s.manufacturers, s.filter.manufacturer, { it }, vm::setManufacturer)
                        FilterDropdown("Kunde", s.customers, s.filter.customer, { it }, vm::setCustomer)
                        FilterDropdown("Controller", s.controllers, s.filter.controller, { it }, vm::setController)
                        if (s.filter.isActive) TextButton(onClick = vm::clearFilters, Modifier.padding(start = 4.dp)) { Text("Zurücksetzen") }
                    }
                }
                item {
                    Text("${s.plants.size} von ${s.total} Anlagen", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (s.total == 0) item { EmptyState("Noch keine Anlagen. Lege mit „Neue Anlage“ deine erste an.") }
                else if (s.plants.isEmpty()) item { EmptyState("Keine Anlage passt zu Suche und Filtern.") }
                items(s.plants, key = { it.id }) { p -> PlantCard(p) { onOpenPlant(p.id) } }
            }
        }
    }
}

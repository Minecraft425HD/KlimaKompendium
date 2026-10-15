package de.klimakompendium.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.klimakompendium.domain.convert.Measurement
import de.klimakompendium.domain.model.Plant
import de.klimakompendium.domain.model.Refrigerant
import de.klimakompendium.ui.components.CenteredContent
import de.klimakompendium.ui.components.EmptyState
import de.klimakompendium.ui.components.KCard
import de.klimakompendium.ui.components.SearchField
import de.klimakompendium.ui.components.SectionHeader
import de.klimakompendium.ui.components.SmallChip
import de.klimakompendium.ui.components.TermRow
import de.klimakompendium.ui.theme.TouchTarget
import de.klimakompendium.ui.util.appViewModel
import androidx.compose.foundation.layout.heightIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenTerm: (Long) -> Unit,
    onOpenPlant: (Long) -> Unit,
    onOpenRefrigerant: (String) -> Unit,
    onOpenFault: (String) -> Unit,
    onOpenCalc: (String) -> Unit,
    onOpenSignal: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KlimaKompendium") },
                actions = {
                    IconButton(onClick = onOpenSettings, Modifier.heightIn(min = TouchTarget).padding(end = 4.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                },
            )
        },
    ) { padding ->
        CenteredContent(Modifier.padding(padding)) {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    SearchField(
                        value = vm.search.text, onValueChange = vm::onQuery,
                        placeholder = "ND, R32, AL33, Anlage, \"10 bar\" …",
                    )
                }
                if (state.query.isBlank()) idleContent(state, vm::onQuery, onOpenTerm, onOpenPlant)
                else resultContent(state, onOpenTerm, onOpenPlant, onOpenRefrigerant, onOpenFault, onOpenCalc, onOpenSignal, vm::toggleFavorite)
            }
        }
    }
}

private val QUICK = listOf("ND", "HD", "SAP", "SAT", "RAT", "LP", "HP", "EEV", "SH", "SC", "R32", "R410A")

private fun androidx.compose.foundation.lazy.LazyListScope.idleContent(
    state: HomeState, onQuery: (String) -> Unit, onOpenTerm: (Long) -> Unit, onOpenPlant: (Long) -> Unit,
) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader("Schnellzugriff")
            Row(Modifier.horizontalScrollRow(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK.forEach { q -> SmallChip(q) { onQuery(q) } }
            }
        }
    }
    if (state.recentPlants.isNotEmpty()) {
        item { SectionHeader("Zuletzt geöffnete Anlagen") }
        items(state.recentPlants, key = { "recent-${it.id}" }) { PlantCard(it) { onOpenPlant(it.id) } }
    }
    if (state.favorites.isNotEmpty()) {
        item { SectionHeader("Favoriten") }
        items(state.favorites, key = { "fav-${it.id}" }) { t -> TermRow(t, { onOpenTerm(t.id) }, null) }
    }
    if (state.recentPlants.isEmpty() && state.favorites.isEmpty()) {
        item {
            EmptyState("Tippe oben einen Begriff, ein Kältemittel, eine Anlage oder einen Messwert wie „8,5 bar“ ein.")
        }
    }
}

@Composable
private fun Modifier.horizontalScrollRow(): Modifier = this.fillMaxWidth().horizontalScroll(rememberScrollState())

private fun androidx.compose.foundation.lazy.LazyListScope.resultContent(
    state: HomeState,
    onOpenTerm: (Long) -> Unit,
    onOpenPlant: (Long) -> Unit,
    onOpenRefrigerant: (String) -> Unit,
    onOpenFault: (String) -> Unit,
    onOpenCalc: (String) -> Unit,
    onOpenSignal: (String) -> Unit,
    onFavorite: (de.klimakompendium.domain.model.Term) -> Unit,
) {
    val r = state.result ?: return
    if (r.isEmpty && state.measurementLines.isEmpty()) {
        item { EmptyState("Keine Treffer für „${state.query.trim()}“.") }
        return
    }
    if (r.measurement != null) {
        item {
            KCard {
                Text(
                    when (val m = r.measurement) {
                        null -> ""
                        is Measurement.Pressure -> "Druck ${de.klimakompendium.domain.convert.UnitConverter.format(m.value, 3)} ${m.unit.symbol}"
                        is Measurement.Temperature -> "Temperatur ${de.klimakompendium.domain.convert.UnitConverter.format(m.value, 2)} ${m.unit.symbol}"
                    },
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                )
                state.measurementLines.forEach { line ->
                    Text(line, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
    if (r.refrigerants.isNotEmpty()) {
        item { SectionHeader("Kältemittel") }
        items(r.refrigerants, key = { "ref-${it.id}" }) { ref -> RefrigerantCard(ref) { onOpenRefrigerant(ref.id) } }
    }
    if (r.terms.isNotEmpty()) {
        item { SectionHeader("Begriffe & Abkürzungen") }
        items(r.terms, key = { "term-${it.term.id}" }) { hit ->
            TermRow(hit.term, { onOpenTerm(hit.term.id) }, { onFavorite(hit.term) })
        }
    }
    if (r.calcs.isNotEmpty()) {
        item { SectionHeader("Berechnungen") }
        items(r.calcs, key = { "calc-${it.id}" }) { c ->
            KCard(onClick = { onOpenCalc(c.id) }) {
                Text(c.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text("Benötigt: " + c.needs.joinToString(" · ") { it.label }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (r.signals.isNotEmpty()) {
        item { SectionHeader("Signale & Fühlerkürzel") }
        items(r.signals, key = { "sig-${it.series}-${it.board}-${it.terminal}-${it.code}" }) { s ->
            KCard(onClick = { onOpenSignal(s.code) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.code, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text(s.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                }
                Text("${s.terminal} · ${s.board} · ${s.manufacturer} ${s.series}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (r.faults.isNotEmpty()) {
        item { SectionHeader("Störungscodes") }
        items(r.faults, key = { "fault-${it.id}" }) { f ->
            KCard(onClick = { onOpenFault(f.code) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(f.code, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text(f.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                }
                Text("${f.manufacturer} · ${f.series}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (r.plants.isNotEmpty()) {
        item { SectionHeader("Anlagen") }
        items(r.plants, key = { "plant-${it.id}" }) { p -> PlantCard(p) { onOpenPlant(p.id) } }
    }
}

@Composable
private fun RefrigerantCard(r: Refrigerant, onClick: () -> Unit) {
    KCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(r.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text("Klasse ${r.safetyClass} · GWP ${r.gwp}", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            if (r.hasPtData) "p‑T‑Tabelle öffnen" else "Keine p‑T‑Daten hinterlegt",
            Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun PlantCard(p: Plant, onClick: () -> Unit) {
    KCard(onClick = onClick) {
        Text(p.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        val sub = listOf(p.customer, p.location).filter { it.isNotBlank() }.joinToString(" · ")
        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyLarge)
        val tech = listOf(p.refrigerant, p.manufacturer, p.controllerType).filter { it.isNotBlank() }.joinToString(" · ")
        if (tech.isNotBlank()) Text(tech, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (p.controllers.isNotEmpty()) {
            Text(
                "${p.controllers.size} Controller", Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

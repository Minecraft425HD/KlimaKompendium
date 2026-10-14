package de.klimakompendium.ui.service

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.klimakompendium.domain.model.FaultCode
import de.klimakompendium.domain.model.Guide
import de.klimakompendium.domain.model.Manufacturer
import de.klimakompendium.ui.components.BackTopBar
import de.klimakompendium.ui.components.CenteredContent
import de.klimakompendium.ui.components.EmptyState
import de.klimakompendium.ui.components.FilterDropdown
import de.klimakompendium.ui.components.FormColumn
import de.klimakompendium.ui.components.KCard
import de.klimakompendium.ui.components.LabeledField
import de.klimakompendium.ui.components.SearchField
import de.klimakompendium.ui.components.SectionHeader
import de.klimakompendium.ui.theme.MonoStyle
import de.klimakompendium.ui.theme.TouchTarget
import de.klimakompendium.ui.util.appViewModel

private val TABS = listOf("faults" to "Störungen", "guides" to "Leitfäden", "makers" to "Hersteller")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceScreen(initialTab: String, initialQuery: String, onNewFault: () -> Unit, onEditFault: (Long) -> Unit) {
    val vm = appViewModel(key = "service-$initialTab-$initialQuery") { ServiceViewModel(it, initialQuery) }
    var tab by rememberSaveable(initialTab, initialQuery) { mutableIntStateOf(TABS.indexOfFirst { it.first == initialTab }.coerceAtLeast(0)) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Service") }) },
        floatingActionButton = {
            if (TABS[tab].first == "faults") ExtendedFloatingActionButton(
                onClick = onNewFault, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Eigener Code") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                TABS.forEachIndexed { i, (_, title) ->
                    Tab(selected = tab == i, onClick = { tab = i }, modifier = Modifier.heightIn(min = 56.dp), text = { Text(title) })
                }
            }
            CenteredContent {
                when (TABS[tab].first) {
                    "faults" -> FaultsTab(vm, onEditFault)
                    "guides" -> GuidesTab(vm)
                    else -> MakersTab(vm, onShowFaults = { name -> vm.setFaultQuery(name); vm.setFaultGroup(null); tab = 0 },
                        onShowGuides = { vm.setGuideQuery("Carel"); tab = 1 })
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------- Störungscodes

@Composable
private fun FaultsTab(vm: ServiceViewModel, onEdit: (Long) -> Unit) {
    val s by vm.faults.collectAsStateWithLifecycle()
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SearchField(s.query, vm::setFaultQuery, "Code (AL33, 33) oder Text (Hochdruck, Fühler …)") }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterDropdown("Gerät / Serie", s.groups, s.series, { it }, vm::setFaultGroup)
            }
        }
        item { Text("${s.codes.size} von ${s.all} Codes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (s.codes.isEmpty()) item { EmptyState("Kein Code gefunden. Über „Eigener Code“ kannst du Alarme deiner Anlagen ergänzen.") }
        items(s.codes, key = { it.id }) { f -> FaultRow(f, onEdit = { onEdit(f.id) }, onDelete = { deleteId = f.id }) }
        item {
            Text(
                "Mitgelieferte Codes: Originaltexte aus den Aermec-Handbüchern (NRG 0282-0804, Dok. 5723310_05; NRP/NXP/CPS, Dok. 4724311_03), " +
                    "Titel sinngemäß übersetzt. Ursachen und Abhilfe stehen dort nicht – am Gerät und im Handbuch prüfen. " +
                    "Alarme mit Passwort-Reset nur durch den Kundendienst.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Eigenen Code löschen?") },
            confirmButton = { TextButton(onClick = { deleteId = null; vm.deleteFault(id) }, Modifier.heightIn(min = TouchTarget)) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { deleteId = null }, Modifier.heightIn(min = TouchTarget)) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun FaultRow(f: FaultCode, onEdit: () -> Unit, onDelete: () -> Unit) {
    KCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(f.code, style = MonoStyle.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            Text(f.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (f.isCustom) {
                IconButton(onClick = onEdit, Modifier.size(TouchTarget)) { Icon(Icons.Default.Edit, "Code bearbeiten") }
                IconButton(onClick = onDelete, Modifier.size(TouchTarget)) { Icon(Icons.Default.Delete, "Code löschen") }
            }
        }
        Text("${f.manufacturer} · ${f.series}" + if (f.isCustom) " · eigen" else "",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        if (f.cause.isNotBlank()) {
            Text((if (f.isCustom) "Ursache: " else "Original: ") + f.cause, Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (f.remedy.isNotBlank()) {
            Text((if (f.isCustom) "Abhilfe: " else "") + f.remedy, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun FaultEditScreen(id: Long, onBack: () -> Unit) {
    val vm = appViewModel(key = "faultEdit-$id") { FaultEditViewModel(it, id) }
    val existing by vm.existing.collectAsStateWithLifecycle()
    if (id != 0L && existing == null) { Scaffold(topBar = { BackTopBar("Code bearbeiten", onBack) }) { EmptyState("Lade …", Modifier.padding(it)) }; return }
    val e = existing
    var maker by rememberSaveable { mutableStateOf(e?.manufacturer ?: "") }
    var series by rememberSaveable { mutableStateOf(e?.series ?: "") }
    var code by rememberSaveable { mutableStateOf(e?.code ?: "") }
    var title by rememberSaveable { mutableStateOf(e?.title ?: "") }
    var cause by rememberSaveable { mutableStateOf(e?.cause ?: "") }
    var remedy by rememberSaveable { mutableStateOf(e?.remedy ?: "") }

    Scaffold(topBar = { BackTopBar(if (id == 0L) "Eigener Code" else "Code bearbeiten", onBack) }) { padding ->
        FormColumn(Modifier.padding(padding)) {
            LabeledField("Hersteller *", maker, { maker = it }, supportingText = "z. B. " + vm.knownManufacturers.joinToString(", "))
            LabeledField("Gerät / Serie / Controller *", series, { series = it })
            LabeledField("Code *", code, { code = it }, supportingText = "z. B. AL12 oder E05")
            LabeledField("Bezeichnung *", title, { title = it })
            LabeledField("Ursache", cause, { cause = it }, singleLine = false, minLines = 2)
            LabeledField("Abhilfe / Rücksetzen", remedy, { remedy = it }, singleLine = false, minLines = 2)
            Button(
                onClick = {
                    vm.save(
                        FaultCode(id = id, manufacturer = maker, series = series, code = code, title = title, cause = cause, remedy = remedy, isCustom = true),
                        onBack,
                    )
                },
                enabled = maker.isNotBlank() && series.isNotBlank() && code.isNotBlank() && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
            ) { Text("Speichern") }
        }
    }
}

// ----------------------------------------------------------------------------- Leitfäden

@Composable
private fun GuidesTab(vm: ServiceViewModel) {
    val guides by vm.guides.collectAsStateWithLifecycle()
    val state by vm.guideState.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(listOf<String>()) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SearchField(state.first, vm::setGuideQuery, "z. B. pLAN, Passwort, pCOWeb, Tasten") }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                FilterDropdown("Kategorie", vm.guideCategories, state.second, { it }, vm::setGuideCategory)
            }
        }
        if (guides.isEmpty()) item { EmptyState("Kein Leitfaden gefunden.") }
        items(guides, key = { it.id }) { g ->
            val expanded = g.id in open
            GuideCard(g, expanded) { open = if (expanded) open - g.id else open + g.id }
        }
    }
}

@Composable
private fun GuideCard(g: Guide, expanded: Boolean, onToggle: () -> Unit) {
    KCard(onClick = onToggle) {
        Text(g.category.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(g.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("Gilt für: ${g.appliesTo}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (expanded) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                g.steps.forEachIndexed { i, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${i + 1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(step, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (g.notes.isNotBlank()) Text("Hinweis: ${g.notes}", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
                Text("Quelle: ${g.source}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text("Antippen für Details", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ----------------------------------------------------------------------------- Hersteller

@Composable
private fun MakersTab(vm: ServiceViewModel, onShowFaults: (String) -> Unit, onShowGuides: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(vm.manufacturers, key = { it.id }) { m -> MakerCard(m, vm.hasFaultCodes(m.name), onShowFaults, onShowGuides) }
    }
}

@Composable
private fun MakerCard(m: Manufacturer, hasFaults: Boolean, onShowFaults: (String) -> Unit, onShowGuides: () -> Unit) {
    KCard {
        Text(m.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(listOf(m.fullName, m.location, m.country, m.website).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(m.description, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge)
        if (m.series.isNotEmpty()) {
            SectionHeader("Geräteserien")
            m.series.forEach { s ->
                Text(s.name + if (s.controller.isNotBlank()) " – ${s.controller}" else "", fontWeight = FontWeight.Bold)
                if (s.note.isNotBlank()) Text(s.note, style = MaterialTheme.typography.bodyMedium)
                if (s.source.isNotBlank()) Text("Quelle: ${s.source}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (m.tips.isNotEmpty()) {
            SectionHeader("Hinweise")
            m.tips.forEach { Text("• $it", Modifier.padding(vertical = 2.dp), style = MaterialTheme.typography.bodyLarge) }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasFaults) Button(onClick = { onShowFaults(m.name) }, Modifier.heightIn(min = TouchTarget)) { Text("Störungscodes") }
            if (m.id != "carel") OutlinedButton(onClick = onShowGuides, Modifier.heightIn(min = TouchTarget)) { Text("Carel-Leitfäden") }
        }
    }
}

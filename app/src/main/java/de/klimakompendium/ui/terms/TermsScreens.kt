package de.klimakompendium.ui.terms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.klimakompendium.domain.model.MeasurementKind
import de.klimakompendium.domain.model.Term
import de.klimakompendium.domain.model.TermCategory
import de.klimakompendium.domain.model.TermLanguage
import de.klimakompendium.ui.components.BackTopBar
import de.klimakompendium.ui.components.CenteredContent
import de.klimakompendium.ui.components.EmptyState
import de.klimakompendium.ui.components.FilterDropdown
import de.klimakompendium.ui.components.FormColumn
import de.klimakompendium.ui.components.InfoRow
import de.klimakompendium.ui.components.KCard
import de.klimakompendium.ui.components.LabeledField
import de.klimakompendium.ui.components.SearchField
import de.klimakompendium.ui.components.SelectField
import de.klimakompendium.ui.components.TermRow
import de.klimakompendium.ui.theme.TouchTarget
import de.klimakompendium.ui.util.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(onOpenTerm: (Long) -> Unit, onNewTerm: () -> Unit) {
    val vm = appViewModel { TermsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Begriffe & Abkürzungen") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewTerm, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Eigener Begriff") })
        },
    ) { padding ->
        CenteredContent(Modifier.padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { SearchField(vm.search.text, vm::setQuery, "Abkürzung oder Begriff (DE/EN/IT)") }
                item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterDropdown("Sprache", TermLanguage.entries.toList(), s.language, { it.label }, vm::setLanguage)
                        FilterDropdown("Kategorie", TermCategory.entries.toList(), s.category, { it.label }, vm::setCategory)
                        FilterChip(
                            selected = s.favoritesOnly, onClick = vm::toggleFavoritesOnly,
                            label = { Text("Favoriten") },
                            leadingIcon = { Icon(Icons.Default.Star, null) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                item {
                    Text("${s.terms.size} von ${s.total} Begriffen", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (s.terms.isEmpty()) item { EmptyState("Keine Begriffe gefunden.") }
                items(s.terms, key = { it.id }) { t -> TermRow(t, { onOpenTerm(t.id) }, { vm.toggleFavorite(t) }) }
            }
        }
    }
}

@Composable
fun TermDetailScreen(id: Long, onBack: () -> Unit, onEdit: (Long) -> Unit, onOpenRefrigerant: (String) -> Unit) {
    val vm = appViewModel(key = "term-$id") { TermDetailViewModel(it, id) }
    val term by vm.term.collectAsStateWithLifecycle()
    val refrigerant by vm.refrigerant.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = {
        BackTopBar(term?.abbreviation ?: "Begriff", onBack, actions = {
            term?.let { t ->
                IconButton(onClick = vm::toggleFavorite, Modifier.heightIn(min = TouchTarget)) {
                    Icon(if (t.isFavorite) Icons.Default.Star else Icons.Default.StarBorder, "Favorit",
                        tint = if (t.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (t.isCustom) {
                    IconButton(onClick = { onEdit(t.id) }, Modifier.heightIn(min = TouchTarget)) { Icon(Icons.Default.Edit, "Bearbeiten") }
                    IconButton(onClick = { confirmDelete = true }, Modifier.heightIn(min = TouchTarget)) { Icon(Icons.Default.Delete, "Löschen") }
                }
            }
        })
    }) { padding ->
        val t = term
        if (t == null) { EmptyState("Begriff nicht gefunden.", Modifier.padding(padding)); return@Scaffold }
        FormColumn(Modifier.padding(padding)) {
            KCard {
                Text(t.abbreviation, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text(t.fullTerm, style = MaterialTheme.typography.titleMedium)
                if (t.variants.isNotEmpty()) Text("Gleichbedeutend: " + t.variants.joinToString(" · "), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                Text("Gebräuchlich: ${t.language.label}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            KCard {
                InfoRow("Deutsch", t.termDe)
                InfoRow("Englisch", t.termEn)
                InfoRow("Italienisch", t.termIt)
            }
            KCard {
                InfoRow("Kategorie", t.category.label)
                if (t.kind != MeasurementKind.NONE) InfoRow("Messgröße", t.kind.label)
                InfoRow("Typische Einheit", t.unit)
                InfoRow("Bedeutung", t.meaning)
                InfoRow("Kontext / Hinweis", t.context)
                InfoRow("Richtwerte", t.typicalRange)
            }
            refrigerant?.let { r ->
                Button(onClick = { onOpenRefrigerant(r.id) }, Modifier.fillMaxWidth().heightIn(min = TouchTarget)) {
                    Text(if (r.hasPtData) "p‑T‑Tabelle ${r.name} öffnen" else "${r.name}: Kältemitteldaten")
                }
            }
            if (t.kind != MeasurementKind.NONE) {
                Text(
                    "Richtwerte sind allgemeine Erfahrungswerte. Maßgeblich sind immer die Herstellerangaben der Anlage.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Begriff löschen?") },
            text = { Text("„${term?.abbreviation}“ wird dauerhaft entfernt.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }, Modifier.heightIn(min = TouchTarget)) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }, Modifier.heightIn(min = TouchTarget)) { Text("Abbrechen") } },
        )
    }
}

@Composable
fun TermEditScreen(id: Long, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val vm = appViewModel(key = "termEdit-$id") { TermEditViewModel(it, id) }
    val existing by vm.existing.collectAsStateWithLifecycle()
    // Formular erst initialisieren, wenn der bestehende Begriff geladen ist
    if (id != 0L && existing == null) { Scaffold(topBar = { BackTopBar("Begriff bearbeiten", onBack) }) { EmptyState("Lade …", Modifier.padding(it)) }; return }
    val e = existing
    var abbr by rememberSaveable { mutableStateOf(e?.abbreviation ?: "") }
    var lang by rememberSaveable { mutableStateOf(e?.language ?: TermLanguage.DE) }
    var full by rememberSaveable { mutableStateOf(e?.fullTerm ?: "") }
    var de by rememberSaveable { mutableStateOf(e?.termDe ?: "") }
    var en by rememberSaveable { mutableStateOf(e?.termEn ?: "") }
    var trIt by rememberSaveable { mutableStateOf(e?.termIt ?: "") }
    var kind by rememberSaveable { mutableStateOf(e?.kind ?: MeasurementKind.NONE) }
    var cat by rememberSaveable { mutableStateOf(e?.category ?: TermCategory.OTHER) }
    var unit by rememberSaveable { mutableStateOf(e?.unit ?: "") }
    var meaning by rememberSaveable { mutableStateOf(e?.meaning ?: "") }
    var context by rememberSaveable { mutableStateOf(e?.context ?: "") }
    var range by rememberSaveable { mutableStateOf(e?.typicalRange ?: "") }
    val valid = abbr.isNotBlank() && full.isNotBlank() && meaning.isNotBlank()

    Scaffold(topBar = { BackTopBar(if (id == 0L) "Eigener Begriff" else "Begriff bearbeiten", onBack) }) { padding ->
        FormColumn(Modifier.padding(padding)) {
            LabeledField("Abkürzung *", abbr, { abbr = it })
            SelectField("Sprache der Abkürzung", TermLanguage.entries.toList(), lang, { it.label }, { lang = it })
            LabeledField("Vollständiger Begriff *", full, { full = it })
            LabeledField("Deutsch", de, { de = it })
            LabeledField("Englisch", en, { en = it })
            LabeledField("Italienisch", trIt, { trIt = it })
            SelectField("Kategorie", TermCategory.entries.toList(), cat, { it.label }, { cat = it })
            SelectField("Messgröße", MeasurementKind.entries.toList(), kind, { it.label }, { kind = it })
            LabeledField("Typische Einheit", unit, { unit = it })
            LabeledField("Bedeutung *", meaning, { meaning = it }, singleLine = false, minLines = 3)
            LabeledField("Kontext / Hinweis", context, { context = it }, singleLine = false, minLines = 2)
            LabeledField("Richtwerte", range, { range = it }, singleLine = false, minLines = 2)
            Button(
                onClick = {
                    val base = e ?: Term(
                        key = "", abbreviation = "", language = lang, fullTerm = "", termDe = "", termEn = "", termIt = "",
                        kind = kind, category = cat, unit = "", meaning = "", context = "", typicalRange = "", isCustom = true,
                    )
                    vm.save(
                        base.copy(
                            abbreviation = abbr.trim(), language = lang, fullTerm = full.trim(),
                            termDe = de.trim().ifBlank { if (lang == TermLanguage.DE) full.trim() else "" }, termEn = en.trim(), termIt = trIt.trim(),
                            kind = kind, category = cat, unit = unit.trim(), meaning = meaning.trim(),
                            context = context.trim(), typicalRange = range.trim(),
                        ),
                        onSaved,
                    )
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
            ) { Text("Speichern") }
        }
    }
}

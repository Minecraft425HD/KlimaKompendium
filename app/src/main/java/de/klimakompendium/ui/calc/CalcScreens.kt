package de.klimakompendium.ui.calc

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.klimakompendium.domain.calc.CalcContext
import de.klimakompendium.domain.calc.CalcOutcome
import de.klimakompendium.domain.calc.Measure
import de.klimakompendium.domain.calc.MeasureGroup
import de.klimakompendium.domain.calc.Quantities
import de.klimakompendium.domain.calc.Quantity
import de.klimakompendium.domain.calc.QuantityCategory
import de.klimakompendium.domain.calc.QuantitySearch
import de.klimakompendium.domain.convert.PressureReference
import de.klimakompendium.domain.convert.UnitConverter
import de.klimakompendium.domain.hvac.RangeStatus
import de.klimakompendium.ui.components.BackTopBar
import de.klimakompendium.ui.components.DecimalField
import de.klimakompendium.ui.components.EmptyState
import de.klimakompendium.ui.components.FilterDropdown
import de.klimakompendium.ui.components.FormColumn
import de.klimakompendium.ui.components.KCard
import de.klimakompendium.ui.components.SectionHeader
import de.klimakompendium.ui.components.SelectField
import de.klimakompendium.ui.theme.MonoStyle
import de.klimakompendium.ui.theme.OkColor
import de.klimakompendium.ui.theme.TouchTarget
import de.klimakompendium.ui.theme.WarnColor
import de.klimakompendium.ui.util.appViewModel

// ============================================================================ Tab „Berechnen“

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalcTab(onOpen: (String) -> Unit) {
    var selectedNames by rememberSaveable { mutableStateOf(listOf<String>()) }
    var showGlossary by rememberSaveable { mutableStateOf(false) }
    val selected = selectedNames.mapNotNull { n -> Measure.entries.firstOrNull { it.name == n } }.toSet()
    val availability = QuantitySearch.availability(selected)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            KCard {
                Text("Was kann ich berechnen?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Tippe an, was du an der Anlage gemessen hast. Die App zeigt dir, welche Größen sich damit berechnen lassen – " +
                        "und welcher Messwert noch fehlt. Oder öffne unten direkt eine Größe: dort steht, was sie bedeutet und was du dafür brauchst.",
                    Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        MeasureGroup.entries.forEach { group ->
            item(key = "grp-${group.name}") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader("Gemessen: ${group.label}")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Measure.entries.filter { it.group == group }.forEach { m ->
                            FilterChip(
                                selected = m in selected,
                                onClick = { selectedNames = if (m in selected) selectedNames - m.name else selectedNames + m.name },
                                label = { Text(m.label) }, modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                }
            }
        }
        if (selected.isNotEmpty()) {
            item {
                TextButton(onClick = { selectedNames = emptyList() }, Modifier.heightIn(min = TouchTarget)) { Text("Auswahl zurücksetzen") }
            }
            val ready = availability.filter { it.ready }
            val almost = availability.filter { !it.ready }
            item { SectionHeader("Damit berechenbar (${ready.size})") }
            if (ready.isEmpty()) item { EmptyState("Mit diesen Messwerten allein ist noch nichts berechenbar – siehe „Fast berechenbar“.") }
            items(ready, key = { "ready-${it.quantity.id}" }) { QuantityCard(it.quantity, missing = emptyList(), onOpen) }
            if (almost.isNotEmpty()) {
                item { SectionHeader("Fast berechenbar – ein Messwert fehlt (${almost.size})") }
                items(almost, key = { "almost-${it.quantity.id}" }) { QuantityCard(it.quantity, it.missing, onOpen) }
            }
        } else {
            QuantityCategory.entries.forEach { cat ->
                item(key = "cat-${cat.name}") { SectionHeader(cat.label) }
                items(Quantities.all.filter { it.category == cat }, key = { "q-${it.id}" }) { QuantityCard(it, emptyList(), onOpen) }
            }
        }
        item {
            OutlinedButton(onClick = { showGlossary = !showGlossary }, Modifier.fillMaxWidth().heightIn(min = TouchTarget)) {
                Text(if (showGlossary) "Messwert-Übersicht ausblenden" else "Messwert-Übersicht: Wo messe ich was?")
            }
        }
        if (showGlossary) {
            Measure.entries.forEach { m ->
                item(key = "glossary-${m.name}") { MeasureCard(m) }
            }
        }
    }
}

@Composable
private fun QuantityCard(q: Quantity, missing: List<Measure>, onOpen: (String) -> Unit) {
    KCard(onClick = { onOpen(q.id) }) {
        Text(q.category.label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(q.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(q.summary, style = MaterialTheme.typography.bodyMedium)
        Text("Benötigt: " + q.needs.joinToString(" · ") { it.label }, Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (missing.isNotEmpty()) {
            Text("Es fehlt: " + missing.joinToString(" · ") { it.label }, style = MaterialTheme.typography.bodyMedium,
                color = WarnColor, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MeasureCard(m: Measure) {
    KCard {
        Text("${m.label}  (${m.symbol})", style = MaterialTheme.typography.titleMedium)
        Text(m.hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val users = Quantities.usedBy(m)
        if (users.isNotEmpty()) {
            Text("Wird gebraucht für: " + users.joinToString(", ") { it.name }, Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

// ============================================================================ Rechner je Größe

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalcScreen(id: String, onBack: () -> Unit) {
    val q = Quantities.byId(id)
    val vm = appViewModel { CalcViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var raw by rememberSaveable(id) { mutableStateOf(HashMap<String, String>()) }
    var unitIndex by rememberSaveable(id) { mutableStateOf(HashMap<String, Int>()) }

    Scaffold(topBar = { BackTopBar(q?.name ?: "Berechnung", onBack) }) { padding ->
        if (q == null) { EmptyState("Berechnung nicht gefunden.", Modifier.padding(padding)); return@Scaffold }
        FormColumn(Modifier.padding(padding)) {
            KCard {
                Text("Was ist das?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(q.summary, style = MaterialTheme.typography.bodyLarge)
                Text("Was sagt der Wert?", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(q.interpretation, style = MaterialTheme.typography.bodyLarge)
                Text("Formel", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(q.formula, style = MonoStyle)
            }
            KCard {
                Text("Benötigte Messwerte", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                q.needs.forEach { m -> Text("• ${m.label} (${m.symbol}): ${m.hint}", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium) }
                if (q.optional.isNotEmpty()) {
                    Text("Optional, ergänzt das Ergebnis", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    q.optional.forEach { m -> Text("• ${m.label} (${m.symbol})", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium) }
                }
                if (q.needsRefrigerant) Text("• Kältemittel mit hinterlegter p‑T‑Tabelle", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
            }

            SectionHeader("Eingabe")
            val refrigerant = vm.refrigerants.firstOrNull { it.id == settings.lastRefrigerant } ?: vm.refrigerants.firstOrNull()
            if (q.needsRefrigerant && refrigerant != null) {
                SelectField("Kältemittel", vm.refrigerants, refrigerant, { "${it.name}  (${it.safetyClass})" }, { vm.setRefrigerant(it.id) })
            }
            if (q.usesPressure) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PressureReference.entries.forEach { r ->
                        FilterChip(selected = settings.pressureReference == r, onClick = { vm.setReference(r) },
                            label = { Text("Druck: ${r.label}") }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
            }
            (q.needs + q.optional).forEach { m ->
                val optional = m in q.optional
                val idx = unitIndex[m.name] ?: 0
                val unit = m.units[idx.coerceIn(0, m.units.lastIndex)]
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        DecimalField(m.label + if (optional) " (optional)" else "", raw[m.name] ?: "",
                            { v -> raw = HashMap(raw).also { it[m.name] = v } }, suffix = unit.symbol)
                        Text(m.hint, Modifier.padding(start = 4.dp, top = 2.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (m.units.size > 1) {
                        FilterDropdown("Einheit", m.units.indices.toList(), idx, { m.units[it].symbol },
                            { i -> unitIndex = HashMap(unitIndex).also { it[m.name] = i ?: 0 } }, allLabel = null, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            // --- Ergebnis ---
            val parsed = (q.needs + q.optional).associateWith { m ->
                val unit = m.units[(unitIndex[m.name] ?: 0).coerceIn(0, m.units.lastIndex)]
                UnitConverter.parseDecimal(raw[m.name] ?: "")?.let { it * unit.factor }
            }
            val missing = q.needs.filter { parsed[it] == null }
            SectionHeader("Ergebnis")
            if (missing.isNotEmpty()) {
                Text("Noch benötigt: " + missing.joinToString(", ") { it.label }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            } else {
                val values = parsed.filterValues { it != null }.mapValues { it.value!! }
                val negativeProblem = values.entries.firstOrNull { (m, v) -> v < 0 && !m.allowNegative }
                if (negativeProblem != null) {
                    Text("${negativeProblem.key.label}: negative Werte sind hier nicht möglich.", color = MaterialTheme.colorScheme.error)
                } else {
                    when (val out = q.compute(CalcContext(values, refrigerant, settings.pressureReference))) {
                        is CalcOutcome.Error -> KCard { Text(out.message, color = WarnColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge) }
                        is CalcOutcome.Ok -> KCard {
                            out.lines.forEach { l ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(l.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${l.value} ${l.unit}".trim(), style = MaterialTheme.typography.titleLarge,
                                        fontWeight = if (l.emphasis) FontWeight.Bold else FontWeight.Normal,
                                        color = if (l.emphasis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                }
                                if (l.note.isNotBlank()) Text(l.note, style = MaterialTheme.typography.bodyMedium,
                                    color = when (l.status) { null -> MaterialTheme.colorScheme.onSurfaceVariant; RangeStatus.TYPICAL -> OkColor; else -> WarnColor },
                                    fontWeight = if (l.status != null) FontWeight.Bold else FontWeight.Normal)
                            }
                            if (out.hint.isNotBlank()) Text(out.hint, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Text("Richtwerte sind allgemeine Erfahrungswerte; maßgeblich sind die Vorgaben des Geräteherstellers.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

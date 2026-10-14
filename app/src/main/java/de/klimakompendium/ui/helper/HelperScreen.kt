package de.klimakompendium.ui.helper

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.klimakompendium.domain.convert.PressureReference
import de.klimakompendium.domain.convert.PressureUnit
import de.klimakompendium.domain.convert.TemperatureUnit
import de.klimakompendium.domain.convert.UnitConverter
import de.klimakompendium.domain.hvac.HvacCalculator
import de.klimakompendium.domain.hvac.RangeStatus
import de.klimakompendium.domain.hvac.Saturation
import de.klimakompendium.domain.model.MeasurementKind
import de.klimakompendium.domain.model.Refrigerant
import de.klimakompendium.domain.model.SaturationPhase
import de.klimakompendium.domain.model.Term
import de.klimakompendium.ui.components.CenteredContent
import de.klimakompendium.ui.components.DecimalField
import de.klimakompendium.ui.components.EmptyState
import de.klimakompendium.ui.components.InfoRow
import de.klimakompendium.ui.components.KCard
import de.klimakompendium.ui.components.SearchField
import de.klimakompendium.ui.components.SectionHeader
import de.klimakompendium.ui.components.SelectField
import de.klimakompendium.ui.components.SmallChip
import de.klimakompendium.ui.theme.OkColor
import de.klimakompendium.ui.theme.WarnColor
import de.klimakompendium.ui.util.appViewModel

private val TABS = listOf("explain" to "Erklärung", "calc" to "Berechnen", "convert" to "Umrechner", "pt" to "p‑T‑Tabelle", "superheat" to "Überhitzung")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelperScreen(initialTab: String, initialRefrigerant: String, onOpenCalc: (String) -> Unit) {
    val vm = appViewModel { HelperViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(TABS.indexOfFirst { it.first == initialTab }.coerceAtLeast(0)) }
    LaunchedEffect(initialRefrigerant) { if (initialRefrigerant.isNotBlank()) vm.setRefrigerant(initialRefrigerant) }

    Scaffold(topBar = { TopAppBar(title = { Text("Druck & Temperatur") }) }) { padding ->
        Column(Modifier.padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                TABS.forEachIndexed { i, (_, title) ->
                    Tab(selected = tab == i, onClick = { tab = i }, modifier = Modifier.heightIn(min = 56.dp), text = { Text(title) })
                }
            }
            CenteredContent {
                when (TABS[tab].first) {
                    "explain" -> ExplainTab(vm)
                    "calc" -> de.klimakompendium.ui.calc.CalcTab(onOpenCalc)
                    "convert" -> ConvertTab(settings.pressureReference)
                    "pt" -> PtTab(vm, settings.lastRefrigerant, settings.pressureReference)
                    else -> SuperheatTab(vm, settings.lastRefrigerant, settings.pressureReference)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Erklärung: Was bedeutet ND/HD/SAP/SAT … (Druck oder Temperatur?)
// ---------------------------------------------------------------------------------------------

@Composable
private fun ExplainTab(vm: HelperViewModel) {
    val s by vm.explain.collectAsStateWithLifecycle()
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SearchField(s.query, vm::setQuery, "z. B. ND, SAP, SAT, Verdampfungstemperatur") }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ND", "HD", "SAP", "SAT", "RAT", "t0", "tc", "SH", "SC").forEach { q -> SmallChip(q) { vm.setQuery(q) } }
            }
        }
        if (s.terms.isEmpty()) item { EmptyState("Kein Druck- oder Temperaturbegriff gefunden.") }
        items(s.terms, key = { it.id }) { ExplainCard(it) }
    }
}

@Composable
private fun ExplainCard(t: Term) {
    KCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t.abbreviation, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Column {
                Text(t.fullTerm, style = MaterialTheme.typography.titleMedium)
                Text(
                    (if (t.kind == MeasurementKind.PRESSURE) "DRUCK" else "TEMPERATUR") + if (t.unit.isNotBlank()) " · ${t.unit}" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (t.kind == MeasurementKind.PRESSURE) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(t.meaning, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge)
        InfoRow("Zusammenhang", t.context)
        InfoRow("Richtwerte", t.typicalRange)
        InfoRow("Englisch", t.termEn.takeIf { it != t.fullTerm } ?: "")
        InfoRow("Italienisch", t.termIt.takeIf { it != t.fullTerm } ?: "")
    }
}

// ---------------------------------------------------------------------------------------------
// Umrechner
// ---------------------------------------------------------------------------------------------

@Composable
private fun ConvertTab(reference: PressureReference) {
    var pText by rememberSaveable { mutableStateOf("10") }
    var pUnit by rememberSaveable { mutableStateOf(PressureUnit.BAR) }
    var tText by rememberSaveable { mutableStateOf("20") }
    var tUnit by rememberSaveable { mutableStateOf(TemperatureUnit.CELSIUS) }
    val p = UnitConverter.parseDecimal(pText)
    val t = UnitConverter.parseDecimal(tText)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionHeader("Druck") }
        item {
            KCard {
                DecimalField("Wert", pText, { pText = it })
                UnitChips(PressureUnit.entries, pUnit, { it.symbol }) { pUnit = it }
                if (p == null) Text("Bitte Zahl eingeben.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                else {
                    UnitConverter.allPressures(p, pUnit).forEach { (u, v) ->
                        ResultLine(UnitConverter.format(v, if (u == PressureUnit.PSI || u == PressureUnit.MBAR) 2 else 3), u.symbol, highlight = u == pUnit)
                    }
                    val bar = UnitConverter.convertPressure(p, pUnit, PressureUnit.BAR)
                    Text("Überdruck ↔ absolut", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    ResultLine(UnitConverter.format(UnitConverter.gaugeToAbsoluteBar(bar), 3), "bar abs. (wenn Eingabe = Überdruck)")
                    ResultLine(UnitConverter.format(UnitConverter.absoluteToGaugeBar(bar), 3), "bar ü (wenn Eingabe = absolut)")
                    Text("Atmosphärendruck 1,01325 bar, ohne Höhenkorrektur.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SectionHeader("Temperatur") }
        item {
            KCard {
                DecimalField("Wert", tText, { tText = it })
                UnitChips(TemperatureUnit.entries, tUnit, { it.symbol }) { tUnit = it }
                if (t == null) Text("Bitte Zahl eingeben.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                else UnitConverter.allTemperatures(t, tUnit).forEach { (u, v) -> ResultLine(UnitConverter.format(v, 2), u.symbol, highlight = u == tUnit) }
            }
        }
        item {
            Text("Standard-Bezug in den p‑T‑Rechnern: ${reference.label} (Einstellungen).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun <T> UnitChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
private fun ResultLine(value: String, unit: String, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text(unit, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------------------------
// p-T-Tabelle
// ---------------------------------------------------------------------------------------------

@Composable
private fun refrigerantPicker(vm: HelperViewModel, selectedId: String, reference: PressureReference): Refrigerant {
    val selected = vm.refrigerants.firstOrNull { it.id == selectedId && it.hasPtData } ?: vm.refrigerants.first { it.hasPtData }
    SelectField("Kältemittel", vm.refrigerants.filter { it.hasPtData }, selected, { "${it.name}  (${it.safetyClass})" }, { vm.setRefrigerant(it.id) })
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PressureReference.entries.forEach { r ->
            FilterChip(selected = reference == r, onClick = { vm.setReference(r) }, label = { Text(r.label) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    return selected
}

@Composable
private fun PtTab(vm: HelperViewModel, refrigerantId: String, reference: PressureReference) {
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 = Druck→Temp, 1 = Temp→Druck
    var input by rememberSaveable { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val r = refrigerantPicker(vm, refrigerantId, reference)
                RefrigerantInfo(r)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(mode == 0, { mode = 0; input = "" }, { Text("Druck → Temperatur") }, Modifier.heightIn(min = 48.dp))
                    FilterChip(mode == 1, { mode = 1; input = "" }, { Text("Temperatur → Druck") }, Modifier.heightIn(min = 48.dp))
                }
                DecimalField(
                    if (mode == 0) "Druck" else "Temperatur", input, { input = it },
                    suffix = if (mode == 0) (if (reference == PressureReference.GAUGE) "bar ü" else "bar abs.") else "°C",
                )
                PtResult(r, mode, input, reference)
            }
        }
        val r = vm.refrigerants.firstOrNull { it.id == refrigerantId && it.hasPtData } ?: vm.refrigerants.first { it.hasPtData }
        item { SectionHeader("Tabelle ${r.name} (${reference.label})") }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text("°C", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(if (r.isZeotropic) "Tau bar" else "bar", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                if (r.isZeotropic) Text("Siede bar", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            }
        }
        items(r.ptTable, key = { "${r.id}-${it.tempC}" }) { pt ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text(UnitConverter.format(pt.tempC, 0), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(UnitConverter.format(UnitConverter.fromAbsoluteBar(pt.pDewBar, PressureUnit.BAR, reference), 2), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                if (r.isZeotropic) Text(UnitConverter.format(UnitConverter.fromAbsoluteBar(pt.pBubbleBar, PressureUnit.BAR, reference), 2), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            Text(
                "Quelle: ${r.source.ifBlank { "–" }}. Richtwerte für die Praxis – bei Abweichungen gilt die Herstellertabelle. " +
                    "Bei zeotropen Gemischen: Taupunkt für Verdampfer/Überhitzung, Siedepunkt für Verflüssiger/Unterkühlung.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RefrigerantInfo(r: Refrigerant) {
    Text(
        "${r.type} · Klasse ${r.safetyClass} · GWP ${r.gwp}" + if (r.isZeotropic) " · Glide ≈ ${UnitConverter.format(r.glideK, 1)} K" else "",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PtResult(r: Refrigerant, mode: Int, input: String, reference: PressureReference) {
    val v = UnitConverter.parseDecimal(input) ?: return
    KCard {
        if (mode == 0) {
            val abs = UnitConverter.toAbsoluteBar(v, PressureUnit.BAR, reference)
            val dew = Saturation.temperatureC(r, abs, SaturationPhase.DEW)
            val bub = Saturation.temperatureC(r, abs, SaturationPhase.BUBBLE)
            if (dew == null || bub == null) {
                OutOfRange(r, "Druck", "${UnitConverter.format(UnitConverter.fromAbsoluteBar(r.ptTable.first().pDewBar, PressureUnit.BAR, reference), 2)} … " +
                    "${UnitConverter.format(UnitConverter.fromAbsoluteBar(r.ptTable.last().pBubbleBar, PressureUnit.BAR, reference), 2)} bar")
            } else if (r.isZeotropic) {
                ResultLine(UnitConverter.format(dew, 1), "°C Taupunkt (Verdampfer, t0)")
                ResultLine(UnitConverter.format(bub, 1), "°C Siedepunkt (Verflüssiger, tc)")
            } else {
                ResultLine(UnitConverter.format(dew, 1), "°C Sättigungstemperatur", highlight = true)
            }
        } else {
            val dew = Saturation.pressureAbsBar(r, v, SaturationPhase.DEW)
            val bub = Saturation.pressureAbsBar(r, v, SaturationPhase.BUBBLE)
            if (dew == null || bub == null) {
                OutOfRange(r, "Temperatur", "${UnitConverter.format(r.minTempC ?: 0.0, 0)} … ${UnitConverter.format(r.maxTempC ?: 0.0, 0)} °C")
            } else {
                val unit = if (reference == PressureReference.GAUGE) "bar ü" else "bar abs."
                fun show(abs: Double) = UnitConverter.format(UnitConverter.fromAbsoluteBar(abs, PressureUnit.BAR, reference), 2)
                if (r.isZeotropic) {
                    ResultLine(show(dew), "$unit Taudruck")
                    ResultLine(show(bub), "$unit Siededruck")
                } else ResultLine(show(dew), unit, highlight = true)
                ResultLine(UnitConverter.format(UnitConverter.fromAbsoluteBar(dew, PressureUnit.PSI, reference), 1), "psi")
                ResultLine(UnitConverter.format(UnitConverter.fromAbsoluteBar(dew, PressureUnit.KPA, reference), 0), "kPa")
            }
        }
    }
}

@Composable
private fun OutOfRange(r: Refrigerant, what: String, range: String) {
    Text("$what außerhalb der hinterlegten ${r.name}-Tabelle.", color = WarnColor, fontWeight = FontWeight.Bold)
    Text("Gültiger Bereich: $range", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---------------------------------------------------------------------------------------------
// Überhitzung / Unterkühlung
// ---------------------------------------------------------------------------------------------

@Composable
private fun SuperheatTab(vm: HelperViewModel, refrigerantId: String, reference: PressureReference) {
    var nd by rememberSaveable { mutableStateOf("") }
    var sgt by rememberSaveable { mutableStateOf("") }
    var hd by rememberSaveable { mutableStateOf("") }
    var flt by rememberSaveable { mutableStateOf("") }
    val unit = if (reference == PressureReference.GAUGE) "bar ü" else "bar abs."

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val r = refrigerantPicker(vm, refrigerantId, reference)
                RefrigerantInfo(r)

                SectionHeader("Überhitzung (Saugseite)")
                KCard {
                    DecimalField("Saugdruck (ND)", nd, { nd = it }, suffix = unit)
                    Column(Modifier.padding(top = 8.dp)) { DecimalField("Sauggastemperatur (Rohr am Verdampferaustritt/Verdichter)", sgt, { sgt = it }, suffix = "°C") }
                    val p = UnitConverter.parseDecimal(nd)
                    val t = UnitConverter.parseDecimal(sgt)
                    if (p != null && t != null) {
                        val res = HvacCalculator.superheat(r, p, reference, t)
                        if (res == null) OutOfRange(r, "Saugdruck", "p‑T‑Tabelle ${r.name}")
                        else {
                            Spacer8()
                            ResultLine(UnitConverter.format(res.evaporatingTempC, 1), "°C t0 (Taupunkt)")
                            StatusLine("Überhitzung", res.superheatK, res.status, HvacCalculator.SUPERHEAT_TYPICAL_K)
                        }
                    }
                }

                SectionHeader("Unterkühlung (Hochdruckseite)")
                KCard {
                    DecimalField("Hochdruck (HD)", hd, { hd = it }, suffix = unit)
                    Column(Modifier.padding(top = 8.dp)) { DecimalField("Flüssigkeitstemperatur (Rohr nach Verflüssiger)", flt, { flt = it }, suffix = "°C") }
                    val p = UnitConverter.parseDecimal(hd)
                    val t = UnitConverter.parseDecimal(flt)
                    if (p != null && t != null) {
                        val res = HvacCalculator.subcooling(r, p, reference, t)
                        if (res == null) OutOfRange(r, "Hochdruck", "p‑T‑Tabelle ${r.name}")
                        else {
                            Spacer8()
                            ResultLine(UnitConverter.format(res.condensingTempC, 1), "°C tc (Siedepunkt)")
                            StatusLine("Unterkühlung", res.subcoolingK, res.status, HvacCalculator.SUBCOOLING_TYPICAL_K)
                        }
                    }
                }
                Text(
                    "Die Richtwertbereiche sind allgemeine Erfahrungswerte (Überhitzung 5–10 K, Unterkühlung 3–10 K). " +
                        "Maßgeblich sind die Vorgaben des Geräteherstellers.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))

@Composable
private fun StatusLine(name: String, value: Double, status: RangeStatus, typical: ClosedFloatingPointRange<Double>) {
    val color = if (status == RangeStatus.TYPICAL) OkColor else WarnColor
    ResultLine(UnitConverter.format(value, 1), "K $name", highlight = true)
    Text(
        "${status.label} (${UnitConverter.format(typical.start, 0)}–${UnitConverter.format(typical.endInclusive, 0)} K)",
        color = color, fontWeight = FontWeight.Bold,
    )
}

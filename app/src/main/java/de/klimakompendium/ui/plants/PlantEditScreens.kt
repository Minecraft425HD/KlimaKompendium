package de.klimakompendium.ui.plants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.klimakompendium.domain.model.AccessType
import de.klimakompendium.domain.model.Controller
import de.klimakompendium.domain.model.PasswordChange
import de.klimakompendium.domain.model.Plant
import de.klimakompendium.ui.components.BackTopBar
import de.klimakompendium.ui.components.EmptyState
import de.klimakompendium.ui.components.FormColumn
import de.klimakompendium.ui.components.LabeledField
import de.klimakompendium.ui.components.SelectField
import de.klimakompendium.ui.theme.TouchTarget
import de.klimakompendium.ui.util.appViewModel

@Composable
fun PlantEditScreen(id: Long, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val vm = appViewModel(key = "plantEdit-$id") { PlantEditViewModel(it, id) }
    val existing by vm.existing.collectAsStateWithLifecycle()
    val all by vm.plants.collectAsStateWithLifecycle()
    if (id != 0L && existing == null) { Scaffold(topBar = { BackTopBar("Anlage bearbeiten", onBack) }) { EmptyState("Lade …", Modifier.padding(it)) }; return }
    val e = existing

    var name by rememberSaveable { mutableStateOf(e?.name ?: "") }
    var customer by rememberSaveable { mutableStateOf(e?.customer ?: "") }
    var location by rememberSaveable { mutableStateOf(e?.location ?: "") }
    var year by rememberSaveable { mutableStateOf(e?.buildYear?.toString() ?: "") }
    var refrigerant by rememberSaveable { mutableStateOf(e?.refrigerant ?: "") }
    var maker by rememberSaveable { mutableStateOf(e?.manufacturer ?: "") }
    var serial by rememberSaveable { mutableStateOf(e?.serialNumber ?: "") }
    var ctype by rememberSaveable { mutableStateOf(e?.controllerType ?: "") }
    var notes by rememberSaveable { mutableStateOf(e?.notes ?: "") }
    val yearValue = year.trim().toIntOrNull()
    val yearOk = year.isBlank() || (yearValue != null && yearValue in 1900..2100)
    val valid = name.isNotBlank() && yearOk

    Scaffold(topBar = { BackTopBar(if (id == 0L) "Neue Anlage" else "Anlage bearbeiten", onBack) }) { padding ->
        FormColumn(Modifier.padding(padding)) {
            LabeledField("Name der Anlage *", name, { name = it })
            LabeledField("Kunde", customer, { customer = it })
            SuggestionRow(all.map { it.customer }, customer) { customer = it }
            LabeledField("Standort", location, { location = it })
            LabeledField("Baujahr", year, { year = it.filter(Char::isDigit).take(4) }, keyboardType = KeyboardType.Number, isError = !yearOk,
                supportingText = if (yearOk) null else "Jahr zwischen 1900 und 2100")
            LabeledField("Kältemittel", refrigerant, { refrigerant = it })
            SuggestionRow(vm.refrigerantNames, refrigerant, onlyWhenBlank = true) { refrigerant = it }
            LabeledField("Hersteller", maker, { maker = it })
            SuggestionRow(all.map { it.manufacturer }, maker) { maker = it }
            LabeledField("Seriennummer", serial, { serial = it })
            LabeledField("Controller-Typ (Übersicht)", ctype, { ctype = it })
            SuggestionRow(all.map { it.controllerType }, ctype) { ctype = it }
            LabeledField("Notizen", notes, { notes = it }, singleLine = false, minLines = 3)
            Button(
                onClick = {
                    val base = e ?: Plant(name = "", customer = "", location = "", buildYear = null, refrigerant = "", manufacturer = "",
                        serialNumber = "", controllerType = "", notes = "", lastOpenedAt = null)
                    vm.save(base.copy(name = name, customer = customer, location = location, buildYear = yearValue, refrigerant = refrigerant,
                        manufacturer = maker, serialNumber = serial, controllerType = ctype, notes = notes), onSaved)
                },
                enabled = valid, modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
            ) { Text("Speichern") }
        }
    }
}

/** Antipp-Vorschläge aus bereits vorhandenen Werten (spart Tipparbeit auf der Baustelle). */
@Composable
private fun SuggestionRow(source: List<String>, current: String, onlyWhenBlank: Boolean = false, onPick: (String) -> Unit) {
    val options = source.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
        .filter { current.isBlank() || (!onlyWhenBlank && it.contains(current, true) && !it.equals(current, true)) }
        .take(8)
    if (options.isEmpty() || (onlyWhenBlank && current.isNotBlank())) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> SuggestionChip(onClick = { onPick(o) }, label = { Text(o) }, modifier = Modifier.heightIn(min = 40.dp)) }
    }
}

@Composable
fun ControllerEditScreen(plantId: Long, controllerId: Long, onBack: () -> Unit, onSaved: () -> Unit) {
    val vm = appViewModel(key = "ctrlEdit-$plantId-$controllerId") { ControllerEditViewModel(it, plantId, controllerId) }
    val existing by vm.existing.collectAsStateWithLifecycle()
    if (controllerId != 0L && existing == null) { Scaffold(topBar = { BackTopBar("Controller bearbeiten", onBack) }) { EmptyState("Lade …", Modifier.padding(it)) }; return }
    val e = existing

    var label by rememberSaveable { mutableStateOf(e?.label ?: "") }
    var model by rememberSaveable { mutableStateOf(e?.model ?: "") }
    var access by rememberSaveable { mutableStateOf(e?.accessType ?: AccessType.WEB) }
    var address by rememberSaveable { mutableStateOf(e?.address ?: "") }
    var user by rememberSaveable { mutableStateOf(e?.username ?: "") }
    // Bewusst remember statt rememberSaveable: das Klartext-Passwort darf nicht in den gespeicherten Instanzzustand gelangen.
    var password by remember { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var clearPassword by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf(e?.notes ?: "") }
    val hadPassword = e?.hasPassword == true

    Scaffold(topBar = { BackTopBar(if (controllerId == 0L) "Neuer Controller" else "Controller bearbeiten", onBack) }) { padding ->
        FormColumn(Modifier.padding(padding)) {
            LabeledField("Bezeichnung *", label, { label = it }, supportingText = "z. B. „Hauptregler Kühlstelle 1“")
            LabeledField("Modell", model, { model = it })
            SelectField("Zugriffsart", AccessType.entries.toList(), access, { it.label }, { access = it })
            LabeledField("IP-Adresse / Host / Bus-Adresse", address, { address = it }, keyboardType = KeyboardType.Uri)
            LabeledField("Benutzername", user, { user = it })
            LabeledField(
                if (hadPassword) "Neues Passwort (leer = unverändert)" else "Passwort",
                password, { password = it; if (it.isNotEmpty()) clearPassword = false },
                enabled = !clearPassword,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardType = KeyboardType.Password,
                supportingText = "Wird verschlüsselt (AES-256, Android Keystore) nur auf diesem Gerät gespeichert.",
                trailing = {
                    IconButton(onClick = { showPassword = !showPassword }, Modifier.heightIn(min = TouchTarget)) {
                        Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (showPassword) "Verbergen" else "Anzeigen")
                    }
                },
            )
            if (hadPassword) {
                Row(Modifier.fillMaxWidth().heightIn(min = TouchTarget), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = clearPassword, onCheckedChange = { clearPassword = it; if (it) password = "" })
                    Text("Gespeichertes Passwort löschen", color = MaterialTheme.colorScheme.onSurface)
                }
            }
            LabeledField("Hinweise", notes, { notes = it }, singleLine = false, minLines = 3)
            Button(
                onClick = {
                    val change = when {
                        clearPassword -> PasswordChange.Clear
                        password.isNotEmpty() -> PasswordChange.Set(password)
                        else -> PasswordChange.Keep
                    }
                    val c = Controller(
                        id = controllerId, plantId = plantId, label = label.trim(), model = model.trim(), accessType = access,
                        address = address.trim(), username = user.trim(), hasPassword = hadPassword, notes = notes,
                    )
                    vm.save(c, change, onSaved)
                },
                enabled = label.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
            ) { Text("Speichern") }
        }
    }
}

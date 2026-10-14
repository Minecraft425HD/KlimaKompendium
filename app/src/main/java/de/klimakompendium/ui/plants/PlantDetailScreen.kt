package de.klimakompendium.ui.plants

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.klimakompendium.domain.model.AccessType
import de.klimakompendium.domain.model.Controller
import de.klimakompendium.domain.model.PlantPhoto
import de.klimakompendium.ui.components.BackTopBar
import de.klimakompendium.ui.components.EmptyState
import de.klimakompendium.ui.components.FormColumn
import de.klimakompendium.ui.components.InfoRow
import de.klimakompendium.ui.components.KCard
import de.klimakompendium.ui.components.SectionHeader
import de.klimakompendium.ui.theme.MonoStyle
import de.klimakompendium.ui.theme.TouchTarget
import de.klimakompendium.ui.util.ClipboardHelper
import de.klimakompendium.ui.util.appViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PlantDetailScreen(
    id: Long,
    onBack: () -> Unit,
    onEditPlant: (Long) -> Unit,
    onEditController: (plantId: Long, controllerId: Long) -> Unit,
    onOpenRefrigerant: (String) -> Unit,
    onOpenTerm: (Long) -> Unit,
    onOpenService: (tab: String, query: String) -> Unit,
) {
    val vm = appViewModel(key = "plant-$id") { PlantDetailViewModel(it, id) }
    val plant by vm.plant.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val revealed by vm.revealed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDeletePlant by rememberSaveable { mutableStateOf(false) }
    var revealRequest by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteController by rememberSaveable { mutableStateOf<Long?>(null) }
    var viewPhoto by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is PlantEvent.Message -> snackbar.showSnackbar(e.text)
                is PlantEvent.CopyPassword -> {
                    ClipboardHelper.copy(context, "Passwort", e.password, sensitive = true)
                    snackbar.showSnackbar("Passwort kopiert – wird nach 45 s aus der Zwischenablage gelöscht")
                }
                PlantEvent.Deleted -> onBack()
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { uris ->
        if (uris.isNotEmpty()) vm.addPhotos(uris.map { it.toString() })
    }

    Scaffold(
        topBar = {
            BackTopBar(plant?.name ?: "Anlage", onBack, actions = {
                IconButton(onClick = { onEditPlant(id) }, Modifier.heightIn(min = TouchTarget)) { Icon(Icons.Default.Edit, "Anlage bearbeiten") }
                IconButton(onClick = { confirmDeletePlant = true }, Modifier.heightIn(min = TouchTarget)) { Icon(Icons.Default.Delete, "Anlage löschen") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val p = plant
        if (p == null) { EmptyState("Anlage nicht gefunden.", Modifier.padding(padding)); return@Scaffold }
        FormColumn(Modifier.padding(padding)) {
            KCard {
                Text(p.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                InfoRow("Kunde", p.customer)
                InfoRow("Standort", p.location)
                InfoRow("Baujahr", p.buildYear?.toString() ?: "")
                InfoRow("Kältemittel", p.refrigerant)
                InfoRow("Hersteller", p.manufacturer)
                InfoRow("Seriennummer", p.serialNumber)
                InfoRow("Controller-Typ", p.controllerType)
                InfoRow("Notizen", p.notes)
            }

            // --- Automatische Vorschläge zur Anlage ---
            suggestions.refrigerant?.let { r ->
                SectionHeader("Vorschläge für diese Anlage")
                KCard {
                    Text("${r.name} · Klasse ${r.safetyClass} · GWP ${r.gwp}", style = MaterialTheme.typography.titleMedium)
                    if (r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (r.hasPtData) OutlinedButton(onClick = { onOpenRefrigerant(r.id) }, Modifier.heightIn(min = TouchTarget)) { Text("p‑T‑Tabelle") }
                        suggestions.refrigerantTerm?.let { t ->
                            OutlinedButton(onClick = { onOpenTerm(t.id) }, Modifier.heightIn(min = TouchTarget)) { Text("Begriff ${t.abbreviation}") }
                        }
                    }
                }
            }

            suggestions.manufacturer?.let { m ->
                SectionHeader("Hersteller: ${m.name}")
                KCard {
                    m.tips.take(2).forEach { Text("• $it", Modifier.padding(vertical = 2.dp), style = MaterialTheme.typography.bodyMedium) }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (suggestions.hasFaultCodes) OutlinedButton(onClick = { onOpenService("faults", m.name) }, Modifier.heightIn(min = TouchTarget)) { Text("Störungscodes") }
                        OutlinedButton(onClick = { onOpenService("makers", "") }, Modifier.heightIn(min = TouchTarget)) { Text("Profil") }
                    }
                }
            }
            if (suggestions.carelRelated || suggestions.manufacturer != null) {
                OutlinedButton(onClick = { onOpenService("guides", "Carel") }, Modifier.fillMaxWidth().heightIn(min = TouchTarget)) {
                    Text("Carel-Leitfäden (Tasten, pLAN, Passwörter, pCOWeb)")
                }
            }

            // --- Controller ---
            SectionHeader("Controller (${p.controllers.size})")
            if (p.controllers.isEmpty()) Text("Noch kein Controller hinterlegt.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            p.controllers.forEach { c ->
                ControllerCard(
                    c = c, revealed = revealed[c.id],
                    onReveal = { revealRequest = c.id }, onHide = { vm.hide(c.id) },
                    onCopyUser = { ClipboardHelper.copy(context, "Benutzername", c.username, sensitive = false) },
                    onCopyAddress = { ClipboardHelper.copy(context, "Adresse", c.address, sensitive = false) },
                    onCopyPassword = { vm.copyPassword(c.id) },
                    onOpenBrowser = { openInBrowser(context, c.address) },
                    onEdit = { onEditController(p.id, c.id) }, onDelete = { deleteController = c.id },
                )
            }
            Button(onClick = { onEditController(p.id, 0) }, Modifier.fillMaxWidth().heightIn(min = TouchTarget)) {
                Icon(Icons.Default.Add, null); Text("  Controller hinzufügen")
            }

            // --- Fotos ---
            SectionHeader("Fotos (${p.photos.size})")
            if (p.photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(p.photos, key = { it.id }) { photo -> PhotoThumb(photo, 120) { viewPhoto = photo.id } }
                }
            }
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                Modifier.fillMaxWidth().heightIn(min = TouchTarget),
            ) { Text("Foto hinzufügen") }
        }

        // Dialoge
        revealRequest?.let { cid ->
            AlertDialog(
                onDismissRequest = { revealRequest = null },
                title = { Text("Passwort anzeigen?") },
                text = { Text("Das Passwort wird sichtbar angezeigt und nach kurzer Zeit automatisch wieder verborgen. Stelle sicher, dass niemand mitliest.") },
                confirmButton = { TextButton(onClick = { revealRequest = null; vm.reveal(cid) }, Modifier.heightIn(min = TouchTarget)) { Text("Anzeigen") } },
                dismissButton = { TextButton(onClick = { revealRequest = null }, Modifier.heightIn(min = TouchTarget)) { Text("Abbrechen") } },
            )
        }
        deleteController?.let { cid ->
            val name = p.controllers.firstOrNull { it.id == cid }?.label ?: "Controller"
            AlertDialog(
                onDismissRequest = { deleteController = null },
                title = { Text("Controller löschen?") },
                text = { Text("„$name“ inklusive gespeichertem Passwort wird dauerhaft gelöscht.") },
                confirmButton = { TextButton(onClick = { deleteController = null; vm.deleteController(cid) }, Modifier.heightIn(min = TouchTarget)) { Text("Löschen") } },
                dismissButton = { TextButton(onClick = { deleteController = null }, Modifier.heightIn(min = TouchTarget)) { Text("Abbrechen") } },
            )
        }
        viewPhoto?.let { pid ->
            val photo = p.photos.firstOrNull { it.id == pid }
            if (photo == null) viewPhoto = null else PhotoDialog(photo, onDismiss = { viewPhoto = null }, onDelete = { viewPhoto = null; vm.removePhoto(pid) })
        }
    }
    if (confirmDeletePlant) {
        AlertDialog(
            onDismissRequest = { confirmDeletePlant = false },
            title = { Text("Anlage löschen?") },
            text = { Text("„${plant?.name}“ mit allen Controllern, Passwörtern und Fotos wird dauerhaft gelöscht.") },
            confirmButton = { TextButton(onClick = { confirmDeletePlant = false; vm.deletePlant() }, Modifier.heightIn(min = TouchTarget)) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDeletePlant = false }, Modifier.heightIn(min = TouchTarget)) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun ControllerCard(
    c: Controller,
    revealed: String?,
    onReveal: () -> Unit,
    onHide: () -> Unit,
    onCopyUser: () -> Unit,
    onCopyAddress: () -> Unit,
    onCopyPassword: () -> Unit,
    onOpenBrowser: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    KCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(listOf(c.model, c.accessType.label).filter { it.isNotBlank() }.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit, Modifier.size(TouchTarget)) { Icon(Icons.Default.Edit, "Controller bearbeiten") }
            IconButton(onClick = onDelete, Modifier.size(TouchTarget)) { Icon(Icons.Default.Delete, "Controller löschen") }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        if (c.address.isNotBlank()) CopyRow("Adresse", c.address, onCopyAddress, "Adresse kopieren")
        if (c.address.isNotBlank() && c.accessType == AccessType.WEB) {
            TextButton(onClick = onOpenBrowser, Modifier.heightIn(min = TouchTarget)) { Text("Im Browser öffnen") }
        }
        if (c.username.isNotBlank()) CopyRow("Benutzer", c.username, onCopyUser, "Benutzername kopieren")
        if (c.hasPassword) {
            Row(Modifier.fillMaxWidth().heightIn(min = TouchTarget), verticalAlignment = Alignment.CenterVertically) {
                Text("Passwort", Modifier.weight(0.3f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(revealed ?: "••••••••", Modifier.weight(0.7f), style = MonoStyle,
                    color = if (revealed != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = if (revealed != null) onHide else onReveal, Modifier.size(TouchTarget)) {
                    Icon(if (revealed != null) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (revealed != null) "Passwort verbergen" else "Passwort anzeigen")
                }
                IconButton(onClick = onCopyPassword, Modifier.size(TouchTarget)) { Icon(Icons.Default.ContentCopy, "Passwort kopieren") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("  Kein Passwort hinterlegt", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (c.notes.isNotBlank()) Text(c.notes, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CopyRow(label: String, value: String, onCopy: () -> Unit, copyDescription: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = TouchTarget), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(0.3f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.7f), style = MonoStyle)
        IconButton(onClick = onCopy, Modifier.size(TouchTarget)) { Icon(Icons.Default.ContentCopy, copyDescription) }
    }
}

private fun openInBrowser(context: Context, address: String) {
    val url = if (address.contains("://")) address else "http://$address"
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun PhotoThumb(photo: PlantPhoto, sizeDp: Int, onClick: () -> Unit) {
    var bmp by remember(photo.filePath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photo.filePath, sizeDp) { bmp = decodeSampled(photo.filePath, sizeDp * 3) }
    Box(
        Modifier.size(sizeDp.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        bmp?.let { Image(it.asImageBitmap(), "Anlagenfoto", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
private fun PhotoDialog(photo: PlantPhoto, onDismiss: () -> Unit, onDelete: () -> Unit) {
    var bmp by remember(photo.filePath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photo.filePath) { bmp = decodeSampled(photo.filePath, 1600) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 720.dp),
        text = { bmp?.let { Image(it.asImageBitmap(), "Anlagenfoto", Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) } },
        confirmButton = { TextButton(onClick = onDismiss, Modifier.heightIn(min = TouchTarget)) { Text("Schließen") } },
        dismissButton = {
            TextButton(onClick = { if (confirm) onDelete() else confirm = true }, Modifier.heightIn(min = TouchTarget),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(if (confirm) "Wirklich löschen?" else "Löschen", fontWeight = FontWeight.Bold)
            }
        },
    )
}

private suspend fun decodeSampled(path: String, targetPx: Int): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val file = File(path)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

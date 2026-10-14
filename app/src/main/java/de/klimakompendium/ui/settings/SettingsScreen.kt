package de.klimakompendium.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.klimakompendium.BuildConfig
import de.klimakompendium.data.prefs.Settings
import de.klimakompendium.domain.convert.PressureReference
import de.klimakompendium.ui.components.BackTopBar
import de.klimakompendium.ui.components.FormColumn
import de.klimakompendium.ui.components.KCard
import de.klimakompendium.ui.components.SectionHeader
import de.klimakompendium.ui.theme.TouchTarget
import de.klimakompendium.ui.util.LocalAppContainer
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val prefs = LocalAppContainer.current.preferences
    val settings by prefs.settings.collectAsStateWithLifecycle(Settings())
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { BackTopBar("Einstellungen", onBack) }) { padding ->
        FormColumn(Modifier.padding(padding)) {
            SectionHeader("Sicherheit")
            KCard {
                Row(Modifier.fillMaxWidth().heightIn(min = TouchTarget), verticalAlignment = Alignment.CenterVertically) {
                    Text("Bildschirmfotos & App-Vorschau blockieren", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = settings.secureScreen, onCheckedChange = { v -> scope.launch { prefs.setSecureScreen(v) } })
                }
                Text(
                    "Verhindert, dass Passwörter in Screenshots, Bildschirmaufnahmen oder der Übersicht zuletzt verwendeter Apps auftauchen. " +
                        "Zum Erstellen von Screenshots für die Doku vorübergehend ausschalten.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Passwort-Anzeigedauer", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30, 60).forEach { s ->
                        FilterChip(
                            selected = settings.passwordVisibleSeconds == s,
                            onClick = { scope.launch { prefs.setPasswordVisibleSeconds(s) } },
                            label = { Text("$s s") }, modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
            SectionHeader("Druckanzeige")
            KCard {
                Text("Standard-Bezug für p‑T-Rechner", style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PressureReference.entries.forEach { r ->
                        FilterChip(
                            selected = settings.pressureReference == r,
                            onClick = { scope.launch { prefs.setPressureReference(r) } },
                            label = { Text(r.label) }, modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                Text("Manometer zeigen in der Regel Überdruck (bar ü).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionHeader("Datenschutz")
            KCard {
                Text(
                    "Alle Daten liegen ausschließlich auf diesem Gerät. Die App besitzt keine Internet-Berechtigung, kein Konto, keine Werbung und kein Tracking. " +
                        "Passwörter werden mit AES-256-GCM verschlüsselt; der Schlüssel liegt im Android Keystore dieses Geräts und ist nicht exportierbar. " +
                        "Es gibt kein Cloud-Backup – bei Gerätewechsel müssen Passwörter neu eingetragen werden.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            SectionHeader("Über")
            KCard {
                Text("KlimaKompendium ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Kältemittel-Sättigungsdaten: CoolProp. Richtwerte ersetzen keine Herstellerangaben.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

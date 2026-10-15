package de.klimakompendium.ui.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow

/**
 * Suchtext eines Eingabefelds.
 *
 * Wichtig: Der Text muss SYNCHRON im Snapshot-Zustand liegen. Läuft er stattdessen über einen asynchronen Flow
 * (z. B. `combine(...).flowOn(Default)`) und kommt erst verzögert zurück ins Textfeld, setzt Compose den Cursor zurück
 * und Zeichen landen an der falschen Stelle. Ergebnisse dürfen asynchron berechnet werden, der Text selbst nicht.
 */
class SearchState(initial: String = "") {
    var text: String by mutableStateOf(initial)

    /** Der Text als Flow für die (asynchrone) Ergebnisberechnung. */
    val flow: Flow<String> = snapshotFlow { text }
}

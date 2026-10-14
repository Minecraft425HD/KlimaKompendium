package de.klimakompendium.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.klimakompendium.domain.convert.PressureReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    val pressureReference: PressureReference = PressureReference.GAUGE,
    val lastRefrigerant: String = "R32",
    /** Bildschirmfotos/Recent-Apps-Vorschau blockieren (FLAG_SECURE). */
    val secureScreen: Boolean = true,
    /** Sekunden, nach denen ein angezeigtes Passwort wieder verborgen wird. */
    val passwordVisibleSeconds: Int = 15,
)

class UserPreferences(private val context: Context) {
    private object Keys {
        val pressureGauge = booleanPreferencesKey("pressure_gauge")
        val lastRefrigerant = stringPreferencesKey("last_refrigerant")
        val secureScreen = booleanPreferencesKey("secure_screen")
        val passwordSeconds = intPreferencesKey("password_visible_seconds")
        val seedVersion = intPreferencesKey("seed_version")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            pressureReference = if (p[Keys.pressureGauge] ?: true) PressureReference.GAUGE else PressureReference.ABSOLUTE,
            lastRefrigerant = p[Keys.lastRefrigerant] ?: d.lastRefrigerant,
            secureScreen = p[Keys.secureScreen] ?: d.secureScreen,
            passwordVisibleSeconds = p[Keys.passwordSeconds] ?: d.passwordVisibleSeconds,
        )
    }

    suspend fun setPressureReference(ref: PressureReference) =
        context.dataStore.edit { it[Keys.pressureGauge] = ref == PressureReference.GAUGE }

    suspend fun setLastRefrigerant(id: String) = context.dataStore.edit { it[Keys.lastRefrigerant] = id }
    suspend fun setSecureScreen(value: Boolean) = context.dataStore.edit { it[Keys.secureScreen] = value }
    suspend fun setPasswordVisibleSeconds(value: Int) = context.dataStore.edit { it[Keys.passwordSeconds] = value }

    suspend fun seedVersion(): Int = context.dataStore.data.first()[Keys.seedVersion] ?: 0
    suspend fun setSeedVersion(v: Int) = context.dataStore.edit { it[Keys.seedVersion] = v }
}

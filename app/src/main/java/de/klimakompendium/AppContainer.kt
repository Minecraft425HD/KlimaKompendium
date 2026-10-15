package de.klimakompendium

import android.content.Context
import de.klimakompendium.data.FilePhotoStore
import de.klimakompendium.data.db.AppDatabase
import de.klimakompendium.data.prefs.UserPreferences
import de.klimakompendium.data.repo.FaultCodeRepository
import de.klimakompendium.data.repo.PlantRepository
import de.klimakompendium.data.repo.RefrigerantRepository
import de.klimakompendium.data.repo.ServiceRepository
import de.klimakompendium.data.repo.TermRepository
import de.klimakompendium.data.security.KeystoreKeyProvider
import de.klimakompendium.data.security.SecretCipher
import de.klimakompendium.data.seed.SeedParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Einfache manuelle Dependency-Injection (kein Framework nötig für diese App-Größe). */
class AppContainer(
    context: Context,
    /** Austauschbar für Tests (Android Keystore ist unter Robolectric nicht verfügbar). */
    cipherFactory: () -> SecretCipher = { KeystoreKeyProvider.cipher() },
) {
    private val cipher by lazy(cipherFactory)
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val preferences = UserPreferences(appContext)

    val refrigerants: RefrigerantRepository by lazy {
        RefrigerantRepository(SeedParser.parseRefrigerants(readAsset("refrigerants.json")))
    }
    val faultCodes: FaultCodeRepository by lazy { FaultCodeRepository(database.referenceDao(), preferences) }
    /** Signal-/Fühlerkürzel (SIW, TAP1 …) – statisch aus assets, im Speicher. */
    val signals: List<de.klimakompendium.domain.model.SignalCode> by lazy { SeedParser.parseSignals(readAsset("signal_codes.json")) }
    val service: ServiceRepository by lazy { ServiceRepository(SeedParser.parseService(readAsset("service.json"))) }
    private val termsFile by lazy { SeedParser.parseTerms(readAsset("terms.json")) }
    val terms: TermRepository by lazy { TermRepository(database.termDao(), preferences, termsFile.groups.map { it.keys }) }
    val plants: PlantRepository by lazy {
        PlantRepository(database.plantDao(), cipher, FilePhotoStore(appContext))
    }

    /** Spielt Begriffe und Störungscodes beim ersten Start bzw. nach App-Updates ein. */
    fun seedAsync() {
        appScope.launch(Dispatchers.IO) {
            terms.seedIfNeeded(termsFile)
            faultCodes.seedIfNeeded(SeedParser.parseFaultCodes(readAsset("fault_codes.json")))
        }
    }

    private fun readAsset(name: String): String =
        appContext.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
}

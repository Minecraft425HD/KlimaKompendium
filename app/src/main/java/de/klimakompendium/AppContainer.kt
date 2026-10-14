package de.klimakompendium

import android.content.Context
import de.klimakompendium.data.FilePhotoStore
import de.klimakompendium.data.db.AppDatabase
import de.klimakompendium.data.prefs.UserPreferences
import de.klimakompendium.data.repo.PlantRepository
import de.klimakompendium.data.repo.RefrigerantRepository
import de.klimakompendium.data.repo.TermRepository
import de.klimakompendium.data.security.KeystoreKeyProvider
import de.klimakompendium.data.seed.SeedParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Einfache manuelle Dependency-Injection (kein Framework nötig für diese App-Größe). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val preferences = UserPreferences(appContext)

    val refrigerants: RefrigerantRepository by lazy {
        RefrigerantRepository(SeedParser.parseRefrigerants(readAsset("refrigerants.json")))
    }
    val terms: TermRepository by lazy { TermRepository(database.termDao(), preferences) }
    val plants: PlantRepository by lazy {
        PlantRepository(database.plantDao(), KeystoreKeyProvider.cipher(), FilePhotoStore(appContext))
    }

    /** Spielt die mitgelieferte Begriffsdatenbank beim ersten Start bzw. nach App-Updates ein. */
    fun seedAsync() {
        appScope.launch(Dispatchers.IO) { terms.seedIfNeeded(SeedParser.parseTerms(readAsset("terms.json"))) }
    }

    private fun readAsset(name: String): String =
        appContext.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
}

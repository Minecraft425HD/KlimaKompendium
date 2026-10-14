package de.klimakompendium.data.repo

import de.klimakompendium.data.db.FaultCodeEntity
import de.klimakompendium.data.db.ReferenceDao
import de.klimakompendium.data.prefs.UserPreferences
import de.klimakompendium.data.seed.FaultCodesFile
import de.klimakompendium.domain.model.FaultCode
import de.klimakompendium.domain.model.Manufacturer
import de.klimakompendium.domain.model.ServiceData
import de.klimakompendium.domain.search.TextNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private fun FaultCodeEntity.toDomain() = FaultCode(id, manufacturer, controllerModel, code, title, cause, remedy, isCustom)

class FaultCodeRepository(private val dao: ReferenceDao, private val prefs: UserPreferences) {
    val codes: Flow<List<FaultCode>> = dao.observeAllFaultCodes().map { list -> list.map { it.toDomain() } }

    fun observe(id: Long): Flow<FaultCode?> = dao.observeFaultCode(id).map { it?.toDomain() }

    suspend fun saveCustom(code: FaultCode): Long = dao.upsertFaultCode(
        FaultCodeEntity(
            id = code.id, manufacturer = code.manufacturer.trim(), controllerModel = code.series.trim(), code = code.code.trim(),
            title = code.title.trim(), cause = code.cause.trim(), remedy = code.remedy.trim(), isCustom = true,
        ),
    )

    suspend fun deleteCustom(id: Long) = dao.deleteCustomFaultCode(id)

    /** Spielt die mitgelieferten Codes ein, wenn sich die Datenversion erhöht hat. Eigene Codes bleiben unberührt. */
    suspend fun seedIfNeeded(file: FaultCodesFile): Int {
        // Zusätzlich zur Version prüfen, ob die Tabelle wirklich gefüllt ist (z. B. nach einer geleerten Datenbank).
        if (prefs.faultSeedVersion() >= file.version && dao.seededFaultCodeCount() > 0) return 0
        val entities = file.sets.flatMap { set ->
            set.codes.map { c ->
                FaultCodeEntity(
                    manufacturer = set.manufacturer, controllerModel = set.series, code = c.code, title = c.title,
                    cause = c.original, remedy = if (c.reset.isBlank()) "" else "Rücksetzen: ${c.reset}", isCustom = false,
                )
            }
        }
        dao.deleteSeededFaultCodes()
        dao.insertFaultCodes(entities)
        prefs.setFaultSeedVersion(file.version)
        return entities.size
    }
}

/** Hersteller-Profile, Leitfäden und Controller-Vorlagen aus assets/service.json (im Speicher). */
class ServiceRepository(val data: ServiceData) {
    /** Findet den Hersteller zu einem frei getippten Namen ("Aermec S.p.A.", "aermec srl" …). */
    fun manufacturerFor(name: String): Manufacturer? {
        val n = TextNormalizer.compact(name)
        if (n.isEmpty()) return null
        return data.manufacturers.firstOrNull { n.contains(TextNormalizer.compact(it.id)) || n.contains(TextNormalizer.compact(it.name)) }
    }

    val guideCategories: List<String> get() = data.guides.map { it.category }.filter { it.isNotBlank() }.distinct()
}

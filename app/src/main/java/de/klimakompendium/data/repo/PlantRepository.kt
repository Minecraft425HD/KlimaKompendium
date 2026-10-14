package de.klimakompendium.data.repo

import de.klimakompendium.data.db.ControllerEntity
import de.klimakompendium.data.db.PlantDao
import de.klimakompendium.data.db.PlantEntity
import de.klimakompendium.data.db.PlantPhotoEntity
import de.klimakompendium.data.security.SecretCipher
import de.klimakompendium.domain.model.Controller
import de.klimakompendium.domain.model.PasswordChange
import de.klimakompendium.domain.model.Plant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Legt Fotodateien ab und löscht sie wieder (app-intern). Abstrahiert, damit Tests ohne Dateisystem laufen. */
interface PhotoStore {
    /** Kopiert die Quelle in den privaten Speicher und liefert den Zielpfad. */
    suspend fun import(source: String, plantId: Long): String?
    fun delete(path: String)
}

class PlantRepository(
    private val dao: PlantDao,
    private val cipher: SecretCipher,
    private val photoStore: PhotoStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val plants: Flow<List<Plant>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observe(id: Long): Flow<Plant?> = dao.observeById(id).map { it?.toDomain() }

    suspend fun savePlant(plant: Plant): Long {
        val now = clock()
        return if (plant.id == 0L) {
            dao.insert(plant.toEntity(createdAt = now, updatedAt = now))
        } else {
            val old = dao.getById(plant.id) ?: error("Anlage ${plant.id} nicht gefunden")
            dao.update(plant.toEntity(createdAt = old.createdAt, updatedAt = now))
            plant.id
        }
    }

    suspend fun deletePlant(id: Long) {
        dao.photosOf(id).forEach { photoStore.delete(it.filePath) }
        dao.delete(id)
    }

    suspend fun markOpened(id: Long) = dao.markOpened(id, clock())

    /**
     * Speichert einen Controller. Das Passwort wird hier – und nur hier – verschlüsselt;
     * in die Datenbank gelangt ausschließlich das Chiffrat.
     */
    suspend fun saveController(controller: Controller, password: PasswordChange): Long {
        val existing = if (controller.id != 0L) dao.getController(controller.id) else null
        val enc: String? = when (password) {
            PasswordChange.Keep -> existing?.passwordEnc
            PasswordChange.Clear -> null
            is PasswordChange.Set -> password.plain.takeIf { it.isNotEmpty() }?.let { cipher.encrypt(it) }
        }
        val entity = ControllerEntity(
            id = controller.id, plantId = controller.plantId, label = controller.label, model = controller.model,
            accessType = controller.accessType.name, address = controller.address, username = controller.username,
            passwordEnc = enc, notes = controller.notes, sortOrder = existing?.sortOrder ?: 0,
        )
        return if (existing == null) dao.insertController(entity.copy(id = 0)) else { dao.updateController(entity); entity.id }
    }

    suspend fun deleteController(id: Long) = dao.deleteController(id)

    /** Entschlüsselt das Passwort erst auf ausdrückliche Anforderung (Anzeigen/Kopieren). */
    suspend fun revealPassword(controllerId: Long): String? {
        val enc = dao.getController(controllerId)?.passwordEnc ?: return null
        return cipher.decrypt(enc)
    }

    suspend fun addPhoto(plantId: Long, source: String): Boolean {
        val path = photoStore.import(source, plantId) ?: return false
        dao.insertPhoto(PlantPhotoEntity(plantId = plantId, filePath = path, createdAt = clock()))
        return true
    }

    suspend fun removePhoto(photoId: Long) {
        val photo = dao.getPhoto(photoId) ?: return
        photoStore.delete(photo.filePath)
        dao.deletePhoto(photo)
    }

    private fun Plant.toEntity(createdAt: Long, updatedAt: Long) = PlantEntity(
        id = id, name = name.trim(), customer = customer.trim(), location = location.trim(), buildYear = buildYear,
        refrigerant = refrigerant.trim(), manufacturer = manufacturer.trim(), serialNumber = serialNumber.trim(),
        controllerType = controllerType.trim(), notes = notes, createdAt = createdAt, updatedAt = updatedAt,
        lastOpenedAt = lastOpenedAt,
    )
}

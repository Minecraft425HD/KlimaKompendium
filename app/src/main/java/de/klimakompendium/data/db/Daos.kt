package de.klimakompendium.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TermDao {
    @Query("SELECT * FROM terms ORDER BY abbreviation COLLATE NOCASE")
    fun observeAll(): Flow<List<TermEntity>>

    @Query("SELECT * FROM terms WHERE id = :id")
    fun observeById(id: Long): Flow<TermEntity?>

    @Query("SELECT * FROM terms WHERE key = :key LIMIT 1")
    suspend fun getByKey(key: String): TermEntity?

    @Query("SELECT COUNT(*) FROM terms")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(term: TermEntity): Long

    @Update
    suspend fun update(term: TermEntity)

    @Query("UPDATE terms SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    /** Nur eigene Begriffe sind löschbar; mitgelieferte bleiben erhalten. */
    @Query("DELETE FROM terms WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustom(id: Long)
}

@Dao
interface PlantDao {
    @Transaction
    @Query("SELECT * FROM plants ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<PlantWithDetails>>

    @Transaction
    @Query("SELECT * FROM plants WHERE id = :id")
    fun observeById(id: Long): Flow<PlantWithDetails?>

    @Insert
    suspend fun insert(plant: PlantEntity): Long

    @Update
    suspend fun update(plant: PlantEntity)

    @Query("SELECT * FROM plants WHERE id = :id")
    suspend fun getById(id: Long): PlantEntity?

    @Query("UPDATE plants SET lastOpenedAt = :time WHERE id = :id")
    suspend fun markOpened(id: Long, time: Long)

    @Query("DELETE FROM plants WHERE id = :id")
    suspend fun delete(id: Long)

    // Controller
    @Insert
    suspend fun insertController(controller: ControllerEntity): Long

    @Update
    suspend fun updateController(controller: ControllerEntity)

    @Query("SELECT * FROM controllers WHERE id = :id")
    suspend fun getController(id: Long): ControllerEntity?

    @Query("DELETE FROM controllers WHERE id = :id")
    suspend fun deleteController(id: Long)

    // Fotos
    @Insert
    suspend fun insertPhoto(photo: PlantPhotoEntity): Long

    @Query("SELECT * FROM plant_photos WHERE id = :id")
    suspend fun getPhoto(id: Long): PlantPhotoEntity?

    @Query("SELECT * FROM plant_photos WHERE plantId = :plantId")
    suspend fun photosOf(plantId: Long): List<PlantPhotoEntity>

    @Delete
    suspend fun deletePhoto(photo: PlantPhotoEntity)
}

/** Zugriff auf die für spätere Funktionen vorbereiteten Tabellen. */
@Dao
interface ReferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFaultCode(code: FaultCodeEntity): Long

    @Query("SELECT * FROM fault_codes WHERE manufacturer = :manufacturer ORDER BY code")
    fun observeFaultCodes(manufacturer: String): Flow<List<FaultCodeEntity>>

    @Insert
    suspend fun insertMaintenance(item: MaintenanceIntervalEntity): Long

    @Query("SELECT * FROM maintenance_intervals WHERE plantId = :plantId ORDER BY title")
    fun observeMaintenance(plantId: Long): Flow<List<MaintenanceIntervalEntity>>

    @Insert
    suspend fun insertChecklist(list: ChecklistEntity): Long

    @Insert
    suspend fun insertChecklistItems(items: List<ChecklistItemEntity>)

    @Query("SELECT * FROM checklists ORDER BY title")
    fun observeChecklists(): Flow<List<ChecklistEntity>>

    @Query("SELECT * FROM checklist_items WHERE checklistId = :checklistId ORDER BY position")
    fun observeChecklistItems(checklistId: Long): Flow<List<ChecklistItemEntity>>
}

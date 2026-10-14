package de.klimakompendium.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "terms", indices = [Index(value = ["key"], unique = true)])
data class TermEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stabiler Schlüssel: bei mitgelieferten Begriffen aus terms.json, bei eigenen "custom-<uuid>". */
    val key: String,
    val abbreviation: String,
    val language: String,
    val fullTerm: String,
    val termDe: String,
    val termEn: String,
    val termIt: String,
    val kind: String,
    val category: String,
    val unit: String,
    val meaning: String,
    val context: String,
    val typicalRange: String,
    val isFavorite: Boolean = false,
    val isCustom: Boolean = false,
)

@Entity(tableName = "plants")
data class PlantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val customer: String,
    val location: String,
    val buildYear: Int?,
    val refrigerant: String,
    val manufacturer: String,
    val serialNumber: String,
    val controllerType: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long? = null,
)

@Entity(
    tableName = "plant_photos",
    foreignKeys = [ForeignKey(PlantEntity::class, ["id"], ["plantId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("plantId")],
)
data class PlantPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long,
    /** Absoluter Pfad in den app-internen Dateien (nicht öffentlich zugänglich). */
    val filePath: String,
    val createdAt: Long,
)

@Entity(
    tableName = "controllers",
    foreignKeys = [ForeignKey(PlantEntity::class, ["id"], ["plantId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("plantId")],
)
data class ControllerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long,
    val label: String,
    val model: String,
    val accessType: String,
    /** IP-Adresse, Hostname, Bus-Adresse o. Ä. */
    val address: String,
    val username: String,
    /** AES-256-GCM-Chiffrat (siehe SecretCipher). Niemals Klartext! */
    @ColumnInfo(name = "passwordEnc") val passwordEnc: String?,
    val notes: String,
    val sortOrder: Int = 0,
)

data class PlantWithDetails(
    @Embedded val plant: PlantEntity,
    @Relation(parentColumn = "id", entityColumn = "plantId") val controllers: List<ControllerEntity>,
    @Relation(parentColumn = "id", entityColumn = "plantId") val photos: List<PlantPhotoEntity>,
)

// ---------------------------------------------------------------------------------------------
// Vorbereitete Datenstrukturen für spätere Erweiterungen (Störungscodes, Wartung, Checklisten).
// Tabellen und DAO existieren bereits; eine Oberfläche dafür ist bewusst noch nicht Teil von v1.
// ---------------------------------------------------------------------------------------------

@Entity(tableName = "fault_codes", indices = [Index("manufacturer", "controllerModel", "code", unique = true)])
data class FaultCodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val manufacturer: String,
    val controllerModel: String,
    val code: String,
    val title: String,
    val cause: String,
    val remedy: String,
    val isCustom: Boolean = true,
)

@Entity(
    tableName = "maintenance_intervals",
    foreignKeys = [ForeignKey(PlantEntity::class, ["id"], ["plantId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("plantId")],
)
data class MaintenanceIntervalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long,
    val title: String,
    val intervalMonths: Int,
    val lastDoneAt: Long?,
    val notes: String,
)

@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String,
    val isCustom: Boolean = true,
)

@Entity(
    tableName = "checklist_items",
    foreignKeys = [ForeignKey(ChecklistEntity::class, ["id"], ["checklistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("checklistId")],
)
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val checklistId: Long,
    val position: Int,
    val text: String,
    /** Optionale Einheit, wenn ein Messwert erfasst werden soll (z. B. "bar", "K"). */
    val valueUnit: String = "",
)

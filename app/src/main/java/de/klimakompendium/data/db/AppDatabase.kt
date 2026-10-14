package de.klimakompendium.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TermEntity::class, PlantEntity::class, PlantPhotoEntity::class, ControllerEntity::class,
        FaultCodeEntity::class, MaintenanceIntervalEntity::class, ChecklistEntity::class, ChecklistItemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun termDao(): TermDao
    abstract fun plantDao(): PlantDao
    abstract fun referenceDao(): ReferenceDao

    companion object {
        const val NAME = "klimakompendium.db"

        /**
         * Bewusst OHNE fallbackToDestructiveMigration: Nutzerdaten (Anlagen, Passwörter) dürfen bei einem
         * Schema-Update nie stillschweigend gelöscht werden. Neue Versionen brauchen eine echte Migration.
         */
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME).build()
    }
}

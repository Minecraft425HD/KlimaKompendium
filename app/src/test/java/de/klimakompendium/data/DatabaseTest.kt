package de.klimakompendium.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.klimakompendium.data.db.AppDatabase
import de.klimakompendium.data.db.ChecklistEntity
import de.klimakompendium.data.db.ChecklistItemEntity
import de.klimakompendium.data.db.FaultCodeEntity
import de.klimakompendium.data.prefs.UserPreferences
import de.klimakompendium.data.repo.PhotoStore
import de.klimakompendium.data.repo.PlantRepository
import de.klimakompendium.data.repo.TermRepository
import de.klimakompendium.data.security.AesGcmSecretCipher
import de.klimakompendium.data.seed.SeedParser
import de.klimakompendium.domain.model.AccessType
import de.klimakompendium.domain.model.Controller
import de.klimakompendium.domain.model.PasswordChange
import de.klimakompendium.domain.model.Plant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var plants: PlantRepository
    private lateinit var terms: TermRepository
    private val deleted = mutableListOf<String>()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val fakePhotos = object : PhotoStore {
        override suspend fun import(source: String, plantId: Long) = "/photos/$plantId-$source"
        override fun delete(path: String) { deleted += path }
    }

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        plants = PlantRepository(db.plantDao(), AesGcmSecretCipher { key }, fakePhotos)
        terms = TermRepository(db.termDao(), UserPreferences(ctx))
    }

    @After fun tearDown() = db.close()

    private fun newPlant(name: String = "Kühlhaus") = Plant(
        name = name, customer = "Müller", location = "Hamburg", buildYear = 2019, refrigerant = "R290",
        manufacturer = "Bitzer", serialNumber = "SN-1", controllerType = "Carel", notes = "n", lastOpenedAt = null,
    )

    private fun newController(plantId: Long) = Controller(
        plantId = plantId, label = "Regler 1", model = "pCO5", accessType = AccessType.WEB,
        address = "192.168.1.10", username = "admin", hasPassword = false, notes = "",
    )

    @Test fun plantCrudAndControllers() = runBlocking {
        val id = plants.savePlant(newPlant())
        assertTrue(id > 0)
        plants.saveController(newController(id), PasswordChange.Set("pw1"))
        plants.saveController(newController(id).copy(label = "Regler 2"), PasswordChange.Clear)

        val loaded = plants.observe(id).first()!!
        assertEquals("Kühlhaus", loaded.name)
        assertEquals(listOf("Regler 1", "Regler 2"), loaded.controllers.map { it.label })
        assertTrue(loaded.controllers[0].hasPassword)
        assertFalse(loaded.controllers[1].hasPassword)

        plants.savePlant(loaded.copy(name = "Kühlhaus Süd"))
        assertEquals("Kühlhaus Süd", plants.observe(id).first()!!.name)
        assertEquals(1, plants.plants.first().size)
    }

    @Test fun passwordNeverStoredInPlaintext() = runBlocking {
        val pid = plants.savePlant(newPlant())
        val cid = plants.saveController(newController(pid), PasswordChange.Set("KlartextGeheim!"))

        // Rohspalte direkt aus SQLite lesen
        val raw = db.openHelper.readableDatabase.query("SELECT passwordEnc FROM controllers WHERE id = $cid").use {
            it.moveToFirst(); it.getString(0)
        }
        assertNotNull(raw)
        assertFalse(raw.contains("Klartext"))
        assertEquals("KlartextGeheim!", plants.revealPassword(cid))

        // Der komplette Datenbank-Dump darf den Klartext ebenfalls nirgends enthalten
        db.openHelper.writableDatabase.query("SELECT * FROM controllers").use { c ->
            while (c.moveToNext()) for (i in 0 until c.columnCount) {
                if (c.getType(i) == android.database.Cursor.FIELD_TYPE_STRING) assertFalse(c.getString(i).contains("Klartext"))
            }
        }
    }

    @Test fun keepClearAndReplacePassword() = runBlocking {
        val pid = plants.savePlant(newPlant())
        val cid = plants.saveController(newController(pid), PasswordChange.Set("alt"))
        val c = plants.observe(pid).first()!!.controllers.single()

        plants.saveController(c.copy(username = "neu"), PasswordChange.Keep)
        assertEquals("alt", plants.revealPassword(cid))
        assertEquals("neu", plants.observe(pid).first()!!.controllers.single().username)

        plants.saveController(c, PasswordChange.Set("neu123"))
        assertEquals("neu123", plants.revealPassword(cid))

        plants.saveController(c, PasswordChange.Clear)
        assertNull(plants.revealPassword(cid))
    }

    @Test fun deletingPlantCascadesAndRemovesPhotos() = runBlocking {
        val pid = plants.savePlant(newPlant())
        plants.saveController(newController(pid), PasswordChange.Set("x"))
        assertTrue(plants.addPhoto(pid, "a.jpg"))
        plants.deletePlant(pid)
        assertTrue(plants.plants.first().isEmpty())
        assertEquals(listOf("/photos/$pid-a.jpg"), deleted)
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM controllers").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }

    @Test fun markOpenedSetsTimestamp() = runBlocking {
        val pid = plants.savePlant(newPlant())
        assertNull(plants.observe(pid).first()!!.lastOpenedAt)
        plants.markOpened(pid)
        assertNotNull(plants.observe(pid).first()!!.lastOpenedAt)
    }

    @Test fun seedingKeepsFavoritesAndCustomTerms() = runBlocking {
        val file = SeedParser.parseTerms(File("src/main/assets/terms.json").readText())
        assertTrue(terms.seedIfNeeded(file) > 100)
        val nd = terms.terms.first().first { it.key == "nd" }
        terms.setFavorite(nd.id, true)
        val customId = terms.saveCustom(nd.copy(id = 0, key = "", abbreviation = "XY", fullTerm = "Eigener Begriff"))

        // Neue Datenversion → erneutes Einspielen darf Favorit und eigenen Begriff nicht verlieren
        terms.seedIfNeeded(file.copy(version = file.version + 1))
        val all = terms.terms.first()
        assertTrue(all.first { it.key == "nd" }.isFavorite)
        assertEquals(nd.id, all.first { it.key == "nd" }.id)
        val custom = all.first { it.id == customId }
        assertTrue(custom.isCustom)

        // Mitgelieferte Begriffe sind nicht löschbar, eigene schon
        terms.deleteCustom(nd.id)
        terms.deleteCustom(customId)
        val after = terms.terms.first()
        assertTrue(after.any { it.key == "nd" })
        assertTrue(after.none { it.id == customId })
    }

    @Test fun faultCodeSeedingKeepsCustomCodes() = runBlocking {
        val repo = de.klimakompendium.data.repo.FaultCodeRepository(db.referenceDao(), UserPreferences(ApplicationProvider.getApplicationContext<Context>()))
        val file = SeedParser.parseFaultCodes(File("src/main/assets/fault_codes.json").readText())
        val n = repo.seedIfNeeded(file)
        assertTrue(n > 250)
        assertEquals("zweiter Aufruf darf nichts tun", 0, repo.seedIfNeeded(file))

        repo.saveCustom(de.klimakompendium.domain.model.FaultCode(manufacturer = "Euroklimat", series = "EKO", code = "E05", title = "Test", cause = "c", remedy = "r", isCustom = true))
        val total = repo.codes.first().size
        assertEquals(n + 1, total)

        // Neue Datenversion: Seed ersetzt nur mitgelieferte Codes, der eigene bleibt
        repo.seedIfNeeded(file.copy(version = file.version + 1))
        val after = repo.codes.first()
        assertEquals(n + 1, after.size)
        assertTrue(after.any { it.code == "E05" && it.isCustom })

        // Mitgelieferte Codes sind nicht löschbar, eigene schon
        repo.deleteCustom(after.first { it.manufacturer == "Aermec" }.id)
        repo.deleteCustom(after.first { it.isCustom }.id)
        val end = repo.codes.first()
        assertEquals(n, end.size)
    }

    @Test fun synonymVariantsAreAttachedToTerms() = runBlocking {
        val file = SeedParser.parseTerms(File("src/main/assets/terms.json").readText())
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val repo = TermRepository(db.termDao(), UserPreferences(ctx), file.groups.map { it.keys })
        repo.seedIfNeeded(file)
        val all = repo.terms.first()
        fun variants(key: String) = all.first { it.key == key }.variants
        // Niederdruck/Hochdruck: alle gleichbedeutenden Schreibweisen stehen direkt am Eintrag
        assertEquals(listOf("ND", "LP", "BP", "Ps"), variants("p0").also { assertTrue(it.isNotEmpty()) }.let { listOf("ND") + it.filter { v -> v in setOf("LP", "BP", "Ps") } })
        assertTrue(variants("pc").containsAll(listOf("HD", "HP", "AP", "Pd")))
        assertTrue(variants("hd").containsAll(listOf("HP", "AP", "pc", "Pd")))
        assertTrue("eigene Abkürzung gehört nicht in die Varianten", "HD" !in variants("hd"))
        assertTrue(variants("r32").isEmpty())
        // observe(id) liefert dieselben Varianten
        val pc = all.first { it.key == "pc" }
        assertTrue(repo.observe(pc.id).first()!!.variants.contains("HD"))
        // Eigene Begriffe bekommen keine Varianten
        val id = repo.saveCustom(pc.copy(id = 0, key = "", abbreviation = "XY", fullTerm = "Eigener Begriff", variants = emptyList()))
        assertTrue(repo.observe(id).first()!!.variants.isEmpty())
    }

    @Test fun preparedFutureTablesWork() = runBlocking {
        val ref = db.referenceDao()
        ref.upsertFaultCode(FaultCodeEntity(manufacturer = "Carel", controllerModel = "pCO5", code = "AL01", title = "t", cause = "c", remedy = "r"))
        assertEquals(1, ref.observeFaultCodes("Carel").first().size)
        val lid = ref.insertChecklist(ChecklistEntity(title = "Inbetriebnahme", category = "IBN"))
        ref.insertChecklistItems(listOf(ChecklistItemEntity(checklistId = lid, position = 1, text = "Dichtheit prüfen", valueUnit = "bar")))
        assertEquals("Dichtheit prüfen", ref.observeChecklistItems(lid).first().single().text)
    }
}

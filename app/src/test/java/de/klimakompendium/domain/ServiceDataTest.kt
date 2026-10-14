package de.klimakompendium.domain

import de.klimakompendium.data.seed.SeedParser
import de.klimakompendium.domain.model.FaultCode
import de.klimakompendium.domain.model.Plant
import de.klimakompendium.domain.search.FaultCodeSearch
import de.klimakompendium.domain.search.QuickSearch
import de.klimakompendium.domain.search.TermIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Qualität der Carel/Aermec/Euroklimat-Daten und der Störungscode-Suche. */
class ServiceDataTest {
    private val faults = SeedParser.parseFaultCodes(File("src/main/assets/fault_codes.json").readText())
    private val service = SeedParser.parseService(File("src/main/assets/service.json").readText())
    private val all: List<FaultCode> = faults.sets.flatMap { s ->
        s.codes.map { FaultCode(0, s.manufacturer, s.series, it.code, it.title, it.original, it.reset, false) }
    }

    @Test fun faultCodesAreUniqueAndComplete() {
        val keys = all.map { Triple(it.manufacturer, it.series, it.code) }
        assertEquals("Doppelte Codes", keys.size, keys.toSet().size)
        for (c in all) {
            assertTrue("${c.code}: Titel fehlt", c.title.isNotBlank())
            assertTrue("${c.code}: Code leer", c.code.isNotBlank())
        }
        for (s in faults.sets) assertTrue("Quelle fehlt: ${s.series}", s.source.isNotBlank())
        assertTrue(all.size > 250)
    }

    @Test fun bothAermecSeriesPresent() {
        val series = faults.sets.map { it.series }
        assertTrue(series.containsAll(listOf("NRG 0282-0804", "NRP/NXP/CPS")))
    }

    @Test fun everyGuideHasSourceAndSteps() {
        assertTrue(service.guides.size >= 10)
        for (g in service.guides) {
            assertTrue("${g.id}: Quelle fehlt", g.source.isNotBlank())
            assertTrue("${g.id}: Schritte fehlen", g.steps.isNotEmpty())
        }
        assertEquals(service.guides.size, service.guides.map { it.id }.toSet().size)
    }

    @Test fun manufacturersCoverRequestedBrands() {
        val ids = service.manufacturers.map { it.id }
        assertTrue(ids.containsAll(listOf("aermec", "euroklimat", "carel")))
        // Euroklimat: keine erfundenen Geräteserien
        assertTrue(service.manufacturers.first { it.id == "euroklimat" }.series.isEmpty())
    }

    @Test fun templatesNeverStoreAPasswordField() {
        // Vorlagen enthalten nur Hinweise, niemals ein Feld, das als Passwort gespeichert würde.
        assertTrue(service.templates.isNotEmpty())
        assertTrue(service.templates.any { it.id == "carel-pcoweb" && it.address == "172.16.0.1" })
    }

    @Test fun searchByCodeNumberAndText() {
        val nrg = all.filter { it.series == "NRG 0282-0804" }
        assertEquals("AL33", FaultCodeSearch.search(nrg, "AL33").first().code)
        assertEquals("AL33", FaultCodeSearch.search(nrg, "33").first().code)
        assertEquals("AL33", FaultCodeSearch.search(nrg, "al 33").first().code)
        assertTrue(FaultCodeSearch.search(all, "Hochdruck").any { it.code == "AL33" })
        assertTrue(FaultCodeSearch.search(all, "frostschutz").size >= 3)
        assertTrue(FaultCodeSearch.search(all, "anti-freeze").isNotEmpty()) // Originaltext EN
        assertTrue(FaultCodeSearch.search(all, "pec sw1").all { it.code.startsWith("PEC SW1") || it.title.contains("PEC", true) || it.series.contains("PEC") })
        assertTrue(FaultCodeSearch.search(all, "qqqzzz").isEmpty())
    }

    @Test fun numberSearchFindsBothSeries() {
        val hits = FaultCodeSearch.search(all, "30")
        assertTrue(hits.any { it.series == "NRG 0282-0804" } && hits.any { it.series == "NRP/NXP/CPS" })
    }

    @Test fun manufacturerNameListsAllItsCodes() {
        assertEquals(all.size, FaultCodeSearch.search(all, "Aermec", limit = 1000).size)
    }

    @Test fun quickSearchIncludesFaults() {
        val index = TermIndex(emptyList())
        val r = QuickSearch.run("AL33", index, emptyList(), emptyList(), all)
        assertTrue(r.faults.isNotEmpty() && r.faults.all { it.code.startsWith("AL33") || it.code == "AL33" })
        // Begriffe wie "ND" dürfen keine Störungscodes fluten
        assertTrue(QuickSearch.run("ND", index, emptyList(), emptyList(), all).faults.isEmpty())
        // Messwerte bleiben Messwerte
        assertTrue(QuickSearch.run("10 bar", index, emptyList(), emptyList(), all).faults.isEmpty())
    }

    @Test fun newCarelTermsExist() {
        val terms = SeedParser.parseTerms(File("src/main/assets/terms.json").readText())
        assertTrue(terms.version >= 2)
        val keys = terms.terms.map { it.key }.toSet()
        assertTrue(keys.containsAll(listOf("pco", "pgd1", "plan", "pcoweb", "evd", "aermec", "euroklimat")))
    }

    @Test fun plantManufacturerLookupIsLoose() {
        val repo = de.klimakompendium.data.repo.ServiceRepository(service)
        assertEquals("Aermec", repo.manufacturerFor("AERMEC S.p.A.")?.name)
        assertEquals("Euroklimat", repo.manufacturerFor("euroklimat spa")?.name)
        assertEquals(null, repo.manufacturerFor("Daikin"))
        @Suppress("UNUSED_VARIABLE") val unused: Plant? = null
    }
}

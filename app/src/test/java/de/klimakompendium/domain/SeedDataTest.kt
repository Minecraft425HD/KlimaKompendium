package de.klimakompendium.domain

import de.klimakompendium.data.seed.SeedParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Qualitätssicherung für die mitgelieferten JSON-Daten – schützt vor Tippfehlern beim Erweitern. */
class SeedDataTest {
    private val terms = SeedParser.parseTerms(File("src/main/assets/terms.json").readText())
    private val refs = SeedParser.parseRefrigerants(File("src/main/assets/refrigerants.json").readText())

    @Test fun termKeysUnique() {
        val dup = terms.terms.groupBy { it.key }.filterValues { it.size > 1 }.keys
        assertTrue("Doppelte Schlüssel: $dup", dup.isEmpty())
    }

    @Test fun everyTermIsComplete() {
        for (t in terms.terms) {
            assertTrue("${t.key}: Abkürzung fehlt", t.abbr.isNotBlank())
            assertTrue("${t.key}: Bedeutung fehlt", t.meaning.isNotBlank())
            assertTrue("${t.key}: Deutsch fehlt", t.de.isNotBlank())
            assertTrue("${t.key}: Englisch fehlt", t.en.isNotBlank())
            assertTrue("${t.key}: Italienisch fehlt", t.it.isNotBlank())
            assertTrue("${t.key}: Sprache ungültig", t.lang in setOf("DE", "EN", "IT", "INT"))
            assertTrue("${t.key}: kind ungültig", t.kind in setOf("PRESSURE", "TEMPERATURE", "NONE"))
        }
    }

    @Test fun synonymGroupsAreValid() {
        val keys = terms.terms.map { it.key }.toSet()
        val members = terms.groups.flatMap { it.keys }
        assertTrue("Gruppen fehlen", terms.groups.isNotEmpty())
        for (g in terms.groups) {
            assertTrue("Gruppe mit weniger als 2 Mitgliedern: ${g.keys}", g.keys.size >= 2)
            for (k in g.keys) assertTrue("Unbekannter Schlüssel in Gruppe: $k", k in keys)
        }
        assertEquals("Ein Begriff darf nur in einer Gruppe stehen", members.size, members.toSet().size)
    }

    @Test fun requiredTermsFromSpecPresent() {
        val abbr = terms.terms.map { it.abbr.lowercase() }.toSet()
        for (a in listOf("nd", "hd", "sap", "sat", "rat", "lp", "hp", "eev", "vfd", "cop", "eer", "seer", "r32", "r410a", "r290", "r134a", "r1234ze")) {
            assertTrue("Pflichtbegriff fehlt: $a", a in abbr)
        }
    }

    @Test fun refrigerantTablesMonotonic() {
        for (r in refs.filter { it.hasPtData }) {
            val p = r.ptTable
            for (i in 1 until p.size) {
                assertTrue("${r.id}: Temperatur nicht aufsteigend", p[i].tempC > p[i - 1].tempC)
                assertTrue("${r.id}: Dampfdruck nicht steigend bei ${p[i].tempC}", p[i].pDewBar > p[i - 1].pDewBar)
                assertTrue("${r.id}: Siededruck nicht steigend bei ${p[i].tempC}", p[i].pBubbleBar > p[i - 1].pBubbleBar)
                assertTrue("${r.id}: Siededruck muss ≥ Taudruck sein", p[i].pBubbleBar >= p[i].pDewBar - 1e-9)
            }
        }
    }

    @Test fun refrigerantsIncludeSpecified() {
        val ids = refs.map { it.id }
        assertTrue(ids.containsAll(listOf("R32", "R410A", "R290", "R134a", "R1234ze")))
        assertEquals(ids.size, ids.toSet().size)
    }
}

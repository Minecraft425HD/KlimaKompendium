package de.klimakompendium.domain

import de.klimakompendium.data.seed.SeedParser
import de.klimakompendium.domain.calc.CalcContext
import de.klimakompendium.domain.calc.CalcOutcome
import de.klimakompendium.domain.calc.Measure
import de.klimakompendium.domain.calc.Quantities
import de.klimakompendium.domain.convert.PressureReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Referenz: Foto der Anzeige eines testo 570s (R410A, Druckanzeige Überdruck):
 * ND 7,69 bar · T verdampf 2,7 °C · T1 9,2 °C · Überhitzung 6,5 K
 * HD 19,06 bar · T kondensat 32,4 °C · T2 21,8 °C · Unterkühlung 10,6 K
 */
class TestoReferenceTest {
    private val r410a = SeedParser.parseRefrigerants(File("src/main/assets/refrigerants.json").readText()).first { it.id == "R410A" }

    private fun run(id: String, vararg v: Pair<Measure, Double>): List<de.klimakompendium.domain.calc.CalcLine> {
        val out = Quantities.byId(id)!!.compute(CalcContext(mapOf(*v), r410a, PressureReference.GAUGE))
        return (out as? CalcOutcome.Ok ?: error("Fehler: $out")).lines
    }

    private fun num(lines: List<de.klimakompendium.domain.calc.CalcLine>, label: String) =
        lines.first { it.label.startsWith(label) }.value.replace(',', '.').toDouble()

    @Test fun appReproducesTestoDisplayValues() {
        val sh = run("superheat", Measure.SUCTION_P to 7.69, Measure.SUCTION_T to 9.2)
        assertEquals("T verdampf", 2.7, num(sh, "Verdampfungstemperatur"), 0.1)
        assertEquals("Überhitzung", 6.5, num(sh, "Überhitzung"), 0.15)

        val sc = run("subcooling", Measure.DISCHARGE_P to 19.06, Measure.LIQUID_T to 21.8)
        assertEquals("T kondensat", 32.4, num(sc, "Verflüssigungstemperatur"), 0.1)
        assertEquals("Unterkühlung", 10.6, num(sc, "Unterkühlung"), 0.15)

        assertEquals(2.7, num(run("t0", Measure.SUCTION_P to 7.69), "Verdampfungstemperatur"), 0.1)
        assertEquals(32.4, num(run("tc", Measure.DISCHARGE_P to 19.06), "Verflüssigungstemperatur"), 0.1)
    }

    @Test fun resultsCarryTheDeviceLabels() {
        val sh = run("superheat", Measure.SUCTION_P to 7.69, Measure.SUCTION_T to 9.2)
        assertTrue(sh.any { it.note.contains("T verdampf") })
        assertTrue(sh.any { it.note.contains("testo: „Überhitzung“") })
        val sc = run("subcooling", Measure.DISCHARGE_P to 19.06, Measure.LIQUID_T to 21.8)
        assertTrue(sc.any { it.note.contains("T kondensat") })
        assertTrue(sc.any { it.note.contains("testo: „Unterkühlung“") })
    }

    @Test fun measuresAndQuantitiesShowDeviceNames() {
        assertEquals("Sauggastemperatur · testo: T1", Measure.SUCTION_T.display)
        assertEquals("Flüssigkeitstemperatur · testo: T2", Measure.LIQUID_T.display)
        assertEquals("Saugdruck (ND)", Measure.SUCTION_P.display) // ND steht schon im Namen
        assertTrue(Quantities.byId("t0")!!.device.contains("T verdampf"))
        assertTrue(Quantities.byId("tc")!!.device.contains("T kondensat"))
        assertTrue(Quantities.byId("superheat")!!.device.contains("T1"))
        assertTrue(Quantities.byId("subcooling")!!.device.contains("T2"))
    }

    @Test fun testoTermsAndGuideExist() {
        val terms = SeedParser.parseTerms(File("src/main/assets/terms.json").readText())
        val abbr = terms.terms.map { it.abbr }
        assertTrue(abbr.containsAll(listOf("T verdampf", "T kondensat", "T1", "T2")))
        // T verdampf steht in der Variantengruppe von t0 (ND/HD-ähnlich gruppiert)
        assertTrue(terms.groups.any { "tverdampf" in it.keys && "t0" in it.keys })
        assertTrue(terms.groups.any { "tkondensat" in it.keys && "tc" in it.keys })
        val guides = SeedParser.parseService(File("src/main/assets/service.json").readText()).guides
        assertTrue(guides.any { it.id == "testo-570s" && it.source.isNotBlank() })
    }
}

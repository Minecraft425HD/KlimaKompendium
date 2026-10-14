package de.klimakompendium.domain

import de.klimakompendium.data.seed.SeedParser
import de.klimakompendium.domain.calc.CalcContext
import de.klimakompendium.domain.calc.CalcOutcome
import de.klimakompendium.domain.calc.Measure
import de.klimakompendium.domain.calc.Quantities
import de.klimakompendium.domain.calc.QuantitySearch
import de.klimakompendium.domain.convert.PressureReference
import de.klimakompendium.domain.hvac.Saturation
import de.klimakompendium.domain.model.Refrigerant
import de.klimakompendium.domain.model.SaturationPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Prüft jede Berechnung gegen von Hand nachgerechnete Werte. */
class QuantitiesTest {
    private val refs: List<Refrigerant> = SeedParser.parseRefrigerants(File("src/main/assets/refrigerants.json").readText())
    private fun ref(id: String) = refs.first { it.id == id }

    private fun run(id: String, vararg v: Pair<Measure, Double>, r: Refrigerant? = null, p: PressureReference = PressureReference.ABSOLUTE): CalcOutcome =
        Quantities.byId(id)!!.compute(CalcContext(mapOf(*v), r, p))

    private fun ok(o: CalcOutcome) = (o as? CalcOutcome.Ok ?: error("Fehler: $o")).lines
    /** Wert der Zeile, deren Label mit [label] beginnt, als Zahl (deutsches Komma). */
    private fun value(o: CalcOutcome, label: String) = ok(o).first { it.label.startsWith(label) }.value.replace(".", "").replace(',', '.').toDouble()

    @Test fun everyQuantityIsWellFormed() {
        val ids = Quantities.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (q in Quantities.all) {
            assertTrue("${q.id}: Pflichtwerte fehlen", q.needs.isNotEmpty())
            assertTrue("${q.id}: Erklärung fehlt", q.summary.isNotBlank() && q.interpretation.isNotBlank() && q.formula.isNotBlank())
            assertEquals("${q.id}: doppelte Pflichtwerte", q.needs.size, q.needs.toSet().size)
        }
        assertTrue(Quantities.all.size >= 30)
    }

    @Test fun requestedQuantitiesExist() {
        for (id in listOf("superheat", "subcooling", "discharge", "t0", "tc", "pi")) assertTrue(id, Quantities.byId(id) != null)
    }

    @Test fun superheatAndSubcooling() {
        val r = ref("R410A")
        val pAbs5 = Saturation.pressureAbsBar(r, 5.0, SaturationPhase.DEW)!!
        val sh = run("superheat", Measure.SUCTION_P to pAbs5, Measure.SUCTION_T to 12.0, r = r)
        assertEquals(7.0, value(sh, "Überhitzung"), 0.1)
        val pBub40 = Saturation.pressureAbsBar(r, 40.0, SaturationPhase.BUBBLE)!!
        val sc = run("subcooling", Measure.DISCHARGE_P to pBub40, Measure.LIQUID_T to 35.0, r = r)
        assertEquals(5.0, value(sc, "Unterkühlung"), 0.1)
    }

    @Test fun gaugeReferenceIsConverted() {
        val r = ref("R32")
        val pAbs = Saturation.pressureAbsBar(r, 0.0, SaturationPhase.DEW)!!
        val o = run("t0", Measure.SUCTION_P to (pAbs - 1.01325), r = r, p = PressureReference.GAUGE)
        assertEquals(0.0, value(o, "Verdampfungstemperatur"), 0.05)
    }

    @Test fun dischargeSuperheat() {
        val r = ref("R32")
        val p = Saturation.pressureAbsBar(r, 45.0, SaturationPhase.DEW)!!
        val o = run("discharge", Measure.DISCHARGE_P to p, Measure.DISCHARGE_T to 95.0, r = r)
        assertEquals(50.0, value(o, "Heißgas-Überhitzung"), 0.1)
        // Druckgas kälter als Sättigung ist physikalisch unmöglich -> Fehlermeldung statt Zahl
        assertTrue(run("discharge", Measure.DISCHARGE_P to p, Measure.DISCHARGE_T to 30.0, r = r) is CalcOutcome.Error)
    }

    @Test fun compressionRatioUsesAbsolutePressures() {
        val o = run("pi", Measure.SUCTION_P to 5.0, Measure.DISCHARGE_P to 20.0, p = PressureReference.GAUGE)
        assertEquals((20 + 1.01325) / (5 + 1.01325), value(o, "Verdichtungsverhältnis"), 0.01)
        assertTrue(run("pi", Measure.SUCTION_P to 20.0, Measure.DISCHARGE_P to 5.0) is CalcOutcome.Error)
    }

    @Test fun glideOfZeotropeVersusAzeotrope() {
        assertTrue(value(run("glide", Measure.SUCTION_P to 6.0, r = ref("R407C")), "Glide bei Saugdruck") > 4.0)
        assertTrue(value(run("glide", Measure.SUCTION_P to 6.0, r = ref("R32")), "Glide bei Saugdruck") < 0.05)
    }

    @Test fun outOfRangePressureGivesError() {
        assertTrue(run("t0", Measure.SUCTION_P to 500.0, r = ref("R32")) is CalcOutcome.Error)
        assertTrue(run("t0", Measure.SUCTION_P to 5.0, r = ref("R454B")) is CalcOutcome.Error) // keine Tabelle
        assertTrue(run("t0", Measure.SUCTION_P to 5.0, r = null) is CalcOutcome.Error)
    }

    @Test fun waterAndAirPower() {
        val w = run("water_q", Measure.WATER_FLOW to 10.0, Measure.WATER_IN_T to 12.0, Measure.WATER_OUT_T to 7.0)
        assertEquals(58.15, value(w, "Kälteleistung"), 0.01)
        val h = run("water_q", Measure.WATER_FLOW to 10.0, Measure.WATER_IN_T to 40.0, Measure.WATER_OUT_T to 45.0)
        assertEquals(58.15, value(h, "Heizleistung"), 0.01)
        val a = run("air_q", Measure.AIR_FLOW to 3000.0, Measure.AIR_IN_T to 27.0, Measure.AIR_OUT_T to 17.0)
        assertEquals(10.05, value(a, "Sensible Kälteleistung"), 0.01)
        assertEquals(10.0, value(run("water_v", Measure.CAPACITY to 58.15, Measure.WATER_IN_T to 12.0, Measure.WATER_OUT_T to 7.0), "Volumenstrom"), 0.01)
        assertEquals(3000.0, value(run("air_v", Measure.CAPACITY to 10.05, Measure.AIR_IN_T to 27.0, Measure.AIR_OUT_T to 17.0), "Luftvolumenstrom"), 1.0)
        assertTrue(run("water_q", Measure.WATER_FLOW to 10.0, Measure.WATER_IN_T to 7.0, Measure.WATER_OUT_T to 7.0) is CalcOutcome.Error)
    }

    @Test fun ductFlow() {
        val round = run("air_v_round", Measure.AIR_VELOCITY to 5.0, Measure.DUCT_DIAMETER to 400.0)
        assertEquals(2261.9, value(round, "Luftvolumenstrom"), 0.5)
        val rect = run("air_v_rect", Measure.AIR_VELOCITY to 4.0, Measure.DUCT_WIDTH to 500.0, Measure.DUCT_HEIGHT to 300.0)
        assertEquals(2160.0, value(rect, "Luftvolumenstrom"), 0.5)
    }

    @Test fun psychrometrics() {
        val o = run("psychro", Measure.AIR_T to 20.0, Measure.RH to 50.0)
        assertEquals(9.3, value(o, "Taupunkt"), 0.1)
        assertEquals(7.3, value(o, "Absolute Feuchte"), 0.1)
        assertEquals(38.6, value(o, "Enthalpie"), 0.3)
        assertEquals(20.0, value(run("psychro", Measure.AIR_T to 20.0, Measure.RH to 100.0), "Taupunkt"), 0.05)
        assertTrue(run("psychro", Measure.AIR_T to 20.0, Measure.RH to 120.0) is CalcOutcome.Error)
    }

    @Test fun heatRecoveryAndOutdoorShare() {
        assertEquals(63.0, value(run("wrg", Measure.OUTDOOR_T to -5.0, Measure.SUPPLY_T to 12.0, Measure.RETURN_T to 22.0), "Rückwärmzahl"), 0.1)
        assertTrue(run("wrg", Measure.OUTDOOR_T to 20.0, Measure.SUPPLY_T to 21.0, Measure.RETURN_T to 22.0) is CalcOutcome.Error)
        assertEquals(50.0, value(run("oa_share", Measure.OUTDOOR_T to 0.0, Measure.RETURN_T to 22.0, Measure.MIXED_T to 11.0), "Außenluftanteil"), 0.1)
    }

    @Test fun electrical() {
        val p = run("power3", Measure.VOLTAGE to 400.0, Measure.CURRENT to 10.0, Measure.COSPHI to 0.85)
        assertEquals(5.889, value(p, "Wirkleistung"), 0.01)
        assertEquals(6.928, value(p, "Scheinleistung"), 0.01)
        // ohne cos φ nur Scheinleistung
        val noPhi = ok(run("power3", Measure.VOLTAGE to 400.0, Measure.CURRENT to 10.0))
        assertEquals(1, noPhi.size)
        assertEquals(2.07, value(run("power1", Measure.VOLTAGE to 230.0, Measure.CURRENT to 10.0, Measure.COSPHI to 0.9), "Wirkleistung"), 0.01)
        assertEquals(0.85, value(run("cosphi", Measure.POWER_EL to 5.889, Measure.VOLTAGE to 400.0, Measure.CURRENT to 10.0), "cos φ"), 0.01)
        assertTrue(run("cosphi", Measure.POWER_EL to 20.0, Measure.VOLTAGE to 400.0, Measure.CURRENT to 10.0) is CalcOutcome.Error)
        assertTrue(run("power3", Measure.VOLTAGE to 400.0, Measure.CURRENT to 10.0, Measure.COSPHI to 1.4) is CalcOutcome.Error)
    }

    @Test fun efficiencyAndLoad() {
        assertEquals(3.5, value(run("cop", Measure.CAPACITY to 10.5, Measure.POWER_EL to 3.0), "COP"), 0.01)
        assertTrue(run("cop", Measure.CAPACITY to 10.5, Measure.POWER_EL to 0.0) is CalcOutcome.Error)
        assertEquals(5.0, value(run("cop_water", Measure.WATER_FLOW to 10.0, Measure.WATER_IN_T to 12.0, Measure.WATER_OUT_T to 7.0, Measure.POWER_EL to 11.63), "EER"), 0.01)
        assertEquals(13.5, value(run("qc", Measure.CAPACITY to 10.0, Measure.POWER_EL to 3.5), "Verflüssigerleistung"), 0.01)
        assertEquals(80.0, value(run("load", Measure.CURRENT to 8.0, Measure.RATED_CURRENT to 10.0), "Auslastung"), 0.01)
    }

    @Test fun motorSlip() {
        val o = run("slip", Measure.FREQUENCY to 50.0, Measure.POLES to 4.0, Measure.SPEED to 1450.0)
        assertEquals(1500.0, value(o, "Synchrone Drehzahl"), 0.1)
        assertEquals(3.33, value(o, "Schlupf"), 0.01)
        assertTrue(run("slip", Measure.FREQUENCY to 50.0, Measure.POLES to 3.0, Measure.SPEED to 1450.0) is CalcOutcome.Error)
    }

    @Test fun availabilityShowsWhatIsComputable() {
        val have = setOf(Measure.SUCTION_P, Measure.SUCTION_T, Measure.DISCHARGE_P)
        val av = QuantitySearch.availability(have)
        val ready = av.filter { it.ready }.map { it.quantity.id }
        assertTrue(ready.containsAll(listOf("superheat", "t0", "tc", "pi", "dp")))
        assertTrue("subcooling" !in ready)
        val almost = av.first { it.quantity.id == "subcooling" }
        assertEquals(listOf(Measure.LIQUID_T), almost.missing) // es fehlt genau die Flüssigkeitstemperatur
        assertTrue(av.none { it.quantity.id == "slip" }) // nichts davon vorhanden -> nicht angeboten
    }

    @Test fun searchFindsByNameAndKeyword() {
        assertEquals("discharge", QuantitySearch.search("Heißgas").first().id)
        assertEquals("discharge", QuantitySearch.search("heissgas").first().id)
        assertEquals("superheat", QuantitySearch.search("Überhitzung").first().id)
        assertEquals("subcooling", QuantitySearch.search("unterkühlung").first().id)
        assertEquals("pi", QuantitySearch.search("Verdichtungsverhältnis").first().id)
        assertTrue(QuantitySearch.search("ND").isEmpty() || QuantitySearch.search("ND").size < 6)
        assertTrue(QuantitySearch.search("qqqqq").isEmpty())
    }

    @Test fun everyMeasureIsUsedByAtLeastOneQuantity() {
        for (m in Measure.entries) assertTrue("${m.name} wird nirgends gebraucht", Quantities.usedBy(m).isNotEmpty())
    }
}

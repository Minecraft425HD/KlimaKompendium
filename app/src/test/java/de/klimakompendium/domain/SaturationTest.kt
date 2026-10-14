package de.klimakompendium.domain

import de.klimakompendium.data.seed.SeedParser
import de.klimakompendium.domain.convert.PressureReference
import de.klimakompendium.domain.hvac.HvacCalculator
import de.klimakompendium.domain.hvac.RangeStatus
import de.klimakompendium.domain.hvac.Saturation
import de.klimakompendium.domain.model.SaturationPhase.BUBBLE
import de.klimakompendium.domain.model.SaturationPhase.DEW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SaturationTest {
    private val refs = SeedParser.parseRefrigerants(File("src/main/assets/refrigerants.json").readText())
    private fun ref(id: String) = refs.first { it.id == id }

    @Test fun tableValueReturnedExactly() {
        // R134a bei 0 °C: ca. 2,93 bar abs. (bekannter Referenzwert)
        assertEquals(2.928, Saturation.pressureAbsBar(ref("R134a"), 0.0, DEW)!!, 0.005)
        // R32 bei 0 °C: ca. 8,13 bar abs.
        assertEquals(8.131, Saturation.pressureAbsBar(ref("R32"), 0.0, DEW)!!, 0.005)
        // R410A bei 0 °C: Tau 7,98 / Siede 8,01 bar abs.
        assertEquals(7.981, Saturation.pressureAbsBar(ref("R410A"), 0.0, DEW)!!, 0.005)
        assertEquals(8.007, Saturation.pressureAbsBar(ref("R410A"), 0.0, BUBBLE)!!, 0.005)
    }

    @Test fun interpolationIsMonotonicAndCloseToTable() {
        val r = ref("R32")
        val p7 = Saturation.pressureAbsBar(r, 7.0, DEW)!!
        val p5 = Saturation.pressureAbsBar(r, 5.0, DEW)!!
        val p10 = Saturation.pressureAbsBar(r, 10.0, DEW)!!
        assertTrue(p5 < p7 && p7 < p10)
        // Referenz direkt aus CoolProp (nicht aus der Tabelle): R32 bei 7 °C = 10,115 bar abs.
        assertEquals(10.115, p7, 10.115 * 0.002)
    }

    @Test fun inverseIsConsistent() {
        for (r in refs.filter { it.hasPtData }) {
            for (t in listOf(-20.0, 3.3, 27.5, 41.0)) {
                if (t < r.minTempC!! || t > r.maxTempC!!) continue
                for (ph in listOf(DEW, BUBBLE)) {
                    val p = Saturation.pressureAbsBar(r, t, ph)!!
                    assertEquals("${r.id} $ph $t", t, Saturation.temperatureC(r, p, ph)!!, 0.02)
                }
            }
        }
    }

    @Test fun outsideTableIsNull() {
        assertNull(Saturation.pressureAbsBar(ref("R32"), 200.0, DEW))
        assertNull(Saturation.pressureAbsBar(ref("R32"), -100.0, DEW))
        assertNull(Saturation.temperatureC(ref("R32"), 500.0, DEW))
        assertNull(Saturation.temperatureC(ref("R32"), 0.0, DEW))
        assertNull(Saturation.pressureAbsBar(ref("R454B"), 0.0, DEW)) // keine Tabelle hinterlegt
    }

    @Test fun zeotropeDewAboveBubbleTemperature() {
        // R407C: bei gleichem Druck liegt die Taupunkttemperatur deutlich über der Siedetemperatur (Glide)
        val r = ref("R407C")
        val dew = Saturation.temperatureC(r, 6.0, DEW)!!
        val bub = Saturation.temperatureC(r, 6.0, BUBBLE)!!
        assertTrue("Glide erwartet > 4 K, war ${dew - bub}", dew - bub > 4.0)
    }

    @Test fun superheatCalculation() {
        val r = ref("R410A")
        // Saugdruck so wählen, dass t0 ≈ 5 °C (9,332 bar abs → 8,319 bar ü), Sauggas 12 °C → SH ≈ 7 K
        val res = HvacCalculator.superheat(r, 9.332 - 1.01325, PressureReference.GAUGE, 12.0)!!
        assertEquals(5.0, res.evaporatingTempC, 0.05)
        assertEquals(7.0, res.superheatK, 0.05)
        assertEquals(RangeStatus.TYPICAL, res.status)
        assertEquals(RangeStatus.LOW, HvacCalculator.superheat(r, 9.332, PressureReference.ABSOLUTE, 6.0)!!.status)
        assertEquals(RangeStatus.HIGH, HvacCalculator.superheat(r, 9.332, PressureReference.ABSOLUTE, 20.0)!!.status)
    }

    @Test fun subcoolingUsesBubblePoint() {
        val r = ref("R407C")
        val pBubble40 = Saturation.pressureAbsBar(r, 40.0, BUBBLE)!!
        val res = HvacCalculator.subcooling(r, pBubble40, PressureReference.ABSOLUTE, 35.0)!!
        assertEquals(40.0, res.condensingTempC, 0.05)
        assertEquals(5.0, res.subcoolingK, 0.05)
        assertEquals(RangeStatus.TYPICAL, res.status)
    }

    @Test fun calculatorReturnsNullWithoutData() {
        assertNull(HvacCalculator.superheat(ref("R454B"), 5.0, PressureReference.GAUGE, 10.0))
        assertNotNull(HvacCalculator.superheat(ref("R32"), 5.0, PressureReference.GAUGE, 10.0))
    }
}

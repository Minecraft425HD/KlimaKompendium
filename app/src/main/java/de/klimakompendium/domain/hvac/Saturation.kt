package de.klimakompendium.domain.hvac

import de.klimakompendium.domain.convert.PressureReference
import de.klimakompendium.domain.convert.UnitConverter
import de.klimakompendium.domain.model.PtPoint
import de.klimakompendium.domain.model.Refrigerant
import de.klimakompendium.domain.model.SaturationPhase
import kotlin.math.exp
import kotlin.math.ln

/**
 * p-T-Berechnung auf Basis der hinterlegten Sättigungstabelle eines Kältemittels.
 *
 * Zwischen den Stützpunkten wird nach ln(p) über 1/T interpoliert (Clausius-Clapeyron-Form) –
 * deutlich genauer als linear. Außerhalb der Tabelle wird NICHT extrapoliert (Ergebnis null).
 */
object Saturation {
    private fun kelvin(c: Double) = c + 273.15

    /** Sättigungsdruck (absolut, bar) bei Temperatur [tempC]. */
    fun pressureAbsBar(r: Refrigerant, tempC: Double, phase: SaturationPhase): Double? {
        val pts = r.ptTable.sortedBy { it.tempC }
        if (pts.size < 2 || tempC < pts.first().tempC || tempC > pts.last().tempC) return null
        val hi = pts.indexOfFirst { it.tempC >= tempC }
        if (pts[hi].tempC == tempC) return pts[hi].pressure(phase)
        val a = pts[hi - 1]
        val b = pts[hi]
        val x = (1.0 / kelvin(tempC) - 1.0 / kelvin(a.tempC)) / (1.0 / kelvin(b.tempC) - 1.0 / kelvin(a.tempC))
        return exp(ln(a.pressure(phase)) + x * (ln(b.pressure(phase)) - ln(a.pressure(phase))))
    }

    /** Sättigungstemperatur (°C) bei absolutem Druck [pAbsBar]. */
    fun temperatureC(r: Refrigerant, pAbsBar: Double, phase: SaturationPhase): Double? {
        if (pAbsBar <= 0.0) return null
        val pts: List<PtPoint> = r.ptTable.sortedBy { it.tempC }
        if (pts.size < 2) return null
        if (pAbsBar < pts.first().pressure(phase) || pAbsBar > pts.last().pressure(phase)) return null
        val hi = pts.indexOfFirst { it.pressure(phase) >= pAbsBar }
        if (pts[hi].pressure(phase) == pAbsBar) return pts[hi].tempC
        val a = pts[hi - 1]
        val b = pts[hi]
        val lnA = ln(a.pressure(phase))
        val lnB = ln(b.pressure(phase))
        val x = (ln(pAbsBar) - lnA) / (lnB - lnA)
        val invT = 1.0 / kelvin(a.tempC) + x * (1.0 / kelvin(b.tempC) - 1.0 / kelvin(a.tempC))
        return 1.0 / invT - 273.15
    }

    fun temperatureFromInput(
        r: Refrigerant, value: Double, ref: PressureReference, phase: SaturationPhase,
    ): Double? = temperatureC(r, UnitConverter.toAbsoluteBar(value, de.klimakompendium.domain.convert.PressureUnit.BAR, ref), phase)
}

/** Bewertung eines Überhitzungs-/Unterkühlungswerts gegenüber üblichen Richtwerten. */
enum class RangeStatus(val label: String) {
    LOW("unter üblichem Richtwert"), TYPICAL("im üblichen Bereich"), HIGH("über üblichem Richtwert")
}

data class SuperheatResult(
    val evaporatingTempC: Double,
    val superheatK: Double,
    val status: RangeStatus,
)

data class SubcoolingResult(
    val condensingTempC: Double,
    val subcoolingK: Double,
    val status: RangeStatus,
)

object HvacCalculator {
    /** Üblicher Richtwertbereich Überhitzung am Verdampferaustritt in K (herstellerabhängig!). */
    val SUPERHEAT_TYPICAL_K = 5.0..10.0
    val SUBCOOLING_TYPICAL_K = 3.0..10.0

    /**
     * Überhitzung = Sauggastemperatur − Verdampfungstemperatur (Taupunkt bei Saugdruck).
     * @return null, wenn der Druck außerhalb der p-T-Tabelle liegt oder keine Daten vorhanden sind.
     */
    fun superheat(r: Refrigerant, suctionPressure: Double, ref: PressureReference, suctionTempC: Double): SuperheatResult? {
        val t0 = Saturation.temperatureFromInput(r, suctionPressure, ref, SaturationPhase.DEW) ?: return null
        val sh = suctionTempC - t0
        return SuperheatResult(t0, sh, classify(sh, SUPERHEAT_TYPICAL_K))
    }

    /** Unterkühlung = Verflüssigungstemperatur (Siedepunkt bei Hochdruck) − Flüssigkeitstemperatur. */
    fun subcooling(r: Refrigerant, highPressure: Double, ref: PressureReference, liquidTempC: Double): SubcoolingResult? {
        val tc = Saturation.temperatureFromInput(r, highPressure, ref, SaturationPhase.BUBBLE) ?: return null
        val sc = tc - liquidTempC
        return SubcoolingResult(tc, sc, classify(sc, SUBCOOLING_TYPICAL_K))
    }

    private fun classify(value: Double, range: ClosedFloatingPointRange<Double>) = when {
        value < range.start -> RangeStatus.LOW
        value > range.endInclusive -> RangeStatus.HIGH
        else -> RangeStatus.TYPICAL
    }
}

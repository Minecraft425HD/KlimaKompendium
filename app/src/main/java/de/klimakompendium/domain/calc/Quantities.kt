package de.klimakompendium.domain.calc

import de.klimakompendium.domain.convert.PressureReference
import de.klimakompendium.domain.convert.PressureUnit
import de.klimakompendium.domain.convert.UnitConverter
import de.klimakompendium.domain.hvac.HvacCalculator
import de.klimakompendium.domain.hvac.RangeStatus
import de.klimakompendium.domain.hvac.Saturation
import de.klimakompendium.domain.model.Refrigerant
import de.klimakompendium.domain.model.SaturationPhase.BUBBLE
import de.klimakompendium.domain.model.SaturationPhase.DEW
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.PI
import kotlin.math.sqrt

enum class QuantityCategory(val label: String) {
    REFRIGERANT_CIRCUIT("Kältekreis"), WATER("Wasser"), AIR("Luft & Feuchte"), ELECTRIC("Elektrik & Leistung")
}

/** Eingaben einer Berechnung. Alle Werte stehen bereits in der Basiseinheit von [Measure.symbol]. */
class CalcContext(
    private val values: Map<Measure, Double>,
    val refrigerant: Refrigerant?,
    val reference: PressureReference,
) {
    fun get(m: Measure): Double = values.getValue(m)
    fun opt(m: Measure): Double? = values[m]

    /** Druck-Messwert als Absolutdruck in bar (Bezug laut Einstellung). */
    fun absBar(m: Measure): Double = UnitConverter.toAbsoluteBar(get(m), PressureUnit.BAR, reference)
}

data class CalcLine(
    val label: String,
    val value: String,
    val unit: String,
    val emphasis: Boolean = false,
    val note: String = "",
    val status: RangeStatus? = null,
)

sealed interface CalcOutcome {
    data class Ok(val lines: List<CalcLine>, val hint: String = "") : CalcOutcome
    data class Error(val message: String) : CalcOutcome
}

/**
 * Eine berechenbare Größe mit Erklärung. [needs] sind Pflicht-Messwerte, [optional] erweitern das Ergebnis.
 * Formeln und Richtwerte sind allgemeine Fachregeln; Herstellerangaben der Anlage haben Vorrang.
 */
class Quantity(
    val id: String,
    val name: String,
    val category: QuantityCategory,
    val summary: String,
    val interpretation: String,
    val formula: String,
    val needs: List<Measure>,
    val optional: List<Measure> = emptyList(),
    val needsRefrigerant: Boolean = false,
    val keywords: List<String> = emptyList(),
    /** Anzeigebezeichnung am Messgerät (testo 570s), falls die Größe dort vorkommt. */
    val device: String = "",
    val compute: (CalcContext) -> CalcOutcome,
) {
    val usesPressure: Boolean get() = (needs + optional).any { it == Measure.SUCTION_P || it == Measure.DISCHARGE_P }
}

object Quantities {
    // ------------------------------------------------------------------ Hilfsfunktionen
    private fun f(v: Double, d: Int = 1) = UnitConverter.format(v, d)
    private fun line(label: String, v: Double, unit: String, d: Int = 1, emphasis: Boolean = false, note: String = "", status: RangeStatus? = null) =
        CalcLine(label, f(v, d), unit, emphasis, note, status)

    private fun err(msg: String) = CalcOutcome.Error(msg)

    private fun refrigerantOrError(c: CalcContext): Refrigerant? = c.refrigerant?.takeIf { it.hasPtData }

    private const val NO_REFRIGERANT = "Bitte ein Kältemittel mit hinterlegter p‑T‑Tabelle wählen."

    private fun rangeError(r: Refrigerant, what: String) =
        err("$what liegt außerhalb der p‑T‑Tabelle von ${r.name} (${f(r.minTempC ?: 0.0, 0)} … ${f(r.maxTempC ?: 0.0, 0)} °C). Messwert und Bezug (Überdruck/absolut) prüfen.")

    private fun dew(r: Refrigerant, c: CalcContext, m: Measure) = Saturation.temperatureC(r, c.absBar(m), DEW)
    private fun bubble(r: Refrigerant, c: CalcContext, m: Measure) = Saturation.temperatureC(r, c.absBar(m), BUBBLE)

    /** Wasser: Wärmekapazität × Dichte ≈ 1,163 kWh/(m³·K) (reines Wasser, ca. 20 °C). */
    const val WATER_KWH_PER_M3K = 1.163

    /** Luft: ρ ≈ 1,2 kg/m³, cp ≈ 1,006 kJ/(kg·K) → 0,335 Wh/(m³·K). */
    const val AIR_KWH_PER_M3K = 0.000335

    // ------------------------------------------------------------------ Kältekreis
    private val evapTemp = Quantity(
        "t0", "Verdampfungstemperatur t0", QuantityCategory.REFRIGERANT_CIRCUIT,
        "Sättigungstemperatur des Kältemittels im Verdampfer, aus dem Saugdruck über die p‑T‑Tabelle.",
        "Basis für Überhitzung und Verdampfer-Differenz. Bei Gemischen mit Glide zählt der Taupunkt (Dampfseite).",
        "t0 = Tsat,Dampf(p_ND,abs)", listOf(Measure.SUCTION_P), needsRefrigerant = true,
        keywords = listOf("Verdampfungstemperatur", "Sättigungstemperatur", "Saugdruck", "ND", "SST", "T verdampf", "testo"),
        device = "„T verdampf“ (aus ND)",
    ) { c ->
        val r = refrigerantOrError(c) ?: return@Quantity err(NO_REFRIGERANT)
        val d = dew(r, c, Measure.SUCTION_P); val b = bubble(r, c, Measure.SUCTION_P)
        if (d == null || b == null) return@Quantity rangeError(r, "Der Saugdruck")
        val lines = mutableListOf(line("Verdampfungstemperatur t0 (Taupunkt)", d, "°C", emphasis = true, note = "testo: „T verdampf“"))
        if (r.isZeotropic) lines += line("Siedetemperatur bei gleichem Druck", b, "°C", note = "Gemisch mit Temperaturgleit")
        CalcOutcome.Ok(lines)
    }

    private val condTemp = Quantity(
        "tc", "Verflüssigungstemperatur tc", QuantityCategory.REFRIGERANT_CIRCUIT,
        "Sättigungstemperatur des Kältemittels im Verflüssiger, aus dem Hochdruck über die p‑T‑Tabelle.",
        "Basis für Unterkühlung und Verflüssiger-Differenz. Bei Gemischen mit Glide zählt der Siedepunkt (Flüssigseite).",
        "tc = Tsat,Flüssig(p_HD,abs)", listOf(Measure.DISCHARGE_P), needsRefrigerant = true,
        keywords = listOf("Verflüssigungstemperatur", "Kondensationstemperatur", "Hochdruck", "HD", "SDT", "T kondensat", "testo"),
        device = "„T kondensat“ (aus HD)",
    ) { c ->
        val r = refrigerantOrError(c) ?: return@Quantity err(NO_REFRIGERANT)
        val d = dew(r, c, Measure.DISCHARGE_P); val b = bubble(r, c, Measure.DISCHARGE_P)
        if (d == null || b == null) return@Quantity rangeError(r, "Der Hochdruck")
        val lines = mutableListOf(line("Verflüssigungstemperatur tc (Siedepunkt)", b, "°C", emphasis = true, note = "testo: „T kondensat“"))
        if (r.isZeotropic) lines += line("Taupunkt bei gleichem Druck", d, "°C", note = "Gemisch mit Temperaturgleit")
        CalcOutcome.Ok(lines)
    }

    private val superheat = Quantity(
        "superheat", "Überhitzung (Saugseite)", QuantityCategory.REFRIGERANT_CIRCUIT,
        "Temperaturabstand des Sauggases zur Verdampfungstemperatur: wie weit das Kältemittel nach dem Verdampfen noch erwärmt wurde.",
        "Zu klein: Nassdampf, Gefahr von Flüssigkeitsschlag. Zu groß: Verdampfer unterversorgt (Kältemittelmangel, Ventil zu weit zu, Druckverlust). Misst du am Verdampferaustritt, erhältst du die Verdampfer-Überhitzung; kurz vor dem Verdichter die Gesamtüberhitzung (Leitungserwärmung eingerechnet).",
        "Überhitzung = T_Sauggas − t0", listOf(Measure.SUCTION_P, Measure.SUCTION_T), needsRefrigerant = true,
        keywords = listOf("Überhitzung", "Superheat", "SH", "Sauggas", "Surriscaldamento", "testo", "T1"),
        device = "„Überhitzung“ (aus ND und T1)",
    ) { c ->
        val r = refrigerantOrError(c) ?: return@Quantity err(NO_REFRIGERANT)
        val res = HvacCalculator.superheat(r, c.get(Measure.SUCTION_P), c.reference, c.get(Measure.SUCTION_T))
            ?: return@Quantity rangeError(r, "Der Saugdruck")
        CalcOutcome.Ok(
            listOf(
                line("Verdampfungstemperatur t0 (Taupunkt)", res.evaporatingTempC, "°C", note = "testo: „T verdampf“"),
                line("Überhitzung", res.superheatK, "K", emphasis = true, status = res.status,
                    note = "${res.status.label} (Richtwert 5–10 K) · testo: „Überhitzung“"),
            ),
            "Richtwert für Anlagen mit Expansionsventil; die Vorgabe des Geräteherstellers hat Vorrang.",
        )
    }

    private val subcooling = Quantity(
        "subcooling", "Unterkühlung (Flüssigkeit)", QuantityCategory.REFRIGERANT_CIRCUIT,
        "Temperaturabstand der Flüssigkeit zur Verflüssigungstemperatur: wie weit das Kältemittel nach dem Verflüssigen weiter abgekühlt wurde.",
        "Zu klein: Blasenbildung vor dem Expansionsventil, Kältemittelmangel oder schlechter Verflüssiger. Sehr groß: Überfüllung (Flüssigkeit staut sich im Verflüssiger).",
        "Unterkühlung = tc − T_Flüssigkeit", listOf(Measure.DISCHARGE_P, Measure.LIQUID_T), needsRefrigerant = true,
        keywords = listOf("Unterkühlung", "Subcooling", "SC", "Flüssigkeit", "Sottoraffreddamento", "testo", "T2"),
        device = "„Unterkühlung“ (aus HD und T2)",
    ) { c ->
        val r = refrigerantOrError(c) ?: return@Quantity err(NO_REFRIGERANT)
        val res = HvacCalculator.subcooling(r, c.get(Measure.DISCHARGE_P), c.reference, c.get(Measure.LIQUID_T))
            ?: return@Quantity rangeError(r, "Der Hochdruck")
        CalcOutcome.Ok(
            listOf(
                line("Verflüssigungstemperatur tc (Siedepunkt)", res.condensingTempC, "°C", note = "testo: „T kondensat“"),
                line("Unterkühlung", res.subcoolingK, "K", emphasis = true, status = res.status,
                    note = "${res.status.label} (Richtwert 3–10 K) · testo: „Unterkühlung“"),
            ),
            "Richtwert; die Vorgabe des Geräteherstellers hat Vorrang.",
        )
    }

    /** Absolutdruck in bar → Anzeigewert im gewählten Bezug (Überdruck/absolut). */
    private fun shown(c: CalcContext, absBar: Double) = UnitConverter.fromAbsoluteBar(absBar, PressureUnit.BAR, c.reference)
    private fun refLabel(c: CalcContext) = if (c.reference == PressureReference.GAUGE) "bar ü" else "bar abs."

    private val suctionTemp = Quantity(
        "suction_t", "Sauggastemperatur aus Saugdruck und Überhitzung", QuantityCategory.REFRIGERANT_CIRCUIT,
        "Erwartete Sauggastemperatur: Verdampfungstemperatur t0 (aus dem Saugdruck) plus die bekannte oder geforderte Überhitzung.",
        "Zum Vergleich mit der gemessenen Sauggastemperatur: Weicht sie deutlich ab, ist die tatsächliche Überhitzung nicht der Sollwert (Expansionsventil, Fühler, Kältemittelmenge). Ohne bekannte Überhitzung lässt sich die Sauggastemperatur nicht errechnen – sie ergibt sich aus dem Betrieb und wird gemessen.",
        "T_Sauggas = t0 + Überhitzung", listOf(Measure.SUCTION_P, Measure.SUPERHEAT_K), needsRefrigerant = true,
        keywords = listOf("Sauggastemperatur", "Sauggas", "Saugtemperatur", "erwartet", "Soll", "berechnen"),
    ) { c ->
        val r = refrigerantOrError(c) ?: return@Quantity err(NO_REFRIGERANT)
        val t0 = dew(r, c, Measure.SUCTION_P) ?: return@Quantity rangeError(r, "Der Saugdruck")
        CalcOutcome.Ok(listOf(line("Verdampfungstemperatur t0 (Taupunkt)", t0, "°C"),
            line("Überhitzung", c.get(Measure.SUPERHEAT_K), "K"),
            line("Sauggastemperatur", t0 + c.get(Measure.SUPERHEAT_K), "°C", emphasis = true)),
            "Bei Gemischen zählt der Taupunkt. Gilt für die Messstelle, auf die sich die Überhitzung bezieht (Verdampferaustritt oder Verdichtereintritt).")
    }

    private val liquidTemp = Quantity(
        "liquid_t", "Flüssigkeitstemperatur aus Hochdruck und Unterkühlung", QuantityCategory.REFRIGERANT_CIRCUIT,
        "Erwartete Flüssigkeitstemperatur: Verflüssigungstemperatur tc (aus dem Hochdruck) minus die bekannte oder geforderte Unterkühlung.",
        "Zum Vergleich mit der am Flüssigkeitsrohr gemessenen Temperatur. Ohne bekannte Unterkühlung nicht errechenbar – sie ergibt sich aus dem Betrieb.",
        "T_Flüssigkeit = tc − Unterkühlung", listOf(Measure.DISCHARGE_P, Measure.SUBCOOLING_K), needsRefrigerant = true,
        keywords = listOf("Flüssigkeitstemperatur", "Flüssigkeit", "Soll", "erwartet", "berechnen"),
    ) { c ->
        val r = refrigerantOrError(c) ?: return@Quantity err(NO_REFRIGERANT)
        val tc = bubble(r, c, Measure.DISCHARGE_P) ?: return@Quantity rangeError(r, "Der Hochdruck")
        CalcOutcome.Ok(listOf(line("Verflüssigungstemperatur tc (Siedepunkt)", tc, "°C"),
            line("Unterkühlung", c.get(Measure.SUBCOOLING_K), "K"),
            line("Flüssigkeitstemperatur", tc - c.get(Measure.SUBCOOLING_K), "°C", emphasis = true)))
    }

    private val suctionPressure = Quantity(
        "suction_p", "Saugdruck aus Sauggastemperatur und Überhitzung", QuantityCategory.REFRIGERANT_CIRCUIT,
        "Erwarteter Saugdruck: aus Sauggastemperatur minus Überhitzung ergibt sich t0, daraus über die p‑T‑Tabelle der Druck.",
        "Nützlich, wenn kein Manometer angeschlossen ist, aber Sauggastemperatur und Überhitzung (z. B. vom Controller) vorliegen.",
        "t0 = T_Sauggas − Überhitzung; p_ND = psat,Dampf(t0)", listOf(Measure.SUCTION_T, Measure.SUPERHEAT_K), needsRefrigerant = true,
        keywords = listOf("Saugdruck", "ND", "Niederdruck", "aus Temperatur", "berechnen"),
    ) { c ->
        val r = refrigerantOrError(c) ?: return@Quantity err(NO_REFRIGERANT)
        val t0 = c.get(Measure.SUCTION_T) - c.get(Measure.SUPERHEAT_K)
        val p = Saturation.pressureAbsBar(r, t0, DEW) ?: return@Quantity rangeError(r, "Die Verdampfungstemperatur (${f(t0)} °C)")
        CalcOutcome.Ok(listOf(line("Verdampfungstemperatur t0", t0, "°C"), line("Saugdruck", shown(c, p), refLabel(c), d = 2, emphasis = true)))
    }

    private val dischargePressure = Quantity(
        "discharge_p", "Hochdruck aus Flüssigkeitstemperatur und Unterkühlung", QuantityCategory.REFRIGERANT_CIRCUIT,
        "Erwarteter Hochdruck: Flüssigkeitstemperatur plus Unterkühlung ergibt tc, daraus über die p‑T‑Tabelle der Druck.",
        "Nützlich, wenn kein Manometer angeschlossen ist, aber Flüssigkeitstemperatur und Unterkühlung vorliegen.",
        "tc = T_Flüssigkeit + Unterkühlung; p_HD = psat,Flüssig(tc)", listOf(Measure.LIQUID_T, Measure.SUBCOOLING_K), needsRefrigerant = true,
        keywords = listOf("Hochdruck", "HD", "aus Temperatur", "berechnen"),
    ) { c ->
        val r = refrigerantOrError(c) ?: return@Quantity err(NO_REFRIGERANT)
        val tc = c.get(Measure.LIQUID_T) + c.get(Measure.SUBCOOLING_K)
        val p = Saturation.pressureAbsBar(r, tc, BUBBLE) ?: return@Quantity rangeError(r, "Die Verflüssigungstemperatur (${f(tc)} °C)")
        CalcOutcome.Ok(listOf(line("Verflüssigungstemperatur tc", tc, "°C"), line("Hochdruck", shown(c, p), refLabel(c), d = 2, emphasis = true)))
    }

    private val discharge = Quantity(
        "discharge", "Heißgas-Überhitzung (Druckgas)", QuantityCategory.REFRIGERANT_CIRCUIT,
        "Temperaturabstand des Druckgases (Heißgas) zur Taupunkttemperatur beim Hochdruck.",
        "Zeigt, wie stark der Verdichter das Gas über die Sättigung hinaus erhitzt. Hohe Werte treten bei hohem Verdichtungsverhältnis, Überhitzung der Saugseite (Kältemittelmangel) oder Verdichterproblemen auf. Die absolute Druckgastemperatur gegen die Herstellergrenze des Verdichters prüfen (kältemittelabhängig, R32 deutlich heißer als R410A).",
        "Heißgas-Überhitzung = T_Druckgas − Tsat,Dampf(p_HD,abs)", listOf(Measure.DISCHARGE_P, Measure.DISCHARGE_T), needsRefrigerant = true,
        keywords = listOf("Heißgas", "Druckgas", "Heissgas", "Druckgastemperatur", "DGT", "Verdichtungsendtemperatur"),
    ) { c ->
        val r = refrigerantOrError(c) ?: return@Quantity err(NO_REFRIGERANT)
        val d = dew(r, c, Measure.DISCHARGE_P) ?: return@Quantity rangeError(r, "Der Hochdruck")
        val t = c.get(Measure.DISCHARGE_T)
        if (t < d) return@Quantity err("Die Druckgastemperatur (${f(t)} °C) liegt unter der Sättigungstemperatur (${f(d)} °C) – Messwerte oder Messstelle prüfen.")
        CalcOutcome.Ok(
            listOf(
                line("Sättigungstemperatur bei Hochdruck (Taupunkt)", d, "°C"),
                line("Druckgastemperatur (gemessen)", t, "°C"),
                line("Heißgas-Überhitzung", t - d, "K", emphasis = true),
            ),
            "Kein allgemeiner Richtwert: Grenzwert der Druckgastemperatur laut Verdichterhersteller beachten.",
        )
    }

    private val compressionRatio = Quantity(
        "pi", "Verdichtungsverhältnis π", QuantityCategory.REFRIGERANT_CIRCUIT,
        "Verhältnis aus Hochdruck und Saugdruck, beide absolut.",
        "Je höher π, desto höher die Druckgastemperatur und desto schlechter Wirkungsgrad und Liefergrad. Grenzwerte (Anwendungsgrenzen) nennt der Verdichterhersteller.",
        "π = p_HD,abs ÷ p_ND,abs", listOf(Measure.SUCTION_P, Measure.DISCHARGE_P),
        keywords = listOf("Verdichtungsverhältnis", "Druckverhältnis", "Kompression", "Pi"),
    ) { c ->
        val lo = c.absBar(Measure.SUCTION_P); val hi = c.absBar(Measure.DISCHARGE_P)
        if (lo <= 0.0) return@Quantity err("Der absolute Saugdruck muss größer als 0 sein (Bezug Überdruck/absolut prüfen).")
        if (hi <= lo) return@Quantity err("Der Hochdruck muss größer als der Saugdruck sein.")
        CalcOutcome.Ok(listOf(line("Verdichtungsverhältnis π", hi / lo, "", d = 2, emphasis = true),
            line("Saugdruck absolut", lo, "bar", d = 2), line("Hochdruck absolut", hi, "bar", d = 2)))
    }

    private val pressureDiff = Quantity(
        "dp", "Druckdifferenz HD − ND", QuantityCategory.REFRIGERANT_CIRCUIT,
        "Unterschied zwischen Hochdruck und Saugdruck.",
        "Manche Verdichter und Expansionsventile brauchen eine Mindestdruckdifferenz (z. B. zum Anlauf oder für die Ventilleistung).",
        "Δp = p_HD − p_ND", listOf(Measure.SUCTION_P, Measure.DISCHARGE_P),
        keywords = listOf("Druckdifferenz", "Differenzdruck", "Delta p", "dp"),
    ) { c ->
        val d = c.get(Measure.DISCHARGE_P) - c.get(Measure.SUCTION_P)
        if (d <= 0.0) return@Quantity err("Der Hochdruck muss größer als der Saugdruck sein.")
        CalcOutcome.Ok(listOf(line("Druckdifferenz", d, "bar", d = 2, emphasis = true)))
    }

    private val evapDeltaAir = Quantity(
        "evapdt1", "Verdampfer-Temperaturdifferenz (Luft)", QuantityCategory.REFRIGERANT_CIRCUIT,
        "Abstand zwischen Lufteintrittstemperatur und Verdampfungstemperatur (oft ΔT1 oder „TD“).",
        "Steigt bei Luftmangel (Filter, Verschmutzung, Ventilator) oder Unterleistung des Verdampfers, weil t0 absinkt. Sehr kleiner Wert kann auf zu viel Luft oder zu geringe Last hindeuten.",
        "ΔT1 = T_Lufteintritt − t0", listOf(Measure.SUCTION_P, Measure.AIR_IN_T), needsRefrigerant = true,
        keywords = listOf("Verdampfer", "Temperaturdifferenz", "ΔT1", "TD", "Luft"),
    ) { c ->
        val r = refrigerantOrError(c) ?: return@Quantity err(NO_REFRIGERANT)
        val t0 = dew(r, c, Measure.SUCTION_P) ?: return@Quantity rangeError(r, "Der Saugdruck")
        CalcOutcome.Ok(listOf(line("Verdampfungstemperatur t0", t0, "°C"),
            line("Temperaturdifferenz Verdampfer ΔT1", c.get(Measure.AIR_IN_T) - t0, "K", emphasis = true)),
            "Der passende Wert hängt von Verdampferbauart und Anwendung ab – Herstellerangabe beachten.")
    }

    private val condDeltaAir = Quantity(
        "conddt", "Verflüssiger-Temperaturdifferenz (Luft)", QuantityCategory.REFRIGERANT_CIRCUIT,
        "Abstand zwischen Verflüssigungstemperatur und Außen-/Ansaugluft des Verflüssigers.",
        "Luftgekühlt liegt tc üblicherweise etwa 10–20 K über der Außenlufttemperatur (Richtwert). Deutlich mehr: verschmutzter Verflüssiger, Ventilator, Luftkurzschluss, Überfüllung oder Inertgas.",
        "Δtc = tc − T_Außenluft", listOf(Measure.DISCHARGE_P, Measure.OUTDOOR_T), needsRefrigerant = true,
        keywords = listOf("Verflüssiger", "Außenluft", "Temperaturdifferenz", "Kondensator", "Verschmutzung"),
    ) { c ->
        val r = refrigerantOrError(c) ?: return@Quantity err(NO_REFRIGERANT)
        val tc = bubble(r, c, Measure.DISCHARGE_P) ?: return@Quantity rangeError(r, "Der Hochdruck")
        CalcOutcome.Ok(listOf(line("Verflüssigungstemperatur tc", tc, "°C"),
            line("Differenz zur Außenluft", tc - c.get(Measure.OUTDOOR_T), "K", emphasis = true,
                note = "Richtwert luftgekühlt ca. 10–20 K")))
    }

    private val glide = Quantity(
        "glide", "Temperaturgleit (Glide)", QuantityCategory.REFRIGERANT_CIRCUIT,
        "Temperaturunterschied zwischen Taupunkt und Siedepunkt eines Kältemittelgemischs bei gleichem Druck.",
        "Einkomponentige Stoffe und azeotrope Gemische haben kein bzw. kaum Glide (R32, R134a, R290). Zeotrope wie R407C haben mehrere Kelvin: Überhitzung am Taupunkt, Unterkühlung am Siedepunkt bestimmen.",
        "Glide = Tsat,Dampf(p) − Tsat,Flüssig(p)", listOf(Measure.SUCTION_P), optional = listOf(Measure.DISCHARGE_P), needsRefrigerant = true,
        keywords = listOf("Glide", "Gleit", "Temperaturgleit", "zeotrop", "Gemisch"),
    ) { c ->
        val r = refrigerantOrError(c) ?: return@Quantity err(NO_REFRIGERANT)
        val dN = dew(r, c, Measure.SUCTION_P); val bN = bubble(r, c, Measure.SUCTION_P)
        if (dN == null || bN == null) return@Quantity rangeError(r, "Der Saugdruck")
        val lines = mutableListOf(line("Glide bei Saugdruck", dN - bN, "K", emphasis = true, note = "Taupunkt ${f(dN)} °C · Siedepunkt ${f(bN)} °C"))
        if (c.opt(Measure.DISCHARGE_P) != null) {
            val dH = dew(r, c, Measure.DISCHARGE_P); val bH = bubble(r, c, Measure.DISCHARGE_P)
            if (dH != null && bH != null) lines += line("Glide bei Hochdruck", dH - bH, "K", note = "Taupunkt ${f(dH)} °C · Siedepunkt ${f(bH)} °C")
        }
        CalcOutcome.Ok(lines)
    }

    private val approachEvap = Quantity(
        "approach_evap", "Verdampfer-Approach (Wasser)", QuantityCategory.REFRIGERANT_CIRCUIT,
        "Abstand zwischen Wasseraustrittstemperatur und Verdampfungstemperatur beim Kaltwassersatz.",
        "Wird der Abstand größer, überträgt der Verdampfer schlechter (Verschmutzung, Ölfilm, zu wenig Wasser, Luft im Wasserkreis).",
        "Approach = T_Wasser,aus − t0", listOf(Measure.SUCTION_P, Measure.WATER_OUT_T), needsRefrigerant = true,
        keywords = listOf("Approach", "Verdampfer", "Wasser", "Kaltwassersatz", "Chiller"),
    ) { c ->
        val r = refrigerantOrError(c) ?: return@Quantity err(NO_REFRIGERANT)
        val t0 = dew(r, c, Measure.SUCTION_P) ?: return@Quantity rangeError(r, "Der Saugdruck")
        CalcOutcome.Ok(listOf(line("Verdampfungstemperatur t0", t0, "°C"),
            line("Verdampfer-Approach", c.get(Measure.WATER_OUT_T) - t0, "K", emphasis = true)),
            "Normalwert vom Gerätetyp abhängig – im Datenblatt/Inbetriebnahmeprotokoll vergleichen.")
    }

    private val approachCond = Quantity(
        "approach_cond", "Verflüssiger-Approach (Wasser)", QuantityCategory.REFRIGERANT_CIRCUIT,
        "Abstand zwischen Verflüssigungstemperatur und Wasseraustrittstemperatur eines wassergekühlten Verflüssigers.",
        "Wird der Abstand größer, überträgt der Verflüssiger schlechter (Verkalkung, Verschmutzung, zu wenig Wasser, Inertgas).",
        "Approach = tc − T_Wasser,aus", listOf(Measure.DISCHARGE_P, Measure.WATER_OUT_T), needsRefrigerant = true,
        keywords = listOf("Approach", "Verflüssiger", "wassergekühlt", "Kondensator"),
    ) { c ->
        val r = refrigerantOrError(c) ?: return@Quantity err(NO_REFRIGERANT)
        val tc = bubble(r, c, Measure.DISCHARGE_P) ?: return@Quantity rangeError(r, "Der Hochdruck")
        CalcOutcome.Ok(listOf(line("Verflüssigungstemperatur tc", tc, "°C"),
            line("Verflüssiger-Approach", tc - c.get(Measure.WATER_OUT_T), "K", emphasis = true)),
            "Normalwert vom Gerätetyp abhängig – im Datenblatt/Inbetriebnahmeprotokoll vergleichen.")
    }

    // ------------------------------------------------------------------ Wasser
    private fun waterDelta(c: CalcContext): Pair<Double, Boolean> {
        val dIn = c.get(Measure.WATER_IN_T); val dOut = c.get(Measure.WATER_OUT_T)
        return abs(dIn - dOut) to (dIn > dOut) // true = Kühlen
    }

    private val waterSpread = Quantity(
        "water_dt", "Wasser-Spreizung ΔT", QuantityCategory.WATER,
        "Temperaturunterschied zwischen Eintritt und Austritt des Wassers am Wärmetauscher.",
        "Kaltwassersatz typisch ca. 5 K (z. B. 12/7 °C). Zu kleine Spreizung bei normaler Leistung: zu viel Wasser; zu große: zu wenig Wasser.",
        "ΔT = |T_ein − T_aus|", listOf(Measure.WATER_IN_T, Measure.WATER_OUT_T),
        keywords = listOf("Spreizung", "Wasser", "Delta T", "Vorlauf", "Rücklauf"),
    ) { c ->
        val (d, cooling) = waterDelta(c)
        CalcOutcome.Ok(listOf(line("Spreizung", d, "K", emphasis = true, note = if (cooling) "Kühlbetrieb (Eintritt wärmer)" else "Heizbetrieb (Austritt wärmer)")))
    }

    private val waterPower = Quantity(
        "water_q", "Leistung aus Wasserseite", QuantityCategory.WATER,
        "Übertragene Kälte- bzw. Heizleistung aus Volumenstrom und Temperaturspreizung des Wassers.",
        "Gute Kontrolle der tatsächlichen Leistung. Reines Wasser; bei Glykol liegt die Leistung je nach Anteil 5–15 % niedriger (Wärmekapazität und Dichte sinken).",
        "Q [kW] = 1,163 × V̇ [m³/h] × ΔT [K]", listOf(Measure.WATER_FLOW, Measure.WATER_IN_T, Measure.WATER_OUT_T),
        keywords = listOf("Leistung", "Kälteleistung", "Heizleistung", "Wasser", "Q", "Volumenstrom"),
    ) { c ->
        val (d, cooling) = waterDelta(c)
        if (d < 0.1) return@Quantity err("Die Spreizung ist praktisch null – keine Leistung berechenbar.")
        val q = WATER_KWH_PER_M3K * c.get(Measure.WATER_FLOW) * d
        CalcOutcome.Ok(listOf(line(if (cooling) "Kälteleistung" else "Heizleistung", q, "kW", d = 2, emphasis = true), line("Spreizung", d, "K")),
            "Reines Wasser angenommen (ρ·cp = 1,163 kWh/m³K).")
    }

    private val waterFlow = Quantity(
        "water_v", "Wasser-Volumenstrom aus Leistung", QuantityCategory.WATER,
        "Benötigter Wasservolumenstrom für eine bekannte Leistung und Spreizung.",
        "Zum Prüfen von Pumpeneinstellung und Durchflusswächter oder zum Vergleich mit dem Auslegungswert.",
        "V̇ [m³/h] = Q [kW] ÷ (1,163 × ΔT [K])", listOf(Measure.CAPACITY, Measure.WATER_IN_T, Measure.WATER_OUT_T),
        keywords = listOf("Volumenstrom", "Wasser", "Durchfluss", "Pumpe"),
    ) { c ->
        val (d, _) = waterDelta(c)
        if (d < 0.1) return@Quantity err("Die Spreizung ist praktisch null.")
        val v = c.get(Measure.CAPACITY) / (WATER_KWH_PER_M3K * d)
        CalcOutcome.Ok(listOf(line("Volumenstrom", v, "m³/h", d = 2, emphasis = true), line("", v / 3.6, "l/s", d = 2), line("", v / 0.06, "l/min", d = 0)))
    }

    // ------------------------------------------------------------------ Luft & Feuchte
    private fun airDelta(c: CalcContext): Pair<Double, Boolean> {
        val i = c.get(Measure.AIR_IN_T); val o = c.get(Measure.AIR_OUT_T)
        return abs(i - o) to (i > o)
    }

    private val airSpread = Quantity(
        "air_dt", "Luft-Spreizung ΔT", QuantityCategory.AIR,
        "Temperaturunterschied der Luft vor und nach dem Wärmetauscher.",
        "Im Kühlbetrieb typisch ca. 8–12 K (Richtwert). Zu klein: zu viel Luft oder zu wenig Verdampferleistung; zu groß: zu wenig Luft (Filter, Ventilator) oder Vereisung.",
        "ΔT = |T_ein − T_aus|", listOf(Measure.AIR_IN_T, Measure.AIR_OUT_T),
        keywords = listOf("Spreizung", "Luft", "Delta T", "Temperaturdifferenz", "Verdampfer"),
    ) { c ->
        val (d, cooling) = airDelta(c)
        CalcOutcome.Ok(listOf(line("Luft-Spreizung", d, "K", emphasis = true, note = if (cooling) "Kühlbetrieb" else "Heizbetrieb")))
    }

    private val airPower = Quantity(
        "air_q", "Leistung aus Luftseite (sensibel)", QuantityCategory.AIR,
        "Sensible Wärmeleistung der Luft aus Volumenstrom und Temperaturspreizung (ohne Entfeuchtung).",
        "Die Entfeuchtungsleistung (latent) kommt dazu und ist hier nicht enthalten – die Gesamtleistung ist daher höher. Luftdichte nicht höhenkorrigiert.",
        "Q [kW] ≈ 0,335 × V̇ [m³/h] × ΔT [K] ÷ 1000", listOf(Measure.AIR_FLOW, Measure.AIR_IN_T, Measure.AIR_OUT_T),
        keywords = listOf("Leistung", "Luft", "sensibel", "Kälteleistung", "Volumenstrom"),
    ) { c ->
        val (d, cooling) = airDelta(c)
        if (d < 0.1) return@Quantity err("Die Spreizung ist praktisch null – keine Leistung berechenbar.")
        CalcOutcome.Ok(listOf(line(if (cooling) "Sensible Kälteleistung" else "Heizleistung", AIR_KWH_PER_M3K * c.get(Measure.AIR_FLOW) * d, "kW", d = 2, emphasis = true)),
            "ρ = 1,2 kg/m³, cp = 1,006 kJ/(kg·K) angenommen.")
    }

    private val airFlowFromPower = Quantity(
        "air_v", "Luft-Volumenstrom aus Leistung", QuantityCategory.AIR,
        "Luftmenge, die für eine sensible Leistung bei gegebener Spreizung nötig ist.",
        "Zum Vergleich mit gemessener bzw. am Gerät eingestellter Luftmenge.",
        "V̇ [m³/h] ≈ Q [kW] × 1000 ÷ (0,335 × ΔT [K])", listOf(Measure.CAPACITY, Measure.AIR_IN_T, Measure.AIR_OUT_T),
        keywords = listOf("Volumenstrom", "Luftmenge", "Luft"),
    ) { c ->
        val (d, _) = airDelta(c)
        if (d < 0.1) return@Quantity err("Die Spreizung ist praktisch null.")
        CalcOutcome.Ok(listOf(line("Luftvolumenstrom", c.get(Measure.CAPACITY) * 1000 / (AIR_KWH_PER_M3K * 1000 * d), "m³/h", d = 0, emphasis = true)))
    }

    private val flowRound = Quantity(
        "air_v_round", "Luft-Volumenstrom, runder Kanal", QuantityCategory.AIR,
        "Volumenstrom aus mittlerer Luftgeschwindigkeit und Kanalquerschnitt (rund).",
        "Geschwindigkeit an mehreren Punkten im Querschnitt messen und mitteln; ausreichend lange gerade Strecke vor der Messstelle.",
        "V̇ [m³/h] = v [m/s] × π × d² ÷ 4 × 3600", listOf(Measure.AIR_VELOCITY, Measure.DUCT_DIAMETER),
        keywords = listOf("Volumenstrom", "Kanal", "rund", "Anemometer", "Luftmenge"),
    ) { c ->
        val dM = c.get(Measure.DUCT_DIAMETER) / 1000.0
        if (dM <= 0.0) return@Quantity err("Der Durchmesser muss größer als 0 sein.")
        val a = PI * dM * dM / 4.0
        CalcOutcome.Ok(listOf(line("Luftvolumenstrom", c.get(Measure.AIR_VELOCITY) * a * 3600, "m³/h", d = 0, emphasis = true), line("Querschnittsfläche", a, "m²", d = 4)))
    }

    private val flowRect = Quantity(
        "air_v_rect", "Luft-Volumenstrom, eckiger Kanal", QuantityCategory.AIR,
        "Volumenstrom aus mittlerer Luftgeschwindigkeit und Kanalquerschnitt (rechteckig).",
        "Geschwindigkeit an mehreren Punkten im Querschnitt messen und mitteln.",
        "V̇ [m³/h] = v [m/s] × b × h × 3600", listOf(Measure.AIR_VELOCITY, Measure.DUCT_WIDTH, Measure.DUCT_HEIGHT),
        keywords = listOf("Volumenstrom", "Kanal", "eckig", "rechteckig", "Anemometer", "Luftmenge"),
    ) { c ->
        val a = c.get(Measure.DUCT_WIDTH) / 1000.0 * c.get(Measure.DUCT_HEIGHT) / 1000.0
        if (a <= 0.0) return@Quantity err("Breite und Höhe müssen größer als 0 sein.")
        CalcOutcome.Ok(listOf(line("Luftvolumenstrom", c.get(Measure.AIR_VELOCITY) * a * 3600, "m³/h", d = 0, emphasis = true), line("Querschnittsfläche", a, "m²", d = 4)))
    }

    /** Magnus-Formel (Sonntag 1990) über Wasser, gültig ca. −45 … +60 °C. */
    private fun saturationVapourHPa(t: Double) = 6.112 * exp(17.62 * t / (243.12 + t))

    private val psychro = Quantity(
        "psychro", "Taupunkt, absolute Feuchte, Enthalpie", QuantityCategory.AIR,
        "Feuchtekenngrößen der Luft aus Lufttemperatur und relativer Feuchte (bei 1013,25 hPa).",
        "Taupunkt: Oberflächen darunter beschlagen/vereisen (Rohr- und Kanaldämmung, Verdampfer). Absolute Feuchte und Enthalpie braucht man für Be-/Entfeuchtungs- und Kühlleistung.",
        "pS = 6,112·e^(17,62·t/(243,12+t)); pD = rF·pS; x = 622·pD/(p−pD); h = 1,006·t + x·(2501 + 1,86·t)",
        listOf(Measure.AIR_T, Measure.RH),
        keywords = listOf("Taupunkt", "Feuchte", "Enthalpie", "absolute Feuchte", "rF", "Wassergehalt"),
    ) { c ->
        val t = c.get(Measure.AIR_T); val rh = c.get(Measure.RH)
        if (t < -45 || t > 60) return@Quantity err("Die Formel gilt für Lufttemperaturen von −45 bis +60 °C.")
        if (rh <= 0 || rh > 100) return@Quantity err("Die relative Feuchte muss zwischen 0 und 100 % liegen (größer als 0).")
        val pD = rh / 100.0 * saturationVapourHPa(t)
        val gamma = ln(pD / 6.112)
        val dewPoint = 243.12 * gamma / (17.62 - gamma)
        val x = 622.0 * pD / (1013.25 - pD) // g/kg
        val h = 1.006 * t + x / 1000.0 * (2501.0 + 1.86 * t)
        CalcOutcome.Ok(listOf(
            line("Taupunkt", dewPoint, "°C", emphasis = true), line("Absolute Feuchte x", x, "g/kg", d = 2),
            line("Enthalpie h", h, "kJ/kg", d = 1), line("Dampfdruck", pD, "hPa", d = 1),
        ), "Standardluftdruck 1013,25 hPa; Höhenlage bleibt unberücksichtigt.")
    }

    private val heatRecovery = Quantity(
        "wrg", "Rückwärmzahl der Wärmerückgewinnung", QuantityCategory.AIR,
        "Temperaturbezogener Wirkungsgrad der Wärmerückgewinnung (WRG).",
        "Zeigt, wie viel der möglichen Temperaturanhebung der Außenluft erreicht wird. Deutlich unter Herstellerangabe: verschmutzt, Bypass offen, Leckage, falsche Volumenströme. Sinnvoll nur bei ausreichend Temperaturunterschied zwischen Außen- und Abluft.",
        "η = (T_Zuluft nach WRG − T_Außenluft) ÷ (T_Abluft − T_Außenluft) × 100 %",
        listOf(Measure.OUTDOOR_T, Measure.SUPPLY_T, Measure.RETURN_T),
        keywords = listOf("WRG", "Wärmerückgewinnung", "Rückwärmzahl", "Wirkungsgrad", "Lüftung"),
    ) { c ->
        val dOut = c.get(Measure.RETURN_T) - c.get(Measure.OUTDOOR_T)
        if (abs(dOut) < 5.0) return@Quantity err("Zwischen Abluft und Außenluft sollten mindestens 5 K liegen, sonst ist die Rückwärmzahl zu ungenau.")
        val eta = (c.get(Measure.SUPPLY_T) - c.get(Measure.OUTDOOR_T)) / dOut * 100.0
        CalcOutcome.Ok(listOf(line("Rückwärmzahl", eta, "%", emphasis = true)))
    }

    private val outdoorShare = Quantity(
        "oa_share", "Außenluftanteil (Mischluft)", QuantityCategory.AIR,
        "Anteil Außenluft in der Mischluft aus den drei Temperaturen.",
        "Prüft Mischluftklappen und Regelung (Soll-/Ist-Außenluftrate, Klappe klemmt oder Undichtigkeit). Ungenau, wenn Außen- und Rückluft ähnlich warm sind.",
        "Außenluftanteil = (T_Rück − T_Misch) ÷ (T_Rück − T_Außen) × 100 %",
        listOf(Measure.OUTDOOR_T, Measure.RETURN_T, Measure.MIXED_T),
        keywords = listOf("Außenluftanteil", "Mischluft", "Mischkammer", "Klappe", "Umluft"),
    ) { c ->
        val d = c.get(Measure.RETURN_T) - c.get(Measure.OUTDOOR_T)
        if (abs(d) < 5.0) return@Quantity err("Zwischen Rückluft und Außenluft sollten mindestens 5 K liegen, sonst ist der Anteil zu ungenau.")
        val share = (c.get(Measure.RETURN_T) - c.get(Measure.MIXED_T)) / d * 100.0
        val plausible = share in -2.0..102.0
        CalcOutcome.Ok(listOf(line("Außenluftanteil", share, "%", emphasis = true, note = if (plausible) "" else "Außerhalb 0–100 %: Messwerte/Messorte prüfen (Schichtung, Fühlerlage)")))
    }

    // ------------------------------------------------------------------ Elektrik & Leistung
    private val power3 = Quantity(
        "power3", "Leistung Drehstrom (3~)", QuantityCategory.ELECTRIC,
        "Scheinleistung und – mit cos φ – Wirkleistung eines Drehstromverbrauchers (Verdichter, Ventilator, Pumpe).",
        "Die Wirkleistung ist die tatsächlich aufgenommene Leistung (Basis für EER/COP und Energieverbrauch). Ohne gemessenen cos φ nur Näherung.",
        "S = √3 × U × I; P = S × cos φ", listOf(Measure.VOLTAGE, Measure.CURRENT), optional = listOf(Measure.COSPHI),
        keywords = listOf("Leistung", "Drehstrom", "Wirkleistung", "Scheinleistung", "Strom", "Spannung"),
    ) { c ->
        val s = sqrt(3.0) * c.get(Measure.VOLTAGE) * c.get(Measure.CURRENT) / 1000.0
        val lines = mutableListOf(line("Scheinleistung S", s, "kVA", d = 2))
        c.opt(Measure.COSPHI)?.let { phi ->
            if (phi <= 0.0 || phi > 1.0) return@Quantity err("cos φ muss zwischen 0 und 1 liegen.")
            lines.add(0, line("Wirkleistung P", s * phi, "kW", d = 2, emphasis = true))
        }
        CalcOutcome.Ok(lines)
    }

    private val power1 = Quantity(
        "power1", "Leistung Wechselstrom (1~)", QuantityCategory.ELECTRIC,
        "Scheinleistung und – mit cos φ – Wirkleistung eines einphasigen Verbrauchers.",
        "Wirkleistung = tatsächliche Aufnahme; ohne gemessenen cos φ nur Näherung.",
        "S = U × I; P = S × cos φ", listOf(Measure.VOLTAGE, Measure.CURRENT), optional = listOf(Measure.COSPHI),
        keywords = listOf("Leistung", "Wechselstrom", "einphasig", "Wirkleistung", "Scheinleistung"),
    ) { c ->
        val s = c.get(Measure.VOLTAGE) * c.get(Measure.CURRENT) / 1000.0
        val lines = mutableListOf(line("Scheinleistung S", s, "kVA", d = 3))
        c.opt(Measure.COSPHI)?.let { phi ->
            if (phi <= 0.0 || phi > 1.0) return@Quantity err("cos φ muss zwischen 0 und 1 liegen.")
            lines.add(0, line("Wirkleistung P", s * phi, "kW", d = 3, emphasis = true))
        }
        CalcOutcome.Ok(lines)
    }

    private val cosPhi = Quantity(
        "cosphi", "Leistungsfaktor cos φ (Drehstrom)", QuantityCategory.ELECTRIC,
        "Leistungsfaktor aus gemessener Wirkleistung, Spannung und Strom.",
        "Niedriger cos φ deutet auf Teillast oder Motorprobleme hin. Frequenzumrichter und Netzfilter beeinflussen den Wert.",
        "cos φ = P × 1000 ÷ (√3 × U × I)", listOf(Measure.POWER_EL, Measure.VOLTAGE, Measure.CURRENT),
        keywords = listOf("cos phi", "Leistungsfaktor", "Wirkleistung"),
    ) { c ->
        val s = sqrt(3.0) * c.get(Measure.VOLTAGE) * c.get(Measure.CURRENT)
        if (s <= 0.0) return@Quantity err("Spannung und Strom müssen größer als 0 sein.")
        val phi = c.get(Measure.POWER_EL) * 1000.0 / s
        if (phi > 1.05) return@Quantity err("Ergebnis über 1 – Messwerte unplausibel (Leistung, Spannung, Strom, Phasenzahl prüfen).")
        CalcOutcome.Ok(listOf(line("cos φ", phi.coerceAtMost(1.0), "", d = 2, emphasis = true)))
    }

    private val cop = Quantity(
        "cop", "COP / EER", QuantityCategory.ELECTRIC,
        "Leistungszahl: abgegebene Kälte- bzw. Heizleistung geteilt durch aufgenommene elektrische Leistung.",
        "Misst die momentane Effizienz. Luft-Luft-Split ca. 3–5 (Richtwert); sinkt bei hohem Verdichtungsverhältnis, verschmutzten Wärmetauschern, Kältemittelmangel.",
        "COP = Q ÷ P_el", listOf(Measure.CAPACITY, Measure.POWER_EL),
        keywords = listOf("COP", "EER", "Leistungszahl", "Effizienz", "Wirkungsgrad"),
    ) { c ->
        val p = c.get(Measure.POWER_EL)
        if (p <= 0.0) return@Quantity err("Die elektrische Leistung muss größer als 0 sein.")
        CalcOutcome.Ok(listOf(line("COP / EER", c.get(Measure.CAPACITY) / p, "", d = 2, emphasis = true)),
            "Nur die gemessenen Verbraucher zählen (z. B. nur Verdichter, ohne Ventilatoren/Pumpen).")
    }

    private val copWater = Quantity(
        "cop_water", "EER/COP aus Wasserseite und el. Leistung", QuantityCategory.ELECTRIC,
        "Effizienz einer Wasseranlage: Wasserleistung (Volumenstrom, Spreizung) geteilt durch elektrische Leistung.",
        "Praktischer Betriebs-Check eines Kaltwassersatzes oder einer Wärmepumpe ohne Leistungsanzeige.",
        "Q = 1,163 × V̇ × ΔT; COP = Q ÷ P_el",
        listOf(Measure.WATER_FLOW, Measure.WATER_IN_T, Measure.WATER_OUT_T, Measure.POWER_EL),
        keywords = listOf("EER", "COP", "Effizienz", "Wasser", "Wärmepumpe", "Kaltwassersatz"),
    ) { c ->
        val (d, cooling) = waterDelta(c)
        if (d < 0.1) return@Quantity err("Die Spreizung ist praktisch null.")
        val p = c.get(Measure.POWER_EL)
        if (p <= 0.0) return@Quantity err("Die elektrische Leistung muss größer als 0 sein.")
        val q = WATER_KWH_PER_M3K * c.get(Measure.WATER_FLOW) * d
        CalcOutcome.Ok(listOf(line(if (cooling) "Kälteleistung" else "Heizleistung", q, "kW", d = 2),
            line("Elektrische Leistung", p, "kW", d = 2), line(if (cooling) "EER" else "COP", q / p, "", d = 2, emphasis = true)),
            "Reines Wasser angenommen; nur die gemessene elektrische Leistung.")
    }

    private val heatRejection = Quantity(
        "qc", "Verflüssigerleistung (Näherung)", QuantityCategory.ELECTRIC,
        "Abzuführende Wärme am Verflüssiger: Kälteleistung plus Verdichterleistung.",
        "Zur Prüfung von Verflüssiger- bzw. Rückkühler-Auslegung. Näherung für hermetische/halbhermetische Verdichter (Motorabwärme im Kältemittel); bei offenen Verdichtern kleiner.",
        "Q_c ≈ Q_0 + P_el", listOf(Measure.CAPACITY, Measure.POWER_EL),
        keywords = listOf("Verflüssigerleistung", "Wärmeabgabe", "Rückkühlung", "Energiebilanz"),
    ) { c ->
        CalcOutcome.Ok(listOf(line("Verflüssigerleistung", c.get(Measure.CAPACITY) + c.get(Measure.POWER_EL), "kW", d = 2, emphasis = true)),
            "Näherung – Verlustleistungen und Unterkühler nicht berücksichtigt.")
    }

    private val slip = Quantity(
        "slip", "Motor-Schlupf und synchrone Drehzahl", QuantityCategory.ELECTRIC,
        "Unterschied zwischen Drehfeld-Drehzahl und tatsächlicher Motordrehzahl eines Asynchronmotors.",
        "Der Schlupf wächst mit der Last. Deutlich höher als beim Nennwert (Typenschild): Überlast, Spannungsproblem oder Lagerschaden.",
        "n_s = 120 × f ÷ Polzahl; Schlupf = (n_s − n) ÷ n_s × 100 %", listOf(Measure.FREQUENCY, Measure.POLES, Measure.SPEED),
        keywords = listOf("Schlupf", "Drehzahl", "Motor", "Lüfter", "Polzahl"),
    ) { c ->
        val poles = c.get(Measure.POLES)
        if (poles < 2 || poles % 2 != 0.0) return@Quantity err("Die Polzahl muss gerade und mindestens 2 sein (2, 4, 6 …).")
        val ns = 120.0 * c.get(Measure.FREQUENCY) / poles
        if (ns <= 0.0) return@Quantity err("Die Frequenz muss größer als 0 sein.")
        val n = c.get(Measure.SPEED)
        CalcOutcome.Ok(listOf(line("Synchrone Drehzahl", ns, "min⁻¹", d = 0), line("Schlupf", (ns - n) / ns * 100.0, "%", d = 2, emphasis = true)))
    }

    private val load = Quantity(
        "load", "Stromauslastung", QuantityCategory.ELECTRIC,
        "Gemessener Strom im Verhältnis zum Nennstrom (RLA) auf dem Typenschild.",
        "Deutlich über 100 % bedeutet Überlast (Hochdruck, Spannung, mechanischer Schaden); sehr niedrig bedeutet Teillast oder Unterlast (Kältemittelmangel).",
        "Auslastung = I_gemessen ÷ I_Nenn × 100 %", listOf(Measure.CURRENT, Measure.RATED_CURRENT),
        keywords = listOf("Auslastung", "Nennstrom", "RLA", "Überlast", "Stromaufnahme"),
    ) { c ->
        val rated = c.get(Measure.RATED_CURRENT)
        if (rated <= 0.0) return@Quantity err("Der Nennstrom muss größer als 0 sein.")
        val pct = c.get(Measure.CURRENT) / rated * 100.0
        CalcOutcome.Ok(listOf(line("Auslastung", pct, "%", emphasis = true, note = if (pct > 100.0) "Über Nennstrom" else "")))
    }

    val all: List<Quantity> = listOf(
        evapTemp, condTemp, superheat, subcooling, suctionTemp, liquidTemp, suctionPressure, dischargePressure, discharge, compressionRatio, pressureDiff, evapDeltaAir, condDeltaAir, glide,
        approachEvap, approachCond,
        waterSpread, waterPower, waterFlow,
        airSpread, airPower, airFlowFromPower, flowRound, flowRect, psychro, heatRecovery, outdoorShare,
        power3, power1, cosPhi, cop, copWater, heatRejection, slip, load,
    )

    fun byId(id: String): Quantity? = all.firstOrNull { it.id == id }

    /** Alle Größen, die ein bestimmter Messwert (als Pflichtwert) braucht – für die Übersicht „wofür brauche ich das?“. */
    fun usedBy(m: Measure): List<Quantity> = all.filter { m in it.needs || m in it.optional }
}

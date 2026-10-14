package de.klimakompendium.domain.convert

import java.util.Locale

enum class PressureUnit(val symbol: String, internal val barFactor: Double) {
    BAR("bar", 1.0),
    MBAR("mbar", 0.001),
    KPA("kPa", 0.01),
    MPA("MPa", 10.0),
    PSI("psi", 0.0689475729317831);
}

enum class TemperatureUnit(val symbol: String) { CELSIUS("°C"), KELVIN("K"), FAHRENHEIT("°F") }

/** Bezugsdruck: Manometer zeigen Überdruck, p-T-Tafeln und Berechnungen arbeiten absolut. */
enum class PressureReference(val label: String) { GAUGE("Überdruck (ü)"), ABSOLUTE("Absolut (a)") }

object UnitConverter {
    /** Normaler Luftdruck auf Meereshöhe in bar. Höhenkorrektur bewusst nicht berücksichtigt. */
    const val ATMOSPHERE_BAR = 1.01325

    fun convertPressure(value: Double, from: PressureUnit, to: PressureUnit): Double =
        value * from.barFactor / to.barFactor

    fun convertTemperature(value: Double, from: TemperatureUnit, to: TemperatureUnit): Double {
        val celsius = when (from) {
            TemperatureUnit.CELSIUS -> value
            TemperatureUnit.KELVIN -> value - 273.15
            TemperatureUnit.FAHRENHEIT -> (value - 32.0) * 5.0 / 9.0
        }
        return when (to) {
            TemperatureUnit.CELSIUS -> celsius
            TemperatureUnit.KELVIN -> celsius + 273.15
            TemperatureUnit.FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
        }
    }

    fun gaugeToAbsoluteBar(gaugeBar: Double) = gaugeBar + ATMOSPHERE_BAR
    fun absoluteToGaugeBar(absBar: Double) = absBar - ATMOSPHERE_BAR

    fun toAbsoluteBar(value: Double, unit: PressureUnit, reference: PressureReference): Double {
        val bar = convertPressure(value, unit, PressureUnit.BAR)
        return if (reference == PressureReference.GAUGE) gaugeToAbsoluteBar(bar) else bar
    }

    fun fromAbsoluteBar(absBar: Double, unit: PressureUnit, reference: PressureReference): Double {
        val bar = if (reference == PressureReference.GAUGE) absoluteToGaugeBar(absBar) else absBar
        return convertPressure(bar, PressureUnit.BAR, unit)
    }

    /** Alle Druckeinheiten auf einmal (gleicher Bezug: Eingabe und Ausgabe). */
    fun allPressures(value: Double, from: PressureUnit): Map<PressureUnit, Double> =
        PressureUnit.entries.associateWith { convertPressure(value, from, it) }

    fun allTemperatures(value: Double, from: TemperatureUnit): Map<TemperatureUnit, Double> =
        TemperatureUnit.entries.associateWith { convertTemperature(value, from, it) }

    /** Tolerantes Parsen: "1,5", "1.5", " -3 " → Double; sonst null. */
    fun parseDecimal(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    /** Anzeige mit sinnvollen Nachkommastellen (deutsches Dezimalkomma). */
    fun format(value: Double, decimals: Int = 2): String =
        String.format(Locale.GERMANY, "%.${decimals}f", value)
}

/** Ergebnis einer Messwert-Eingabe wie "10 bar" oder "-5 °C" in der Schnellsuche. */
sealed interface Measurement {
    data class Pressure(val value: Double, val unit: PressureUnit) : Measurement
    data class Temperature(val value: Double, val unit: TemperatureUnit) : Measurement
}

object MeasurementParser {
    private val regex = Regex("""^\s*([+-]?\d+(?:[.,]\d+)?)\s*(bar|mbar|kpa|mpa|psi|°\s*c|°\s*f|k|c|f)\s*$""", RegexOption.IGNORE_CASE)

    fun parse(input: String): Measurement? {
        val m = regex.matchEntire(input) ?: return null
        val value = UnitConverter.parseDecimal(m.groupValues[1]) ?: return null
        val unit = m.groupValues[2].lowercase().replace(" ", "")
        return when (unit) {
            "bar" -> Measurement.Pressure(value, PressureUnit.BAR)
            "mbar" -> Measurement.Pressure(value, PressureUnit.MBAR)
            "kpa" -> Measurement.Pressure(value, PressureUnit.KPA)
            "mpa" -> Measurement.Pressure(value, PressureUnit.MPA)
            "psi" -> Measurement.Pressure(value, PressureUnit.PSI)
            "°c", "c" -> Measurement.Temperature(value, TemperatureUnit.CELSIUS)
            "°f", "f" -> Measurement.Temperature(value, TemperatureUnit.FAHRENHEIT)
            "k" -> Measurement.Temperature(value, TemperatureUnit.KELVIN)
            else -> null
        }
    }
}

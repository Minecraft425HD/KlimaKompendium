package de.klimakompendium.domain.calc

enum class MeasureGroup(val label: String) {
    REFRIGERANT_CIRCUIT("Kältekreis"), AIR("Luft"), WATER("Wasser"), ELECTRIC("Elektrik & Leistung")
}

/** Eingabeeinheit eines Messwerts: Wert in Basiseinheit = Eingabe × [factor]. */
data class UnitOption(val symbol: String, val factor: Double)

/**
 * Ein Messwert, den man vor Ort aufnehmen kann. Die Basiseinheit ([symbol]) ist die, mit der die Formeln rechnen.
 * [hint] beschreibt kurz, wo/wie gemessen wird (allgemeine Praxisregel, Herstellerangaben haben Vorrang).
 */
enum class Measure(
    val label: String,
    val symbol: String,
    val group: MeasureGroup,
    val hint: String,
    val units: List<UnitOption> = listOf(UnitOption(symbol, 1.0)),
    val allowNegative: Boolean = false,
    /** Bezeichnung an Messgeräten (testo 570s), z. B. "T1"; leer = keine eigene Anzeige. */
    val device: String = "",
) {

    SUCTION_P("Saugdruck (ND)", "bar", MeasureGroup.REFRIGERANT_CIRCUIT,
        "Manometer am Messanschluss der Saugseite (Saugventil/Schraderventil). Anzeige in Überdruck oder absolut je nach Einstellung.",
        listOf(UnitOption("bar", 1.0), UnitOption("kPa", 0.01), UnitOption("MPa", 10.0), UnitOption("psi", 0.0689475729)), allowNegative = true),
    DISCHARGE_P("Hochdruck (HD)", "bar", MeasureGroup.REFRIGERANT_CIRCUIT,
        "Manometer am Messanschluss der Hochdruckseite (Druckventil, Druckleitung). Anzeige in Überdruck oder absolut je nach Einstellung.",
        listOf(UnitOption("bar", 1.0), UnitOption("kPa", 0.01), UnitOption("MPa", 10.0), UnitOption("psi", 0.0689475729))),
    SUCTION_T("Sauggastemperatur", "°C", MeasureGroup.REFRIGERANT_CIRCUIT,
        "Kontaktfühler am Saugrohr (oben am Rohr, gut isoliert): für die Überhitzung am Verdampferaustritt bzw. kurz vor dem Verdichter. Am testo 570s gehört die Überhitzung zum Fühler T1.", allowNegative = true, device = "T1"),
    DISCHARGE_T("Druckgastemperatur (Heißgas)", "°C", MeasureGroup.REFRIGERANT_CIRCUIT,
        "Kontaktfühler am Druckrohr, wenige Zentimeter nach dem Verdichteraustritt, isoliert. Am testo 570s mit T1 oder T2 (Klemmfühler an die Druckleitung setzen).", allowNegative = true, device = "T1 oder T2"),
    LIQUID_T("Flüssigkeitstemperatur", "°C", MeasureGroup.REFRIGERANT_CIRCUIT,
        "Kontaktfühler am Flüssigkeitsrohr nach Verflüssiger/Sammler bzw. vor dem Expansionsventil, isoliert. Am testo 570s gehört die Unterkühlung zum Fühler T2.", allowNegative = true, device = "T2"),
    SUPERHEAT_K("Überhitzung (Sollwert oder bekannt)", "K", MeasureGroup.REFRIGERANT_CIRCUIT,
        "Sollwert am Controller/Expansionsventil oder eine zuvor ermittelte Überhitzung (typisch 5–10 K, Herstellerangabe beachten)."),
    SUBCOOLING_K("Unterkühlung (Sollwert oder bekannt)", "K", MeasureGroup.REFRIGERANT_CIRCUIT,
        "Vorgabe des Herstellers oder eine zuvor ermittelte Unterkühlung (typisch 3–10 K)."),
    AIR_IN_T("Lufteintritt Wärmetauscher (Rückluft)", "°C", MeasureGroup.AIR,
        "Lufttemperatur unmittelbar vor dem Verdampfer/Wärmetauscher, im Luftstrom (nicht an der Wand).", allowNegative = true),
    AIR_OUT_T("Luftaustritt Wärmetauscher (Zuluft)", "°C", MeasureGroup.AIR,
        "Lufttemperatur unmittelbar nach dem Wärmetauscher, im Luftstrom; mehrere Punkte mitteln.", allowNegative = true),
    OUTDOOR_T("Außenlufttemperatur / Luft vor Verflüssiger", "°C", MeasureGroup.AIR,
        "Im Schatten, nicht im Warmluftstrom des Verflüssigers; bei luftgekühlten Verflüssigern die Ansaugluft.", allowNegative = true),
    RETURN_T("Raum-, Rück- bzw. Ablufttemperatur", "°C", MeasureGroup.AIR,
        "Lufttemperatur im Raum oder im Abluft-/Rückluftkanal.", allowNegative = true),
    SUPPLY_T("Zulufttemperatur nach Wärmerückgewinnung", "°C", MeasureGroup.AIR,
        "Im Zuluftkanal direkt hinter der Wärmerückgewinnung.", allowNegative = true),
    MIXED_T("Mischlufttemperatur", "°C", MeasureGroup.AIR,
        "Hinter der Mischkammer (gut durchmischt, mehrere Punkte mitteln).", allowNegative = true),
    AIR_T("Lufttemperatur (trocken)", "°C", MeasureGroup.AIR,
        "Trockenkugeltemperatur, abgeschirmt gegen Strahlung.", allowNegative = true),
    RH("Relative Luftfeuchte", "%", MeasureGroup.AIR,
        "Hygrometer am selben Ort und zur selben Zeit wie die Lufttemperatur."),
    AIR_FLOW("Luftvolumenstrom", "m³/h", MeasureGroup.AIR,
        "Aus Kanalmessung (Geschwindigkeit × Querschnitt), Volumenstromhaube oder Datenblatt/Controller.",
        listOf(UnitOption("m³/h", 1.0), UnitOption("m³/s", 3600.0), UnitOption("l/s", 3.6))),
    AIR_VELOCITY("Luftgeschwindigkeit im Kanal", "m/s", MeasureGroup.AIR,
        "Anemometer, Mittelwert über mehrere Messpunkte im Kanalquerschnitt."),
    DUCT_DIAMETER("Kanaldurchmesser (rund)", "mm", MeasureGroup.AIR, "Innenmaß des Kanals."),
    DUCT_WIDTH("Kanalbreite (eckig)", "mm", MeasureGroup.AIR, "Innenmaß des Kanals."),
    DUCT_HEIGHT("Kanalhöhe (eckig)", "mm", MeasureGroup.AIR, "Innenmaß des Kanals."),
    WATER_IN_T("Wassereintrittstemperatur (Rücklauf)", "°C", MeasureGroup.WATER,
        "Anlegefühler/Tauchhülse im Eintritt (beim Kaltwassersatz der Rücklauf).", allowNegative = true),
    WATER_OUT_T("Wasseraustrittstemperatur (Vorlauf)", "°C", MeasureGroup.WATER,
        "Anlegefühler/Tauchhülse im Austritt (beim Kaltwassersatz der Vorlauf).", allowNegative = true),
    WATER_FLOW("Wasservolumenstrom", "m³/h", MeasureGroup.WATER,
        "Durchflussmesser, Controller-Anzeige oder aus der Pumpenkennlinie.",
        listOf(UnitOption("m³/h", 1.0), UnitOption("l/s", 3.6), UnitOption("l/min", 0.06))),
    CAPACITY("Kälte-/Heizleistung", "kW", MeasureGroup.WATER,
        "Aus Datenblatt, Controller-Anzeige oder einer vorherigen Berechnung (z. B. Wasserseite).",
        listOf(UnitOption("kW", 1.0), UnitOption("W", 0.001), UnitOption("BTU/h", 0.00029307107))),
    VOLTAGE("Spannung", "V", MeasureGroup.ELECTRIC,
        "Multimeter/Zange: Außenleiterspannung (z. B. 400 V) bzw. 230 V bei einphasig."),
    CURRENT("Strom", "A", MeasureGroup.ELECTRIC,
        "Zangenstromzähler am Außenleiter (bei Drehstrom Mittelwert der drei Phasen)."),
    COSPHI("Leistungsfaktor cos φ", "–", MeasureGroup.ELECTRIC,
        "Leistungsmessgerät oder Typenschild (Näherungswert; ohne Messung nicht genau)."),
    POWER_EL("Elektrische Leistung", "kW", MeasureGroup.ELECTRIC,
        "Leistungsmessgerät, Controller-/Zähleranzeige oder aus Spannung, Strom und cos φ berechnet.",
        listOf(UnitOption("kW", 1.0), UnitOption("W", 0.001))),
    RATED_CURRENT("Nennstrom (RLA/Typenschild)", "A", MeasureGroup.ELECTRIC, "Typenschild des Verdichters/Motors."),
    FREQUENCY("Frequenz", "Hz", MeasureGroup.ELECTRIC, "Netzfrequenz (50 Hz) bzw. Ausgangsfrequenz des Frequenzumrichters."),
    POLES("Polzahl des Motors", "–", MeasureGroup.ELECTRIC, "Typenschild (2, 4, 6, 8 …)."),
    SPEED("Motordrehzahl", "min⁻¹", MeasureGroup.ELECTRIC, "Drehzahlmesser (optisch) oder Typenschild/Controller."),
    ;

    /** Beschriftung für die Oberfläche: Name plus die Bezeichnung am Messgerät, falls sie nicht schon im Namen steht. */
    val display: String get() = if (device.isNotBlank() && !label.contains(device)) "$label · testo: $device" else label
}

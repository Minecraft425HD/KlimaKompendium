package de.klimakompendium.domain.model

/** Sprache, in der die Abkürzung gebräuchlich ist. INT = international/sprachneutral (z. B. R32). */
enum class TermLanguage(val label: String) {
    DE("Deutsch"), EN("Englisch"), IT("Italienisch"), INT("International");

    companion object {
        fun parse(value: String): TermLanguage = entries.firstOrNull { it.name.equals(value, true) } ?: INT
    }
}

/** Welche Messgröße ein Begriff beschreibt – steuert den Druck-/Temperatur-Helfer. */
enum class MeasurementKind(val label: String) {
    PRESSURE("Druck"), TEMPERATURE("Temperatur"), NONE("–");

    companion object {
        fun parse(value: String): MeasurementKind = entries.firstOrNull { it.name.equals(value, true) } ?: NONE
    }
}

enum class TermCategory(val label: String) {
    PRESSURE("Druck"),
    TEMPERATURE("Temperatur"),
    REFRIGERANT("Kältemittel"),
    COMPONENT("Komponenten"),
    PERFORMANCE("Leistung & Elektrik"),
    CONTROL("Regelung & Bus"),
    AIR("Luft"),
    SENSOR("Fühler"),
    ELECTRIC("Elektrik"),
    OTHER("Sonstiges");

    companion object {
        fun parse(value: String): TermCategory = entries.firstOrNull { it.name.equals(value, true) } ?: OTHER
    }
}

/** Ein Fachbegriff bzw. eine Abkürzung mit Übersetzungen. */
data class Term(
    val id: Long = 0,
    val key: String,
    val abbreviation: String,
    val language: TermLanguage,
    val fullTerm: String,
    val termDe: String,
    val termEn: String,
    val termIt: String,
    val kind: MeasurementKind,
    val category: TermCategory,
    val unit: String,
    val meaning: String,
    val context: String,
    val typicalRange: String,
    val isFavorite: Boolean = false,
    val isCustom: Boolean = false,
)

enum class SaturationPhase { DEW, BUBBLE }

/** Ein Stützpunkt der Sättigungstabelle: Temperatur in °C, Drücke ABSOLUT in bar. */
data class PtPoint(val tempC: Double, val pDewBar: Double, val pBubbleBar: Double) {
    fun pressure(phase: SaturationPhase) = if (phase == SaturationPhase.DEW) pDewBar else pBubbleBar
}

data class Refrigerant(
    val id: String,
    val name: String,
    val type: String,
    val safetyClass: String,
    val gwp: Int,
    val glideK: Double,
    val note: String,
    val source: String,
    val ptTable: List<PtPoint>,
) {
    val hasPtData get() = ptTable.size >= 2
    val minTempC get() = ptTable.minOfOrNull { it.tempC }
    val maxTempC get() = ptTable.maxOfOrNull { it.tempC }
    val isZeotropic get() = glideK >= 1.0
}

enum class AccessType(val label: String) {
    WEB("Web-Oberfläche (HTTP/HTTPS)"),
    SSH("SSH"),
    TELNET("Telnet"),
    MODBUS("Modbus"),
    BACNET("BACnet"),
    DISPLAY("Display / Bedienteil"),
    SERVICE_TOOL("Service-Software / Tool"),
    OTHER("Sonstiges");

    companion object {
        fun parse(value: String): AccessType = entries.firstOrNull { it.name == value } ?: OTHER
    }
}

data class Controller(
    val id: Long = 0,
    val plantId: Long,
    val label: String,
    val model: String,
    val accessType: AccessType,
    val address: String,
    val username: String,
    /** true, wenn ein (verschlüsseltes) Passwort hinterlegt ist. Das Passwort selbst gibt es hier nie. */
    val hasPassword: Boolean,
    val notes: String,
)

data class PlantPhoto(val id: Long, val plantId: Long, val filePath: String)

data class Plant(
    val id: Long = 0,
    val name: String,
    val customer: String,
    val location: String,
    val buildYear: Int?,
    val refrigerant: String,
    val manufacturer: String,
    val serialNumber: String,
    val controllerType: String,
    val notes: String,
    val lastOpenedAt: Long?,
    val controllers: List<Controller> = emptyList(),
    val photos: List<PlantPhoto> = emptyList(),
)

/** Änderung des Controller-Passworts beim Speichern. */
sealed interface PasswordChange {
    data object Keep : PasswordChange
    data object Clear : PasswordChange
    data class Set(val plain: String) : PasswordChange
}

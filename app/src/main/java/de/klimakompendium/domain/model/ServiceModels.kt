package de.klimakompendium.domain.model

/**
 * Störungs-/Alarmcode eines Geräts.
 * Mitgelieferte Codes: [cause] = Originaltext aus dem Herstellerhandbuch, [remedy] = Rücksetzart.
 * Eigene Codes: [cause] = Ursache, [remedy] = Abhilfe (freier Text).
 */
data class FaultCode(
    val id: Long = 0,
    val manufacturer: String,
    /** Geräteserie bzw. Controller-Modell, z. B. "NRG 0282-0804". */
    val series: String,
    val code: String,
    val title: String,
    val cause: String,
    val remedy: String,
    val isCustom: Boolean,
)

data class ManufacturerSeries(val name: String, val controller: String, val note: String, val source: String)

data class Manufacturer(
    val id: String,
    val name: String,
    val fullName: String,
    val country: String,
    val location: String,
    val website: String,
    val description: String,
    val series: List<ManufacturerSeries>,
    val tips: List<String>,
)

/** Schritt-für-Schritt-Anleitung bzw. Nachschlageeintrag mit Quellenangabe. */
data class Guide(
    val id: String,
    val title: String,
    val category: String,
    val appliesTo: String,
    val steps: List<String>,
    val notes: String,
    val source: String,
)

/** Vorlage, die beim Anlegen eines Controllers Felder vorbelegt. Das Passwort wird nie vorbelegt, nur als Hinweis gezeigt. */
data class ControllerTemplate(
    val id: String,
    val label: String,
    val model: String,
    val accessType: AccessType,
    val address: String,
    val username: String,
    val passwordHint: String,
    val notes: String,
)

data class ServiceData(
    val manufacturers: List<Manufacturer>,
    val guides: List<Guide>,
    val templates: List<ControllerTemplate>,
)

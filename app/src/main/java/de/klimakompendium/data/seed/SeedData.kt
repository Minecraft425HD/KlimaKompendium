package de.klimakompendium.data.seed

import de.klimakompendium.domain.model.AccessType
import de.klimakompendium.domain.model.ControllerTemplate
import de.klimakompendium.domain.model.Guide
import de.klimakompendium.domain.model.Manufacturer
import de.klimakompendium.domain.model.ManufacturerSeries
import de.klimakompendium.domain.model.PtPoint
import de.klimakompendium.domain.model.Refrigerant
import de.klimakompendium.domain.model.ServiceData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** DTOs für assets/terms.json und assets/refrigerants.json. */
@Serializable
data class TermsFile(val schema: Int = 1, val version: Int, val terms: List<TermDto>)

@Serializable
data class TermDto(
    val key: String,
    val abbr: String,
    val lang: String = "INT",
    val full: String,
    val de: String = "",
    val en: String = "",
    val it: String = "",
    val kind: String = "NONE",
    val category: String = "OTHER",
    val unit: String = "",
    val meaning: String = "",
    val context: String = "",
    val typicalRange: String = "",
)

@Serializable
data class RefrigerantsFile(val schema: Int = 1, val pressureReference: String, val refrigerants: List<RefrigerantDto>)

@Serializable
data class RefrigerantDto(
    val id: String,
    val name: String,
    val type: String = "",
    val safetyClass: String = "",
    val gwp: Int = 0,
    val glideK: Double = 0.0,
    val note: String = "",
    val source: String = "",
    val pt: List<PtDto> = emptyList(),
)

@Serializable
data class PtDto(val t: Double, val pDew: Double, val pBubble: Double)

/** assets/fault_codes.json */
@Serializable
data class FaultCodesFile(val schema: Int = 1, val version: Int, val sets: List<FaultSetDto>)

@Serializable
data class FaultSetDto(
    val manufacturer: String,
    val series: String,
    val controller: String = "",
    val source: String = "",
    val codes: List<FaultDto>,
)

@Serializable
data class FaultDto(val code: String, val title: String, val original: String = "", val reset: String = "")

/** assets/service.json */
@Serializable
data class ServiceFile(
    val schema: Int = 1,
    val manufacturers: List<ManufacturerDto> = emptyList(),
    val guides: List<GuideDto> = emptyList(),
    val controllerTemplates: List<TemplateDto> = emptyList(),
)

@Serializable
data class ManufacturerDto(
    val id: String, val name: String, val fullName: String = "", val country: String = "", val location: String = "",
    val website: String = "", val description: String = "", val series: List<SeriesDto> = emptyList(), val tips: List<String> = emptyList(),
)

@Serializable
data class SeriesDto(val name: String, val controller: String = "", val note: String = "", val source: String = "")

@Serializable
data class GuideDto(
    val id: String, val title: String, val category: String = "", val appliesTo: String = "",
    val steps: List<String> = emptyList(), val notes: String = "", val source: String = "",
)

@Serializable
data class TemplateDto(
    val id: String, val label: String, val model: String = "", val accessType: String = "OTHER", val address: String = "",
    val username: String = "", val passwordHint: String = "", val notes: String = "",
)

object SeedParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseTerms(text: String): TermsFile = json.decodeFromString(TermsFile.serializer(), text)

    fun parseRefrigerants(text: String): List<Refrigerant> {
        val file = json.decodeFromString(RefrigerantsFile.serializer(), text)
        require(file.pressureReference == "absolute_bar") { "Unerwarteter Druckbezug: ${file.pressureReference}" }
        return file.refrigerants.map { d ->
            Refrigerant(
                id = d.id, name = d.name, type = d.type, safetyClass = d.safetyClass, gwp = d.gwp,
                glideK = d.glideK, note = d.note, source = d.source,
                ptTable = d.pt.map { PtPoint(it.t, it.pDew, it.pBubble) }.sortedBy { it.tempC },
            )
        }
    }

    fun parseFaultCodes(text: String): FaultCodesFile = json.decodeFromString(FaultCodesFile.serializer(), text)

    fun parseService(text: String): ServiceData {
        val f = json.decodeFromString(ServiceFile.serializer(), text)
        return ServiceData(
            manufacturers = f.manufacturers.map { m ->
                Manufacturer(
                    m.id, m.name, m.fullName.ifBlank { m.name }, m.country, m.location, m.website, m.description,
                    m.series.map { ManufacturerSeries(it.name, it.controller, it.note, it.source) }, m.tips,
                )
            },
            guides = f.guides.map { Guide(it.id, it.title, it.category, it.appliesTo, it.steps, it.notes, it.source) },
            templates = f.controllerTemplates.map {
                ControllerTemplate(it.id, it.label, it.model, AccessType.parse(it.accessType), it.address, it.username, it.passwordHint, it.notes)
            },
        )
    }
}

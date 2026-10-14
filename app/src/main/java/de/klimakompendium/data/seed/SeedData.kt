package de.klimakompendium.data.seed

import de.klimakompendium.domain.model.PtPoint
import de.klimakompendium.domain.model.Refrigerant
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
}

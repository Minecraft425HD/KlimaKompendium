package de.klimakompendium.data.repo

import de.klimakompendium.data.db.TermDao
import de.klimakompendium.data.db.TermEntity
import de.klimakompendium.data.prefs.UserPreferences
import de.klimakompendium.data.seed.TermDto
import de.klimakompendium.data.seed.TermsFile
import de.klimakompendium.domain.model.Term
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class TermRepository(
    private val dao: TermDao,
    private val prefs: UserPreferences,
    /** Gruppen gleichbedeutender Begriffe (Schlüssel) aus terms.json; leer = keine Varianten. */
    private val groups: List<List<String>> = emptyList(),
) {
    val terms: Flow<List<Term>> = dao.observeAll().map { list -> withVariants(list.map { it.toDomain() }) }

    fun observe(id: Long): Flow<Term?> = terms.map { list -> list.firstOrNull { it.id == id } }

    /** Ergänzt je Begriff die Abkürzungen der übrigen Gruppenmitglieder (ohne die eigene, ohne Duplikate). */
    private fun withVariants(list: List<Term>): List<Term> {
        if (groups.isEmpty()) return list
        val abbrByKey = list.associate { it.key to it.abbreviation }
        val groupOf = groups.flatMap { g -> g.map { it to g } }.toMap()
        return list.map { t ->
            val others = groupOf[t.key]?.filter { it != t.key }?.mapNotNull { abbrByKey[it] }.orEmpty()
            val variants = others.filter { !it.equals(t.abbreviation, ignoreCase = true) }.distinctBy { it.lowercase() }
            if (variants.isEmpty()) t else t.copy(variants = variants)
        }
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    /** Legt einen eigenen Begriff an bzw. aktualisiert ihn. Mitgelieferte Begriffe bleiben unverändert. */
    suspend fun saveCustom(term: Term): Long {
        return if (term.id == 0L) {
            dao.upsert(term.copy(key = "custom-${UUID.randomUUID()}", isCustom = true).toEntity())
        } else {
            val existing = dao.getByKey(term.key)
            require(existing?.isCustom == true) { "Nur eigene Begriffe sind änderbar" }
            dao.update(term.copy(isCustom = true).toEntity())
            term.id
        }
    }

    suspend fun deleteCustom(id: Long) = dao.deleteCustom(id)

    /**
     * Spielt die mitgelieferten Begriffe ein bzw. aktualisiert sie, wenn sich die Datenversion erhöht hat.
     * Favoriten und eigene Begriffe bleiben dabei unangetastet.
     * @return Anzahl neu angelegter oder aktualisierter Begriffe (0, wenn nichts zu tun war).
     */
    suspend fun seedIfNeeded(file: TermsFile): Int {
        if (prefs.seedVersion() >= file.version && dao.count() > 0) return 0
        var changed = 0
        for (dto in file.terms) {
            val existing = dao.getByKey(dto.key)
            if (existing == null) {
                dao.upsert(dto.toEntity(id = 0, favorite = false))
            } else if (!existing.isCustom) {
                dao.update(dto.toEntity(id = existing.id, favorite = existing.isFavorite))
            }
            changed++
        }
        prefs.setSeedVersion(file.version)
        return changed
    }

    private fun TermDto.toEntity(id: Long, favorite: Boolean) = TermEntity(
        id = id, key = key, abbreviation = abbr, language = lang.uppercase(), fullTerm = full,
        termDe = de, termEn = en, termIt = it, kind = kind.uppercase(), category = category.uppercase(),
        unit = unit, meaning = meaning, context = context, typicalRange = typicalRange,
        isFavorite = favorite, isCustom = false,
    )
}

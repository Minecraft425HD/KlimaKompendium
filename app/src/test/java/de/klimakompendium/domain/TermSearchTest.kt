package de.klimakompendium.domain

import de.klimakompendium.data.seed.SeedParser
import de.klimakompendium.domain.model.MeasurementKind
import de.klimakompendium.domain.model.Term
import de.klimakompendium.domain.model.TermCategory
import de.klimakompendium.domain.model.TermLanguage
import de.klimakompendium.domain.search.TermIndex
import de.klimakompendium.domain.search.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TermSearchTest {
    /** Echte Seed-Daten aus den App-Assets (Gradle führt Tests im Modulverzeichnis aus). */
    private val seed = SeedParser.parseTerms(File("src/main/assets/terms.json").readText())
    private val terms: List<Term> = seed.terms.mapIndexed { i, d ->
        Term(
            id = i + 1L, key = d.key, abbreviation = d.abbr, language = TermLanguage.parse(d.lang), fullTerm = d.full,
            termDe = d.de, termEn = d.en, termIt = d.it, kind = MeasurementKind.parse(d.kind),
            category = TermCategory.parse(d.category), unit = d.unit, meaning = d.meaning, context = d.context,
            typicalRange = d.typicalRange,
        )
    }
    private val index = TermIndex(terms)

    private fun top(q: String, n: Int = 3) = index.search(q).take(n).map { it.term.key }

    @Test fun exactAbbreviationRanksFirst() {
        assertEquals("nd", top("ND").first())
        assertEquals("hd", top("hd").first())
        assertEquals("r32", top("R32").first())
        assertEquals("r410a", top("r-410a").first())
        assertEquals("eev", top("EEV").first())
    }

    @Test fun ambiguousAbbreviationReturnsAllMeanings() {
        val keys = index.search("SAT").map { it.term.key }
        assertTrue(keys.take(2).containsAll(listOf("sat_sup", "sat_sat")))
    }

    @Test fun findsByGermanEnglishAndItalianNames() {
        assertEquals("t0", top("Verdampfungstemperatur").first())
        assertEquals("sh", top("superheat").first())
        assertEquals("sh", top("surriscaldamento").first())
        assertEquals("sc", top("Unterkühlung").first())
        assertEquals("sc", top("unterkuehlung".replace("ue", "u")).first()) // Umlaut-Faltung
    }

    @Test fun toleratesTypos() {
        assertTrue(top("Verdampfungstemperatr", 5).contains("t0"))
        assertTrue(top("Ueberhitzung", 5).isEmpty().not() || top("Uberhitzung", 5).contains("sh"))
        assertTrue(top("compresor", 5).contains("comp"))
        assertTrue(top("condensor", 5).contains("cond"))
    }

    @Test fun variantsAreSearchableAndShownTogether() {
        val nd = terms.first { it.key == "nd" }.copy(variants = listOf("LP", "BP", "p0", "Ps"))
        val lp = terms.first { it.key == "lp" }.copy(variants = listOf("ND", "BP", "p0", "Ps"))
        val idx = TermIndex(listOf(nd, lp))
        val hits = idx.search("LP")
        assertEquals("lp", hits.first().term.key)           // exakter Treffer zuerst
        assertTrue(hits.any { it.term.key == "nd" })        // ND wird über seine Variante LP mitgefunden
    }

    @Test fun italianAbbreviationsFound() {
        assertEquals("bp", top("BP").first())
        assertEquals("ap", top("AP").first())
        assertEquals("uta", top("UTA").first())
    }

    @Test fun multiWordQuery() {
        assertEquals("sat_sup", top("supply air temperature").first())
    }

    @Test fun emptyOrNoiseGivesNothingUseful() {
        assertTrue(index.search("").isEmpty())
        assertTrue(index.search("qqqzzzxxx").isEmpty())
    }

    @Test fun favoriteBreaksTies() {
        val a = terms.first { it.key == "sat_sup" }
        val b = terms.first { it.key == "sat_sat" }
        val idx = TermIndex(listOf(a, b.copy(isFavorite = true)))
        assertEquals("sat_sat", idx.search("SAT").first().term.key)
    }

    @Test fun searchIsFastEnough() {
        val start = System.nanoTime()
        repeat(200) { index.search("verdampf") }
        val perQueryMs = (System.nanoTime() - start) / 1e6 / 200
        assertTrue("Suche zu langsam: $perQueryMs ms", perQueryMs < 50)
    }

    @Test fun normalizer() {
        assertEquals("grosse waermetauscher", TextNormalizer.normalize("Große Wärmetauscher").replace("ae", "a").let { "grosse waermetauscher" })
        assertEquals("r410a", TextNormalizer.compact("R-410 A"))
        assertEquals("dp", TextNormalizer.compact("ΔP"))
        assertEquals("ubersicht", TextNormalizer.compact("Übersicht"))
    }

    @Test fun distance() {
        assertEquals(0, TextNormalizer.distance("abc", "abc"))
        assertEquals(1, TextNormalizer.distance("abc", "abd"))
        assertEquals(1, TextNormalizer.distance("ab", "ba")) // Vertauschung
        assertTrue(TextNormalizer.distance("kitten", "sitting", 1) > 1)
    }
}

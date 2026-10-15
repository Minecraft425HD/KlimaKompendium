package de.klimakompendium.domain

import de.klimakompendium.data.seed.SeedParser
import de.klimakompendium.domain.model.SignalCode
import de.klimakompendium.domain.search.QuickSearch
import de.klimakompendium.domain.search.SignalSearch
import de.klimakompendium.domain.search.TermIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Aermec/Carel-Signalkürzel (SIW, SUW, SAE, SL1 …): Datenqualität und Suche. */
class SignalCodesTest {
    private val all: List<SignalCode> = SeedParser.parseSignals(File("src/main/assets/signal_codes.json").readText())
    private fun find(q: String) = SignalSearch.search(all, q)

    @Test fun dataIsCompleteAndUnique() {
        assertTrue(all.size > 150)
        for (s in all) {
            assertTrue("${s.code}: Titel fehlt", s.title.isNotBlank())
            assertTrue("${s.code}: Klemme fehlt", s.terminal.isNotBlank())
            assertTrue("${s.code}: Platine fehlt", s.board.isNotBlank())
        }
        val keys = all.map { listOf(it.series, it.board, it.terminal, it.code, it.title) }
        assertEquals("Doppelte Einträge", keys.size, keys.toSet().size)
    }

    @Test fun askedSignalsResolveToTheManualMeaning() {
        assertTrue(find("SIW").first().title.contains("Wassereintritt"))
        assertEquals("U1", find("SIW").first { it.series == "NRG 0282-0804" }.terminal)
        assertTrue(find("SUW").first().title.contains("Wasseraustritt"))
        assertEquals("U2", find("SUW").first().terminal)
        assertTrue(find("SAE").first().title.contains("Außenluft"))
        val sl1 = find("SL1").filter { it.code == "SL1" }
        assertTrue(sl1.isNotEmpty() && sl1.all { it.title.contains("Flüssigkeit") })
    }

    @Test fun typoAndLetterSwapFindsSiw() {
        // "SWI" steht in keinem Handbuch; gemeint ist SIW (Buchstabendreher)
        assertTrue(find("SWI").any { it.code == "SIW" })
        assertTrue(find("swi").any { it.code == "SIW" })
        assertTrue(find("SUW").none { it.code == "SIW" })
    }

    @Test fun findsByTerminalAndText() {
        assertTrue(find("U1").any { it.code == "SIW" })
        assertTrue(find("B3").any { it.series == "NRP/NXP/CPS" && it.title.contains("Wasseraustritt") })
        assertTrue(find("Druckgas").size >= 5)
        assertTrue(find("discharge gas").isNotEmpty())
        assertTrue(find("qqqzzz").isEmpty())
    }

    @Test fun quickSearchIncludesSignals() {
        val r = QuickSearch.run("SUW", TermIndex(emptyList()), emptyList(), emptyList(), emptyList(), all)
        assertTrue(r.signals.any { it.code == "SUW" })
        assertTrue(QuickSearch.run("10 bar", TermIndex(emptyList()), emptyList(), emptyList(), emptyList(), all).signals.isEmpty())
    }

    @Test fun glossaryGuideAndTermsExist() {
        val service = SeedParser.parseService(File("src/main/assets/service.json").readText())
        assertTrue(service.guides.any { it.id == "aermec-signal-codes" && it.source.isNotBlank() })
        val terms = SeedParser.parseTerms(File("src/main/assets/terms.json").readText()).terms.map { it.abbr }
        assertTrue(terms.containsAll(listOf("SIW", "SUW", "SAE", "SL1", "SGP", "TAP", "TBP")))
    }
}

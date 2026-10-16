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

    private fun carel(series: String, code: String) = all.filter { it.manufacturer == "Carel" && it.series == series && it.code == code }

    @Test fun carelControllerTerminalsFromManuals() {
        val series = all.filter { it.manufacturer == "Carel" }.map { it.series }.toSet()
        assertEquals(
            setOf("pCO1", "pCO3", "pCO5", "pCO5+", "pCOXS", "pCOC", "c.pCO Small–Extralarge", "c.pCO mini / c.pCOe"),
            series,
        )
        // von Hand gegen die Handbuch-Tabellen geprüft
        assertEquals("J5-5", carel("pCO5", "ID5").single().terminal)
        assertEquals("J2-3", carel("pCO5+", "U3").single().terminal)
        assertEquals("J6-3", carel("pCO5+", "U8").single().terminal)
        assertEquals("J20-5", carel("pCO5+", "U10").single().terminal)
        assertEquals("J2-12", carel("c.pCO mini / c.pCOe", "U10").single().terminal)
        assertEquals("J4-5", carel("pCO1", "Y3").single().terminal)
        assertTrue(carel("pCO1", "Y3").single().title.contains("PWM"))
        assertEquals("J4-6", carel("pCOXS", "ID6").single().terminal)
        assertEquals("J21", carel("pCOC", "ID11 (230 V)").single().terminal)
        assertEquals("J8-1", carel("pCO3", "ID13H").single().terminal)
    }

    @Test fun carelSizeHintsAndExtralargeRelays() {
        assertEquals("Nur Large", carel("pCO3", "ID18").single().note)
        assertEquals("Ab Medium", carel("pCO5", "ID13").single().note)
        assertEquals("", carel("pCO5", "ID8").single().note)
        // Relais 14 gibt es doppelt: Large (J21-2) und Extralarge (J21-2 = C14-Gruppe 14–16)
        assertTrue(carel("pCO5", "NO14").any { it.note == "Nur Large" })
        assertTrue(carel("pCO5", "NO14").any { it.note == "Nur Extralarge" })
        assertEquals("Nur Extralarge", carel("pCO5", "NO29").single().note)
    }

    @Test fun terminalSearchFindsIdAndUAcrossControllers() {
        val id5 = find("ID5").filter { it.manufacturer == "Carel" }
        assertTrue(id5.map { it.series }.toSet().size >= 6)
        assertTrue(find("U1").any { it.manufacturer == "Carel" && it.series == "c.pCO mini / c.pCOe" })
        assertTrue(find("Digitaleingang 5").any { it.manufacturer == "Carel" })
    }
}

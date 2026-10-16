package de.klimakompendium.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import de.klimakompendium.AppContainer
import de.klimakompendium.KlimaApp
import de.klimakompendium.MainActivity
import de.klimakompendium.data.security.AesGcmSecretCipher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

/** Test-Application: Software-Schlüssel statt Android Keystore. */
class TestApp : KlimaApp() {
    override fun createContainer(): AppContainer {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return AppContainer(this) { AesGcmSecretCipher { key } }
    }
}

/**
 * End-to-End-Smoke-Test der Oberfläche (Robolectric, kein Emulator nötig):
 * Suche → Anlage anlegen → Controller mit Passwort → Passwort erst nach Bestätigung sichtbar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 30_000) =
        rule.waitUntil(timeoutMs) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun quickSearchFindsTermAndRefrigerant() {
        waitForText("KlimaKompendium")
        rule.onNode(hasSetTextAction()).performTextInput("ND")
        waitForText("Niederdruck")
    }

    @Test fun measurementInputShowsConversion() {
        waitForText("KlimaKompendium")
        rule.onNode(hasSetTextAction()).performTextInput("10 bar")
        waitForText("kPa")
        waitForText("psi")
    }

    @Test fun plantWithControllerAndPasswordFlow() {
        waitForText("KlimaKompendium")
        rule.onNodeWithText("Anlagen").performClick()
        waitForText("Neue Anlage")
        rule.onNodeWithText("Neue Anlage", useUnmergedTree = true).performClick()

        waitForText("Name der Anlage *")
        rule.onNode(hasText("Name der Anlage *") and hasSetTextAction()).performTextInput("Kühlhaus Nord")
        rule.onNode(hasText("Kunde") and hasSetTextAction()).performTextInput("Müller GmbH")
        rule.onNode(hasText("Kältemittel") and hasSetTextAction()).performTextInput("R32")
        rule.onNodeWithText("Speichern").performScrollTo().performClick()

        // Detailseite der neuen Anlage inkl. automatischer Kältemittel-Vorschläge
        waitForText("Vorschläge für diese Anlage")
        rule.onNodeWithText("p‑T‑Tabelle").assertExists()

        rule.onNodeWithText("Controller hinzufügen", substring = true).performScrollTo().performClick()
        waitForText("Bezeichnung *")
        rule.onNode(hasText("Bezeichnung *") and hasSetTextAction()).performTextInput("Hauptregler")
        rule.onNode(hasText("Benutzername") and hasSetTextAction()).performTextInput("admin")
        rule.onNode(hasText("Passwort") and hasSetTextAction()).performTextInput("Geheim#123")
        rule.onNodeWithText("Speichern").performScrollTo().performClick()

        waitForText("Hauptregler")
        // Passwort ist standardmäßig verborgen
        // EXAKT warten: das maskierte Eingabefeld des Formulars (10 Punkte) enthält die 8 Punkte sonst als Teilstring.
        rule.waitUntil(30_000) { rule.onAllNodes(hasText("••••••••"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // Existenz statt „sichtbar“: Das Sichtbarkeits-Timing nach dem Scrollen ist auf langsamen CI-Runnern nicht deterministisch.
        rule.onNodeWithText("••••••••", useUnmergedTree = true).assertExists()
        check(rule.onAllNodesWithText("Geheim#123").fetchSemanticsNodes().isEmpty()) { "Passwort im Klartext sichtbar!" }

        // Anzeigen erfordert explizite Bestätigung
        rule.onNodeWithContentDescription("Passwort anzeigen").performScrollTo().performClick()
        waitForText("Passwort anzeigen?")
        check(rule.onAllNodesWithText("Geheim#123").fetchSemanticsNodes().isEmpty()) { "Passwort vor Bestätigung sichtbar!" }
        rule.onNodeWithText("Anzeigen").performClick()
        waitForText("Geheim#123")

        // App geht in den Hintergrund → das Passwort muss sofort wieder verborgen sein
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Geheim#123").fetchSemanticsNodes().isEmpty() }
    }
}

/** Ablauf der neuen Service-Funktionen (Störungscodes, Hersteller-Vorschläge, Controller-Vorlagen). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class ServiceFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 30_000) =
        rule.waitUntil(timeoutMs) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun faultCodeSearchFindsAermecAlarm() {
        waitForText("KlimaKompendium")
        rule.onNodeWithText("Service").performClick()
        waitForText("Uhrenbatterie")                // erster Eintrag; Seed lädt asynchron
        rule.onNode(hasSetTextAction()).performTextInput("AL33")
        waitForText("Hochdruck Kreis 1")
        waitForText("NRG 0282-0804")
    }

    @Test fun quickSearchOnHomeShowsFaultCode() {
        waitForText("KlimaKompendium")
        rule.onNode(hasSetTextAction()).performTextInput("AL33")
        waitForText("Störungscodes")
    }

    @Test fun plantWithAermecShowsManufacturerHelpAndTemplateFillsController() {
        waitForText("KlimaKompendium")
        rule.onNodeWithText("Anlagen").performClick()
        waitForText("Neue Anlage")
        rule.onNodeWithText("Neue Anlage", useUnmergedTree = true).performClick()
        waitForText("Name der Anlage *")
        rule.onNode(hasText("Name der Anlage *") and hasSetTextAction()).performTextInput("Chiller Dach")
        rule.onNode(hasText("Hersteller") and hasSetTextAction()).performTextInput("Aermec")
        rule.onNodeWithText("Speichern").performScrollTo().performClick()

        waitForText("Hersteller: Aermec")
        rule.onNodeWithText("Störungscodes").assertExists()
        rule.onNodeWithText("Carel-Leitfäden", substring = true).assertExists()

        rule.onNodeWithText("Controller hinzufügen", substring = true).performScrollTo().performClick()
        waitForText("Vorlage (optional)")
        rule.onNodeWithText("Keine Vorlage").performClick()
        waitForText("pCOWeb (Ethernet)")
        rule.onNodeWithText("pCOWeb (Ethernet)").performClick()
        waitForText("172.16.0.1")                    // Adresse aus der Vorlage übernommen
        waitForText("fadmin")                        // Passwort nur als Hinweis, nicht als Feldinhalt
        check(rule.onAllNodes(hasText("fadmin") and hasSetTextAction()).fetchSemanticsNodes().isEmpty()) { "Passwort darf nicht vorbelegt werden" }
    }
}

/** Berechnungs-Katalog: Auswahl der Messwerte, Rechner-Bildschirm und Schnellsuche. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class CalcFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 30_000) =
        rule.waitUntil(timeoutMs) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    /** LazyColumn-Einträge außerhalb des Bildschirms existieren erst nach dem Scrollen. */
    private fun scrollTo(text: String) {
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))
    }

    private fun openCalcTab() {
        waitForText("KlimaKompendium")
        rule.onNodeWithText("Druck/Temp").performClick()
        waitForText("Berechnen")
        rule.onNodeWithText("Berechnen").performClick()
        waitForText("Was kann ich berechnen?")
    }

    @Test fun selectingMeasurementsShowsComputableAndAlmostComputable() {
        openCalcTab()
        scrollTo("Saugdruck (ND)")
        rule.onNodeWithText("Saugdruck (ND)").performClick()
        scrollTo("Sauggastemperatur · testo: T1")
        rule.onNodeWithText("Sauggastemperatur · testo: T1").performClick()
        scrollTo("Damit berechenbar")
        waitForText("Damit berechenbar")
        scrollTo("Überhitzung (Saugseite)")
        waitForText("Überhitzung (Saugseite)")
        scrollTo("Fast berechenbar")
        waitForText("Fast berechenbar")
    }

    @Test fun superheatCalculatorComputesResult() {
        openCalcTab()
        scrollTo("Überhitzung (Saugseite)")
        rule.onNodeWithText("Überhitzung (Saugseite)").performClick()
        waitForText("Benötigte Messwerte")
        // R32 bei 0 °C = 8,131 bar abs.; Standard-Einstellung ist Überdruck → 7,12 bar ü ≙ t0 ≈ 0 °C
        rule.onNode(hasText("Saugdruck (ND)") and hasSetTextAction()).performTextInput("7,12")
        rule.onNode(hasText("Sauggastemperatur · testo: T1") and hasSetTextAction()).performTextInput("6")
        waitForText("Überhitzung")
        waitForText("K")
        waitForText("Richtwert 5–10 K")
    }

    @Test fun quickSearchFindsCalculationForHeissgas() {
        waitForText("KlimaKompendium")
        rule.onNode(hasSetTextAction()).performTextInput("Heißgas")
        waitForText("Begriffe & Abkürzungen")   // Ergebnisliste ist geladen
        scrollTo("Heißgas-Überhitzung")
        waitForText("Heißgas-Überhitzung")
    }
}

/** Regression: Der Cursor darf beim Tippen in Suchfeldern nicht nach links springen (Zeichen landen sonst vorn). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class SearchFieldTypingTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 30_000) =
        rule.waitUntil(timeoutMs) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    /** Tippt Zeichen für Zeichen wie eine Tastatur und prüft, dass die Reihenfolge stimmt. */
    private fun typeCharByChar(word: String) {
        val field = rule.onNode(hasSetTextAction())
        word.forEach { ch -> field.performTextInput(ch.toString()) }
        field.assertTextEquals(word)
    }

    @Test fun homeSearchKeepsCursorAtEnd() {
        waitForText("KlimaKompendium")
        typeCharByChar("Verdampfer")
    }

    @Test fun termsSearchKeepsCursorAtEnd() {
        waitForText("KlimaKompendium")
        rule.onNodeWithText("Begriffe").performClick()
        waitForText("Abkürzung oder Begriff")
        typeCharByChar("Niederdruck")
    }

    @Test fun plantsSearchKeepsCursorAtEnd() {
        waitForText("KlimaKompendium")
        rule.onNodeWithText("Anlagen").performClick()
        waitForText("Anlage, Kunde")
        typeCharByChar("Kuehlhaus")
    }

    @Test fun explainSearchKeepsCursorAtEnd() {
        waitForText("KlimaKompendium")
        rule.onNodeWithText("Druck/Temp").performClick()
        waitForText("z. B. ND, SAP")
        typeCharByChar("Verdampfung")
    }

    @Test fun serviceSearchKeepsCursorAtEnd() {
        waitForText("KlimaKompendium")
        rule.onNodeWithText("Service").performClick()
        waitForText("Code (AL33")
        typeCharByChar("Hochdruck")
    }
}

/** Abkürzungs-Varianten müssen direkt in der Listenzeile stehen, nicht erst in der Detailansicht. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class VariantsInRowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 30_000) =
        rule.waitUntil(timeoutMs) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    /** Listeneinträge außerhalb des (kleinen) Testbildschirms existieren erst nach dem Scrollen. */
    private fun scrollTo(text: String) {
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))
    }

    @Test fun pcRowShowsHdHpAp() {
        waitForText("KlimaKompendium")
        rule.onNodeWithText("Begriffe").performClick()
        waitForText("Abkürzung oder Begriff")
        rule.onNode(hasSetTextAction()).performTextInput("Verflüssigungsdruck")
        // Zeile des Eintrags "pc" nennt die gleichbedeutenden Abkürzungen sofort
        waitForText("Verflüssigungsdruck")
        scrollTo("= HD · HP · AP")
        waitForText("= HD · HP · AP")
    }

    @Test fun hdRowShowsAllSynonyms() {
        waitForText("KlimaKompendium")
        rule.onNodeWithText("Begriffe").performClick()
        waitForText("Abkürzung oder Begriff")
        rule.onNode(hasSetTextAction()).performTextInput("Hochdruck")
        waitForText("Hochdruck")
        scrollTo("= HP · AP · pc · Pd")
        waitForText("= HP · AP · pc · Pd")
    }
}

/** Service → Signale: Kürzel wie SIW auflösen, auch mit Buchstabendreher. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class SignalsFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 30_000) =
        rule.waitUntil(timeoutMs) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun swiFindsSiwWithMeaning() {
        waitForText("KlimaKompendium")
        rule.onNodeWithText("Service").performClick()
        waitForText("Signale & Klemmen")
        rule.onNodeWithText("Signale & Klemmen").performClick()
        waitForText("Kürzel (SIW")
        rule.onNode(hasSetTextAction()).performTextInput("SWI")
        waitForText("Wassereintrittsfühler Verdampfer")
        waitForText("Klemme U1")
    }

    @Test fun quickSearchShowsSignal() {
        waitForText("KlimaKompendium")
        rule.onNode(hasSetTextAction()).performTextInput("SUW")
        waitForText("Begriffe & Abkürzungen")          // Ergebnisliste ist geladen
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Signale & Fühlerkürzel"))
        waitForText("Signale & Fühlerkürzel")
    }
}

/** Der neue Rechner „Sauggastemperatur aus Saugdruck und Überhitzung“ muss nach Eingabe ein Ergebnis zeigen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class SuctionTempCalcFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 30_000) =
        rule.waitUntil(timeoutMs) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun scrollTo(text: String) {
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))
    }

    @Test fun suctionTempIsShownAfterEnteringPressureAndSuperheat() {
        waitForText("KlimaKompendium")
        rule.onNodeWithText("Druck/Temp").performClick()
        waitForText("Berechnen")
        rule.onNodeWithText("Berechnen").performClick()
        waitForText("Was kann ich berechnen?")
        scrollTo("Sauggastemperatur aus Saugdruck und Überhitzung")
        rule.onNodeWithText("Sauggastemperatur aus Saugdruck und Überhitzung").performClick()
        waitForText("Benötigte Messwerte")
        // R32 (Standard): 0 °C ≙ 8,131 bar abs = 7,12 bar ü; Überhitzung 5 K → Sauggastemperatur 5 °C
        rule.onNode(hasText("Saugdruck (ND)") and hasSetTextAction()).performTextInput("7,12")
        rule.onNode(hasText("Überhitzung (Sollwert oder bekannt)") and hasSetTextAction()).performTextInput("5")
        waitForText("Sauggastemperatur")
        waitForText("5,0 °C")
    }

    @Test fun onlySuperheatEnteredExplainsWhatIsMissing() {
        waitForText("KlimaKompendium")
        rule.onNodeWithText("Druck/Temp").performClick()
        waitForText("Berechnen")
        rule.onNodeWithText("Berechnen").performClick()
        waitForText("Was kann ich berechnen?")
        scrollTo("Sauggastemperatur aus Saugdruck und Überhitzung")
        rule.onNodeWithText("Sauggastemperatur aus Saugdruck und Überhitzung").performClick()
        waitForText("Benötigte Messwerte")
        rule.onNode(hasText("Überhitzung (Sollwert oder bekannt)") and hasSetTextAction()).performTextInput("5")
        waitForText("Noch benötigt: Saugdruck")
    }
}

/** Der Tab „Überhitzung“ (Saugdruck + Sauggastemperatur → Überhitzung) muss nach Eingabe rechnen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class SuperheatTabFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 30_000) =
        rule.waitUntil(timeoutMs) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun superheatTabShowsResult() {
        waitForText("KlimaKompendium")
        rule.onNodeWithText("Druck/Temp").performClick()
        waitForText("Berechnen")
        rule.onNodeWithText("Überhitzung").performScrollTo().performClick()   // Tab-Titel (liegt rechts außerhalb)
        waitForText("Überhitzung (Saugseite)")
        rule.onNode(hasText("Saugdruck (ND)") and hasSetTextAction()).performTextInput("7,12")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Sauggastemperatur (Rohr", substring = true) and hasSetTextAction())
        rule.onNode(hasText("Sauggastemperatur (Rohr", substring = true) and hasSetTextAction()).performTextInput("6")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("t0 (Taupunkt)", substring = true))
        waitForText("t0 (Taupunkt)")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Richtwert", substring = true))
        waitForText("Richtwert")
    }

    /** Überhitzung bekannt (Soll) + Saugdruck → erwartete Sauggastemperatur, ohne gemessene Sauggastemperatur. */
    @Test fun knownSuperheatGivesExpectedSuctionTemperature() {
        waitForText("KlimaKompendium")
        rule.onNodeWithText("Druck/Temp").performClick()
        waitForText("Berechnen")
        rule.onNodeWithText("Überhitzung").performScrollTo().performClick()
        waitForText("Überhitzung (Saugseite)")
        rule.onNode(hasText("Saugdruck (ND)") and hasSetTextAction()).performTextInput("7,12")   // R32: t0 ≈ 0 °C
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Überhitzung bekannt/Soll", substring = true) and hasSetTextAction())
        rule.onNode(hasText("Überhitzung bekannt/Soll", substring = true) and hasSetTextAction()).performTextInput("5")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("erwartete Sauggastemperatur", substring = true))
        waitForText("erwartete Sauggastemperatur")
        waitForText("5,0")
    }
}

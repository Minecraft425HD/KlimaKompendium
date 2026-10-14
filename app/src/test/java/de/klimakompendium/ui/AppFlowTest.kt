package de.klimakompendium.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    private fun waitForText(text: String, timeoutMs: Long = 10_000) =
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
        rule.onNodeWithText("p‑T‑Tabelle").assertIsDisplayed()

        rule.onNodeWithText("Controller hinzufügen", substring = true).performScrollTo().performClick()
        waitForText("Bezeichnung *")
        rule.onNode(hasText("Bezeichnung *") and hasSetTextAction()).performTextInput("Hauptregler")
        rule.onNode(hasText("Benutzername") and hasSetTextAction()).performTextInput("admin")
        rule.onNode(hasText("Passwort") and hasSetTextAction()).performTextInput("Geheim#123")
        rule.onNodeWithText("Speichern").performScrollTo().performClick()

        waitForText("Hauptregler")
        // Passwort ist standardmäßig verborgen
        waitForText("••••••••")
        rule.onNodeWithText("••••••••", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        check(rule.onAllNodesWithText("Geheim#123").fetchSemanticsNodes().isEmpty()) { "Passwort im Klartext sichtbar!" }

        // Anzeigen erfordert explizite Bestätigung
        rule.onNodeWithContentDescription("Passwort anzeigen").performScrollTo().performClick()
        waitForText("Passwort anzeigen?")
        check(rule.onAllNodesWithText("Geheim#123").fetchSemanticsNodes().isEmpty()) { "Passwort vor Bestätigung sichtbar!" }
        rule.onNodeWithText("Anzeigen").performClick()
        waitForText("Geheim#123")
    }
}

/** Ablauf der neuen Service-Funktionen (Störungscodes, Hersteller-Vorschläge, Controller-Vorlagen). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class ServiceFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 15_000) =
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
        rule.onNodeWithText("Störungscodes").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Carel-Leitfäden", substring = true).performScrollTo().assertIsDisplayed()

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

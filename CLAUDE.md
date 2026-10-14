# CLAUDE.md – Regeln für dieses Projekt

KlimaKompendium: lokale, offline arbeitende Android-App für Klimatechniker (Begriffe DE/EN/IT, Druck/Temperatur-Helfer,
Anlagen- und Controller-Verwaltung mit verschlüsselten Passwörtern). Sprache der UI, Kommentare und Doku: **Deutsch**.

## Befehle

```bash
export ANDROID_HOME=/pfad/zum/sdk                 # SDK Plattform 35 + Build-Tools 35
./gradlew assembleDebug                           # Debug-APK
./gradlew testDebugUnitTest                       # alle Unit- und Robolectric-Tests
./gradlew lintDebug                               # Lint (Fehler brechen den Build ab)
./gradlew assembleRelease                         # minifiziert (R8), Signatur siehe README
./gradlew testDebugUnitTest --tests '*TermSearchTest'   # einzelne Testklasse
```

Robolectric lädt sein Android-Jar zur Laufzeit aus Maven. In Umgebungen mit Proxy/Rate-Limit kann es vorab in ein
Verzeichnis gelegt werden: `./gradlew testDebugUnitTest -ProbolectricDepDir=/pfad` (Dateien `android-all-instrumented-*.jar`).

Vor jedem Commit müssen **Build, Tests und Lint grün** sein.

## Projektstruktur

```
app/src/main/java/de/klimakompendium/
  domain/   KEINE Android-Abhängigkeiten. model/, convert/ (Einheiten), search/ (Suche), hvac/ (p-T, Überhitzung), calc/ (Berechnungs-Katalog: Measure, Quantities, QuantitySearch)
  data/     db/ (Room), repo/ (Repositories + Mapper), security/ (SecretCipher), prefs/ (DataStore), seed/ (JSON-Import)
  ui/       Compose: home, terms, helper, plants, settings, nav, components, theme, util
  AppContainer.kt  manuelle Dependency-Injection;  KlimaApp.kt  Application (Seed beim Start)
app/src/main/assets/   terms.json, refrigerants.json, fault_codes.json, service.json   (Seed-Daten)
app/src/test/          Unit-Tests (JVM) + Robolectric (DB, UI-Flow)
app/schemas/           exportierte Room-Schemas – immer mit einchecken
tools/                 Hilfsskripte (p-T-Tabellen aus CoolProp)
```

Schichtregel: `ui → data → domain`, `domain` kennt weder Room noch Compose. UI greift nie direkt auf DAOs zu,
sondern über Repositories bzw. ViewModels (`AppContainer` liefert Abhängigkeiten, ViewModels entstehen über `appViewModel`).

## Coding-Standards

- Kotlin, offizieller Code-Style, keine Wildcard-Imports, `data class`/`sealed interface` für Modelle und Zustände.
- Kommentare auf Deutsch, erklären das *Warum*; keine auskommentierten Codeblöcke, keine TODO-Stubs als Endstand.
- UI: Material 3, immer dunkel; **alle antippbaren Elemente mindestens 56 dp** (`TouchTarget`); Inhalte über
  `CenteredContent`/`FormColumn` in der Breite begrenzen (Tablets/Querformat).
- Zustände in ViewModels als `StateFlow`; Compose-Zustand für Formulare mit `rememberSaveable` – **außer Passwörter** (siehe unten).
- Suche und Berechnung gehören in `domain` und müssen per Unit-Test abgedeckt sein.
- Neue berechenbare Größe: in `domain/calc/Quantities.kt` als `Quantity` ergänzen (Erklärung, Formel, Pflicht-/optionale `Measure`s, `compute`) und in `QuantitiesTest`
  gegen einen **von Hand nachgerechneten** Wert prüfen. Die UI (Katalog, Auswahl „Ich habe gemessen“, Schnellsuche) übernimmt sie automatisch.
  Ergebnisse nie ohne Plausibilitätsprüfung ausgeben (Division durch 0, Wertebereich) – stattdessen `CalcOutcome.Error` mit verständlicher Meldung.

## Sicherheitsregeln (nicht verhandelbar)

1. Passwörter nie im Klartext speichern, loggen, exportieren oder einchecken. In der DB steht nur das Chiffrat
   (`SecretCipher`, AES-256-GCM, Schlüssel im Android Keystore). Verschlüsselt wird ausschließlich in `PlantRepository.saveController`.
2. Klartext-Passwörter nicht in `rememberSaveable`, `SavedStateHandle`, Navigationsargumente oder Logs legen.
3. Anzeige nur nach Bestätigung (Dialog) und mit automatischem Verbergen; Kopieren nur mit `ClipboardHelper(sensitive = true)`.
4. Keine `INTERNET`-Berechtigung, kein Analytics/Crash-Reporting, kein Backup (`allowBackup=false`).
5. Keine Secrets, Tokens oder Keystores im Repo – Release-Signatur nur über GitHub Secrets. (`app/debug.keystore` ist der
   bewusst öffentliche Debug-Schlüssel mit Standardpasswort, kein Geheimnis.)
6. Room: **kein** `fallbackToDestructiveMigration`. Schemaänderung ⇒ `version` erhöhen, echte `Migration` schreiben,
   neues Schema in `app/schemas/` einchecken, Migrationstest ergänzen.

## Daten und Fachlogik

- Begriffe: `terms.json` (Pflichtfelder: key, abbr, full, de/en/it, meaning). Datenversion `version` erhöhen, damit Updates
  eingespielt werden; Favoriten und eigene Begriffe bleiben erhalten. `SeedDataTest` muss grün bleiben.
- p‑T-Daten **nie von Hand erfinden**: mit `tools/generate_pt_table.py` (CoolProp) erzeugen oder Herstellertabellen mit Quelle
  eintragen. Drücke in der JSON sind **absolut** (bar). Die Anzeige rechnet je Einstellung auf Überdruck um.
- Richtwerte in Begriffen sind als „Richtwert“ zu kennzeichnen; Herstellerangaben haben Vorrang.
- Bei Gemischen: Überhitzung mit Taupunkt, Unterkühlung mit Siedepunkt.
- Hersteller-/Gerätedaten (Alarmcodes, Passwörter, Menüs, Leitfäden) **nur aus belegbaren Quellen** eintragen und die Quelle im Datensatz
  nennen (`source`), siehe `docs/DATENQUELLEN.md`. Nichts raten; unbekannte Felder leer lassen. Service-/Herstellerpasswörter, die der
  Hersteller nicht veröffentlicht, werden nicht aufgenommen.
- Alarmcodes: `fault_codes.json` (Feld `version` erhöhen). Eigene Codes des Nutzers (`isCustom`) bleiben beim Update erhalten.

## Änderungen und Commits

- Kleine, thematische Commits mit aussagekräftiger Nachricht (`feat:`, `fix:`, `ci:`, `docs:`, `test:`, `refactor:`, `chore:`).
- Neue Funktion ⇒ Test dazu. Fehler ⇒ zuerst Test, der den Fehler zeigt, dann Korrektur.
- Neue Abhängigkeiten nur über `gradle/libs.versions.toml`, mit fester Version.
- Workflows (`.github/workflows`) dürfen keine Secrets ausgeben und müssen externe Eingaben (Tag, Inputs) validieren.
- README/CLAUDE.md aktualisieren, wenn sich Befehle, Struktur oder Sicherheitskonzept ändern.

## Geplante Erweiterungen

- Tabellen `maintenance_intervals`, `checklists`, `checklist_items` samt `ReferenceDao` sind angelegt, aber noch ohne Oberfläche
  (Wartungsintervalle mit Erinnerung, Checklisten für Inbetriebnahme).
- Verschlüsseltes Backup/Export (mit frei gewählter Passphrase; Passwörter nie unverschlüsselt exportieren), App-PIN/Biometrie.
- Weitere p‑T-Tabellen (R454B, R513A) aus Herstellerdaten; Alarmlisten weiterer Gerätehersteller (z. B. Euroklimat) aus Handbüchern.

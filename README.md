# KlimaKompendium

Persönlicher, **vollständig offline** arbeitender Helfer für Klimatechniker (HVAC/Kälte) auf Android –
für die Baustelle, Inbetriebnahme und Wartung. Keine Cloud, keine Registrierung, keine Werbung, kein Tracking.

> Die App besitzt bewusst **keine Internet-Berechtigung**.

## Funktionen

| Bereich | Was die App kann |
|---|---|
| **Schnellsuche** (Startseite) | Ein Suchfeld für alles: Abkürzungen/Begriffe, Kältemittel, Anlagen – und Messwerte wie `8,5 bar` oder `-5 °C` werden direkt umgerechnet (inkl. Sättigungstemperatur des zuletzt genutzten Kältemittels). |
| **Begriffe & Abkürzungen** | 165+ Einträge (u. a. Carel pCO, PGD1, pLAN, pCOWeb, EVD) (ND, HD, SAP, SAT, RAT, LP, HP, EEV, VFD, COP, EER, SEER, R32, R410A, R290, R134a, R1234ze …) mit Bedeutung, Einheit, Kontext und Richtwerten – jeweils **Deutsch / Englisch / Italienisch**. Sofortsuche, tippfehlertolerant, Favoriten, eigene Begriffe ergänzbar. Mehrdeutige Abkürzungen (z. B. `SAT` = Zulufttemperatur *oder* Sättigungstemperatur) werden beide gezeigt. |
| **Berechnen** (in *Druck & Temperatur*) | 31 berechenbare Größen mit Erklärung, Formel, „Was sagt der Wert?“ und den benötigten Messwerten samt „Wo messe ich das?“: Verdampfungs-/Verflüssigungstemperatur, Überhitzung, Unterkühlung, **Heißgas-Überhitzung**, Verdichtungsverhältnis, Druckdifferenz, Verdampfer-/Verflüssiger-Differenz, Glide, Approach, Wasser-/Luftleistung und -Volumenstrom, Kanal-Volumenstrom, Taupunkt/absolute Feuchte/Enthalpie, Rückwärmzahl, Außenluftanteil, elektrische Leistung 1~/3~, cos φ, COP/EER, Verflüssigerleistung, Motor-Schlupf, Stromauslastung. Auswahl „Ich habe gemessen …“ zeigt, was sich berechnen lässt und welcher Messwert noch fehlt. Auch über die Schnellsuche auffindbar. |
| **Druck & Temperatur** | *Erklärung*: ist ND/HD/SAP/SAT … ein Druck oder eine Temperatur, welche Einheit, welcher Richtwert. *Umrechner*: bar, mbar, kPa, MPa, psi · °C, K, °F · Überdruck ↔ absolut. *p‑T‑Tabelle*: Druck → Temperatur und Temperatur → Druck für 13 Kältemittel (Tau-/Siedepunkt bei Gemischen). *Überhitzung/Unterkühlung* aus Druck und Rohrtemperatur. |
| **Anlagen & Controller** | Anlagen mit Name, Kunde, Standort, Baujahr, Kältemittel, Hersteller, Seriennummer, Controller-Typ, Notizen und Fotos. Mehrere Controller je Anlage (Bezeichnung, Modell, Zugriffsart, IP/Adresse, Benutzer, **verschlüsseltes Passwort**, Hinweise). Beim Öffnen einer Anlage erscheinen sofort Controller, Zugangsdaten, Notizen sowie passende Vorschläge (Kältemittel-Info, p‑T‑Tabelle, Begriff). Suche und Filter nach Anlage, Kunde, Controller, Kältemittel, Hersteller. |
| **Service** | *Störungen*: 268 Alarmcodes der Aermec-Serien NRG 0282-0804 und NRP/NXP/CPS (Carel pCO5 + PGD1) – Suche nach Code, Nummer oder Text (DE/EN), Reset-Art, eigene Codes ergänzbar (z. B. für Euroklimat). *Leitfäden*: Carel-Tasten, pLAN-Adresse (PGD0/pCO), Standardwerte, Passwörter, pCOWeb, BMS/Modbus, EVD-Alarme. *Hersteller*: Profile Aermec, Euroklimat, Carel. Alle Angaben mit Quelle, siehe [docs/DATENQUELLEN.md](docs/DATENQUELLEN.md). |
| **Controller-Vorlagen** | Beim Anlegen eines Controllers vorbelegbar (pCOWeb, Aermec NRG/NRP, Carel Standard, Modbus/BMS). Passwörter werden nie vorbelegt, nur als Hinweis angezeigt. |
| **Vorbereitet** | Datenbanktabellen für Wartungsintervalle und Checklisten existieren bereits (noch ohne Oberfläche – bewusste spätere Erweiterung). |

## Screenshots

Folgen. Zum Aufnehmen in den Einstellungen „Bildschirmfotos & App-Vorschau blockieren“ vorübergehend ausschalten
und die Bilder unter `docs/screenshots/` ablegen.

## Installation

### Variante A: APK aus GitHub Releases (empfohlen)

Direkter Download der jeweils neuesten Version:

**https://github.com/Minecraft425HD/KlimaKompendium/releases/latest/download/KlimaKompendium.apk**

1. APK auf das Gerät laden und öffnen.
2. Android fragt ggf. nach der Erlaubnis „Installation aus unbekannten Quellen“ für den Browser/Dateimanager – erlauben.
3. Installieren. Updates lassen sich direkt über die alte Version installieren (gleiche Signatur).

Voraussetzung: Android 8.0 (API 26) oder neuer. Eine Prüfsumme liegt jedem Release als `SHA256SUMS.txt` bei.

Das Release entsteht automatisch, sobald ein Versions-Tag gepusht wird:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

### Variante B: selbst bauen

Voraussetzungen: JDK 17+, Android SDK (Plattform 35, Build-Tools 35).

```bash
export ANDROID_HOME=/pfad/zum/android-sdk
./gradlew assembleDebug            # → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease          # → app/build/outputs/apk/release/app-release.apk
```

## Passwörter: ausschließlich lokal und verschlüsselt

- Controller-Passwörter werden mit **AES-256-GCM** verschlüsselt. Der Schlüssel wird im **Android Keystore** des Geräts erzeugt und ist nicht exportierbar.
- In der Datenbank steht nur das Chiffrat (`v1.<iv>.<ciphertext>`), nie der Klartext. Der Klartext wird erst auf ausdrückliche Anforderung entschlüsselt.
- Ein Passwort wird **nur nach Bestätigung** angezeigt und nach 5–60 s (einstellbar) wieder verborgen. Kopieren markiert die Zwischenablage als vertraulich und leert sie nach 45 s.
- Kein Klartext in Logs, in Exportdateien oder im Git-Repository. Es gibt keinen Export und kein Backup (`allowBackup=false`, keine Cloud-Sicherung).
- Bildschirmfotos und App-Vorschau sind standardmäßig gesperrt (`FLAG_SECURE`, abschaltbar).
- Konsequenz: **Gerätewechsel oder Zurücksetzen ⇒ Passwörter müssen neu eingetragen werden**, weil der Keystore-Schlüssel das Gerät nicht verlässt.

Nicht abgedeckt: ein Gerät mit entsperrtem Display und fremdem Zugriff, Root-Zugriff oder Schadsoftware auf dem Gerät.
Eine App-PIN/Biometrie ist als mögliche Erweiterung nicht enthalten – nutze eine Bildschirmsperre.

## Release signieren (optional, empfohlen)

Ohne Konfiguration signiert der Release-Workflow mit dem **öffentlichen Debug-Schlüssel** aus `app/debug.keystore`
(Passwort `android`): installierbar und updatefähig, aber ohne Vertrauenswürdigkeit und nicht für den Play Store.

Für eine echte Signatur einen eigenen Schlüssel erzeugen und als **GitHub Secrets** hinterlegen (nie ins Repo einchecken):

```bash
keytool -genkeypair -v -keystore klima-release.jks -alias klima -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 klima-release.jks     # Ausgabe → Secret KEYSTORE_BASE64
```

| Secret | Inhalt |
|---|---|
| `KEYSTORE_BASE64` | Base64 der `.jks`-Datei |
| `KEYSTORE_PASSWORD` | Keystore-Passwort |
| `KEY_ALIAS` | z. B. `klima` |
| `KEY_PASSWORD` | Schlüssel-Passwort |

Achtung: Wechselt die Signatur, muss die App einmalig deinstalliert werden (⇒ lokale Daten gehen verloren).

## Architektur

```
app/src/main/java/de/klimakompendium/
├── domain/      reine Kotlin-Logik, ohne Android: Modelle, Suche, Umrechnung, p-T-Berechnung
├── data/        Room (db), Repositories (repo), Verschlüsselung (security), DataStore (prefs), Seed-Import (seed)
└── ui/          Jetpack Compose (Material 3, dunkel): home, terms, helper, plants, settings, nav, components
app/src/main/assets/
├── terms.json          mitgelieferte Begriffe (DE/EN/IT)
├── refrigerants.json   Kältemittel-Stammdaten + p-T-Sättigungstabellen
├── fault_codes.json    Alarmcodes (Aermec NRG, NRP/NXP/CPS)
└── service.json        Hersteller, Leitfäden, Controller-Vorlagen
tools/generate_pt_table.py   erzeugt refrigerants.json aus CoolProp
```

Kotlin · Jetpack Compose · Material 3 · Room · DataStore · ViewModel/Flow · Navigation Compose · minSdk 26 · targetSdk 35.

## Daten erweitern

- **Begriffe**: in der App über „Eigener Begriff“ – oder dauerhaft mitgeliefert in `app/src/main/assets/terms.json` (Datenversion `version` erhöhen; Favoriten und eigene Begriffe bleiben erhalten). `SeedDataTest` prüft Vollständigkeit.
- **Kältemittel / p‑T‑Tabellen**: `refrigerants.json` (Drücke absolut in bar, Temperatur in °C, Tau- und Siededruck). Die mitgelieferten Tabellen stammen aus [CoolProp](http://www.coolprop.org); neu erzeugen mit `pip install CoolProp && python3 tools/generate_pt_table.py`.
  R454B und R513A sind ohne Tabelle hinterlegt – Herstellerdaten können dort ergänzt werden.
- Richtwerte (z. B. Überhitzung 5–10 K) sind allgemeine Erfahrungswerte und **ersetzen keine Herstellerangaben**.

## Tests

```bash
./gradlew testDebugUnitTest    # Unit-Tests inkl. Robolectric (Datenbank, UI-Ablauf)
./gradlew lintDebug
```

Abgedeckt: Suche (DE/EN/IT, Tippfehler), Umrechnung, alle Berechnungen gegen Handrechnungen, p‑T-Interpolation und Überhitzung, Seed-Daten-Qualität,
Passwort-Verschlüsselung (Roundtrip, Manipulation, falscher Schlüssel), Datenbank (CRUD, Kaskaden, kein Klartext in der DB)
und ein UI-Durchlauf (Anlage → Controller → Passwort nur nach Bestätigung sichtbar).

## CI/CD

- `ci.yml`: bei jedem Push/PR → Build, Unit-Tests, Lint; Debug-APK als Workflow-Artefakt.
- `release.yml`: bei Tag `v*` (oder manuell) → Tests, signierte bzw. debug-signierte Release-APK, GitHub-Release mit `KlimaKompendium.apk`, versionierter APK und Prüfsumme.

## Bekannte Einschränkungen

- Die Oberfläche wird per Unit-/Robolectric-Tests abgesichert; Rückmeldungen aus der Praxis bitte als Issue melden.
- Kein Backup/Export: Anlagen, Controller und Passwörter liegen nur auf diesem Gerät (siehe „Passwörter“). Bei Verlust oder Wechsel des Geräts sind sie weg – wichtige Zugangsdaten zusätzlich an anderer, sicherer Stelle verwahren.
- Fotos der Anlagen liegen im privaten App-Speicher, aber unverschlüsselt (anders als die Passwörter).
- Keine Höhenkorrektur des Atmosphärendrucks (1,01325 bar).
- p‑T-Tabellen enden bei −50 … +60 °C (R744 unterkritisch bis ca. 30 °C).

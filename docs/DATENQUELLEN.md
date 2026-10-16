# Datenquellen

Alle Hersteller- und Gerätedaten in der App stammen aus öffentlich zugänglichen Herstellerunterlagen. Jeder Leitfaden und jede
Alarmliste nennt seine Quelle in der App. Es wurde nichts aus dem Gedächtnis ergänzt; wo keine belastbare Quelle gefunden wurde,
ist das Feld bewusst leer.

| Inhalt | Quelle |
|---|---|
| Alarmcodes Aermec **NRG 0282-0804** (AL01–AL157 und PEC-Alarmgruppen), Installer-Passwort 0000, BMS-/Modbus-Parameter, PGD1-Tasten, Menüs | Aermec, *NRG 0282-0804 – User manual, Card pCO5 – Panel PGD1*, Dok. 5723310_05 (04/2026), Kapitel 1, 9, 11 |
| Alarmcodes Aermec **NRP/NXP/CPS**, PGD1-Tasten, Assistance-Menü, Alarm-Reset-Arten | Aermec, *Multipurpose – User manual, Card pCO5 – Panel PGD1* (NRP/NXP/CPS), Dok. 4724311_03 (10/2025), Kapitel 1, 13 |
| Alarmcodes Carel **Standard Chiller/HP** (generische Applikation, pCO-Alarme und EVD-Treiber-Alarme) | Carel, *Standard Chiller/HP modulare per compressore a vite Generico/Bitzer con driver CAREL*, User manual Rel. 1.4, Cod. +030221296 (09/2008), Kap. 23.6, 23.7, 24.3 |
| Alarmcodes Aermec **ANL/ANLI/ANK/NRK/WRL** (Moducontrol: Alarm n, Voralarm n+100; Reset, Eingänge ID1–ID6) | Aermec, *MODUCONTROL – Bedienungsanleitung*, Dok. 6343841_06 (01/2025), Kap. 17 |
| Hardware-Klemmen der Carel-Regler (U, B, ID, Y, NO/NC, Busse; Größen Small–Extralarge): pCO1, pCO3, pCOXS, pCOC | Carel, *pCO sistema – General manual*, +030220336 rel. 1.6 (04/2013), Kap. 2.1.1, 2.3.1, 2.5.1, 2.7.1 |
| Hardware-Klemmen Carel pCO5 | Carel, *pCO5 User manual*, +0300009EN rel. 1.2 (04/2014), Kap. 2.2 |
| Hardware-Klemmen Carel pCO5+ | Carel, *pCO5+ User manual*, +0300020EN rel. 1.4 (01/2015), Kap. 4.12 |
| Hardware-Klemmen Carel c.pCO, c.pCO mini, c.pCOe | Carel, *c.pCO sistema*, +0300057EN rel. 1.8 (12/2025), Kap. 4.8–4.10 |
| Signal-/Fühlerkürzel mit Klemmen (SIW, SUW, SAE, SL1, SGP, TAP/TBP, AP/BP, QM, CP …) | Aermec NRG 0282-0804, Dok. 5723310_05, Kapitel 4.1–4.12 (pCO, PEC, EVD, pCOe); Aermec NRP/NXP/CPS, Dok. 4724311_03, Kapitel 5.7/5.8 (nur Klemmen, keine Kürzel) |
| Carel Tastenfunktionen (6 und 15 Tasten), pLAN-Adressierung (PGD0, pCO1/pCO3, pCO2/EVD-200), Standardwerte neu installieren, Standardpasswörter 1234 | Carel, *Standard Chiller Modular HP 1/4 Generic – User manual 1.4*, Code +030221296 (09/2008) |
| pCOWeb: Werks-IP 172.16.0.1, Konten admin/root/httpadmin/carel/guest, Protokolle, Bootswitch | Carel, *pCOWeb – Scheda di interfaccia Ethernet per pCO\**, +050003238 rel. 1.0 (08.03.2005) |
| testo 570s: Anzeigebezeichnungen (ND, HD, T verdampf, T kondensat, T1, T2, Überhitzung, Unterkühlung) und ihr Zusammenhang | Foto der Geräteanzeige (vom Nutzer); Zusammenhänge rechnerisch bestätigt (T1 − T verdampf = Überhitzung, T kondensat − T2 = Unterkühlung; R410A-Tabelle). Kein Herstellerhandbuch geprüft. |
| p‑T‑Sättigungsdaten der Kältemittel | CoolProp (`tools/generate_pt_table.py`) |
| Euroklimat S.p.A. (Produktprofil) | Öffentliche Herstellerlisten (u. a. DirectIndustry, Green Cooling Initiative). Zu den Applikationen/Alarmen der Euroklimat-Anlagen wurde **kein** verlässliches öffentliches Handbuch gefunden. |

## Einschränkungen

- Signalkürzel: Die Handbücher erklären sie nur auf Englisch. Die Hinweise zur italienischen Herkunft (Sonda = Fühler …) sind eine Lesehilfe und im Leitfaden als solche gekennzeichnet. „SWI“ kommt in den Handbüchern nicht vor.

- Die deutschen Titel der Alarme sind sinngemäße Übersetzungen der englischen Originaltexte; der Originaltext steht in der App mit dabei.
  Tippfehler im Handbuch (z. B. doppelte Texte bei AL21/AL22 oder AL74/AL76) sind übernommen und gekennzeichnet.
- Die Handbücher nennen keine Ursachen und Abhilfen zu den Alarmcodes. Diese Felder sind deshalb bei mitgelieferten Codes leer;
  nur die Rücksetzart (automatisch / manuell / halbautomatisch / manuell mit Passwort) ist angegeben.
- Passwörter: Veröffentlicht sind nur Werte, die in den oben genannten Handbüchern stehen. Service- und Herstellerpasswörter von
  Aermec werden nicht geführt (nur für autorisiertes Personal). Anlagenbauer ändern Standardwerte häufig – maßgeblich ist die Anlage.
- Der pCOWeb-Handbuchstand ist von 2005; neuere Firmware kann andere Werkseinstellungen haben.

## Eigene Daten ergänzen

- **Alarmcodes** der eigenen Anlagen (z. B. Euroklimat): in der App unter *Service → Störungen → Eigener Code*.
- **Mitgelieferte Daten erweitern**: `app/src/main/assets/fault_codes.json` (Feld `version` erhöhen) bzw. `service.json` bearbeiten;
  `ServiceDataTest` prüft Eindeutigkeit und Vollständigkeit. Immer die Quelle angeben.

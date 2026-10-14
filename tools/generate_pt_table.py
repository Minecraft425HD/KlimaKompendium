#!/usr/bin/env python3
"""Erzeugt app/src/main/assets/refrigerants.json (Kältemittel-Stammdaten + p-T-Sättigungstabellen).

Quelle der Sättigungsdaten: CoolProp (Zustandsgleichungen nach Helmholtz-Energie /
Mischungsmodelle). Drücke sind ABSOLUT in bar, Temperaturen in °C.
  pDew    = Taudruck      (Q=1)  -> relevant für Verdampfer (Saugseite)
  pBubble = Siededruck    (Q=0)  -> relevant für Verflüssiger (Hochdruckseite)
Bei azeotropen/einkomponentigen Stoffen sind beide Werte (nahezu) gleich.

Aufruf:  pip install CoolProp && python3 -I tools/generate_pt_table.py
"""
import json, pathlib
import CoolProp.CoolProp as CP

# id, CoolProp-Name, Typ, ASHRAE-Sicherheitsklasse, GWP (AR4, 100 a), Hinweis
FLUIDS = [
    ("R32",      "R32",        "HFC",  "A2L", 675,  "Standard in neuen Split-/VRF-Geräten; mild brennbar."),
    ("R410A",    "R410A",      "HFC",  "A1",  2088, "Klima-/Wärmepumpen-Bestand; hoher Druck, geringer Glide."),
    ("R404A",    "R404A",      "HFC",  "A1",  3922, "Kälteanlagen NK/TK im Bestand; Nachfüllverbot in der EU."),
    ("R407C",    "R407C",      "HFC",  "A1",  1774, "R22-Ersatz; deutlicher Temperatur-Glide."),
    ("R134a",    "R134a",      "HFC",  "A1",  1430, "Chiller, Kfz-Klima, Mitteltemperatur-Kälte."),
    ("R290",     "R290",       "HC",   "A3",  3,    "Propan; brennbar, kleine Füllmengen, Wärmepumpen/Kälte."),
    ("R600a",    "R600a",      "HC",   "A3",  3,    "Isobutan; brennbar, Haushaltskälte."),
    ("R744",     "R744",       "CO2",  "A1",  1,    "CO2; sehr hoher Druck, transkritisch ab ca. 31 °C."),
    ("R1234ze",  "R1234ze(E)", "HFO",  "A2L", 1,    "Chiller; mild brennbar, niedriger Druck."),
    ("R1234yf",  "R1234yf",    "HFO",  "A2L", 4,    "Kfz-Klima; mild brennbar."),
    ("R22",      "R22",        "HCFC", "A1",  1810, "Altanlagen; Verwendungsverbot (Ozonabbau)."),
    ("R507A",    "R507A",      "HFC",  "A1",  3985, "TK-Kälte im Bestand (azeotrop)."),
    ("R717",     "R717",       "NH3",  "B2L", 0,    "Ammoniak; giftig, Industriekälte."),
]
# Nur Metadaten, bewusst ohne p-T-Tabelle (kein quellenbasierter Datensatz eingebunden).
META_ONLY = [
    ("R454B", "HFO/HFC", "A2L", 466, "R410A-Nachfolger; p-T-Tabelle nachtragen (Herstellerangabe)."),
    ("R513A", "HFO/HFC", "A1",  631, "R134a-Ersatz; p-T-Tabelle nachtragen (Herstellerangabe)."),
]

out = []
for rid, cp_name, typ, safety, gwp, note in FLUIDS:
    tcrit = CP.PropsSI("Tcrit", cp_name) - 273.15
    points = []
    t = -50
    while t <= 60 and t < tcrit - 1:
        tk = t + 273.15
        try:
            pd = CP.PropsSI("P", "T", tk, "Q", 1, cp_name) / 1e5
            pb = CP.PropsSI("P", "T", tk, "Q", 0, cp_name) / 1e5
        except ValueError:
            t += 5
            continue
        if pd >= 0.05:  # unterhalb ~0,05 bar abs. praktisch irrelevant
            points.append({"t": t, "pDew": round(pd, 3), "pBubble": round(pb, 3)})
        t += 5
    glide = max(abs(CP.PropsSI("T","P",101325*5,"Q",1,cp_name) - CP.PropsSI("T","P",101325*5,"Q",0,cp_name)), 0)
    out.append({"id": rid, "name": rid, "type": typ, "safetyClass": safety, "gwp": gwp,
                "glideK": round(glide, 1), "note": note, "pt": points,
                "source": "CoolProp %s" % CP.get_global_param_string("version")})
for rid, typ, safety, gwp, note in META_ONLY:
    out.append({"id": rid, "name": rid, "type": typ, "safetyClass": safety, "gwp": gwp,
                "glideK": 0.0, "note": note, "pt": [], "source": ""})

dest = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/refrigerants.json"
dest.write_text(json.dumps({"schema": 1, "pressureReference": "absolute_bar", "refrigerants": out},
                           ensure_ascii=False, indent=1), encoding="utf-8")
print("geschrieben:", dest, len(out), "Kältemittel")

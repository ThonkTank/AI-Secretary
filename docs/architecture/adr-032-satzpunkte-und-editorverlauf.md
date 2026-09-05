# ADR-032: Satzpunkte und Editorverlauf

- Status: angenommen
- Datum: 2026-09-05

## Kontext

Die horizontale Satzleiste verband Fortschritt, Satzkorrektur und Eingabe in einer breiten,
visuell unruhigen Fläche. Mit Trainingsassistent kamen Status, letzte Änderung, Verlauf und Undo
zusätzlich in dieselbe aktive Heute-Zeile. Viele Sätze erzwangen horizontales Scrollen und kleine
satzbezogene Interaktionsflächen.

## Entscheidung

`SetDotsView` ist eine nicht interaktive, beschriebene Fortschrittsanzeige. Sie zeigt höchstens
20 Punkte je Zeile und wächst bei mehr Sätzen nur innerhalb des aktiven Schritts. Bestätigte Sätze
werden über das Schrittmenü zur Korrektur gewählt. Wiederholungen, eine vorhandene Last und RIR
teilen sich genau einen Inline-Editor; Sicherheitsstatus bleibt eine direkte Aktion.

Today erhält aus dem Trainingskontext ausschließlich ein minimales Modell für eine offene
Lastfrage. Verlauf und Undo gehören als unveränderliche Projektion je bestehender Schritt-ID zum
Aufgaben-Editor. Der Verlauf ist einklappbar, bleibt bei einem schmutzigen Entwurf lesbar und
deaktiviert Undo bis zum Speichern oder Verwerfen. Nach Undo werden Aufgabe und Verlauf geladen,
ohne den geöffneten Schritt zu verlassen.

## Konsequenzen

Es gibt keine horizontale Satznavigation und keine virtuellen Punkt-Aktionen mehr. Alle
Interaktionen besitzen reguläre 44–48-dp-Ziele; die Punktfläche meldet ihren Stand als
Fortschrittsanzeige. Today und Editor teilen keine Verlaufsprojektion oder Kompatibilitätsfassade.
Persistenzschema, Trainingsregeln, Timer und Satzpausen bleiben unverändert.

# ADR-036: Kachel-Editor und parallele Abläufe

Status: umgesetzt und mit 0.2.167 veröffentlicht; physische Geräteabnahme offen

Datum: 2026-09-10

Auslieferungsnachweis vom 2026-09-11: [Anforderungsabgleich und getrennte Gates](flow-tiles-acceptance.md).

## Grundlage und Freigabe

Planbasis ist `80476639744ebd4f15a07bfc3c33d87b09c1216d`, Schema 24.
Die isolierte Vertragsphase enthält zusätzlich die bereits gemergten Korrekturen
aus PR #353 und #354, Stand `de6fc4037a974dc6482e91e841befca01c964c64`.
Andere Checkouts bleiben unverändert.

Der Nutzer hat den revidierten Kachelentwurf ausdrücklich zur Umsetzung freigegeben:
Name, Wartezeit und eine direkt sichtbare Checkbox „Beim Start nachfragen“.
„Zeitoptionen“ und „Nach Ablauf bestätigen“ entfallen. Diese jüngere Freigabe ersetzt
die früheren Verbindungslinien, Verbindungsdialoge und den überladenen Schrittdialog.
Die [visuelle Referenz](../reference/flow-editor/README.md) betrifft ausschließlich Abläufe;
der bestehende Vertrag für normale Aufgaben bleibt unverändert.

## Bedienvertrag

1. Ein eigener Ablauf-Typ öffnet immer denselben Ablaufeditor, auch bei nur einem Schritt.
   Blatt 1 zeigt Ablaufname, direkt verschiebbare Schrittkacheln und „+ Schritt“.
2. Voraussetzungen stehen über Folgeschritten; gleichrangige Schritte nebeneinander.
   Ein gemeinsamer Folgeschritt erstreckt sich über die Breite seiner Voraussetzungen.
   Keine Verbindungslinien, Ports, Zoomfläche, Vorlagen oder Sortierbuttons im Dialog.
3. Davor/dahinter platziert fügt ein; daneben platziert verzweigt ohne implizite
   Zusammenführung. Eine ausdrücklich unter mehreren Zweigenden platzierte gemeinsame
   Folgekachel verbindet diese. Die Vorschau zeigt die resultierenden Kachelbreiten und
   Voraussetzungen vor dem Loslassen; Abbruch verändert nichts. Zyklen sind keine Ziele.
   Einzelkachel und zusammenhängender Zweig bis zur nächsten Zusammenführung sind
   getrennte Auswahlziele auf der Fläche, nicht zusätzliche Dialogformulare.
4. Jede Strukturänderung ist rückgängig machbar; IDs bleiben stabil. Maus, Touch,
   Autoscroll und zugängliche Aktionen ohne Ziehen führen dieselben Entwurfsoperationen aus.
5. Tippen öffnet nur Name, Wartezeit danach (Wert + Minuten/Stunden/Tage),
   „Beim Start nachfragen“, Abbrechen und Speichern. Null bedeutet keine Wartezeit.
   Die Wartezeit gehört zum Schritt und zieht mit ihm um. Sie beginnt nach dessen Aktion;
   die Zeitabfrage geschieht vor dieser Aktion und kann ohne Zustandsänderung abgebrochen werden.
   Auch letzte Schritte können warten; es gibt keine zusätzliche Zeitablaufbestätigung.
6. Startmöglichkeiten haben getrennt vom Schrittdialog ihren Rhythmus und sind ausdrücklich
   als Starts erkennbar. Alternative Starts sind keine parallelen Pflichtschritte.
7. Blatt 2 legt Kapazitäten an, bearbeitet Gesamtmengen und ordnet benötigte Menge,
   Reservierungsbeginn und Freigabeschritt samt Freigabe nach Aktion/nach Wartezeit zu.
   Keine Zusammenfassungsseite; Fertig speichert atomar. Fehler öffnen das betroffene Element.

Ein ViewModel besitzt den gesamten Entwurf inklusive Kachelanordnung und Kapazitäten.
Rotation, Prozesswiederherstellung und Rücknavigation verlieren keine Eingaben.
Abbrechen verwirft nur den Entwurf; es entsteht keine neue Lauf-Abbruchfunktion.

## Definition und Laufzeit

Die lineare Cursorannahme wird in einem zusammenhängenden Produkt-Cutover ersetzt.
Definitionen besitzen stabile Schritt-IDs, gerichtete Verbindungen und Schrittwartezeiten.
Runs übernehmen ausschließlich den vom ausgewählten Start erreichbaren Teil als unveränderlichen
Snapshot. Änderungen an Definition, Zeitvorgaben oder Zuordnungen betreffen nur neue Runs.

Laufzeitschritte besitzen eigene Zustände: blockiert durch Vorgänger, wartend auf Kapazität,
angeboten, Aktion erledigt/zeitlich wartend, abgeschlossen. Das sind fachliche Zustände;
die konkrete Persistenz benennt sie in der Produktphase. Ein Schritt ist genau dann verfügbar,
wenn alle **am Run beteiligten** Vorgänger einschließlich ihrer Wartephasen fertig sind und
die benötigten Kapazitäten atomar beansprucht werden können. Ein blockierter Zweig blockiert
keine unabhängigen Zweige. Die Kette endet erst nach allen beteiligten Schritten und Wartephasen.

Aktionen adressieren Laufzeitschritt-IDs, Zeitänderungen eine stabile Wartephasen-ID,
nicht eine mehrdeutige Run-ID. Doppelklicks und Wiederholungen sind idempotent.
Eine Zeitänderung verlängert nur die ausgewählte Wartephase. Die Uhr allein startet keinen Run.

## Kapazitäten, Tau und Projektion

- Startzulassung, Reservierung und Zustandsübergang bilden eine Transaktion.
  Reservierungen für spätere Schritte sind möglich; stabile Schritt-IDs ersetzen Positionen.
- Freigabe nach Aktion und nach anschließender Wartezeit sind verschieden. Eine reduzierte
  Gesamtmenge nimmt bestehenden Runs keine Reservierungen weg, sperrt aber neue Überbelegung.
- Wäsche: eine Waschmaschine, drei ab Start reservierte Trockenplätze, Freigabe der Maschine
  nach Waschwartezeit und des Trockenplatzes nach Abhängen. Kein vierter Start bei voller Belegung.
- Tau sammelt sich pro Run; die letzte Aktion bucht genau einmal den Gesamtbetrag. Bleiben
  zuletzt nur Wartephasen, entsteht danach genau eine Einsammelaktion. Bestehende Ledger-
  Buchungen bleiben autoritativ; bereits ausgezahlte Beträge werden nicht erneut ausgezahlt.
- Ein gemeinsames Blatt pro Aufgabe/Zeitplatzierung enthält nur verfügbare Starts/Aktionen,
  bei parallelen Schritten mit eindeutiger Herkunft. Kleine Warteübersicht, kein Tau-Container
  oben rechts, kein redundantes Start-/Läuft-Label. Tau erscheint an der Abschlussaktion.
- Reines Warten erzeugt kein Heute-Blatt. Zeitänderungen bleiben unter Alles erreichbar.
  Später ändert nur die persistente Blattposition, niemals Run, Timer oder Reservierungen.

## Migration und Kompatibilität

Die nächste Schema-Version nach 24 übernimmt Definitionen und lineare Runs einmalig:

1. Bestehende IDs, Reihenfolge/Blattpositionen, Kandidaten, Schritte und Ledger erhalten.
2. Übergänge werden zu Verbindungen; `delayAfter` und gewählte Dauer zur Wartephase am
   Quellschritt. Kein stilles Abschließen alter offener Arbeit. Eine zusätzliche
   Zeitablaufbestätigung wird nicht neu eingeführt.
3. Positionen vor dem Cursor werden abgeschlossene Laufzeitschritte. Die aktuelle angebotene
   Aktion behält ihre Occurrence-ID; eine aktive Wartezeit behält den absoluten Zeitpunkt.
   Nachfolgende Positionen werden durch Vorgänger blockierte Schritte.
4. Ressourcenpositionen werden über den bestehenden Snapshot in stabile Laufzeitschritt-IDs
   übersetzt; Reservierungszustand und Mengen bleiben erhalten.
5. Bereits geerntete Occurrences und Gegenbuchungen bleiben im Ledger. Abschluss zahlt nur
   noch nicht gebuchten Tau aus, mit einmaliger Run-Abschlussidentität.
6. Alte Cursorfelder und der lineare Laufzeitmechanismus werden beim Cutover entfernt.
   Kein produktiver Parallelbetrieb zweier Laufzeitmodelle, keine destruktive Neuanlage.

Migrationen und signierte Upgrades müssen insbesondere Schema-24-Runs in jedem vorhandenen
Zustand einschließlich laufender Wartezeiten und bereits gebuchter Rewards prüfen.
Normale Aufgaben und deren Editor, Rewards und Wiederholungen bleiben unverändert.

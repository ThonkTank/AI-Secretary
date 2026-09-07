# Roadmap: echte Ablaufkandidaten und gemeinsame Today-Blätter

Status: verbindlich

Beschlossen: 2026-09-07

Ausgangsstand: `bf189a9f` (`forest-android-1015501`, Auto Secretary 0.2.155)

Ausgangsschema: Room 22

## Zielzustand: Abläufe als echte Kandidaten, Läufe und Aufgabenblätter

Der Zielzustand ist generisch für alle Ablauf-Aufgaben; es entsteht keine fest verdrahtete
Wäsche-Sonderlogik.

## 1. Fachliches Zielmodell

```text
Schritt wird fällig
      ↓
FlowCandidate
      ↓ Nutzer startet + Kapazitätsprüfung
StepFlowRun
      ↓
Warten → angebotener Folgeschritt → Abschluss
```

### `FlowCandidate`

Eine fällige, noch nicht begonnene Wäsche wird als eigener Kandidat gespeichert:

- Aufgaben-, Startschritt- und Zeitplatzierungs-ID
- ursprünglicher Fälligkeitstermin
- stabile Deduplizierungs-ID
- Reihenfolge
- noch keine Laufzeitschritte
- keine Ressourcenreservierungen
- kein Hintergrundstatus

Pro Aufgabe, Startschritt und Tageszeit darf höchstens ein offener Kandidat existieren.

### `StepFlowRun`

Ein Lauf entsteht ausschließlich durch die Kreisaktion des Nutzers. `PENDING_START` entfällt
vollständig.

Verbleibende Laufzustände:

- `WAITING_RESOURCE`
- `WAITING_TIME`
- `OFFERED`
- `COMPLETED`
- `CANCELLED`

Start, erneute Kapazitätsprüfung, Erzeugung des unveränderlichen Laufzeit-Snapshots,
Ressourcenreservierung und Abschluss des Startschritts erfolgen in einer Transaktion. Ändert
sich die Kapazität zwischen Anzeige und Klick, bleibt der Kandidat bestehen und es entsteht
kein halber Lauf.

### `FlowTaskSheet`

Das gemeinsame Blatt wird ein ausdrückliches Today-Modell mit stabiler Identität:

- eine Blatt-ID pro Ablauf-Aufgabe und Tageszeit
- eine eigene persistierte Today-Sortierposition
- Kandidatenzeilen für mögliche Starts
- aktive Zeilen für angebotene Folgeschritte
- aktive Folgeschritte zuerst, danach startbare Kandidaten
- keine leeren Blätter

Für die bestehende Wäsche-Aufgabe ergibt das genau ein Wäscheblatt.

Persistente Einzelvorkommen eines Runs werden künftig als interne Ausführungs-/Reward-Datensätze
behandelt und nicht mehr als UI-Blätter. `FLOW_SHEET`, `currentSheetOccurrenceId`,
`flowAggregate` und `flowRunByStepId` verschwinden aus dem Zielmodell.

## 2. Verbindliches Verhalten

| Zustand | Heute | Alles | Ressourcen |
|---|---|---|---|
| Wäsche nur fällig | Kandidat im gemeinsamen Blatt | unsichtbar | frei |
| Keine Startkapazität | Kandidat ausgeblendet | unsichtbar | unverändert |
| Start bestätigt | Kandidat verschwindet | echter Lauf sichtbar | atomar belegt |
| Folgeschritt bereit | im gemeinsamen Blatt, z. B. „Buntwäsche: Aufhängen“ | Laufstatus sichtbar | gemäß Leasing |
| Zeit-/Ressourcenwartezeit | blockiert Heute nicht | Laufstatus sichtbar | gemäß Leasing |
| „Später“ im Today-Blatt | gesamtes Blatt wandert hinter andere Tagesaufgaben | unverändert | unverändert |
| „Noch nicht fertig“ bei Folgeschritt | Folgeschritt verschwindet bis zur neuen Zeit | Lauf bleibt sichtbar | bleibt belegt |
| Letzte Kette beendet | Blatt verschwindet vollständig | Lauf abgeschlossen | freigegeben |

Die drei Trockenplätze werden bereits bei der Startzulassung berücksichtigt. Sind drei passende
Plätze durch gestartete Ketten gebunden, kann kein vierter Waschgang beginnen.

## 3. Today-Aktionsvertrag

`TodayAction.defer(String)` mit einer mehrdeutigen Aufgaben-/Occurrence-ID wird ersetzt durch
ein typisiertes Ziel:

- normale Aufgabe: `OccurrenceTarget`
- gemeinsames Ablaufblatt: `FlowTaskSheetTarget`
- Ablaufzeile: `FlowCandidateTarget` oder `FlowRunStepTarget`

Damit gelten die Today-Regeln zentral:

- Jedes fokussierbare Blatt kann „Später“ anbieten, wenn ein anderes Blatt offen ist.
- „Später“ verändert ausschließlich die Today-Reihenfolge.
- Das Wäscheblatt kann nicht über „Alles erledigen“ oder das Widget an Zeitabfragen
  vorbeigeschleust werden.
- Der linke Kreis führt genau die für die jeweilige Zeile deklarierte Aktion aus.
- Views leiten keine Fachlogik mehr aus `flowAggregate` oder IDs ab.

## 4. Visueller Zielzustand

Für das Wäscheblatt:

- kein Erklärungshinweis
- kein „Starten“
- kein „Läuft“
- rechts ausschließlich Zeit- oder Mengenangaben
- aktive Zeilen tragen ihre Herkunft im Titel
- normaler „Später“-Button wie bei allen anderen Blättern

Für die Satzpunkte:

- „Satz 1 von 3“ entfällt
- „0/3 erledigt“ entfällt
- aktueller Satz bleibt über den hervorgehobenen Punkt erkennbar
- gespeicherte Sätze bleiben gefüllt
- ein bearbeiteter Satz bleibt durch den Auswahlring markiert
- vollständige TalkBack-Beschreibung und Fortschrittswert bleiben erhalten

## 5. Migration von Version 0.2.155

Datenbankschema 23 übernimmt bestehende Installationen verlustfrei:

1. Neue Tabellen für `flow_candidates` und die Sortierposition des gemeinsamen Blatts anlegen.
2. Saubere `PENDING_START`-Runs in Kandidaten umwandeln.
3. Deren unbenutzte Run-Schritte, Ressourcen-Snapshots und Einzelblätter entfernen.
4. Tatsächlich gestartete Runs einschließlich Wartezeit, gewählter Dauer, aktiver Ressourcen
   und angebotener Folgeschritte erhalten.
5. Bestehende Einzelblatt-Reihenfolgen deterministisch zu einer gemeinsamen Blattposition
   zusammenführen.
6. Run-/Occurrence-Tabellen so umbauen, dass alte Blattfelder und `PENDING_START` anschließend
   nicht mehr im Produktivmodell existieren.
7. Inkonsistente Altdaten mit bereits beanspruchten Ressourcen als begonnenen Lauf erhalten,
   statt sie fälschlich in einen Kandidaten zurückzuverwandeln.

Die Migration wird gegen eine reale 0.2.155-Ausgangsdatenbank mit mehreren Kandidaten, wartenden
und laufenden Ketten geprüft.

## 6. Umsetzungsphasen

Jede Phase erhält gemäß Repository-Regel einen eigenen `codex/…`-Branch, Commit, PR, grüne
Checks und Squash-Merge.

### Phase A – Architekturvertrag

Dokumentations-PR ohne Produktänderung:

- ADR für Kandidat, Run und gemeinsames Today-Blatt
- Invarianten und Zustandsübergänge
- Today-Aktionsmatrix
- Migrationsvertrag
- Aktualisierung von Architekturkarte und Teststrategie

Damit wird der Zielzustand vor der Implementierung verbindlich und auffindbar.

### Phase B – Releaseweg beschleunigen

Die doppelte Geräteprüfung wird ohne Absenkung der Sicherheit reduziert:

- PR behält Qualitätsjob und vollständige Geräte-/Animationsmatrix.
- Nach Squash-Merge vergleicht `main` den exakten Git-Baum mit dem grün geprüften PR-Baum.
- Bei identischem Baum und nachgewiesen grünem PR-Gate überspringt `main` nur die identischen
  Wiederholungsjobs.
- Produktionspaket, Signaturprüfung, Upgrade-Matrix und Veröffentlichung laufen weiterhin auf
  dem exakten `main`-Commit.
- Bei direktem Push, fehlendem Nachweis oder abweichendem Baum läuft automatisch die
  vollständige Matrix.
- Kein vom PR gebautes APK wird als Produktionspaket übernommen.

### Phase C – Redundante Satztexte entfernen

Kleine, isolierte UI-Phase:

- sichtbare Fortschrittszeile entfernen
- Punkte, Auswahlring und Accessibility erhalten
- betroffene Goldens kontrolliert aktualisieren
- Tests für normalen Fortschritt und Bearbeitung eines früheren Satzes

Vor dem Merge erfolgt eine visuelle Abnahme der betroffenen Today-Ansicht.

### Phase D – Atomarer Ablauf-Cutover

Ein zusammenhängender Produkt-PR, damit kein halbfertiges Zwischenmodell veröffentlicht wird:

- `FlowCandidate` und Schema-23-Migration
- atomarer `StartFlowCandidate`
- Entfernung von `PENDING_START`
- erstes echtes `FlowTaskSheet`
- stabile Blattposition und typisierte Today-Ziele
- Umbenennung interner Run-Vorkommen
- Entfernung der alten Aggregate-Flags und Suchlogik
- Umstellung von Today, Alles und Widget
- Entfernung sämtlicher Migrationsadapter aus dem Laufzeitpfad

### Phase E – Abschlussaudit

Read-only-Abgleich gegen den ADR:

- keine leeren oder mehrfachen Ablaufblätter
- keine Kandidaten im Hintergrundbereich
- keine Ressourcenbelegung vor Start
- keine mehrdeutigen Today-IDs
- keine alten Aggregate-Sonderfelder
- keine Wäsche-spezifische Fachlogik
- Dokumentation und Tests entsprechen dem ausgelieferten Modell

## 7. Abnahmetests

Der maßgebliche End-to-End-Fall verwendet weiterhin:

- vier offene Wäschearten
- eine Waschmaschine
- drei Trockenplätze
- zweistündige Waschdauer mit Nachfrage
- eintägige Trocknungszeit
- verlängerbare Wartezeit
- mindestens eine weitere normale Tagesaufgabe

Er prüft:

1. Genau ein Wäscheblatt erscheint.
2. Nur startbare Wäschearten sind sichtbar.
3. Der Kreis fragt die Dauer ab und erzeugt erst danach einen Lauf.
4. Es läuft nie mehr als ein Waschgang gleichzeitig.
5. Nach drei belegten Trockenplätzen wird kein vierter Start angeboten.
6. Alle Folgeschritte erscheinen im selben Blatt mit Herkunftstitel.
7. „Später“ zeigt sofort die nächste Tagesaufgabe.
8. „Später“ verändert weder Run-Zustände noch Ressourcen.
9. „Noch nicht fertig“ behält den Trockenplatz und verschiebt nur den Folgeschritt.
10. Kandidaten erscheinen nie als Hintergrundlauf.
11. Neustart und Upgrade erhalten sämtliche laufenden Ketten.
12. Nach dem letzten Abschluss bleibt kein leeres Blatt zurück.

Zusätzlich laufen Domain-, Transaktions-, Room-, Migrations-, Widget-, UI-, Golden-,
Accessibility-, Geräte-, Animations- und Produktionsupgradeprüfungen.

## Festgelegte Nicht-Ziele

- Keine hart codierte Wäschelogik
- Keine Änderung der bestehenden Intervalle oder Ressourcenmengen
- Kein Redesign des Alles-Tabs oder Aufgabeneditors
- Keine sichtbaren Ersatztexte für entfernte Redundanzen
- Keine dauerhafte Kompatibilitätsschicht nach abgeschlossener Schema-Migration
- Keine Hintergrunddarstellung ungestarteter Kandidaten

# Roadmap: Kachel-Editor und parallele Abläufe

Status: Produktphase abgeschlossen und als 0.2.167 veröffentlicht; Geräteabnahme offen

## Phasen und Gates

| Phase | Liefergegenstand | Abschluss |
|---|---|---|
| D | Neues klickbares Mockup, letzte Dialogbereinigung | Nutzerfreigabe vom 2026-09-10 liegt vor |
| C | ADR-036, visuelle Referenz, Übergangs-/Migrationsvertrag | abgeschlossen: PR #355, grüne Prüfungen, Squash, Remote-main `f42d625e` |
| P | Editor, Graph-Laufzeit, Schema, Tau und Blattprojektion als ein Cutover | abgeschlossen: PR #356, alle lokalen/PR/Main-/Upgrade-Gates grün, Main `06cd96b8`, Stable 0.2.167 |
| A | Anforderungsabgleich und tatsächlich installiertes Update | technischer Abgleich dokumentiert; installierte Version und Laufzeitprüfung mangels verbundenem Gerät offen |

Die [Abnahme](flow-tiles-acceptance.md) ordnet T01–T10 konkreten Prüfungen zu und
trennt lokale Tests, PR, Main, Produktions-Upgrades, veröffentlichtes Paket und Gerät.

Die Produktphase beginnt erst nach Gate C. Es gibt keinen veröffentlichbaren Teil-Cutover.
PRs laufen gegen main; keine direkten main-Pushes. Bestehende Checkouts und parallele
Worktrees bleiben unangetastet. Fortschritt und Abweichungen stehen append-only im
[Ausführungsprotokoll](flow-tiles-execution.md).

## Abnahmematrix für P und A

- T01: Anlegen/Benennen, Wartewert und Einheit, direkt sichtbare Zeitabfrage; weder
  Ablaufbestätigung noch Zeitoptionen-Dropdown, Reihenfolge- oder Kapazitätsfelder im Dialog.
- T02: Kacheln davor/dahinter/daneben, ausdrückliche gemeinsame Folgekachel, ungleich lange
  Zweige, Drag-Abbruch, Undo, stabile IDs und mitziehende Wartezeiten.
- T03: Maus/Touch/Autoscroll, TalkBack-/Tastaturalternative, 320 dp und große Schrift,
  eingeschaltete/reduzierte/ausgeschaltete Animationen; normale Aufgaben unverändert.
- T04: Alternative Starts gegen echte Parallelzweige, mehrere gleichzeitige Aktionen,
  Join nur über beteiligte Vorgänger, unabhängiges Ressourcenwarten.
- T05: Waschmaschine 1/Trockenplätze 3, atomare konkurrierende Starts, Vorreservierung,
  Freigabe nach Aktion versus Wartezeit, Reduktion der Gesamtmenge bei laufenden Runs.
- T06: Zeitabfrage an jedem Schritt, Nullzeit, letzte Wartephase, Verlängerung genau einer
  Wartephase, Neustart/Prozesswiederherstellung; laufende Snapshots bleiben unverändert.
- T07: Ganzes Blatt auf Später, keine leeren/doppelten Blätter, keine Warte-only-Blätter,
  keine Kandidaten als Hintergrundruns, Warteübersicht und Zeitänderung unter Alles.
- T08: Tau pro Run, konkurrierende letzte Aktionen/Doppelklick, Einsammeln nach letzter
  Wartephase, keine Doppelauszahlung nach Migration, bereits gebuchte Beträge erhalten.
- T09: Alle Migrationswege einschließlich Schema 24, aktive/abgeschlossene Runs,
  Zeitabfragen, Reservierungen, Blattpositionen, Ledger und signierte Produktions-Upgrades.
- T10: Ein Editor für bestehende/neue/einschrittige Abläufe, persistenter Gesamtentwurf,
  atomarer Save einschließlich Kapazitäten, Cancel ohne Änderung laufender Runs.

Lokales Gesamtgate: `./scripts/ci/check-all.sh`, zusätzlich die zur Referenz gehörenden
UI-/Golden-/Accessibility-Prüfungen. PR-Geräte-/Animationsmatrix API 26, 35, 37 sowie
signierte Upgrades sind verpflichtend. Referenzbilder werden nicht in CI aktualisiert.
Lokale Tests, PR, exakter main-Workflow, Veröffentlichung und installiertes Gerät sind
getrennte Nachweise; kein grünes Teilergebnis ersetzt das folgende Gate.

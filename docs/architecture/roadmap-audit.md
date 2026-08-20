# Roadmap-Abschlussaudit

Stand: 2026-08-20, nach Phase 4a

## Phasenstatus

| Phase | Ergebnis | Nachweis |
|---|---|---|
| 0 | abgeschlossen | Architekturkarte, Baseline-Gates und Schuldregister in `architecture-map.md` |
| 1 | abgeschlossen | Planungscursor-Semantik, `ScheduleProjector.Input` und Regressionstests |
| 2 | abgeschlossen | `DueDatePlanner`, `OccurrenceCarryForward`, `OccurrenceAssembler`, idempotente Transaktion |
| 3 | abgeschlossen | `HARVESTED_WITH_MISSED_STEPS`, Carry-forward-Grund und Herkunft in Domain und Persistenz |
| 4 | abgeschlossen | SQLite-Invarianten-Trigger, Schema 9/10 und Migrationstests |
| 4a | abgeschlossen | Schema 11: optionale Task-Daten verwenden SQL-`NULL` statt leerer Datumsstrings |
| 5 | abgeschlossen | `TaskDefinitionRepository` und `TransactionalRepository` als fokussierte Ports |
| 6 | abgeschlossen | explizite `DashboardRefreshReason` und `DashboardRefreshPolicy` |
| 7 | abgeschlossen | unveränderliches Reward-Ledger, Gegenbuchungen und Completion-Szenariotests |
| 8 | abgeschlossen mit Gerätevorbehalt | Hosttests, Android-Testkompilierung, Migration-/Golden-/Architekturtests; Instrumentation lokal mangels Gerät nicht gestartet |

## Verifikation

- `./gradlew --no-daemon --max-workers=1 testDebugUnitTest` erfolgreich.
- `./gradlew --no-daemon --max-workers=1 compileDebugAndroidTestJavaWithJavac` erfolgreich.
- `connectedDebugAndroidTest` baut APKs, scheitert ausschließlich an `No connected devices!`.
- Schema 1 bis 11 sind exportiert; die Migrationskette und die nullable Spalten werden durch
  `DatabaseMigrationRobolectricTest` geprüft.

## Bewusst verbleibende Schuld

- `MaterializeDueOccurrences`, `TaskRepository`, `TaskViewModel` und `MainActivity` sind weiterhin
  Orchestratoren; die neuen Ports sind ein schrittweiser Übergang, keine vollständige Modultrennung.
- `nextDueOn` ist fachlich geklärt, heißt im Storage aber weiterhin historisch so.
- SQLite-Trigger werden von Room nicht vollständig als Schema-Metadaten ausgedrückt und müssen
  in jeder Tabellenmigration manuell rekonstruiert werden.
- Teilernte ist fachlich modelliert, aber die Projektion enthält weiterhin Sonderlogik für offene
  Schritte in einem bereits geernteten Eintrag.
- Carry-forward speichert die unmittelbare Herkunft, keine vollständige Ereignis-/Kettenhistorie.
- Datumswechsel-, DST-, Prozessneustart- und Parallelitätsnachweise benötigen weiterhin ein
  echtes Android-Zielsystem; der lokale Hostlauf ersetzt diese Beweise nicht.
- Das Projekt verwendet weiterhin ein einzelnes Android-Modul ohne compiler-erzwungene Domain-
  und Infrastrukturgrenzen.

Diese Punkte sind keine stillschweigend fehlenden Roadmap-Phasen, sondern die dokumentierten
Folgearbeiten für einen nächsten Refactor-Zyklus.

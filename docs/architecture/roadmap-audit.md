# Abschlussaudit: Härtung des Alles-Tabs und Releasepfads

Stand: 2026-08-21, ergänzt nach der Today-/Fokus-Roadmap

## Phasenstatus

| Phase | Ergebnis | Nachweis |
|---|---|---|
| 0 | abgeschlossen | Zentraler `DatabaseContract`, Fixture-/Schema-Gate, exakter Upgradevertrag 0.2.80 auf API 26/35 und Docs-only-Releasefilter |
| 1 | abgeschlossen | `TaskSchedule` und Schema 13 sind alleinige Zeitplanungswahrheit; `catalogOrder` ist davon getrennt |
| 2 | abgeschlossen | Eigener AllTasks-State, reiner Filterzustand, enger Katalogquery und typisierte Management-Commands |
| 3 | abgeschlossen | Explizites Move/Swap, Schema 14 mit separater Reward-Zuordnung und unveränderlichem Ledger |
| 4 | abgeschlossen | Virtualisierte flache `RecyclerView`-Liste, stabile IDs, Drag, Randscrollen und gleichwertige Accessibility-Aktionen |
| 5 | abgeschlossen | Slice-Pakete, ausführbare Architekturregeln, fokussierte Ports und Reads sowie entfernte Legacy-Pfade |

## Erreichte Zielarchitektur

- `task_schedule_entries` ist die einzige persistente Quelle für Slot und Slotreihenfolge.
  Definitionen besitzen nur noch den unabhängigen `catalogOrder`.
- Der Verwaltungsbereich liegt in `presentation.alltasks`; Today-Projektionen liegen in
  `presentation.today`. Today lädt keinen vollständigen Aufgabenkatalog.
- Zeitplanung und Schritttransfers liegen in `domain.schedule` und `domain.steps` und verwenden
  `TaskScheduleRepository` beziehungsweise `StepOrganizationRepository`. Das Today-orientierte
  Der frühere `TaskRepository` ist entfernt. Nur die Verdrahtung bündelt fokussierte Ports im
  `ApplicationTaskRepository`.
- Schedule-Mutationen lesen und normalisieren nur Quell- und Zielslot. Die fokussierten
  Porttests schlagen bei einem globalen Read fehl. Der große In-Memory-Store bleibt nur für
  sliceübergreifende Ausführungs-/Abnahmeszenarien; Porttests verwenden kleine Testdoubles.
- Schritttransfers verändern weder historische Occurrence-Snapshots noch Ledgerzeilen. Eine
  aktuelle Reward-Zuordnung ist eine eigene Projektion; ohne Zielvorkommen wirkt der Transfer
  erst für künftige Vorkommen.
- Die Produktionsmigration registriert nur den unterstützten Pfad ab Schema 8. Exporte und
  Hosttests der älteren Schemata bleiben als Archiv erhalten; 0.2.80 ist die einzige garantierte
  installierte Ausgangsversion.
- `TaskService`, `MoveTask`, alte Create-/Update-Signaturen, veraltete Konstruktoren,
  Editor-Kompatibilitätsprojektionen und der bereits von 0.2.80 ausgeführte Prototype-Cleaner
  sind entfernt.

## Dauerhafte Gates

- Releasewerkzeugtests prüfen Versionsermittlung, Docs-only-Verhalten, Fixtures und Upgradeplan.
- JVM-/Robolectric-Tests prüfen Domaininvarianten, Filter, Projektionen, Commands, Migrationen,
  Ledger, Layoutmatrizen und Virtualisierung.
- Architekturtests verbieten Android-/UI-Abhängigkeiten in der Domain, Management-Abhängigkeiten
  in Today, breite Repository-Abhängigkeiten in Management-Commands und eine erneute produktive
  Registrierung der Schemata 1–7.
- Lint sowie Debug-, Android-Test- und Release-Build laufen vor jedem Phasenabschluss.
- Der Remote-Workflow prüft Instrumentierung und den echten Upgradepfad 0.2.80 jeweils auf API 26
  und API 35, bevor ein Release veröffentlicht wird.

## Bewusst verbleibende Grenzen

- Domain und Android-freier Today-Kern besitzen inzwischen die Java-Module `core-domain` und
  `today-core`; Android-Presentation, Room und Views bleiben gemeinsam im App-Modul.
- `ApplicationTaskRepository` bleibt als breiter Composition-Root-Vertrag der konkreten
  Room-Implementierung bestehen. Einzelne Use Cases importieren ausschließlich Capability-Ports.
- Der sliceübergreifende `InMemoryExecutionRepository` ist absichtlich ein Abnahmespeicher und
  kein wiederverwendetes Testdouble für einzelne Management-Ports.
- Historische Migrationen 1–7 und Schemaexports bleiben testbarer Quellcode beziehungsweise
  Archiv. Sie sind nicht Teil des produktiv registrierten Upgradevertrags.

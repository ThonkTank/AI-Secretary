# Architekturentscheidungen

Die ADRs in diesem Verzeichnis beschreiben das fachliche Verhalten, das während des
Architektur-Refactors erhalten oder bewusst geändert werden muss. Eine Entscheidung wird
nicht stillschweigend im Code geändert: Abweichungen benötigen eine neue ADR, passende
Migrationen und aktualisierte Tests.

## Entscheidungen

- [ADR-001: XP, Gefäß und Kombo-Maserung](adr-001-jahresring.md)
- [ADR-002: Dashboard-Reihenfolge und „später“](adr-002-dashboard-reihenfolge.md)
- [ADR-003: Occurrences und fortlaufende Vorhaben](adr-003-occurrences.md)
- [ADR-004: Widget-Funktionsumfang](adr-004-widget-umfang.md)
- [ADR-005: Release- und Updatevertrag](adr-005-release-und-updatevertrag.md)
- [ADR-006: Update-Schichten und typisierte Fehler](adr-006-update-schichten-und-fehler.md)
- [ADR-007: Update-Präsentation und Android-Systemnavigation](adr-007-update-praesentation-und-systemnavigation.md)
- [ADR-008: Deterministische Update-Abhängigkeiten und Konfiguration](adr-008-deterministische-update-abhaengigkeiten.md)
- [ADR-009: Vertrauensgrenze für Update-Transport und Download](adr-009-update-transport-und-download-vertrauen.md)
- [ADR-010: Unveränderliches Reward-Ledger und Gegenbuchungen](adr-010-reward-ledger-und-gegenbuchungen.md)

Rückblickende Bewertungen der bearbeiteten Bereiche stehen in der
[Architekturkritik der Release- und Updatebereiche](architecture-critique.md) und der
[Architekturkritik von XP, Gefäß, Kombo und Today-Screen](xp-kombo-homescreen-critique.md).
Sie sind keine ADRs, sondern dokumentieren Reibung, technische Schuld und priorisierte
Folgeschritte.

Die verbindlichen visuellen Ausgangsreferenzen sind unter
[`docs/reference/homescreen`](../reference/homescreen/README.md) katalogisiert.
Die aktuelle Testschichtung, Migrationsmatrix und Golden-/Accessibility-Verträge beschreibt
[Phase 7: Testpyramide, Goldens und Accessibility](phase-7-teststrategie.md).
Die vor dem Today-/Fokus-Refactor festgehaltenen Datenflüsse, Charakterisierungstests und
Laufzeit-/Speicherwerte stehen in der
[Phase-0-Baseline des Today-/Fokus-Refactors](today-focus-baseline.md).

## Paketgrenzen des schrittweisen Refactorings

Die App bleibt bewusst ein Android-Modul. Innerhalb dieses Moduls markieren Pakete die aktuell
belastbaren Verantwortungsgrenzen:

- `domain.model` und `domain.usecase`: fachliche Typen und Abläufe, einschließlich `StepAmount`;
- `presentation`: präsentationsfertige Dashboard-Schritte und lokalisierte Textformatierung;
- `editor`: reine, Android-unabhängige Zustandsübergänge des Aufgabeneditors;
- `widget`: größenabhängige Widget-Projektion und ihr UI-Modell;
- Root-Paket: Android-Views, Lifecycle-/Composition-Root und historisch noch nicht verschobene
  kleine Adapter.

Neue fachlich reine Typen sollen direkt in die passende Grenze eingeordnet werden. Bestehende
Root-Klassen werden nur bei konkreter Bearbeitung verschoben; eine mechanische Massenverschiebung
ist ausdrücklich nicht Teil dieses Refactorings. Zusätzliche Gradle-Module, ein DI-Framework,
Compose oder ein generisches Design-System sind dafür nicht vorgesehen.

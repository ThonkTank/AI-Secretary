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

Rückblickende Bewertungen der bearbeiteten Bereiche stehen in der
[Architekturkritik der Release- und Updatebereiche](architecture-critique.md) und der
[Architekturkritik von XP, Gefäß, Kombo und Today-Screen](xp-kombo-homescreen-critique.md).
Sie sind keine ADRs, sondern dokumentieren Reibung, technische Schuld und priorisierte
Folgeschritte.

Die verbindlichen visuellen Ausgangsreferenzen sind unter
[`docs/reference/homescreen`](../reference/homescreen/README.md) katalogisiert.

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
- [ADR-011: Verbraucherspezifische Today-Präsentationsmodelle](adr-011-verbraucherspezifische-praesentationsmodelle.md)
- [ADR-012: Unidirektionaler Dashboard-Zustand](adr-012-unidirektionaler-dashboard-zustand.md)
- [ADR-013: Beobachtbare Präferenzen und vollständiger Renderzustand](adr-013-beobachtbare-renderpraeferenzen.md)
- [ADR-014: Fokuskarte mit explizitem Höhenbudget](adr-014-focuskarte-und-hoehenbudget.md)
- [ADR-015: Virtuelle Accessibility-Knoten für Satzkorrekturen](adr-015-virtuelle-satzaktionen.md)
- [ADR-016: Normalisierte Persistenz von Wiederholungsergebnissen](adr-016-normalisierte-wiederholungsergebnisse.md)
- [ADR-017: Aufgabenverwaltung, Zeitplatzierungen und Schritttransfers](adr-017-aufgabenverwaltung.md)
- [ADR-019: Dauer-Schritte und Satzpausen](adr-019-schritt-timer-und-satzpausen.md)
- [ADR-018: Gemeinsame Blattoberfläche und lokale Grain-Geometrie](adr-018-leaf-surface-und-grain-geometrie.md)
- [ADR-019: Eindeutige Today-Projektion, Rewards und Interaktionszustände](adr-019-today-zustand-rewards-und-aktionen.md)
- [ADR-020: Compilergrenzen für Domain und Today-Kern](adr-020-compilergrenzen.md)
- [ADR-021: Platzierungskarten und Archivbearbeitung im Alles-Tab](adr-021-alles-tab-platzierungskarten.md)
- [ADR-022: schrittweise Compose-Präsentationsarchitektur](adr-022-compose-presentation.md)

Rückblickende Bewertungen der bearbeiteten Bereiche stehen in der
[Architekturkritik der Release- und Updatebereiche](architecture-critique.md) und der
[Architekturkritik von XP, Gefäß, Kombo und Today-Screen](xp-kombo-homescreen-critique.md)
sowie in der [Kritik des Today-/Fokus-Refactors](today-focus-architecture-critique.md).
Sie sind keine ADRs, sondern dokumentieren Reibung, technische Schuld und priorisierte
Folgeschritte.

Die phasenweise Refactor-Baseline mit Zustandsbesitzern, Abhängigkeiten und überprüfbaren Gates
steht in der [Architekturkarte](architecture-map.md).
Die unveränderlichen UX-Verträge und Messwerte der anschließenden Today-/Fokus-Bereinigung stehen
in deren [Phase-0-Baseline](today-focus-cleanup-baseline.md).
Die phasenweisen Implementierungs- und Auditresultate stehen im
[Fortschrittsprotokoll](today-focus-cleanup-progress.md).
Der requirementweise Abschlussabgleich der Roadmap steht im
[Roadmap-Abschlussaudit](roadmap-audit.md).

Die neue, verbindliche [Roadmap der Frontend-Modernisierung](frontend-modernization-roadmap.md)
führt die Präsentation schrittweise zu Kotlin, Compose und eindeutigen Screen-State-Ownern. Ihre
Vorprüfungen, Phasenergebnisse und Selbstkritik werden getrennt im
[Fortschrittsprotokoll der Frontend-Modernisierung](frontend-modernization-progress.md)
festgehalten.

Die verbindlichen visuellen Ausgangsreferenzen sind unter
[`docs/reference/homescreen`](../reference/homescreen/README.md) katalogisiert.
Die [Charakterisierung des Alles-Tabs](all-tasks-characterization.md) hält dessen einmaligen
Handoff-Abgleich, visuelle Zustandsmatrix und sicheren Baseline-Prozess fest.
Die [aktuelle Today-/Fokus-Architektur](today-focus-architecture.md) dokumentiert Datenfluss,
Verantwortlichkeiten, Invarianten, Schema und Widgetinvalidierung. Die aktuelle Testschichtung,
Migrationsmatrix und Golden-/Accessibility-Verträge beschreibt die
[Teststrategie nach dem Today-/Fokus-Refactor](phase-7-teststrategie.md).
Die vor dem Today-/Fokus-Refactor festgehaltenen Datenflüsse, Charakterisierungstests und
Laufzeit-/Speicherwerte stehen in der
[Phase-0-Baseline des Today-/Fokus-Refactors](today-focus-baseline.md).
Die in Phase 1 eingeführten Invarianten und Kompatibilitätsregeln für Wiederholungen beschreibt
der [fachliche Wiederholungsfortschritt](today-focus-domain.md).

## Paketgrenzen des schrittweisen Refactorings

Die App bleibt bewusst ein Android-Modul. Innerhalb dieses Moduls markieren Pakete die aktuell
belastbaren Verantwortungsgrenzen:

- `domain.model` und `domain.usecase`: fachliche Typen und Ausführungsabläufe;
- `domain.schedule` und `domain.steps`: Zeitplatzierungs- und Schrittverwaltungs-Commands mit
  eigenen Persistenzports;
- `presentation.alltasks`: Katalogzustand, Filter, flaches Listenmodell und Verwaltungs-UI;
- `presentation.today`: verbraucherspezifische Today-Projektionen;
- `presentation`: gemeinsam genutzte Präsentationsadapter und lokalisierte Textformatierung;
- `editor`: reine, Android-unabhängige Zustandsübergänge des Aufgabeneditors;
- `widget`: direkte Domain-zu-Widget-Projektion und größenabhängige UI-Modelle;
- `domain.repository`: Definition-, Ausführungs- und Composition-Root-Verträge; Management-
  Commands hängen ausschließlich von den kleineren Slice-Ports ab;
- `data.local`: Room-DAO, Entities, Mapper und konkrete Repository-Implementierung;
- Root-Paket: Android-Views, Lifecycle-/Composition-Root und historisch noch nicht verschobene
  kleine Adapter.

Neue fachlich reine Typen sollen direkt in die passende Grenze eingeordnet werden. Bestehende
Root-Klassen werden nur bei konkreter Bearbeitung verschoben; eine mechanische Massenverschiebung
ist ausdrücklich nicht Teil dieses Refactorings. Zusätzliche Gradle-Module, ein DI-Framework,
Compose oder ein generisches Design-System sind dafür nicht vorgesehen.

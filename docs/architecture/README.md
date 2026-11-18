# Architekturentscheidungen

Die [Architekturkarte](architecture-map.md) führt zu Modulgrenzen, Zustandsbesitzern und
Persistenz im Code. Änderungen an einer ADR-Entscheidung brauchen eine neue ADR und je
nach Auswirkung passende Migrationen und Tests.

- [Schrittregeln für Mengen, XP und Tageswechsel](adr-025-mengenbasierte-schritt-rewards.md)
- [Dialogzustand in Heute](architecture-map.md#dialogzustand-in-heute)
- [Risikobasierte Prüfauswahl und signierte Upgradepflicht](adr-037-risikobasierte-verifikation-und-aktuelles-upgrade.md)

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
- [ADR-023: Ein State-Owner für Optionen, Berechtigung und Updater](adr-023-optionen-state-owner.md)
- [ADR-024: Terminbewusste Kombos und echte Verpflichtungen](adr-024-terminbewusste-kombos.md)
- [ADR-025: Mengenbasierte Schritt-Rewards und Tagesabschluss](adr-025-mengenbasierte-schritt-rewards.md)
- [ADR-026: Getrennte State-Owner für Today und App-Shell](adr-026-today-und-shell-state-owner.md)
- [ADR-031: Fokusprojektion, Zeilenidentität und Grain-Geometrie](adr-031-fokusprojektion-zeilenidentitaet-und-grain-geometrie.md)
- [ADR-032: Satzpunkte und Editorverlauf](adr-032-satzpunkte-und-editorverlauf.md), Trainingsanteile durch ADR-039 ersetzt
- [ADR-033: Ablaufkandidaten und gemeinsame Today-Blätter](adr-033-ablaufkandidaten-und-gemeinsame-today-blaetter.md)
- [ADR-034: Mobile Aufgabenverwaltung und kompakter Ablaufmonitor](adr-034-mobile-aufgabenverwaltung-und-ablaufmonitor.md)
- [ADR-035: Physische Migrationshistorie und signierte Upgrade-Fixtures](adr-035-physische-migrationshistorie-und-upgrade-fixtures.md)
- [ADR-036: Kachel-Editor und parallele Abläufe](adr-036-kachel-editor-und-parallele-ablaeufe.md)
- [ADR-037: Risikobasierte Verifikation und aktuelles signiertes Upgrade](adr-037-risikobasierte-verifikation-und-aktuelles-upgrade.md)
- [ADR-038: Früher Diagnosezugang und geschützte Android-Einstiege](adr-038-frueher-diagnose-bootstrap.md)
- [ADR-039: Schrittnotizen ohne Trainingsassistent](adr-039-schrittnotizen-ohne-trainingsassistent.md)
- [ADR-040: Schritte direkt in Heute bearbeiten](adr-040-schritte-direkt-bearbeiten.md)

## Referenzen und abgeschlossene Nachweise

Freigegebene [Homescreen-Referenzen](../reference/homescreen/README.md) und die
[Charakterisierung des Alles-Tabs](all-tasks-characterization.md) dokumentieren visuelle
Ausgangszustände und den Baseline-Prozess. Die [Teststrategie](phase-7-teststrategie.md)
beschreibt die Testschichtung und die Verträge für Migrationen, Goldens und Accessibility.

### Umsetzung und Ausführungsprotokolle

| Bereich | Plan und Nachweis |
| --- | --- |
| Kachel-Abläufe | [Roadmap](flow-tiles-roadmap.md), [Ausführungsprotokoll](flow-tiles-execution.md) |
| Today-/Fokus-Bereinigung | [Bereinigungsbaseline](today-focus-cleanup-baseline.md), [Fortschrittsprotokoll](today-focus-cleanup-progress.md), [Abschlussaudit](roadmap-audit.md) |
| Frontend-Modernisierung | [Roadmap](frontend-modernization-roadmap.md), [Fortschrittsprotokoll](frontend-modernization-progress.md) |
| Today-Aufgabenblätter | [Roadmap](today-focus-clean-roadmap.md), [Ausführungsprotokoll](today-focus-clean-execution.md) |
| Ablaufkandidaten und gemeinsame Today-Blätter | [Roadmap](flow-task-sheet-roadmap.md), [Ausführungsprotokoll](flow-task-sheet-execution.md) |
| Mobile Aufgabenverwaltung und Ablaufübersicht | [Roadmap](all-tasks-mobile-roadmap.md), [Ausführungsprotokoll](all-tasks-mobile-execution.md) |
| Darstellungs- und Verifikationshärtung | [Roadmap](../mobile-ui/delivery/all-tasks-flow-runs-hardening-roadmap.md), [Ausführungsprotokoll](../mobile-ui/delivery/all-tasks-flow-runs-hardening-execution.md) |
| Datenbankmigrationen und Upgradehistorien | [Roadmap](database-migration-safety-roadmap.md), [Ausführungsprotokoll](database-migration-safety-execution.md) |
| Trainingsassistent vor seiner Entfernung | [ADR-030](adr-030-minimale-trainingsarchitektur-und-automatisierter-abschluss.md), [Roadmap](training-assistant-minimal-roadmap.md), [Ausführungsprotokoll](training-assistant-minimal-execution.md), [Entfernung und Nachweise](adr-039-schrittnotizen-ohne-trainingsassistent.md) |

### Frühere Architekturstände und Bewertungen

- [Today-/Fokus-Architektur des Refactors](today-focus-architecture.md), [Ausgangsbaseline](today-focus-baseline.md)
  und [fachliche Einführung des Wiederholungsfortschritts](today-focus-domain.md)
- [Architekturkritik zu Release und Updates](architecture-critique.md),
  [zu XP, Gefäß und Today](xp-kombo-homescreen-critique.md) und
  [zum Today-/Fokus-Refactor](today-focus-architecture-critique.md)

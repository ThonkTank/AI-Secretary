# Ausführung: minimaler Trainingsassistent

Kanonische Grundlage:
[Roadmap: minimaler Trainingsassistent ohne Übergangsarchitektur](training-assistant-minimal-roadmap.md)

Dieses kompakte Protokoll enthält ausschließlich den aktuellen Phasenstatus, den vor der
Implementierung festgehaltenen Plan, die Validierung und festgestellte Abweichungen. Historische
Nachweise bleiben im Archiv; Commit-, Pull-Request-, Workflow- und Artefaktdetails bleiben in Git
und GitHub.

## Phase 0 – Vertrag, Archiv und automatisierter Abschluss

Status: implementiert

### Plan

Ergebnis: Die Minimal-Roadmap und ADR-030 sind die einzigen aktiven normativen
Trainings-Architekturverträge. Historische Trainingsverträge und Protokolle sind unverändert
archiviert. Release- und Testverträge schließen produktwirksame Phasen ohne nachgelagerten
Folgezustand automatisiert ab.

Betroffene Grenzen:

- aktive Architekturindizes, Release-Dokumentation, Frontend-Roadmap und Teststrategie;
- bisherige Trainings-Roadmap, ihr Ausführungsprotokoll und ADR-027 bis ADR-029;
- historische Frontend-Ausführungsnachweise mit dem früheren Abschlussvertrag;
- der obsolete lokale Freigabe-Runner, dessen Vertragsprüfung und Release-Scope-Verträge.

Reihenfolge:

1. ADR-030 und die aktiven Links auf Minimal-Roadmap, ADR und dieses Protokoll anlegen.
2. Die unveränderten historischen Trainingsdokumente und historischen Gate-Protokolle unter
   `docs/archive/` verschieben; für laufende Architekturarbeit nur kompakte aktuelle Nachfolger
   behalten.
3. Aktive normative Texte auf automatisierten Abschluss umstellen und ältere aktive ADRs mit
   veralteten Gates auf ADR-030 verweisen lassen.
4. Den obsoleten Freigabe-Runner und seine Vertragsprüfung entfernen; den absichtlich
   publish-freien Dokumentationsscope der Phase 0 vertraglich absichern.
5. Diff-, CI-/Release-Vertrags-, fokussierte Gradle- und negative Repository-Prüfungen ausführen.
6. Implementierung getrennt gegen diesen Plan, die Phase-0-Entfernungslisten und die gesamte
   Roadmap auditieren. Vor jeder Korrekturrunde zuerst einen Fixplan ergänzen.

Abnahme: Alle Phase-0-Kriterien der Roadmap sind lokal belegt; Schema, Produktcode und sichtbare
Ressourcen bleiben unverändert. Der Remote-Abschluss erfolgt anschließend ausschließlich über
den vorgeschriebenen Pull-Request-, Squash-Merge- und Main-Workflow-Gate.

### Validierung

- `git diff --check`: grün.
- `python3 -m unittest discover -s scripts/ci -p 'test_*.py' -v`: 16 Tests grün.
- `python3 -m unittest discover -s scripts/release -p 'test_*.py' -v`: 23 Tests grün.
- Tatsächlicher Phase-0-Diff über `scripts/ci/change_scope.py`: Quality und Instrumentierung
  erforderlich, Produktrelease ausdrücklich nicht erforderlich.
- `./gradlew testInstrumentationUnitTest --tests '*ArchitectureBoundaryTest' --tests
  '*CompletionArchitectureTest' --console=plain --no-daemon`: mit JDK 21 und dem vorhandenen
  Android-SDK grün; 35 Tasks ausgeführt.
- Der erste Gradle-Start in der eingeschränkten Sandbox konnte keine nutzbare Wildcard-IP
  bestimmen; der erste externe Wiederanlauf kannte den SDK-Pfad nicht. Der unveränderte Testlauf
  bestand nach expliziter JDK-21- und SDK-Auswahl. Beide Befunde waren Umgebungs-, keine
  Produktabweichungen.

### Audit und Abweichungen

- Planabgleich: ADR-030, aktive Links, kompakte Nachfolger, Vertragsumstellung,
  Runner-Entfernung und Release-Scope-Vertrag sind vollständig umgesetzt.
- Entfernungslisten: Der Negativscan findet außerhalb von Minimal-Roadmap, ADR-030 und Archiv
  keinen verbotenen Runner, keinen nachgelagerten Freigabeschritt und keinen offenen
  Folgezustand. Aktive Dokumentation referenziert keine alte Trainings-Roadmap, kein altes
  Trainingsprotokoll und keine ADR-027 bis ADR-029.
- Archiv: Die fünf historischen Trainingsdateien und das historische Frontend-Protokoll sind
  gegenüber `origin/main` jeweils byteidentisch. Historische Gate-Nachweise liegen nur dort.
- Regressionsgrenzen: Der Diff verändert weder Produktquellen noch Schema-, Migrations-, Golden-,
  Ressourcen- oder Upgradefixture-Dateien.
- Roadmapabgleich: Alle lokal belegbaren Phase-0-Kriterien sind erfüllt. Pull Request,
  Squash-Merge und Main-Workflow fehlen noch; deshalb wird Phase 0 nicht als `implementiert`
  geschlossen und Phase 1 nicht begonnen.
- Es wurde keine inhaltliche Diskrepanz gefunden; eine Fixrunde war nicht erforderlich.

### Korrekturrunde 1 – Status nach dem Remote-Gate

Plan: Nach grünem Pull Request, Squash-Merge und grünem Main-Workflow wird ausschließlich der
Phase-0-Status von der während der Implementierung zutreffenden Arbeitsmarkierung auf
`implementiert` gesetzt. Der frühere Auditbefund bleibt unverändert als damaliger Stand erhalten.
Die Korrektur verändert weder Roadmap noch Produkt-, Schema-, Ressourcen- oder Testcode und wird
erneut über einen Themenbranch, Pull Request und Main-Workflow geprüft. Validiert werden
`git diff --check`, Dokumentationsscope und der negative Gate-Scan.

Ergebnis: Der Phase-0-Status lautet `implementiert`. Der erneute Abgleich findet keine weitere
Status- oder Scopeabweichung; Phase 1 bleibt bis zum Abschluss dieses Docs-Gates gesperrt.

## Phase 1 – Eine kanonische Schritt-, Trainings- und Ergebnisstruktur

Status: wartet auf den vollständig abgeschlossenen Phase-0-Gate

## Phase 2 – Fünf Repositories und eindeutige Composition

Status: wartet auf den vollständig abgeschlossenen Phase-1-Gate

## Phase 3 – UI-Ownership und Abschlussaudit

Status: wartet auf den vollständig abgeschlossenen Phase-2-Gate

# ADR-030: Minimale Trainingsarchitektur und automatisierter Abschluss

- Status: angenommen
- Datum: 2026-09-01

## Kontext

Der Trainingsassistent wurde funktional vollständig eingeführt, seine Architektur führte jedoch
weiterhin mehrere Schritt-, Trainings- und Ergebnisdarstellungen sowie überlappende
Persistenzfähigkeiten. Die bisherigen ADR-027 bis ADR-029 dokumentieren die Entstehung und
Zwischenschritte. Sie sind zusammen mit der alten Roadmap und ihrem Ausführungsprotokoll
unverändert archiviert und keine aktive Zielarchitektur mehr.

Zugleich unterschied der bisherige Releasevertrag zwischen automatisierter Veröffentlichung und
einer nachgelagerten manuellen Geräteabnahme. Dieser offene Folgezustand war nicht reproduzierbar
und konnte trotz vollständig grüner, veröffentlichter Artefaktkette den Phasenabschluss auf
unbestimmte Zeit blockieren.

## Entscheidung

Die verbindliche Zielarchitektur steht in der
[Minimal-Roadmap](training-assistant-minimal-roadmap.md) und wird ohne Übergangsarchitektur in
deren vier Phasen umgesetzt.

Die Domain besitzt genau eine Schritt-, Trainings- und Ergebnissprache:

- `StepPrescription` ist die einzige Ausführungsverordnung für Template, Definition, Occurrence
  und Flow-Snapshot;
- `TrainingPrescription` und das optionale `TrainingAssistantProfile` sind die einzigen
  Trainingsvorgaben; `null` bedeutet Opt-out;
- `SetResult` in `RepetitionProgress.results` ist die einzige Ergebniswahrheit;
- `TrainingDecision` liefert ausschließlich Aktion, Grund, Regelversion, nächste Verordnung,
  nächsten Zustand und bei einer Lastfrage deren Richtung.

Die Persistenzgrenze besteht aus genau `CatalogRepository`, `StepRepository`, `TodayRepository`,
`FlowRepository` und `TrainingRepository`. Keiner dieser Ports erweitert einen anderen Port oder
den separaten `TransactionRunner`. Room stellt je Port genau einen DAO und einen Adapter bereit;
kein Produktionsadapter implementiert mehrere Ports. Portübergreifende Atomizität läuft nur über
`RoomTransactionRunner`.

Editor und Today behalten Layout und Verhalten, erhalten aber klare Ownership:
`TrainingAssistantEditorSection` arbeitet direkt mit den kanonischen Typen,
`TrainingAssistantPanelView` besitzt Status, Lastfrage, Verlauf und Undo, und der kleine
`TrainingAssistantActionHandler` übersetzt die vier typisierten UI-Aktionen ausschließlich in
`TrainingUseCases`. `TodayViewModel` enthält weder Lastparsing noch Trainings-Ergebnismapping.

Room-Schema 22, Migrationen, Schemaexport, Saved-State-Schlüssel, Upgradepfad, App-ID, Signatur,
Updater-Vertrauen und sichtbares Verhalten bleiben unverändert. Es wird keine neue Migration und
kein paralleler Lese- oder Schreibpfad angelegt.

Der Phasenabschluss ist vollständig automatisiert:

- **implementiert** bedeutet, dass der Themenbranch den grünen `pull-request-gate` bestanden hat,
  per Squash nach `main` übernommen wurde und der anwendbare Main-Workflow für den exakten
  Squash-Stand grün ist;
- **veröffentlicht** gilt zusätzlich für produktwirksame Phasen und bedeutet, dass derselbe
  Squash-Stand Produktionsupgrade, Packaging, Signatur-, Hash-, Paket-, Versions- und
  Trust-Prüfungen sowie Publish vollständig bestanden hat.

Es gibt keine zusätzliche manuelle oder physische Gerätefreigabe und keinen nachgelagerten offenen
Abnahmestatus. Die reine Vertrags- und Archivphase 0 bleibt nicht produktwirksam und überspringt
Publish absichtlich; alle späteren Phasen sind produktwirksam.

## Konsequenzen

Die archivierten ADR-027 bis ADR-029 bleiben als historische Begründung lesbar, sind aber nicht
mehr über aktive Architekturindizes referenziert. Aktive Trainingsentscheidungen werden nur aus
dieser ADR, der Minimal-Roadmap und dem getrennten
[Ausführungsprotokoll](training-assistant-minimal-execution.md) abgeleitet.

Lokale Tests belegen nur den lokalen Implementierungsstand. Kein Phasenstatus wird vor dem
vorgeschriebenen Pull-Request-, Squash-Merge- und Main-Gate geschlossen. Für produktwirksame
Phasen ist erst die veröffentlichende Kette des exakten Main-Stands vollständig.

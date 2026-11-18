# Architekturkarte

## Modulgrenzen

`:app` bindet Android, Room, Lifecycle, Kalender, Widgets und die Oberflächen an. Es nutzt
`:today-core` und `:core-domain`. `:today-core` besitzt die Android-freien Today-Projektionen,
Aktionen und Zustandsübergänge und verwendet `:core-domain`. `:core-domain` besitzt Modelle,
Fachregeln, Use Cases und ihre Schnittstellen. Die Abhängigkeiten stehen in den Gradle-Dateien
von [app](../../app/build.gradle.kts), [today-core](../../today-core/build.gradle.kts) und
[core-domain](../../core-domain/build.gradle.kts).

## Today-Datenfluss

Schrittaktionen wie Erfassen und Korrigieren folgen diesem Pfad.

```text
Today-Oberfläche → TodayViewModel → Coordinator / Reducer → Command-Handler
  → Use Case → Repository-Port → lokaler Adapter
  → DashboardPresenter / DashboardUiMapper → TodayScreenState → DashboardRenderer
```

[MainActivity](../../app/src/main/java/de/thonktank/autosecretary/MainActivity.java) bindet
die Eingaben an das ViewModel und rendert den Screen State. Die Projektionen für Fokus,
Tagesfolge und Historie liegen im [Today-Kern](../../today-core/src/main/java/de/thonktank/autosecretary/presentation/today).
Der Widgetpfad verwendet seine [eigene Projektion](../../app/src/main/java/de/thonktank/autosecretary/widget).

[StepExecutionService](../../core-domain/src/main/java/de/thonktank/autosecretary/domain/usecase/StepExecutionService.java)
führt Schritte aus und erfasst oder korrigiert Ergebnisse.
[OccurrenceCompletionService](../../core-domain/src/main/java/de/thonktank/autosecretary/domain/usecase/OccurrenceCompletionService.java)
besitzt Abschluss, Ernte und Undo. [ADR-025](adr-025-mengenbasierte-schritt-rewards.md) hält die
Regeln für Mengen, XP und Tageswechsel fest. Die [Ablaufkandidaten und gemeinsamen Today-Blätter](adr-033-ablaufkandidaten-und-gemeinsame-today-blaetter.md)
trennen fällige Kandidaten vom nutzergestarteten Lauf. [ADR-036](adr-036-kachel-editor-und-parallele-ablaeufe.md)
beschreibt Kachelgraph und parallele Ausführung.

## Dialogzustand in Heute

[TodayViewModel](../../app/src/main/java/de/thonktank/autosecretary/presentation/today/TodayViewModel.java)
publiziert [TodayScreenState](../../app/src/main/java/de/thonktank/autosecretary/presentation/today/TodayScreenState.java)
mit stabilen Hostanfragen, einem Notizentwurf und einem Entwurf für „Schritt bearbeiten“.
`SavedStateHandle` bewahrt die offenen Anfragen und die beiden Entwürfe für die Wiederherstellung.
Die Oberfläche bindet die Entwürfe an die Dialoge. Das ViewModel speichert sie über
[EditStepNote](../../core-domain/src/main/java/de/thonktank/autosecretary/domain/usecase/EditStepNote.java)
oder [EditStep](../../core-domain/src/main/java/de/thonktank/autosecretary/domain/usecase/EditStep.java).
Speicherfehler lassen den Entwurf für eine Korrektur offen. Die Verträge für
[Notizen](adr-039-schrittnotizen-ohne-trainingsassistent.md) und
[Schrittbearbeitung](adr-040-schritte-direkt-bearbeiten.md) beschreiben Vorlagenbezug,
Fortschrittsschutz und Wiederherstellung. Die beiden ADRs beschreiben auch die nativen Prüfungen
für Menü, Dialog, Tastatur und Room.

## Zustandsbesitz der Oberflächen

| Zustand | Owner |
| --- | --- |
| Today-Fokus, Tagesfolge und Historie | `TodayUiModel` als Projektion |
| Laufender Today-Reorder | `TodayCoordinator`; bestätigte Positionsänderungen werden transaktional gespeichert |
| Wiederholungsentwurf, Timer, Rewards und Dialoge in Heute | `TodayScreenState` im `TodayViewModel` |
| Navigation auf oberster Ebene und globale Palette | `AppShellViewModel` |
| Alles-Filter, Modus, Karten und Filterbereich | `AllTasksViewModel` |
| Alles-Dropdown, Swap-Auswahl und aktiver Drag | `AllTasksComposeScreen` |
| Aufgabenentwurf, Wizardnavigation, Feedback und Prompt | `EditorUiState` im `TaskEditorViewModel` |
| Blattform, Clip, Transformation und lokale Anker | `LeafSurface` |

Der [Compose-Aufgabeneditor](adr-022-compose-presentation.md) erhält den veröffentlichten
Editorzustand über seinen Host. [ADR-026](adr-026-today-und-shell-state-owner.md) beschreibt
die Zuständigkeiten von Today und App-Shell. Die
[Frontend-Modernisierung](frontend-modernization-progress.md) dokumentiert die umgesetzten
Umstellungen und ihre Prüfungen.

## Persistenz

- Die aktuelle Schemaversion steht in
  [DatabaseContract](../../app/src/main/java/de/thonktank/autosecretary/DatabaseContract.java).
  [DatabaseMigrations](../../app/src/main/java/de/thonktank/autosecretary/data/local/DatabaseMigrations.java)
  registriert die Migrationsschritte.
- [AppDatabase](../../app/src/main/java/de/thonktank/autosecretary/AppDatabase.java) deklariert
  Entities und DAOs. Die [Ports der Domain](../../core-domain/src/main/java/de/thonktank/autosecretary/domain/repository)
  und [ApplicationUseCaseComposition](../../app/src/main/java/de/thonktank/autosecretary/ApplicationUseCaseComposition.java)
  zeigen die Fachgrenzen und die konkrete Verdrahtung von Use Cases, Room- und SQL-Adaptern.
- Die [Transaktionsgrenze](../../core-domain/src/main/java/de/thonktank/autosecretary/domain/transaction/TransactionRunner.java)
  ist ein eigener Port. Mehrere zusammengehörige Schreibschritte teilen sich diese Grenze.
  Schritt-Reorder ändert beispielsweise nur die betroffenen Positionen innerhalb einer Transaktion.
- [ADR-035](adr-035-physische-migrationshistorie-und-upgrade-fixtures.md) dokumentiert reale
  Datenhistorien und Fixtures. [ADR-037](adr-037-risikobasierte-verifikation-und-aktuelles-upgrade.md)
  regelt die Prüfauswahl und signierte Upgrades.

## Prüfungen und Nachweise

Die [Teststrategie](phase-7-teststrategie.md) beschreibt Architektur-, Golden-,
Accessibility- und Migrationstests. Die [risikobasierte Prüfauswahl](adr-037-risikobasierte-verifikation-und-aktuelles-upgrade.md)
bestimmt den Nachweis je Änderung. Produktreleases benötigen einen aktuellen signierten
In-place-Smoke. Im vollständigen Profil kommen die historischen Upgrades mit demselben
Kandidaten hinzu. Die [Release-Anleitung](../releasing.md) beschreibt die Durchführung.

Messwerte, freigegebene visuelle Referenzen und phasenweise Abnahmen stehen im
[Architekturindex](README.md#referenzen-und-abgeschlossene-nachweise).

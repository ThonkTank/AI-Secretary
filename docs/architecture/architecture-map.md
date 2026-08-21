# Architekturkarte nach der Today-/Fokus-Bereinigung

Stand: 2026-08-21, Datenbankschema 14

## Compilergrenzen

```text
:app (Android application)
  ├── depends on :today-core
  └── depends on :core-domain

:today-core (plain Java)
  └── depends on :core-domain

:core-domain (plain Java)
  └── no project or Android dependency
```

`core-domain` besitzt Domainmodelle, Capability-Ports, Scheduling-/Schrittregeln und Use Cases.
`today-core` besitzt die getrennten Fokus-, Timeline- und Historyprojektionen sowie
`TodayAction`, `TodayCommand`, Reducer und Coordinator. Android-Ressourcen, Room, Lifecycle,
Kalenderzugriff, Widgetcode und Views verbleiben in `app`.

## Today-Datenfluss

```text
ui.today View
  → TodayActionSink
  → TodayCoordinator / TodayReducer                 (:today-core)
  → TodayCommandDispatcher
  → fokussierter Handler im TaskViewModel           (:app)
  → Use Case → Capability-Port                      (:core-domain)
  → RoomTaskRepository / DAO                        (:app)
  → DashboardPresenter / DashboardUiMapper          (:app)
  → TodayUiModel + TodayFeatureState                (:today-core)
  → DashboardRenderer → ui.today View               (:app)
```

`MainActivity` verdrahtet den Sink, verarbeitet aber keine Step-, Repetition-, Reorder-,
Harvest- oder Undo-Fachverzweigung. `DashboardEvent` ist auf Navigation, Einstellungen,
Berechtigungen und Systemaktionen begrenzt.

## UI-Pakete

| Paket | Verantwortung |
|---|---|
| `app/ui/leaf` | `LeafShape`, `LeafSurface`, `GrainSpec`, Clip und asynchrone Grain-Pipeline |
| `app/ui/today` | Header, Fokuskarte, Timelineblätter, Tageshistorie, Gesten und Accessibility |
| `app/presentation/alltasks` | Verwaltungszustand, virtuelle Liste und Managementaktionen |
| `today-core/presentation/today` | Android-freie Today-Modelle, Actions und Zustandsautomat |

`LeafSurface` ist der Owner von Form, Clip, Transformation und lokaler Anchor-Geometrie. Views
erzeugen keine zweite Blattform und persistieren keinen Reorderzustand.

## Persistenzports

`ApplicationTaskRepository` ist ausschließlich der Composition-Root-Vertrag der konkreten
Room-Implementierung. Fachcode hängt von kleinen Fähigkeiten ab:

- `DashboardReadRepository`
- `OccurrenceExecutionRepository`
- `RewardLedgerRepository`
- `MaterializationRepository`
- `TodayStepOrderRepository`
- `TaskDefinitionRepository`
- `TaskScheduleRepository`
- `StepOrganizationRepository`

Schrittausführung liegt in `StepExecutionService`, Occurrence-Abschluss, Ernte und Undo in
`OccurrenceCompletionService`. Reorder schreibt nur geänderte Positionsspalten innerhalb einer
Transaktion; Schema 14 bleibt unverändert.

## Autoritative Zustände

| Zustand | Owner | Persistenz |
|---|---|---:|
| Taskdefinition und Zeitplan | Domain + Capability-Port | ja |
| Occurrence-/Schrittfortschritt | Domain-Use-Case | ja |
| Rewardbuchungen | unveränderliches Ledger | ja |
| Today-Fokus/Timeline/History | `TodayUiModel` | nein, Projektion |
| Reorder `IDLE/DRAGGING/PERSISTING` | `TodayCoordinator` | nur Commandresultat |
| Wiederholungsdraft | `RepetitionInputState` im ViewModel | nein |
| Blatt-/Grain-Geometrie | `LeafSurface` | nein |

## Dauerhafte Gates

- Java-Module verhindern Android- und App-Rückimporte in Domain und Today-Kern.
- Hosttests prüfen Fachregeln, Reducer, Room, Migrationen, Views, Accessibility und Goldens.
- Die Android-Test-APK enthält Today-Long-Press/Drag/Drop, Randscrollen,
  Accessibilityaktionen und Recreation eines nicht persistierten Reorders.
- CI führt Instrumentation auf API 26 und API 35 aus; lokal wird ohne verbundenes Ziel nur der
  Buildstatus berichtet.
- Golden-Baselines und Datenbankschema dürfen durch reine Architekturrefactors nicht geändert
  werden.

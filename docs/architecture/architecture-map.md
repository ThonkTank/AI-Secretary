# Architekturkarte nach der Today-/Fokus-Bereinigung

Stand der beschriebenen Today-/Fokus-Bereinigung: 2026-08-21, damals Datenbankschema 14.
Aktueller Persistenzstand: Datenbankschema 22.

Schema 15 ergänzte den Editorvertrag, Schema 16 persistente Rhythmusanker, Schema 17/18 Timer-
und Kombozustand und Schema 19 den eingefrorenen Planwert quantitativer Rewards. Schema 20 bis 22
normalisieren Satzresultate und persistieren Trainingsentscheidungen, Lastfragen und ihre stabile
Auditordnung. Diese Erweiterungen
ändern die hier beschriebenen Compiler-, Today- und Capability-Port-Grenzen nicht. Die aktuelle
Präsentationsbaseline und ihre weitere Migration stehen in der
[Frontend-Modernisierungsroadmap](frontend-modernization-roadmap.md).
Phase 5b schaltet den Aufgabeneditor vollständig auf Compose um. Der produktive
`TaskEditorComposeHostView` erhält ausschließlich den vom `TaskEditorViewModel` veröffentlichten
`EditorUiState`; der frühere View-Renderer und seine lokale Orchestrierung sind entfernt.

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
  → TodayViewModel                                  (:app)
  → TodayCoordinator / TodayReducer                 (:today-core)
  → TodayCommandDispatcher
  → fokussierter Handler im TodayViewModel          (:app)
  → fokussiertes Use-Case-Bündel → Capability-Port   (:core-domain)
  → RoomTask-/RoomStep-/RoomTraining-Adapter / DAO  (:app)
  → DashboardPresenter / DashboardUiMapper          (:app)
  → StateFlow<TodayScreenState>                     (:app)
  → DashboardRenderer → ui.today View               (:app)
```

`MainActivity` verdrahtet den Sink, verarbeitet aber keine Step-, Repetition-, Reorder-,
Harvest-, Undo-, Verschiebe- oder Löschfachverzweigung. Dialoge und Systemanfragen liegen als
stabile `TodayRequest`s im Screen State. Top-Level-Auswahl und globale Legacy-Palette besitzt
bis zum Navigation-3-Cutover ein eigener `AppShellViewModel`; `DashboardEvent` und
verbrauchbare `UiEvent`s existieren nicht mehr.

## UI-Pakete

| Paket | Verantwortung |
|---|---|
| `app/ui/leaf` | `LeafShape`, `LeafSurface`, `GrainSpec`, Clip und asynchrone Grain-Pipeline |
| `app/ui/today` | Header, Fokuskarte, Timelineblätter, Tageshistorie, Gesten und Accessibility |
| `app/presentation/alltasks` | Verwaltungszustand, virtuelle Liste und Managementaktionen |
| `app/presentation/editor` | zustandsloser Compose-Editor, Hostgrenze und Reducer-Dispatcher; in 5a nur Vergleichsrenderer |
| `app/presentation/options` | Optionen-, Kalender-, Updaterzustand und stabile Hostrequests |
| `app/presentation/today` | Android-State-Owner, Today-Screen-State und Hostrequests |
| `app/presentation/shell` | temporäre Top-Level-Auswahl und globale Legacy-Palette |
| `today-core/presentation/today` | Android-freie Today-Modelle, Actions und Zustandsautomat |

`LeafSurface` ist der Owner von Form, Clip, Transformation und lokaler Anchor-Geometrie. Views
erzeugen keine zweite Blattform und persistieren keinen Reorderzustand.

## Persistenzports

Die Domain besitzt keinen aggregierten Repository-Vertrag. `AppContainer` setzt vier sichtbare
Bündel zusammen: `CatalogUseCases`, `TodayUseCases`, `FlowUseCases` und `TrainingUseCases`.
Transaktionale Abläufe erhalten `TransactionRunner` getrennt von genau fünf Fachports:

- `CatalogRepository` für Tasks, Katalog und Zeitplan;
- `StepRepository` für Vorlagen, materialisierte Schritte und deren Reihenfolge;
- `TodayRepository` für Occurrences, Rewards, Kombos und Verpflichtungen;
- `FlowRepository` für Definitionen, Runs und Kapazitäten;
- `TrainingRepository` für Trainingsvolumen, Anpassungen, Lastfragen und Auditspur.

Use Cases dürfen diese Fähigkeiten weder über Mehrfachport-Typparameter noch über
`instanceof`-Sondierung wieder zu einem impliziten Sammelvertrag verbinden.

Schrittausführung liegt in `StepExecutionService`, Occurrence-Abschluss, Ernte und Undo in
`OccurrenceCompletionService`. `RoomTransactionRunner` besitzt die Room-Transaktionsgrenze;
`CatalogDao`, `StepDao`, `TodayDao`, `FlowDao` und `TrainingDao` werden jeweils von genau einem
gleichnamigen Room-Adapter besessen. `ApplicationUseCaseComposition` erzeugt diese fünf Adapter
und den `RoomTransactionRunner` direkt aus `AppDatabase` jeweils genau einmal. Reorder schreibt
nur geänderte Positionsspalten innerhalb einer Transaktion; diese Invariante gilt auch unter
Schema 22 unverändert.

## Autoritative Zustände

| Zustand | Owner | Persistenz |
|---|---|---:|
| Taskdefinition und Zeitplan | Domain + `CatalogRepository` | ja |
| Occurrence-/Schrittfortschritt | Domain-Use-Case | ja |
| Rewardbuchungen | unveränderliches Ledger | ja |
| Today-Fokus/Timeline/History | `TodayUiModel` | nein, Projektion |
| Reorder `IDLE/DRAGGING/PERSISTING` | `TodayCoordinator` | nur Commandresultat |
| Today-Renderzustand, Requests, Timer und Rewards | `TodayScreenState` im `TodayViewModel` | Requests im `SavedStateHandle` |
| Top-Level-Auswahl und globale Legacy-Palette | `AppShellScreenState` im `AppShellViewModel` | Auswahl im `SavedStateHandle` |
| Alles-Filter, Modus, Karten- und Filterbereich | `AllTasksPresentationState` im `AllTasksViewModel` | ja, `SavedStateHandle` |
| Alles-Dropdown, Swap-Auswahl und aktiver Drag | `AllTasksComposeScreen` | nein, bei Abbruch/Detach/Recreation geschlossen |
| Editor-Draft, Wizardnavigation, Feedback und Prompt | `EditorUiState` im `TaskEditorViewModel` | Recreation über `SavedStateHandle` |
| Wiederholungsdraft | `RepetitionInputState` im `TodayViewModel` | nein |
| Blatt-/Grain-Geometrie | `LeafSurface` | nein |

## Dauerhafte Gates

- Java-Module verhindern Android- und App-Rückimporte in Domain und Today-Kern.
- Hosttests prüfen Fachregeln, Reducer, Room, Migrationen, Views, Accessibility und Goldens.
- Der produktive Compose-Editor wird gegen alle zehn kanonischen und fünf adaptiven freigegebenen
  Baselines sowie über Semantik-, Fokus-, Scroll-, Recreation-, Host-Back- und Actionverträge
  geprüft. Architekturtests verbieten einen zweiten Draft und die entfernte View-Orchestrierung.
- Der produktive Compose-Alles-Tab wird gegen seine 13 unveränderten Baselines sowie über
  LazyList-, Filter-, Dropdown-, Long-Press-Drag-, Randscroll-, Recreation-,
  Accessibility- und Actionverträge geprüft; ein RecyclerView-Ersatzpfad ist nicht mehr zulässig.
- Die Android-Test-APK enthält Today-Long-Press/Drag/Drop, Randscrollen,
  Accessibilityaktionen und Recreation eines nicht persistierten Reorders.
- CI führt normale und animationsaktive Instrumentierung auf API 26, 35 und 37 sowie echte
  Upgradeprüfungen des signierten Produktionskandidaten aus; lokal wird ohne verbundenes Ziel nur
  der Buildstatus berichtet.
- Golden-Baselines und Datenbankschema dürfen durch reine Architekturrefactors nicht geändert
  werden.

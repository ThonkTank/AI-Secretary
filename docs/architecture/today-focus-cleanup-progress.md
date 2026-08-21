# Fortschritt der Today-/Fokus-Architekturbereinigung

Stand: 2026-08-21, Datenbankschema 14

## Phase 1 – eindeutige Today- und Rewardmodelle

Status: implementiert und gegen die Roadmap auditiert.

- `RewardBreakdown` erzeugt Grundwert, Kombostufe, Faktor und gerundetes Ergebnis atomar. Alle
  fachlichen XP-Rundungen laufen durch seine Factory.
- `XpVesselView` bindet ausschließlich `XpVesselUiModel`; die deutsche Faktor- und
  Breakdownformatierung liegt im injizierbaren `RewardTextFormatter`.
- `TaskSnapshot` ist aus Main-, Debug-, Unit- und Android-Testquellen entfernt. Fokus,
  Tageshistorie und Menüaktionen verwenden `FocusTaskUiModel`, `CompletedTaskUiModel` und
  `TaskActionTarget`; die offene Timeline behält `TimelineTaskUiModel`.
- `TodayUiModel` besitzt keine allgemeine Taskliste. Fokus, offene Timeline und erledigte
  Historie sind disjunkt; der Mapper partitioniert über stabile Occurrence-/Task-IDs.
- Jeder Fokus-Schritt besitzt `FocusStepStatus`, `StepExecutionUiAction` und einen
  `RewardBreakdown`. Die View interpretiert weder Listenposition noch Wiederholungstyp als
  fachlichen Command.
- Die Debug-Golden-Fixtures behalten ihren historischen visuellen Grain-Level getrennt vom
  fachlichen Reward. Im Produktiv-Mapping sind Grain-Level und Reward-Kombostufe identisch. Die
  temporäre visuelle Eigenschaft geht in Phase 2 in `GrainSpec` auf.

Der Phase-Gate-Lauf enthält 309 Hosttests, davon 308 erfolgreich und einen bewusst
übersprungenen Test. Rewardfälle decken 0, `15 × 1,5 = 23`, ganze Faktoren, dreistellige Werte
und `.5`-Rundungsgrenzen ab. Fokus- und Homescreen-Goldens sind pixelidentisch; Schema und
PNG-Baselines wurden nicht geändert.

## Phase 2 – gemeinsame Blatt- und Grain-Geometrie

Status: implementiert und gegen die Roadmap auditiert.

- `ui.leaf.LeafShape` ist für Header, Timeline-Blätter und die Fokusvorderseite die einzige
  Quelle der vier asymmetrischen Radien. Corner-Mittelpunkte werden daraus und aus den finalen
  Surface-Bounds abgeleitet.
- `LeafSurface` besitzt Hintergrund, Schatten, Grain-Ebene und Frontinhalt. Die sichtbare
  Vorderseite wird nur noch am gemeinsamen Wrapper rotiert; die frühere Synchronisation von
  Fokus-Surface, Grain und Karte ist entfernt.
- `GrainSpec` beschreibt Corner- und Anchor-Grain immutable über semantische Zielviews. Erst
  `LeafSurface.onLayout()` bildet diese Ziele in ihr lokales Koordinatensystem ab und übergibt
  genau eine immutable Renderanfrage.
- Header-`post()` und Timeline-`post()` sind entfernt. Das allgemeine
  `WoodGrainCoordinates`-Hochlaufen, `setLeafClip(...)`, duplizierte Clipradien und
  Geometrie-Testgetter existieren nicht mehr.
- Der Clip wird rendernah in `WoodGrainView` ausgeführt, erhält aber ausschließlich dieselbe
  `LeafShape`-Instanz vom Owner. Das erhält die Anti-Aliasing-Pixel der bestehenden Goldens,
  ohne eine zweite Radiendefinition einzuführen.
- Standalone-Grain-Renderer behalten ihren eigenen Größen-/Attach-Lifecycle für Benchmark und
  Wiederanheften; Surface-gebundene Renderer werden ausschließlich vom finalen Wrapperlayout
  getrieben. Asynchrone Pipeline und gewichteter 4-MiB-Cache blieben unverändert.

Der Phase-Gate-Lauf enthält 312 Hosttests, davon 311 erfolgreich und einen bewusst
übersprungenen Benchmark. Neue reine/Komponententests decken alle asymmetrischen Eckzentren,
verschachtelte lokale Anchors mit Scrolloffset, Kartenrotation sowie die vorhandenen
Notiz-, Wiederholungs- und Hidden-Row-Goldens ab. Lint, Android-Testkompilierung und Debug-APK
sind grün. Fokus- und Homescreen-Goldens sind pixelidentisch; Schema und PNG-Baselines wurden
nicht geändert. Der isolierte Benchmark bestätigt unverändert 16 Cacheeinträge, 762.128 Byte,
16 Builds und eine Draw-Medianzeit von 0,007 ms. Ein direkt danach auf demselben Host aus
Phase-0-Commit `29d949b2` gebauter Vergleich meldete ebenfalls 0,007 ms Median und mit 0,057 ms
einen höheren p95 als Phase 2 mit 0,022 ms; die Pipeline selbst wurde nicht verändert.

## Phase 3 – reine Schrittreihenfolge und fokussierte Persistenz

Status: implementiert und gegen die Roadmap auditiert.

- `domain.today.TodayStepOrder` ist eine repositoryfreie Permutationsfunktion. Sie erhält den
  aktuellen Occurrence-/Step-Snapshot, bewegte ID und Ziel-ID und liefert einen
  `TodayStepMoveResult` mit `MOVED`, `NO_CHANGE`, `STEP_ALREADY_DONE`, `OCCURRENCE_CLOSED`,
  `INVALID_TARGET` oder `TARGET_IN_OTHER_OCCURRENCE`.
- Das Ergebnis enthält die kanonische vollständige Reihenfolge, die endgültige offene
  ID-Reihenfolge und ausschließlich tatsächlich geänderte `TodayStepPositionUpdate`s.
- `MoveTodayStep` hängt nur vom neuen `TodayStepOrderRepository` ab. Room führt pro Änderung ein
  gezieltes `UPDATE occurrence_steps SET position` innerhalb der Use-Case-Transaktion aus;
  vollständige Entity-Updates und die damit verbundene Wiederholungssynchronisation entfallen.
- `StepExecutionService` besitzt Toggle, Wiederholungserfassung/-korrektur, Advance und
  Schritt-Reward. Occurrence-Abschluss, Harvest und Undo verbleiben in `CompletionService`.
- `AdvanceTodayStepResult` liefert Status, tatsächlich erfassten Planwert, bestätigte offene
  Reihenfolge und Reward-Receipt. Erfolgreiche Wiederholungs- und Korrekturwrites liefern einen
  `StepExecutionResult` statt eines bedeutungslosen `RewardReceipt.none()`.

Reine Tests decken mehrere erledigte Slots, Anfang, Ende, No-op, erledigten Schritt,
geschlossene Occurrence, ungültiges Ziel und fremde Occurrence ab. Ein Room-Test instrumentiert
die realen Tabellen per temporären SQLite-Triggern: Beim geprüften Move entstehen exakt drei
notwendige Positionswrites, null Templatewrites, null Wiederholungswrites und beim anschließenden
No-op keine weiteren Writes. Der Phase-Gate-Lauf enthält 318 Hosttests, davon 317 erfolgreich
und einen bewusst übersprungenen Benchmark. Lint, Android-Testkompilierung, Debug-APK sowie alle
Fokus-/Homescreen-Goldens sind grün; Datenbankschema 14 und PNG-Baselines blieben unverändert.

## Phase 4 – Today-Zustandsautomat und Action Boundary

Status: implementiert und gegen die Roadmap auditiert.

- `presentation.today` besitzt mit `TodayAction`, `TodayActionSink`, `TodayFeatureState`,
  `TodayReducer` und `TodayCoordinator` eine geschlossene, snapshotfreie Aktionsgrenze. Actions
  tragen nur IDs, konkrete Zahlen-/Textwerte und ID-Reihenfolgen.
- Reorder ist explizit `IDLE`, `DRAGGING` oder `PERSISTING`. Kanonische und Preview-Reihenfolge,
  bewegte Step-ID und eindeutige Command-ID sind Teil des Zustands. Cancel erzeugt keinen
  Command, Drop genau einen; weitere Drops während desselben Persistenzvorgangs werden
  ignoriert.
- Ein bestätigter Move übernimmt ausschließlich die vom Use Case gelieferte offene Reihenfolge
  in die vorhandene Today-Projektion. Fehler stellen die kanonische Reihenfolge wieder her;
  ein externer Rebind verwirft eine laufende Preview und publiziert typisiertes Feedback.
- Reorder liest weder Dashboard noch Kalender erneut und invalidiert Widgets nur bei
  `TodayStepMoveResult.MOVED`. Completion, Step-Ausführung, Advance, Wiederholung, Undo, Harvest,
  Defer und Condition-Close laden ausschließlich die Today-Projektion; Kalender-, Editor-,
  Navigations-, Options- und Updatezustand bleiben referenziell erhalten.
- `MainActivity` besitzt nur noch einen generischen `DashboardEvent.Today`-Delegationszweig.
  Der einzige exhaustive Today-Dispatcher liegt im Coordinator/ViewModel-Rand; die früheren
  Step-, Repetition-, Reorder-, Harvest- und Undo-Zweige sind aus der Activity entfernt.

Reducer-Tests decken Begin, Preview, Cancel, Drop, Duplicate Drop, Erfolg, Fehler, Rebind und
konkurrierenden externen Refresh ab. Coordinator- und Room-nahe Presentationtests beweisen genau
einen Persistenzcommand, keinen Dashboard-/Kalenderreload, direkte Übernahme der bestätigten
Reihenfolge sowie unveränderte Geschwisterzustände. Ein Architekturtest vergleicht sämtliche
`TodayAction.Kind`-Werte mit dem zentralen Dispatcher. Der Phase-Gate-Lauf enthält 326 Hosttests,
davon 325 erfolgreich und einen bewusst übersprungenen Benchmark. Lint,
Android-Testkompilierung und Debug-APK sind grün; Datenbankschema 14, Goldens und PNG-Baselines
blieben unverändert.

## Phase 5 – Fokus-Views mit reiner Renderingverantwortung

Status: implementiert und gegen die Roadmap auditiert.

- `FocusStepListLayout` besitzt keine kanonischen Step-Modelle, gebundenen Dashboardevents,
  Drop-Flags oder Persistenzlogik mehr. Es verwaltet ausschließlich wiederverwendete Zeilen,
  Höhenbudget, Messung/Layout und die Übersetzung von Gesten in `TodayAction`.
- Der in Phase 4 eingeführte `TodayFeatureState.Reorder` wird bis in `FocusCardUiModel`
  durchgereicht. Im Zustand `DRAGGING`/`PERSISTING` rendert die Liste die vollständige vom
  Reducer gelieferte Preview; Cancel und bestätigte Reihenfolge kommen ausschließlich durch
  einen neuen State-Bind zurück.
- Long Press liegt auf dem vollständigen Schrittkörper. Drag und Accessibility emittieren
  dieselben drei Intents `BEGIN_REORDER`, `PREVIEW_REORDER`, `DROP_REORDER`; die View berechnet
  oder schreibt keine Persistenzcommands.
- `EdgeAutoScroller` erhält einen injizierten `ScrollHost` und eine Zeitquelle. Er scrollt pro
  Animationsframe anhand verstrichener Zeit; die Distanz ist damit unabhängig von der Anzahl
  eingehender Drag-Events.
- `FocusStepRowView` rendert und dispatcht nur noch `StepExecutionUiAction`/`TodayAction`.
  `CompletedTodayView` bindet ausschließlich `CompletedTaskUiModel` und emittiert eine typisierte
  Undo-Action. Die früheren Renderwert-/Reorder-Testgetter und ReflectionHelpers sind entfernt.
- Beim Audit gefundene Grain-Abweichungen wurden ohne Golden-Update korrigiert: Step-Tau nutzt
  weiterhin seinen expliziten sichtbaren Bounds-Anchor statt eines generischen View-Anchors.

Öffentliche View-/Actiontests decken Long Press samt abgelehntem Plattform-Drag, vollständige
Preview, Cancel, genau einen Drop, alle vier Accessibility-Moves, kleine Viewports, große Schrift
und versteckte Folgezeilen ab. Reine Scrolltests beweisen eventratenunabhängige Geschwindigkeit
und korrektes Stoppen. Der Phase-Gate-Lauf enthält 331 Hosttests, davon 330 erfolgreich und einen
bewusst übersprungenen Benchmark. Lint, Android-Testkompilierung und Debug-APK sind grün;
Datenbankschema 14 und sämtliche Fokus-/Homescreen-Goldens blieben unverändert.

# Today-/Fokus-Architektur

Aktueller Stand: 2026-09-01, Datenbankschema 22

## Zustands- und Aktionsvertrag

Today besitzt eine geschlossene Android-freie Grenze in `:today-core`:

```text
TodayAction → TodayCoordinator → TodayReducer → TodayFeatureState
                                  └───────────→ TodayCommand
```

Actions tragen nur IDs, konkrete typisierte Eingaben oder ID-Reihenfolgen. Die
`TrainingAssistantUiAction` liegt als geschlossene Unteraktion in `TodayAction`; ApplyLoad trägt
Rohtext, aktuelle Lastart und Einheit statt eines in der View geparsten Zahlenwerts. Fokus-, Timeline- und
Tageshistorie sind disjunkte Teile von `TodayUiModel`; ein allgemeiner `TaskSnapshot` oder eine
überlappende `tasks`-Liste existiert nicht. `DashboardEvent` transportiert keine Today-Aktion.

Der Reorderzustand ist `IDLE`, `DRAGGING` oder `PERSISTING`. Preview und kanonische Reihenfolge
liegen beim Coordinator, nicht in der View. Drop erzeugt genau einen Command mit eindeutiger ID;
Erfolg übernimmt die vom Use Case bestätigte Reihenfolge, Fehler oder externer Rebind verwerfen
die Preview mit typisiertem Feedback.

## Fokusprojektion und Ausführung

Jeder `FocusStepUiModel` besitzt explizit Status, Reward und `StepExecutionUiAction`. Deshalb ist
jede sichtbare offene Zeile direkt ausführbar, unabhängig von ihrer Position. Ein späterer
Wiederholungsschritt übernimmt beim Advance den nächsten Planwert und wird, falls er offen
bleibt, zum ersten offenen Schritt. Erledigte Slots behalten ihre Position.

Long Press, Drag und die vier Accessibilitybewegungen emittieren dieselben
`BEGIN_REORDER`-/`PREVIEW_REORDER`-/`DROP_REORDER`-Intents. `FocusStepListLayout` besitzt nur
Zeilenwiederverwendung, Höhenbudget, Messung/Layout und Android-Ereignisübersetzung.
`EdgeAutoScroller` arbeitet frame- und zeitbasiert, nicht proportional zur Drag-Eventrate.

## Trainingsassistent in Editor und Fokus

`TrainingAssistantPanelView` besitzt in Today Status, Lastfrage, Antwortfeld, Verlauf und Undo.
`FocusStepRowView` bindet ausschließlich diesen Owner; die Last-, RIR- und Safety-Controls der
normalen Satzaufnahme bleiben Teil der Zeile. `TrainingAssistantActionHandler` erhält nur
`TrainingUseCases` und übernimmt Punkt/Komma, exakte Milli-Units, Validierung und Domain-
Ergebnismapping. `TodayViewModel` ordnet Completed, Feedback und Rejected lediglich seinem
bestehenden Command-/Feedbackkanal zu.

Im Compose-Editor besitzt `TrainingAssistantEditorSection` alle Assistentencontrols. Sie erhält
direkt `StepPrescription`, nullable `TrainingAssistantPolicy` und `TrainingAssistantState` und
liefert ausschließlich Prescription und nullable Policy zurück; ein paralleles UI-Adaptermodell
existiert nicht.

## Rewards und Jahresringe

`RewardBreakdown` erzeugt Grundwert, Kombostufe, Faktor und gerundeten Endwert atomar. Tau und
Schrittzeilen zeigen `resultXp`; das Gefäß erhält ein `XpVesselUiModel` mit Endwert und dem
formatierten Breakdown `Grundwert × Faktor`.

`LeafShape` ist die einzige Quelle der vier asymmetrischen Radien. `LeafSurface` besitzt Form,
Clip, Grain, Vorderseite und Transformation. `GrainSpec` referenziert semantische Anchor-Views;
erst das finale Layout bildet deren Bounds in lokale Surface-Koordinaten ab. Damit bleiben
Header-, Tau-, Gefäß- und Container-Ringe konzentrisch und korrekt auf das Blatt begrenzt, auch
bei Notizen, Wiederholungen, versteckten Zeilen und Kartenrotation.

## Domain- und Persistenzgrenze

`:core-domain` enthält Modelle, fünf fachliche Persistenzports und Use Cases ohne
Android-Abhängigkeit. `TodayStepOrder` ist eine reine Permutationsfunktion; `MoveTodayStep`
liest die Occurrence über `TodayRepository` und persistiert ausschließlich geänderte
Schrittpositionen über `StepRepository`. Schrittausführung und Occurrence-Abschluss sind in
`StepExecutionService` beziehungsweise `OccurrenceCompletionService` getrennt.

Die Composition erzeugt `CatalogRepository`, `StepRepository`, `TodayRepository`,
`FlowRepository` und `TrainingRepository` als getrennte Room-Adapter. Schema 22, Ledger,
Wiederholungstabellen, Templates und andere Occurrences bleiben beim Reorder unverändert.

## Android-App-Grenze

`MainActivity` besitzt Lifecycle, Navigation, Berechtigungen und Dialoge. `TodayViewModel`
implementiert fokussierte Command-Handler und delegiert allgemeine Today-Aktionen über
`TodayCommandDispatcher`; Assistentenaktionen gehen an den einzelnen typisierten Handler. Completion, Advance, Undo,
Harvest und Defer laden nur die Today-Projektion neu. Kalender-, Editor-, Options- und
Updatezustand bleiben erhalten.

## Qualitätsvertrag

Reine Modulkompilierung ersetzt Importscans für Domain und Today-Kern. Unit-/Robolectrictests
decken Fachregeln, State Machine, Room, Komponenten, Accessibility und unveränderte Goldens ab.
Der Instrumentationstestpfad prüft echte Pointer-Long-Press-/Drag-/Drop-Ereignisse,
Randscrollen, Accessibilityaktionen und Recreation. CI führt normale und animationsaktive
Instrumentierung auf API 26, 35 und 37 aus.

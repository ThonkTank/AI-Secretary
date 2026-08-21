# Today-/Fokus-Architektur

Stand: 2026-08-21, Datenbankschema 14

## Datenfluss und Zustandsbesitz

Der Today-Screen folgt einem unidirektionalen Vertrag:

```text
Android View
  → DashboardEvent
  → MainActivity (System-/Dialog-Routing) oder TaskViewModel
  → RepetitionInputReducer beziehungsweise persistierender Use Case
  → TaskRepository / Room
  → DashboardPresenter und verbraucherspezifische Mapper
  → DashboardUiState
  → DashboardRenderer
  → bind() der bestehenden Views
```

Views emittieren Absichten und mutieren keinen autoritativen Draft. Der
`RepetitionInputReducer` ist der einzige Übergang für Stepperänderung, Auswahl eines gespeicherten
Satzes und Submission. `DashboardUiState` besitzt den renderbaren Snapshot einschließlich
Navigation, Today-Modell, Kalender, Palette, Theme, Fokuslimit, Editor, laufenden Aktionen,
Updatezustand und Wiederholungsdraft.

## Modelle nach Verbraucher

- Die Domain besitzt `Dashboard`, `OccurrenceStep`, `RepetitionProgress`, Reward- und
  Completiontypen. Sie kennt keine Android-Views.
- `TodayUiModel` enthält die Fokus- und Timelineprojektion. `TaskSnapshot` bleibt die
  Kompatibilitätsgrenze der Fokuskarte.
- `FocusStepUiModel` trennt Menge, Notiz und Wiederholungsfortschritt für die Fokuskarte.
- `TimelineTaskUiModel` und `TimelineStepUiModel` enthalten nur Timeline-Daten.
- `WidgetDashboardUiModel`, `WidgetTaskUiModel` und `WidgetStepUiModel` werden direkt aus der
  Domain erzeugt. Das Widget hängt weder von `TodayUiModel` noch von Fokus-Viewmodellen ab.

Gemeinsam ist nur `StepTextFormatter`: Verbraucher teilen Formatierungsregeln, keine fertigen
Modelle.

## Fokuskomponenten

- `FocusTaskView` ist die Android-Kompositionshülle und gibt das verfügbare Höhenbudget weiter.
- `FocusCardView` bindet einen vollständigen `FocusCardUiModel` und reserviert Kopf-, Aktions-
  und Schrittbereich.
- `FocusStepListLayout` misst aktive Zeile, Folgereihen und Resthinweis innerhalb eines harten
  Budgets. Ein langer Druck wechselt temporär in den Sortiermodus, zeigt alle offenen Zeilen
  und persistiert die neue Reihenfolge ausschließlich für das aktuelle Vorkommen; semantische
  Aktionen bieten dieselben Bewegungen ohne Drag-and-drop an.
- `FocusStepLayoutPolicy` entscheidet als reine Funktion, wie viele Folgezeilen hineinpassen.
- `FocusStepRowView` rendert genau einen Schritt und emittiert typisierte Events.
- `FocusCardDecoration` besitzt Papierlagen, Grain und Reward-Anker; sie entscheidet nicht über
  Inhalt.
- `FocusCardAnimationController` besitzt Fokuswechsel, Glint und Afterglow und beendet
  Animationen beim Detach/Rebind.
- `SetBarsView` stellt gespeicherte Sätze über virtuelle Accessibility-Buttons bereit. Visuelle,
  Touch-, Tastatur- und semantische Reihenfolge sind identisch.

## Fachliche Invarianten für Wiederholungen

Der persistierte `Task.nextDueOn`-Wert ist der Planungscursor. Er wird ausschließlich durch die
Materialisierung fälliger Kalendertermine fortgeschrieben. Abschluss, Undo und Condition-Close
projizieren Archiv- und Abschlussfelder, setzen den Cursor aber nicht auf ein zufällig
abgeschlossenes Datum zurück. Die spätere Persistenzphase kann diesen kompatiblen Feldnamen in
getrennte Cursor- und Materialisierungsfelder überführen.

`RepetitionProgress` garantiert positive geplante Slots, höchstens ein nichtnegatives Ergebnis
pro Slot und einen daraus abgeleiteten nächsten offenen Slot. Neue und korrigierte Eingaben
liegen in 0…999; größere historische Werte bleiben lesbar. Die möglichen Zustände sind
`IN_PROGRESS`, `RESULTS_COMPLETE` und `COMPLETED_WITHOUT_RESULTS`.

Eine Submission friert Step-ID, Wert und optionalen Korrekturindex atomar ein. Events für einen
nicht mehr aktiven Schritt werden verworfen beziehungsweise mit dem aktuellen Fokus
reconciled. Wiederöffnen eines vollständig erfassten Schritts entfernt den letzten Wert; ein
explizit ohne vollständige Ergebnisse abgeschlossener Schritt behält seine Teilwerte.

Ein Tau eines sichtbaren späteren Schritts ist ebenfalls ausführbar. Ein einfacher Schritt wird
direkt abgeschlossen; ein Wiederholungsschritt übernimmt atomar den nächsten geplanten Wert.
Bleibt er danach offen, wird er in den ersten offenen Slot verschoben und damit zum aktiven
Schritt. Erledigte Slots behalten bei jeder heutigen Umsortierung ihren Platz.

## Persistenzschema 14

`repetition_results(stepId, slotIndex, actualRepetitions)` normalisiert die früher im
Komma-Text gespeicherten Ergebnisse. `(stepId, slotIndex)` ist der Primärschlüssel; `stepId`
referenziert `occurrence_steps` mit Cascade Delete und besitzt einen Index.

Reads laden Ergebniszeilen geordnet und gruppiert. Writes bilden einen Zeilendiff: neue oder
geänderte Slots werden einzeln upserted, entfernte Endslots gelöscht. Die alte
`occurrence_steps.actualRepetitions`-Spalte ist als `legacyActualRepetitions` nur noch für einen
sicheren Übergang vorhanden und wird produktiv leer geschrieben.

Die Migration 7→8 übernimmt gültige Legacywerte einschließlich 0 und Werten über 999. Bei
fehlerhaftem Text wird keine partielle Liste erzeugt; die Step-ID wird geloggt und der Rohtext
bleibt erhalten. Alle exportierten Schemas 1 bis 11 sind Teil des Migrationsvertrags. Schema 9
speichert Carry-forward-Ursprung und -Grund an jedem Occurrence-Schritt und installiert Trigger
für die Ein-Offene-Occurrence-Invariante. Schema 10 ersetzt den leeren `completedOn`-Sentinel
durch eine nullable Datumsspalte und rekonstruiert die Trigger nach dem sicheren Tabellenumbau.
Schema 11 überführt zusätzlich optionale Task-Datumswerte aus historischen leeren Strings in
echte nullable Spalten; der erforderliche Planungscursor `nextDueOn` bleibt nicht-null.

Schema 12 führt normalisierte `task_schedule_entries` für unabhängige Zeitplatzierungen ein.
Schema 13 entfernt Slot, Zeitmaske und die alte Definitionsreihenfolge aus `tasks`; `catalogOrder`
bleibt ausschließlich die Katalogreihenfolge. Schema 14 ergänzt `reward_assignments` als
veränderliche Zuordnungsprojektion, ohne das unveränderliche Reward-Ledger umzuschreiben.

Der produktiv registrierte Upgradepfad beginnt beim mit Version 0.2.80 ausgelieferten Schema 8.
Die Migrationen 1 bis 7 bleiben ausführbare historische Fixtures, gehören aber nicht mehr zum
Produktionsgraphen.

## Widgetaktualisierung

Widgetinvalidierung ist ein injizierter Nebeneffekt des ViewModels, kein Effekt von
`MainActivity.render`. Sie erfolgt nach erfolgreich persistierenden Commands, tatsächlich
schreibender Materialisierung/Combo-Abrechnung, relevanten externen Kalenderänderungen,
Themeänderung sowie Widgetaktion oder Größenänderung. Draft, Navigation, Editoröffnung und
reines Re-Rendering lösen kein Update aus.

Der Dashboard-Refresh besitzt zusätzlich explizite Ursachen (`INITIAL`, `FOREGROUND`,
`DATE_CHANGED`, `PERSISTED_CHANGE`, `EXTERNAL_DATA`). Die reine `DashboardRefreshPolicy` prüft
den Datumscursor, während das ViewModel die eigentliche Materialisierung und Datenbankarbeit
ausführt.

`WidgetUpdateCoordinator` lädt pro Zyklus einmal, projiziert danach je Widgetgröße und isoliert
Fehler einzelner Widget-IDs. Ein Widgetfehler macht einen bereits erfolgreichen Fachcommand
nicht rückwirkend fehlerhaft.

## Qualitätssicherung

Die Pyramide besteht aus reinen Domain-, Reducer- und Layouttests, In-Memory-Use-Case-Tests,
Room-/Migrationsintegration, repräsentativen Robolectric-Komponententests, Accessibilitytests und
komponentenbezogenen Goldens. Details und ausführbare Befehle stehen in der
[Teststrategie](phase-7-teststrategie.md).

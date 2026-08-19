# ADR-012: Unidirektionaler Dashboard-Zustand

- Status: angenommen
- Datum: 2026-08-20

## Kontext

Die Wiederholungseingabe besaß zwei konkurrierende Renderpfade. Nach einem Stepper-Klick
leitete `FocusTaskView` den Draft nach außen weiter, rief aber zusätzlich rekursiv
`bindSteps()` auf. Parallel veröffentlichte das ViewModel einen neuen `DashboardUiState`, den
die Activity erneut renderte. Außerdem erbte `DashboardRenderer.Actions` mehrere breite
Callback-Interfaces, und jeder Renderdurchlauf aktualisierte sämtliche Homescreen-Widgets –
auch bei rein lokalen Draft-, Navigations- oder Editorzuständen.

## Entscheidung

Dashboard-Views besitzen genau eine Ausgabeschnittstelle: `DashboardEventSink`. Alle
Benutzerabsichten werden als unveränderliche `DashboardEvent`-Typen beschrieben. Insbesondere
emittieren Stepper und Satzbalken nur noch:

- `AdjustRepetition(stepId, delta)`,
- `EditRepetition(stepId, index)`,
- `SubmitRepetition(stepId)`.

`RepetitionInputReducer` wendet diese Events auf den aktuellen Draft und die aktive
Fokusprojektion an. Eine Submission friert Wert und optionalen Korrekturindex ein und setzt den
Draft im selben Übergang auf `idle`. Erst `TaskViewModel` führt anschließend den passenden
persistierenden Use Case aus. Views mutieren keinen Draft und rufen nach der Eventemission keine
Bind-Methode auf. Der einzige Zustandsweg lautet damit:

```text
View → DashboardEvent → Activity/TaskViewModel → RepetitionInputReducer
     → DashboardUiState → DashboardRenderer → View.bind
```

Der Reducer akzeptiert Eingaben ausschließlich für den ersten offenen Schritt des aktuellen
Fokustasks. `DashboardUiState.withContent` verwirft den Draft bei Fokus- oder automatischem
Schrittwechsel. Dadurch können verspätete Click-/Repeat-Events keinen Draft auf einen anderen
Schritt übertragen oder später wiederbeleben.

Widgetaktualisierung ist ein eigener injizierter Port (`WidgetInvalidator`) und kein Nebeneffekt
von `MainActivity.render()`. Sie wird ausgelöst nach:

- erfolgreich persistierenden Task-/Schrittbefehlen,
- tatsächlich schreibender Materialisierung oder Combo-Abrechnung,
- einer extern persistierten Kalenderänderung beziehungsweise Berechtigungsänderung,
- einer widgetrelevanten Themeänderung,
- einer Widgetaktion oder Größenänderung über die bestehenden Widgetadapter.

Draftänderungen, Navigation, Editoröffnung und reine erneute Ladevorgänge invalidieren Widgets
nicht. `MaterializeDueOccurrences` und `ApplyComboDecay` melden deshalb explizit, ob ihre
Transaktion Daten verändert hat. Fehler im Widgetadapter dürfen einen bereits erfolgreichen
Fachbefehl nicht nachträglich als fehlgeschlagen darstellen.

## Konsequenzen

Schnelle Eingaben akkumulieren im Reducer, auch wenn der nächste Android-Renderframe noch nicht
gelaufen ist. Korrekturmodus und Submission lesen denselben autoritativen Draft. Die Activity
bleibt vorerst Controller für Dialoge und Präferenzaktionen; die beobachtbare Integration der
Präferenzen in einen vollständigen Renderzustand ist Gegenstand der nächsten Phase.

Der gesonderte Widget-Invalidierungsport verhindert unnötige Datenbank-, Kalender- und
RemoteViews-Arbeit. Er ist in ViewModel-Tests zählbar, ohne Androids `AppWidgetManager` zu
simulieren.

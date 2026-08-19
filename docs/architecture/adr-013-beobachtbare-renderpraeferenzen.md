# ADR-013: Beobachtbare Präferenzen und vollständiger Renderzustand

- Status: angenommen
- Datum: 2026-08-20

## Kontext

Theme und Fokuslimit wurden während jedes Activity-Renders imperativ aus
`SharedPreferences` gelesen. Das Fokuslimit erzwang über `displayPreferencesChanged()` eine
semantisch unveränderte Palettenkopie, nur um erneut zu rendern. Gleichzeitig erhielt
`DashboardRenderer.render()` Dashboardzustand, Theme, Fokuslimit und Updatezustand als vier
unabhängige Eingaben. Zwischen zwei Reads konnten diese Werte aus unterschiedlichen Zeitpunkten
stammen; eine reine Layoutpräferenz war außerdem unnötig an Refreshmechanik gekoppelt.

## Entscheidung

`UiPreferences` stellt `DisplayPreferences` als unveränderlichen Snapshot und über
`observeDisplayPreferences` als lifecycle-schließbares Observable bereit. Der Snapshot enthält
`UiThemeMode` und `FocusStepLimit`. Ungültige gespeicherte Enumwerte werden weiterhin geloggt und
auf `AUTO` zurückgeführt.

`TaskViewModel` übernimmt den initialen Snapshot in `DashboardUiState` und hält eine
Preference-Subscription für seine Lebensdauer. Jede Änderung erzeugt durch Kopieren einen neuen
Zustand mit:

- Theme-Modus,
- daraus für die aktuelle Uhrzeit abgeleiteter Palette,
- Fokuslimit,
- Dashboard-, Kalender-, Editor- und Wiederholungszustand,
- aktuellem `UpdateUiState`.

Der getrennt langlebige `UpdateViewModel` bleibt für den Updateablauf verantwortlich. Seine
beobachtete Ausgabe wird am Presentation-Composition-Root unmittelbar über
`TaskViewModel.updateUpdateState` in denselben renderbaren Dashboardzustand gefaltet. Der
`DashboardRenderer` akzeptiert ausschließlich dieses eine Objekt; er liest weder Preferences
noch eine zweite Updatequelle.

Eine Fokuslimitänderung kopiert nur den UI-Zustand und behält dieselbe `TodayUiModel`-Instanz.
Sie lädt keine Datenbank- oder Kalenderdaten. Eine Themeänderung invalidiert zusätzlich das
Widget über den in ADR-012 festgelegten Port, weil dessen Palette betroffen ist. Das alte
`displayPreferencesChanged()` entfällt.

## Konsequenzen

Optionsansicht und Fokuskarte werden aus demselben atomaren Snapshot gerendert. Änderungen sind
sofort sichtbar, überleben eine neue `UiPreferences`-/Prozessinstanz und bleiben bei ungültigen
Legacywerten kontrolliert. Minutenticks aktualisieren nur die Palette anhand des bereits im
Zustand befindlichen Theme-Modus; sie lesen keine Preference erneut.

Die SharedPreferences-Implementierung ist weiterhin Android-Infrastruktur im bestehenden
Ein-Modul-Aufbau. Ein späterer Wechsel auf DataStore kann hinter demselben Snapshot-/Observable-
Vertrag erfolgen, ohne den Renderer erneut zu verändern.

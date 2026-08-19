# Today-/Fokus-Refactor: Phase-0-Baseline

Stand: 2026-08-19, Commit `d3ea88d2`

Dieses Dokument fixiert den vor dem schrittweisen Today-/Fokus-Refactor beobachteten
Datenfluss. Es beschreibt ausdrücklich den Ist-Zustand, nicht die angestrebte Architektur.
Phase 0 verändert kein Produktverhalten und keine Golden-Baseline.

## Aktueller Datenfluss

### Stepper `+` und `-`

1. `RepStepperView` ruft seinen `IntConsumer` mit `+1` oder `-1` auf.
2. `FocusStepRowView` erzeugt daraus über `RepetitionInputState.adjust` einen neuen Draft.
3. `FocusTaskView` leitet den Draft über `onRepetitionInputStateChanged` weiter und bindet
   seine Schrittzeilen zusätzlich unmittelbar lokal neu.
4. `MainActivity` leitet denselben Draft an `TaskViewModel.updateRepetitionInput` weiter.
5. Das ViewModel veröffentlicht einen neuen `DashboardUiState`; die Activity rendert den
   vollständigen Dashboardzustand erneut.
6. `MainActivity.render` startet bei jedem solchen Render zusätzlich `TaskWidgetProvider.updateAll`.

Damit existieren vor dem Refactor zwei Renderpfade für dieselbe Eingabe: der lokale Rebind in
`FocusTaskView` und der autoritative ViewModel-/UiState-Pfad. Außerdem löst eine nicht
persistierte Draftänderung aktuell einen Widget-Refresh aus.

Charakterisierung: `FocusTaskViewTest.stepRowOwnsRenderingAnchorAndIdBasedActions`,
`RepetitionInputStateTest` und
`UiComponentRobolectricTest.repetitionStepperConfirmsAndEditsSavedSetsInline`.

### Bestätigung einer Wiederholungszahl

1. Der aktive `DewDotView` ruft `FocusStepRowView.commit` auf.
2. Der Eingabedraft wird zuerst auf `idle` gesetzt.
3. Ohne Korrekturindex wird `onConfirmRepetitions(stepId, value)` emittiert.
4. `MainActivity` delegiert derzeit an `TaskViewModel.confirmSet`.
5. `ConfirmSet` delegiert transaktional an `CompletionService.confirmSet`.
6. `OccurrenceStep.confirmRepetitions` hängt den Wert an und leitet `done` aus der Zahl
   bestätigter Werte für diesen Übergang ab.
7. Das ViewModel lädt Dashboard und Präsentationsprojektion neu; der erste offene Schritt wird
   anschließend als aktiver Schritt gerendert.

Charakterisierung: `FocusTaskViewTest.singleRepetitionsConfirmOnceWhileDurationCompletesDirectly`
und `GymRoutineAcceptanceRobolectricTest.authoredGymDetailsSurviveMaterializationAndReachTheFocusCard`.

### Korrektur eines gespeicherten Satzes

1. `SetBarsView` liefert den Index eines gespeicherten Satzes.
2. `RepetitionInputState.edit` übernimmt dessen Ist-Wert und Index in den Draft.
3. `FocusStepRowView.commit` emittiert bei gesetztem Index
   `onEditRepetition(stepId, index, value)`.
4. `TaskViewModel.editStepRepetition` ruft `EditStepProgress` auf.
5. Der Use Case kopiert die vollständige Ist-Wert-Liste, ersetzt den Index und speichert den
   gesamten `OccurrenceStep` erneut.

Charakterisierung: `UiComponentRobolectricTest.repetitionStepperConfirmsAndEditsSavedSetsInline`,
`RepetitionInputStateTest` und
`AccessibilityLayoutMatrixRobolectricTest.talkBackOrderRolesStatesAndKeyboardFollowTheVisualFlow`.

### Automatischer Wechsel zum nächsten Schritt

1. Die letzte erforderliche Wiederholungsbestätigung erzeugt einen `OccurrenceStep` mit
   `done = true`.
2. `CompletionService` bucht den Schrittabschluss transaktional.
3. Der neu geladene Dashboard-Snapshot sortiert erledigte und offene Schritte nicht lokal um;
   `FocusTaskView.bindSteps` filtert erledigte Schritte aus den offenen Zeilen.
4. Der erste verbleibende offene Schritt wird aktiv und erhält die Eingabesteuerung.

Charakterisierung:
`GymRoutineAcceptanceRobolectricTest.authoredGymDetailsSurviveMaterializationAndReachTheFocusCard`.

### Änderung des Fokuslimits

1. `OptionsView` emittiert das ausgewählte `FocusStepLimit`.
2. `MainActivity` schreibt es synchron in `UiPreferences`.
3. `TaskViewModel.displayPreferencesChanged` erzwingt einen State-Render über eine neue
   Palette-Kopie, obwohl die Palette fachlich unverändert sein kann.
4. `MainActivity.render` liest das Fokuslimit erneut imperativ aus `UiPreferences` und gibt es
   als separaten Parameter an `DashboardRenderer`.
5. `FocusTaskView` behandelt numerische Werte als Obergrenze und reduziert sie beim Messen
   weiter, wenn der verfügbare Viewport nicht genügt.

Charakterisierung: `OptionsViewTest`, `FocusStepLimitPreferencesTest`,
`FocusTaskViewTest.configuredLimitCountsFollowingStepsAndReportsTheRest` und
`FocusTaskViewTest.viewportMeasurementSafelyReducesAutomaticAndNumericLimits`.

### Widget-Aktualisierung

Widget-Updates entstehen aktuell über drei relevante Pfade:

- Jeder `MainActivity.render`, einschließlich eines Stepper-Drafts, ruft
  `TaskWidgetProvider.updateAll` auf.
- Eine Theme-Änderung ruft `TaskWidgetProvider.updateAll` zusätzlich explizit auf.
- Eine Widget-Schreibaktion aktualisiert die Widgets nach Abschluss über
  `TaskActionReceiver`.

`WidgetUpdateCoordinator` lädt pro Zyklus einmal und projiziert denselben Snapshot für alle
Widget-IDs. Dieser effiziente Zyklus verhindert nicht, dass er aus der Activity zu häufig
gestartet wird.

Charakterisierung: `WidgetUpdateCoordinatorTest`, `WidgetRemoteViewsFactoryTest`,
`WidgetPresenterTest` und `HomescreenGoldenRobolectricTest`.

## Reproduzierbare Testbaseline

Die Messungen liefen mit `/usr/bin/time -v`, JDK 21, Gradle 8.10.2 und Robolectric 4.14.1.
`Maximum resident set size` bezeichnet den gemessenen Prozessbaum des jeweiligen Befehls und
ist daher eine Vergleichsgröße, kein isolierter Heapwert des Test-Workers.

| Umfang | Befehl | Ergebnis | Wandzeit | Max. RSS |
| --- | --- | --- | ---: | ---: |
| Today-/Fokus-, Preference- und Widget-Gruppe | `./gradlew --no-daemon --max-workers=1 testDebugUnitTest --tests ...` | erfolgreich | 1:34.78 | 795112 KiB |
| vollständige Unit-/Robolectric-Suite | `./gradlew --no-daemon --max-workers=1 testDebugUnitTest` | erfolgreich | 1:24.50 | 1167380 KiB |
| Debug-APK, inkrementell | `./gradlew --no-daemon --max-workers=1 assembleDebug` | erfolgreich | 0:12.20 | 439000 KiB |

Ein zusätzlicher kalter Versuch mit `testDebugUnitTest --rerun-tasks` und der bisherigen
Standardparallelität verlor unter gleichzeitig hohem systemweitem Speicherdruck den
Gradle-Daemon nach 4:32.79. Der Aufrufer erreichte 635440 KiB RSS; der separate Test-Worker
lag zuletzt bei rund 638 MiB. Dieser Lauf ist keine erfolgreiche Baseline, dokumentiert aber
den vor Phase 8 vorhandenen Stabilitätsengpass. Der serielle vollständige Lauf beweist, dass
keine fachlichen Testfehler vorlagen.

## Golden-Vertrag der Ausgangsbasis

- Phase 0 hat keine PNG- oder Hash-Baseline erzeugt oder aktualisiert.
- `HomescreenGoldenRobolectricTest` war Teil der erfolgreich gemessenen Fokusgruppe.
- Änderungen an Fokus, Widget und Editor müssen weiterhin getrennt bewertet werden; ein
  fehlgeschlagener Golden-Test ist allein keine Erlaubnis, eine Baseline zu überschreiben.

# Ablaufblatt: Ketten im Kopf

Das native Today-Blatt verwendet kleine `XpVesselView`-Container rechts neben dem
Titel. Die Gruppe scrollt horizontal; die Titelhälfte bleibt erhalten. Die
Gruppen wachsen mit der Schrift, haben mindestens 48 dp große Bedienflächen und
tragen den eingefrorenen Startschritttitel. IDs sind immer Lauf-/Warte-IDs.
Unbegonnene Kandidaten erscheinen ausschließlich als vorhandene Schrittzeilen.

## Zustandsvertrag

- Ein ausführbarer Zweig zeigt Tau, auch wenn ein anderer Zweig wartet.
- Ohne ausführbaren Zweig zeigt der Container die früheste gespeicherte Zeitwartephase.
  Die größte Einheit wird abgerundet, unter einer Sekunde bleibt bis zum Ablauf eine
  Sekunde sichtbar. Der gefüllte Kreissektor leert sich ab zwölf Uhr im Uhrzeigersinn;
  sein Anteil verwendet den Aktionszeitpunkt und den aktuellen Endzeitpunkt.
- Kapazitätswarten erhält ein ruhendes Symbol ohne geschätzte Restzeit.
- Einsammelbarer Tau bleibt als voller Container sichtbar. Antippen verwendet den
  bestehenden Sammelbefehl; eine zusätzliche Einsammelzeile gibt es nicht mehr.
- Sind ausschließlich Wartephasen vorhanden, bleibt das gesamte Blatt verborgen.
  Einsammeln zählt weiterhin als verfügbare Handlung, auch bei leerer Schrittliste.
- Die automatische Auszahlung beim letzten Schritt ohne Wartezeit bleibt bestehen.

Tau-Füllstände verwenden gebuchte Schrittbeträge und die bestehende RewardPolicy
für noch ausstehende Beträge. Die angezeigte Tau-Zahl verwendet den Aufgabenmultiplikator.
Schritt-Maserung verwendet unabhängig davon den Schritt-Combo. Es gibt keine neue
Buchungsregel und keine Datenbankmigration.

## Bedienung und Aktualisierung

Antippen einer wartenden Kette öffnet den vorhandenen Zeitdialog, bei mehreren
änderbaren Wartezeiten zuerst eine Schrittauswahl. Langdruck beziehungsweise die
benannte Accessibility-Aktion erhält diese Möglichkeit auch bei Einsammelbereitschaft.
Kapazitätswarten führt zur bestehenden Ablaufübersicht. Abgebrochene Dialoge
schreiben nichts; der bestehende Runtime-Befehl validiert veraltete Identitäten.

Der Container zeichnet seine Uhr nur sichtbar neu. Ein separater einzelner
Vordergrundtermin berücksichtigt alle laufenden Ketten, auch außerhalb des sichtbaren
Blatts. Er aktiviert zum Warteende die vorhandene Dashboard-Neuprojektion, wird bei
Zeitänderungen ersetzt und im Hintergrund abbestellt. Die vorhandene WorkManager-
Aktivierung und das erneute Laden im Vordergrund bleiben erhalten.

## Prüfnachweise

- `FlowChainProjectionTest`: Beträge, Kombo, Zeitbasis, größte Einheit und Kreisanteil.
- `FlowDeadlineSchedulerTest`: verborgenes Blatt, Deadline-Ersetzung, Hintergrund,
  Wiederaufnahme und keine Wiederholungsschleife bei einer veralteten Projektion.
- `GraphFlowRuntimePersistenceTest.nativeProjectionPreservesStepComboAndMovesCollectionToHeader`:
  echte SQLite-Projektion und unveränderte, einmalige Auszahlung auf API 26/35.
- `FlowChainStripViewTest`: gleichnamige Läufe, richtige Sammel-ID und Dialogabbruch.
- `GraphFlowSheetGoldenRobolectricTest`: 320 dp, große Schrift, 412 dp/dunkel,
  Einsammeln ohne Schrittzeilen und Kapazitätswarten.
- `TodayInteractionInstrumentationTest.flowContainersSwipeWithoutCollectingAndTapTheCorrectRun`:
  echte Touch-Geste ohne versehentliche Buchung, gefolgt von gezieltem Einsammeln.

Der Editor-Testhost zeichnet den bestehenden Waldrenderer ohne dessen endlosen
dekorativen Animator. Dieser hatte im API-35-Animationslauf nach Activity-Recreation
die Espresso-Ruheprüfung blockiert. Editor-Animationen bleiben eingeschaltet;
`ForestBackdropLifecycleTest` prüft die Waldanimation separat. Der produktive
Hintergrund bleibt unverändert.

Der Ketten-Gestentest wartet mit harten Zeitgrenzen auf Scrollposition und
Sammelaktion. Er wartet nicht auf globale Main-Looper-Ruhe: fertige native
Tau-Container pulsieren absichtlich dauerhaft. Im Animationslauf prüft er
ausdrücklich, dass dieses Pulsieren während der echten Touch-Geste aktiv bleibt.

Lokaler Gesamtlauf, PR-Matrix, gemergtes Remote-main, signiertes Release und
physisches Geräteupgrade sind getrennte Nachweise. Die bloße Existenz dieser
Tests behauptet weder grüne Läufe noch eine Geräteabnahme.

Die Remediation-Prüfung erweitert `FlowChainStripViewTest` um tatsächliche
SQLite-Wartezeitänderungen: Bestätigungszeitpunkt, Mehrfachauswahl in gleichnamigen
Ketten, beide Abbruchwege und Bearbeiten bei Einsammelbereitschaft. Der native
`readyFlowLongPressAndAccessibleWaitEditNeverCollect` prüft echten Langdruck sowie
die benannte Accessibility-Aktion bei weiterhin aktiver Pulsanimation. Ergebnisse
und Phasenstatus werden in `flow-remediation-execution.md` getrennt geführt.

`FlowDeadlineIntegrationTest` verbindet auf API 26/35 die kontrollierte Zeit mit
dem produktiven Scheduler, ClockInvalidationSource, TodayViewModel,
DashboardPresenter, SQLite und dem gemessenen nativen Renderer. Die Aktivierung
wird nicht aus dem Test aufgerufen. Verlängern, Verkürzen, Hintergrund/Resume und
Einsammeln nach der letzten Wartephase sind eingeschlossen. Flows und Scheduler
werden vor der initialen ViewModel-Projektion verbunden.
`TodayInteractionInstrumentationTest.hiddenFlowReappearsAtDeadlineInTheProductActivity`
und `hiddenFlowReappearsWhenTheProductReturnsAfterDeadline` prüfen dieselben
Übergänge mit realer MainActivity, Systemzeit und Activity-Recreation.

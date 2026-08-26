# Teststrategie nach dem Today-/Fokus-Refactor

Stand: 2026-08-20, Phase 8 abgeschlossen

Der Dateiname bleibt für bestehende Links erhalten. Der Inhalt beschreibt den aktuellen Stand
nach Datenbankschema 14 und der anschließenden Today-/Fokus-Baseline.

## Testschichten

- Reines JUnit prüft Fachregeln, Reducer und Layoutpolitik. `RepetitionProgress`,
  `RepetitionInputReducer` und `FocusStepLayoutPolicy` benötigen weder Android noch Room.
- Tests mit `InMemoryExecutionRepository` prüfen Use Cases, Reward, Completion, Undo, Schedule,
  Transaktionsrollback und Dauerlast ohne SQLite-Setup.
- Room/Robolectric prüft Entity-Mapping, Fremdschlüssel, Unique Constraints, SQL-Querybudgets,
  Prozessneustart und die reale Migrationskette.
- Room/Instrumentation paketiert zusätzlich Migrationstests mit `MigrationTestHelper` gegen die
  exportierten Schemas.
- Robolectric/UI prüft Komponentenverträge, Accessibility, Reduced Motion und Goldens.

Room-Integrationsabdeckung bleibt absichtlich bestehen. Der In-Memory-Port lokalisiert
Fachfehler schnell, simuliert aber weder SQLite noch Room.

## Layout- und Accessibility-Abdeckung

Die kombinatorische Android-Matrix verwendet sechs gezielt repräsentative Fälle statt des
früheren vollständigen 3×3×3-Kreuzprodukts. Jeder Wert aus 320/412/600 dp, Font Scale
1,0/1,3/2,0 und Morgen-/Abend-/Nachtpalette kommt mindestens einmal vor. Die kritischen
Kombinationen 320 dp mit Font Scale 2,0 sowie beide Breitenextreme bleiben enthalten.

Die vollständige Kombinatorik der Höhenentscheidung liegt in schnellen parametrisierten Tests
von `FocusStepLayoutPolicy`: Limit, Höhenbudget, aktive Zeile, Folgereihen und Resthinweis werden
ohne Robolectric geprüft. Androidtests prüfen repräsentativ reale Messung, Text-Clipping,
horizontale Bounds, TalkBack-Reihenfolge, Rollen, Zustände, virtuelle Satzaktionen,
Tastatursteuerung und Mindestziele.

Viewtests beobachten überwiegend öffentliche Events oder sichtbaren Viewzustand über die
Test-Fixtures `DashboardEventRecorder` und `ViewTestQueries`. Verbliebene package-private
Geometrie-/Renderzugriffe sowie `ReflectionHelpers` sind als technische Schuld dieser Baseline
explizit erfasst; die Leaf-Migration und Phase 5 entfernen sie wieder aus dem Produktivcode.

## Migrationsmatrix

`DatabaseMigrationRobolectricTest` deckt die historische Kette ab Schema 1 sowie den produktiv
unterstützten Upgradepfad von Schema 8 bis Schema 14 ab. `ExportedRoomSchemaFixture` baut historische Tabellen, Indizes, Views und Room-
Metadaten direkt aus `app/schemas/de.thonktank.autosecretary.AppDatabase/<version>.json` auf.

Die Migration 7→8 besitzt zusätzliche Fälle für 0, 12, 999, 1200, fehlerhaften Legacytext,
Idempotenz und die Korrektur genau einer Ergebniszeile. Der Instrumentationstest gegen das
exportierte v7-Schema wird lokal als Test-APK gebaut. Im CI-Lauf des Phase-7-Commits
`4ed5201c` liefen Instrumentation und der echte Upgrade-Probe jeweils auf API 26 und API 35
erfolgreich; eine erneute lokale Ausführung benötigt weiterhin Emulator oder Gerät.

Die Migration 8→9 prüft Carry-forward-Spalten, Defaultwerte und den Datenbanktrigger gegen
doppelte offene Occurrences. Die Migration 9→10 prüft die nullable Abschlussdatumsspalte und
rekonstruiert die Invarianten-Trigger. Die Migration 10→11 prüft außerdem, dass optionale
Task-Datumswerte aus historischen leeren Strings als SQL-`NULL` ankommen. Die Migrationen
11→12 und 12→13 sichern normalisierte Zeitplatzierungen und den verlustfreien Tabellenumbau;
13→14 ergänzt die zunächst leere Reward-Zuordnungsprojektion. Domaintests decken außerdem explizite Teilernte und
Refresh-Ursachen ab; ein echter Prozessneustart- und DST-Lauf bleibt ein Geräte-/Instrumentation-Gate.

## Golden-Vertrag

`GoldenAssertions` schreibt bei jedem Lauf das tatsächliche Bild nach
`app/build/reports/goldens/<komponente>`. Bei einer Abweichung liegen dort zusätzlich Expected
und ein magenta markierter Diff. Dieser Diff muss vor jeder Baselineänderung geprüft werden.

Baselines werden ausschließlich komponentenbezogen und in zwei Schritten aktualisiert. Zuerst
läuft der betroffene Test ohne Updatevariable. Bei einer Abweichung muss er fehlschlagen und das
zu genau diesem Renderstand gehörende Expected-/Actual-/Diff-Triplet erzeugen. Erst nach dessen
Prüfung darf derselbe Test mit seiner Updatevariable erneut laufen:

```bash
UPDATE_FOCUS_TASK_GOLDENS=1 ./gradlew testInstrumentationUnitTest \
  --tests de.thonktank.autosecretary.FocusTaskViewGoldenRobolectricTest
UPDATE_HOMESCREEN_GOLDENS=1 ./gradlew testInstrumentationUnitTest \
  --tests de.thonktank.autosecretary.HomescreenGoldenRobolectricTest
UPDATE_TASK_EDITOR_GOLDENS=1 ./gradlew testInstrumentationUnitTest \
  --tests de.thonktank.autosecretary.TaskEditorGoldenRobolectricTest
UPDATE_ALL_TASKS_GOLDENS=1 ./gradlew testInstrumentationUnitTest \
  --tests '*AllTasksRenderRobolectricTest'
```

Es gibt bewusst keinen globalen Update-Schalter. Ein Fokusupdate kann dadurch Widget- oder
Editor-Baselines nicht mitschreiben. `GoldenAssertions` übernimmt eine Baseline nur, wenn das
vorher erzeugte Triplet pixelgenau zum aktuellen Expected und Actual passt; ein fehlendes oder
veraltetes Triplet bricht den Updateversuch ab. Für eine neue Baseline gilt derselbe Ablauf mit
dem zuerst erzeugten Actual; ein Update ohne exakt passendes vorheriges Actual bricht ab. Unter
`CI` oder `GITHUB_ACTIONS` sind Updates gesperrt. Die vollständige Alles-Tab-Matrix ist in der
[Charakterisierung des Alles-Tabs](all-tasks-characterization.md) katalogisiert.

## Lokale Befehle und vollständiges Gate

Schnelle fachliche Rückmeldung:

```bash
./gradlew testInstrumentationUnitTest --tests '*RepetitionProgressTest' \
  --tests '*RepetitionInputReducerTest' --tests '*FocusStepLayoutPolicyTest'
```

Betroffene Fokusoberfläche einschließlich Golden und Accessibility:

```bash
./gradlew testInstrumentationUnitTest \
  --tests '*FocusTaskViewTest' --tests '*FocusTaskViewGoldenRobolectricTest' \
  --tests '*AccessibilityLayoutMatrixRobolectricTest' --tests '*SetBarsViewTest'
```

Verbindliches Abschluss-Gate:

```bash
./gradlew :core-domain:compileJava :today-core:compileJava \
  testInstrumentationUnitTest --no-parallel --max-workers=1
./gradlew assembleDebug assembleInstrumentationAndroidTest --no-parallel --max-workers=1
```

Der serielle Modus ist die reproduzierbare Referenz auf speicherknappen Rechnern. Er ersetzt
keine Ausführung der Instrumentationstests auf einem Android-Zielsystem.

Nach einem UI-Release wird die bewusste Aktualisierung auf genau einem physischen Gerät mit der
vorherigen Produktionsversion ausgeführt:

```bash
./scripts/ci/run-device-acceptance.sh forest-android-1010501
```

Das Skript installiert nichts per ADB. Es validiert Metadaten und APK-Hash, prüft Vorher-/Nachher-
Versionen rund um den am Gerät ausgelösten In-App-Updatepfad und schreibt Screenshot,
UI-Hierarchie, Geräteeigenschaften und `report.json` nach
`build/reports/device-acceptance/<release-tag>`. Ohne genau ein autorisiertes Gerät bleibt der
Bericht auf `pending`; eine Phase ist dann nicht vollständig abgenommen.

## Abschlussmessung

Auf derselben lokalen Umgebung sank die Laufzeit der
`AccessibilityLayoutMatrixRobolectricTest` durch die Verlagerung der Kombinatorik in reine Tests
von 25,495 s auf 11,169 bis 15,979 s in den vollständigen Abschlussläufen. Das letzte serielle
vollständige Gate lief nach dem Abschlussaudit in 63,78 s bei 1.132.312 KiB maximaler RSS. Die
Phase-0-Referenz desselben Gradle-Gates lag bei 84,50 s und 1.167.380 KiB. Damit ist das
Gesamtgate trotz der zwischenzeitlich ergänzten Fach-, Room-, Migration- und
Golden-Vertragstests rund 25 % schneller und die gemessene Spitzenbelegung rund 3 % niedriger.

Die Baseline der anschließenden Today-/Fokus-Bereinigung enthält 307 Hosttests, davon 306
erfolgreich und einen bewusst übersprungenen Test. Der reproduzierbare Lauf mit
`--rerun-tasks --max-workers=1` benötigte 84,00 Sekunden und maximal 1.133.556 KiB RSS. Alle
Fokus-, Homescreen-, Widget- und Editor-Goldens waren byteidentisch.

## Ergänzung: Today-/Fokus-Abschlussphase

`core-domain` und `today-core` sind reine Java-Module. Ihre Compilergrenzen ersetzen die
früheren Quelltextscans auf Android-/Managementimporte; Pakettests bleiben für Android-Views im
App-Modul bestehen. Die App-Unit-Suite testet die Kernklassen weiterhin zusammen mit den realen
Mappern, Room-Adaptern und Views.

`TodayInteractionInstrumentationTest` ergänzt den Gerätepfad in fünf voneinander unabhängigen
Szenarien: Long-Press-Beginn, Preview und persistierter Drop, Randscrollen, Accessibility-Reorder
und Abbruch bei Recreation. `TouchGestureDriver` besitzt als einzige Testklasse Finger-,
Touchscreen-Geräte-, Display- und Eventzeitinformationen. Die Tests warten auf beobachtbare
Today-Aktionen, Commands, Scrollzustand oder Coordinator-State; zeitgesteuert bleiben nur die
reale Long-Press-Schwelle und die Pointer-Bewegungsfolge.

`scripts/ci/run-instrumentation.sh` bewahrt den Gradle-Exitcode und sammelt bei einem Fehler noch
während der laufenden Emulatorinstanz Screenshot, UI-Hierarchie, Logcat, Geräte-, Input-,
Display- und Window-Daten. Die Testlogs ergänzen Start-, Ziel- und Listengeometrie, Touch-Geräte-
und Display-ID, Today-Aktionen, Commands und Scrollstrecke. GitHub lädt diese Daten getrennt je
API als Fehlerartefakt hoch.

Der manuell startbare Workflow `instrumentation-soak.yml` deinstalliert App und Test-App vor
jedem Durchlauf und führt ausschließlich die Today-Gestensuite fünfmal auf API 26 und fünfmal auf
API 35 aus. Er enthält keine Wiederholungslogik nach Fehlern: Ein fehlgeschlagener Versuch beendet
den jeweiligen Matrixjob und liefert dessen Diagnoseartefakt. Jede Änderung am
`TouchGestureDriver` benötigt vor Abschluss der Phase beziehungsweise des Pull Requests einen
vollständig grünen Soak-Lauf.

Die Golden-Suite wurde auf Redundanz geprüft. Die Fokus-Komponentengoldens schützen
Notiz-/Wiederholungs-/Hidden-Row-Geometrie, während die Homescreen-Goldens die gemeinsame
Header-, Timeline-, Fokus- und History-Komposition schützen. Keine Vollbildbaseline wurde
entfernt, weil diese Integrationssemantik nicht vollständig durch eine einzelne Komponente
ersetzt wird; sämtliche PNGs blieben unverändert.

Der abschließende serielle Lauf mit Modulkompilierung und `--rerun-tasks` benötigte 1:20,70 min
bei 1.126.912 KiB maximaler RSS. Gegenüber der Today-/Fokus-Phase-0-Baseline von 1:24,00 min und
1.133.556 KiB ist das Gate trotz 27 zusätzlicher Hosttests rund vier Prozent schneller und
benötigt geringfügig weniger Spitzenspeicher.

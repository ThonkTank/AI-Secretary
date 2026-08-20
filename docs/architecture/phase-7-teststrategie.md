# Teststrategie nach dem Today-/Fokus-Refactor

Stand: 2026-08-20, Phase 8 abgeschlossen

Der Dateiname bleibt für bestehende Links erhalten. Der Inhalt beschreibt den aktuellen Stand
nach Datenbankschema 9 und der Testbereinigung in Phase 8.

## Testschichten

- Reines JUnit prüft Fachregeln, Reducer und Layoutpolitik. `RepetitionProgress`,
  `RepetitionInputReducer` und `FocusStepLayoutPolicy` benötigen weder Android noch Room.
- Tests mit `InMemoryTaskRepository` prüfen Use Cases, Reward, Completion, Undo, Schedule,
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

Produktivcode stellt dafür keine `*ForTest`-Methoden bereit. Viewtests beobachten öffentliche
Events oder sichtbaren Viewzustand über die Test-Fixtures `DashboardEventRecorder` und
`ViewTestQueries`.

## Migrationsmatrix

`DatabaseMigrationRobolectricTest` deckt auf API 26 und 35 alle Ausgangsversionen 1 bis 7 bis
Schema 9 ab. `ExportedRoomSchemaFixture` baut historische Tabellen, Indizes, Views und Room-
Metadaten direkt aus `app/schemas/de.thonktank.autosecretary.AppDatabase/<version>.json` auf.

Die Migration 7→8 besitzt zusätzliche Fälle für 0, 12, 999, 1200, fehlerhaften Legacytext,
Idempotenz und die Korrektur genau einer Ergebniszeile. Der Instrumentationstest gegen das
exportierte v7-Schema wird lokal als Test-APK gebaut. Im CI-Lauf des Phase-7-Commits
`4ed5201c` liefen Instrumentation und der echte Upgrade-Probe jeweils auf API 26 und API 35
erfolgreich; eine erneute lokale Ausführung benötigt weiterhin Emulator oder Gerät.

Die Migration 8→9 prüft Carry-forward-Spalten, Defaultwerte und den Datenbanktrigger gegen
doppelte offene Occurrences. Domaintests decken außerdem explizite Teilernte und Refresh-Ursachen
ab; ein echter Prozessneustart- und DST-Lauf bleibt ein Geräte-/Instrumentation-Gate.

## Golden-Vertrag

`GoldenAssertions` schreibt bei jedem Lauf das tatsächliche Bild nach
`app/build/reports/goldens/<komponente>`. Bei einer Abweichung liegen dort zusätzlich Expected
und ein magenta markierter Diff. Dieser Diff muss vor jeder Baselineänderung geprüft werden.

Baselines werden ausschließlich komponentenbezogen und in zwei Schritten aktualisiert. Zuerst
läuft der betroffene Test ohne Updatevariable. Bei einer Abweichung muss er fehlschlagen und das
zu genau diesem Renderstand gehörende Expected-/Actual-/Diff-Triplet erzeugen. Erst nach dessen
Prüfung darf derselbe Test mit seiner Updatevariable erneut laufen:

```bash
UPDATE_FOCUS_TASK_GOLDENS=1 ./gradlew testDebugUnitTest \
  --tests de.thonktank.autosecretary.FocusTaskViewGoldenRobolectricTest
UPDATE_HOMESCREEN_GOLDENS=1 ./gradlew testDebugUnitTest \
  --tests de.thonktank.autosecretary.HomescreenGoldenRobolectricTest
UPDATE_TASK_EDITOR_GOLDENS=1 ./gradlew testDebugUnitTest \
  --tests de.thonktank.autosecretary.TaskEditorGoldenRobolectricTest
```

Es gibt bewusst keinen globalen Update-Schalter. Ein Fokusupdate kann dadurch Widget- oder
Editor-Baselines nicht mitschreiben. `GoldenAssertions` übernimmt eine Baseline nur, wenn das
vorher erzeugte Triplet pixelgenau zum aktuellen Expected und Actual passt; ein fehlendes oder
veraltetes Triplet bricht den Updateversuch ab. Unter `CI` oder `GITHUB_ACTIONS` sind Updates
gesperrt.

## Lokale Befehle und vollständiges Gate

Schnelle fachliche Rückmeldung:

```bash
./gradlew testDebugUnitTest --tests '*RepetitionProgressTest' \
  --tests '*RepetitionInputReducerTest' --tests '*FocusStepLayoutPolicyTest'
```

Betroffene Fokusoberfläche einschließlich Golden und Accessibility:

```bash
./gradlew testDebugUnitTest \
  --tests '*FocusTaskViewTest' --tests '*FocusTaskViewGoldenRobolectricTest' \
  --tests '*AccessibilityLayoutMatrixRobolectricTest' --tests '*SetBarsViewTest'
```

Verbindliches Abschluss-Gate:

```bash
./gradlew testDebugUnitTest --no-parallel --max-workers=1
./gradlew assembleDebug assembleDebugAndroidTest --no-parallel --max-workers=1
```

Der serielle Modus ist die reproduzierbare Referenz auf speicherknappen Rechnern. Er ersetzt
keine Ausführung der Instrumentationstests auf einem Android-Zielsystem.

## Abschlussmessung

Auf derselben lokalen Umgebung sank die Laufzeit der
`AccessibilityLayoutMatrixRobolectricTest` durch die Verlagerung der Kombinatorik in reine Tests
von 25,495 s auf 11,169 bis 15,979 s in den vollständigen Abschlussläufen. Das letzte serielle
vollständige Gate lief nach dem Abschlussaudit in 63,78 s bei 1.132.312 KiB maximaler RSS. Die
Phase-0-Referenz desselben Gradle-Gates lag bei 84,50 s und 1.167.380 KiB. Damit ist das
Gesamtgate trotz der zwischenzeitlich ergänzten Fach-, Room-, Migration- und
Golden-Vertragstests rund 25 % schneller und die gemessene Spitzenbelegung rund 3 % niedriger.

Der Abschlusslauf enthielt 246 Hosttests, davon 245 erfolgreich und einen bewusst übersprungenen
Test. Alle Fokus-Goldens waren byteidentisch; keine Fokus-, Homescreen-, Widget- oder
Editor-Baseline wurde in Phase 8 geändert.

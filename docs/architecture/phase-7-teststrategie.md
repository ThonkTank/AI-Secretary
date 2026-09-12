# Teststrategie nach dem Today-/Fokus-Refactor

Stand: 2026-09-09, mobile Verifikationshärtung abgeschlossen

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
UPDATE_ALL_TASKS_GOLDENS=1 ./gradlew testInstrumentationUnitTest \
  --tests '*AllTasksRenderRobolectricTest'
```

Es gibt bewusst keinen globalen Update-Schalter. Die Editor-Baselines sind seit dem Compose-
Cutover vollständig schreibgeschützt und werden mit
`TaskEditorComposeGoldenRobolectricTest` nur noch gelesen. Ein Fokusupdate kann dadurch Widget-
oder Editor-Baselines nicht mitschreiben. `GoldenAssertions` übernimmt eine andere Baseline nur, wenn das
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
  --tests '*AccessibilityLayoutMatrixRobolectricTest' --tests '*SetDotsViewTest'
```

Verbindliches Abschluss-Gate:

```bash
./gradlew :core-domain:compileJava :today-core:compileJava \
  testInstrumentationUnitTest --no-parallel --max-workers=1
./gradlew assembleDebug assembleInstrumentationAndroidTest --no-parallel --max-workers=1
```

Der serielle Modus ist die reproduzierbare Referenz auf speicherknappen Rechnern. Die verbindliche
Remote-Matrix des vollständigen Profils ergänzt normale und animationsaktive Instrumentierung auf API 26, 35 und 37. Bei
produktwirksamen Änderungen schließen Produktionsupgrade, Packaging, Signatur-, Hash-, Paket-,
Versions- und Trust-Prüfungen sowie Publish den exakten Main-Stand ab. Der Abschluss folgt
[ADR-030](adr-030-minimale-trainingsarchitektur-und-automatisierter-abschluss.md).

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

Der manuell startbare Workflow `instrumentation-soak.yml` deinstalliert vor jedem Durchlauf nur
die reguläre Instrumentierungs-App und deren Test-APK, niemals die Produktions-App. Er führt
ausschließlich die Today-Gestensuite fünfmal auf API 26 und fünfmal auf API 35 aus. Er enthält
keine Wiederholungslogik nach Fehlern: Ein fehlgeschlagener Versuch beendet den jeweiligen
Matrixjob und liefert dessen Diagnoseartefakt. Jede Änderung am
`TouchGestureDriver` benötigt vor Abschluss der Phase beziehungsweise des Pull Requests einen
vollständig grünen Soak-Lauf.

Die Golden-Suite wurde auf Redundanz geprüft. Die Fokus-Komponentengoldens schützen
Notiz-/Wiederholungs-/Hidden-Row-Geometrie, während die Homescreen-Goldens die gemeinsame
Header-, Timeline-, Fokus- und History-Komposition schützen. Keine Vollbildbaseline wurde
ohne Nachweis entfernt. Die byteidentischen Homescreen-Baselines `complete.png` und
`harvested.png` schützten dasselbe Bild; `harvested.png` und sein doppelter Renderdurchlauf sind
entfallen, während die Abschlusssemantik weiterhin durch den Architekturvertrag geprüft wird.
Jede verbliebene Baseline ist in `app/src/test/resources/golden-risks.tsv` genau einem
eindeutigen visuellen Risiko zugeordnet.

Der abschließende serielle Lauf mit Modulkompilierung und `--rerun-tasks` benötigte 1:20,70 min
bei 1.126.912 KiB maximaler RSS. Gegenüber der Today-/Fokus-Phase-0-Baseline von 1:24,00 min und
1.133.556 KiB ist das Gate trotz 27 zusätzlicher Hosttests rund vier Prozent schneller und
benötigt geringfügig weniger Spitzenspeicher.

## Verbindlicher Ablaufkandidaten-Nachfolger

Die [Ablaufkandidaten-/Today-Blatt-Roadmap](flow-task-sheet-roadmap.md) und
[ADR-033](adr-033-ablaufkandidaten-und-gemeinsame-today-blaetter.md) erweitern diese Strategie
für den ausgelieferten Schema-23-Cutover. Der vollständige Abnahmefall kombiniert vier fällige
Startkandidaten, eine Waschmaschine, drei Trockenplätze, eingegebene Wasch- und Trocknungszeiten,
einen verlängerten angebotenen Folgeschritt und mindestens eine normale Today-Aufgabe.

Die Schichten belegen getrennt:

- reine Domainregeln für Kandidatendeduplizierung, Kapazitätsvorschau und typisierte
  Startausgänge;
- transaktionalen Start ohne partiellen Run, Reward oder Ressourcenclaim;
- Room-Invarianten sowie Migration von einer realen Schema-22-Mischung aus ungestarteten,
  wartenden und angebotenen Runs;
- genau ein `FlowTaskSheet`, stabile Today-Platzierung, normales „Später“ und das vollständige
  Verschwinden leerer Blätter;
- Ausschluss von Kandidaten aus Alles-Hintergrundläufen und Ausschluss eingabepflichtiger
  Kurzschlussaktionen im Widget;
- unveränderte sichtbare Zeit-/Mengenangaben, Herkunftstitel und Accessibility bei entfallenden
  Status- und Satzfortschrittstexten.

Für den damaligen Schema-23-Cutover blieb die PR-Matrix vollständig. Die aktuelle profilabhängige
Auswahl und strenge PR-/Main-Wiederverwendung sind in [ADR-037](adr-037-risikobasierte-verifikation-und-aktuelles-upgrade.md) festgelegt.
Packaging, Signatur, Hash, anwendbare Upgrades und Publish bleiben am exakten Main-Kandidaten.

## Aktueller Verifikationsvertrag

Die lokale und die Remote-Prüfung verwenden die vier Profile aus [ADR-037](adr-037-risikobasierte-verifikation-und-aktuelles-upgrade.md).
Das vollständige Profil besitzt drei getrennte Qualitäts-Lanes:

- `scripts/ci/check-fast.sh` führt Helferverträge sowie nichtvisuelle Unit-, Room- und
  Architekturtests aus;
- `scripts/ci/check-goldens.sh` führt ausschließlich Golden- und Design-System-Verträge aus;
- der Build-Lane führt Lint und alle installierbaren Debug-, Instrumentierungs-, Test- und
  Release-Pakete aus.

Der lokale Vorabcheck `scripts/ci/check-changes.sh [BASIS]` verwendet dieselbe Klassifikation wie
CI: Dokumentverträge für `docs`, die gesamte Hostsuite einschließlich Goldens für `host`, schnelle
Contracts inklusive P1 für `today`, `check-all.sh` für `full`. Ein lokaler Today-Vorabcheck behauptet
keinen lokalen Build oder Gerätelauf; CI verlangt diese zusätzlich. `--all` beziehungsweise der
direkte `check-all.sh` erzwingen weiterhin die Vollprüfung ohne doppelte Hosttest-Aufgabe.

GitHub fasst alle ausgewählten Qualitätsergebnisse fail-closed als `quality` zusammen. Vertrags- und Golden-Jobs
enden nach 15 Minuten, ihre Hauptschritte nach 12 Minuten; der gemessene kalte Build-Lane erhält
30 beziehungsweise 25 Minuten. Geräteaktionen enden nach 20 Minuten. Fehlerpfade laden die
anwendbaren Test-, Golden-, Lint-, Build- oder Gerätedaten hoch; automatische Wiederholung ist
kein Bestandteil des Gates.

Reguläre Instrumentierung besitzt den Paketnamen `de.thonktank.autosecretary.test`, das
zugehörige Test-APK `de.thonktank.autosecretary.test.test` und das sichtbare Label „Auto
Secretary Test“. Sie kann deshalb eine installierte Produktions-App nicht ersetzen. Nur
`-PupgradeProbeRunner=true` baut bewusst gegen `de.thonktank.autosecretary`; dieser Pfad wird
mit dem signierten Produktionskandidaten in der Upgrade-Matrix und für den ausdrücklich
unterstützten lesenden Diagnosezugang verwendet.

Der frühe Diagnosevertrag aus [ADR-038](adr-038-frueher-diagnose-bootstrap.md) wird in den
normalen vollständigen API-26/35/37-Lanes zusätzlich durch frische Prozesse geprüft:
Schema24/27, echte Plattformereignisse, vollständige Nutzdaten-/Schema-Snapshots, reguläres Ende
und Abbruch sowie normaler Timer-/Worker-Wiederanlauf. Der native Treiber akzeptiert ausschließlich
das Emulator-Testpaket; der Produktionshelper deklariert seine Fixture-/Normal-Runner nicht.
Ein fehlgeschlagener Diagnose-Lifecycle sperrt die Lane auch nach erfolgreicher JUnit-Prüfung.

Der Diagnose-Treiber vergleicht den vollständigen Datenbestand vor Ende des gehaltenen Prozesses.
Ein nachfolgender normaler Systemprozess darf bereits Timer und Worker abgleichen; dessen Änderungen
sind kein Verstoß gegen die beendete Diagnose. Activity-Ereignisse werden durch echte Erzeugungs-/
Zerstörungs-Callbacks in der Diagnose-PID belegt, nicht durch `am start -W` auf eine absichtlich
sofort beendete Oberfläche. Der CLI verfolgt ebenfalls die ursprüngliche Diagnose-PID.

# Auto Secretary

Eine kleine, private ADHS-Task-App für Android: nur **jetzt**, **danach** und ein ruhiger Tagesblick.

## Was bereits funktioniert

- Einmalige und wiederkehrende Aufgaben in einem ruhigen Vollbild-Editor
- Tageszeiten Morgen, Mittag, Abend und Später
- Wiederholungen: täglich, alle N Tage und ausgewählte Wochentage, auch mehrmals täglich
- Fristen und Laufzeitgrenzen sowie Schritte mit Wochentagen, Mengen und Notizen
- „Alles“-Arbeitsbereich zum Suchen, Filtern, Bearbeiten und manuellen Ordnen von Aufgaben
  und Schritten, getrennt vom Abarbeiten im Heute-Tab
- Inline bearbeitbare Ist-Wiederholungen für Übungen; bestehende Tagesvorkommen bleiben unverändert
- Sanftes Verschieben mit „Später“, XP-Stufen, Routinegefäßen und einer sichtbaren Kombo-Maserung
- Uhrzeitabhängiger Heute-Screen im Design „Tiefer Wald, goldener Sonnenaufgang“
- Vier responsive Homescreen-Widgetgrößen mit direkten Schritt- und Abschlussaktionen
- Rein lesende Einbindung aller sichtbaren Kalender über die Android-Kalenderfreigabe
- Optionen für automatische, helle oder dunkle Darstellung und einen geprüften GitHub-Updatekanal
- Keine Benachrichtigungen im Stabilitäts-Release: Das Widget bleibt die einzige proaktive Erinnerung
- Tägliche und manuelle Updateprüfung mit signiertem In-App-Download und Android-Installer

## Lokal testen

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Der vollständige lokale Quality-Gate ist:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease
```

Deterministische Zustände für Layout Inspector und Preview-Werkzeuge liegen im Debug-Build
unter `DebugPreviewFixtures`. Die 17 Editor-Referenzzustände werden zusätzlich als
Robolectric-Goldens geprüft. Für den Today-Screen decken Phone-Goldens unter anderem leere,
teilgefüllte, erntereife und geerntete Gefäße sowie Tag, Abend und Nacht ab. Die CI führt die
Room-Migrationstests auf API 26 und API 35 aus.

Die Release-Einrichtung ist in [docs/releasing.md](docs/releasing.md) beschrieben. Die
Produktentscheidung ist in [docs/produktziele.md](docs/produktziele.md) festgehalten. Die
verbindlichen Architekturentscheidungen und visuellen Referenzen beginnen unter
[docs/architecture](docs/architecture/README.md).

Jeder grüne Push auf `main` wird automatisch als installierbarer Build veröffentlicht. Beim
ersten Wechsel von einem Debug-Build ist wegen der dauerhaft anderen Signatur eine einmalige
Neuinstallation nötig; danach bleiben Aufgaben bei Updates erhalten.

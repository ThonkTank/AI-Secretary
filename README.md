# Auto Secretary

Eine kleine, private ADHS-Task-App für Android mit **jetzt**, **danach** und einem ruhigen Tagesblick.

## Was bereits funktioniert

- Einmalige und wiederkehrende Aufgaben in einem ruhigen Vollbild-Editor
- Tageszeiten Morgen, Mittag, Abend und Später
- Wiederholungen: täglich, alle N Tage und ausgewählte Wochentage, auch mehrmals täglich
- Fristen und Laufzeitgrenzen sowie Schritte mit Wochentagen, Mengen und Notizen
- „Alles“-Arbeitsbereich zum Suchen, Filtern, Bearbeiten und manuellen Ordnen von Aufgaben
  und Schritten, getrennt vom Abarbeiten im Heute-Tab
- Ist-Wiederholungen direkt erfassen und Schritte oder Schrittnotizen im Heute-Tab bearbeiten
- Sanftes Verschieben mit „Später“, XP-Stufen, Routinegefäßen und einer sichtbaren Kombo-Maserung
- Uhrzeitabhängiger Heute-Screen im Design „Tiefer Wald, goldener Sonnenaufgang“
- Vier responsive Homescreen-Widgetgrößen mit direkten Schritt- und Abschlussaktionen
- Rein lesende Einbindung aller sichtbaren Kalender über die Android-Kalenderfreigabe
- Optionen für automatische, helle oder dunkle Darstellung und einen geprüften GitHub-Updatekanal
- Keine Benachrichtigungen im Stabilitäts-Release: Das Widget bleibt die einzige proaktive Erinnerung
- Tägliche und manuelle Updateprüfung mit signiertem In-App-Download und Android-Installer

## Einstieg für Änderungen

- [Schrittregeln](docs/architecture/adr-025-mengenbasierte-schritt-rewards.md) für Mengen, XP und
  Tagesabschluss sowie [Speicherregeln für Schrittänderungen](docs/architecture/adr-040-schritte-direkt-bearbeiten.md#speichervertrag)
  für Vorlage, laufende Ausführung und vorhandenen Fortschritt.
- [Dialogzustand in Heute](docs/architecture/architecture-map.md#dialogzustand-in-heute) für
  Entwürfe, Wiederherstellung und Speichern.
- [Signierte Upgradepflicht](docs/architecture/adr-037-risikobasierte-verifikation-und-aktuelles-upgrade.md#wiederverwendung-und-veröffentlichung)
  für Produktreleases und [Release-Anleitung](docs/releasing.md).
- [Produktziele](docs/produktziele.md) und [Architekturindex](docs/architecture/README.md).

Die [gezielten Testbefehle für Mengenfortschritt und Schrittbearbeitung](docs/architecture/phase-7-teststrategie.md#schrittregeln-prüfen)
prüfen XP-Korrekturen und schützen bereits erfasste Ausführungen.

Beantworte im Pull Request drei Fragen.

1. Wo lebt die neue Fachregel?
2. Welchen bestehenden Schreib- oder Lesepfad benutzt sie?
3. Welcher Test würde einen fachlich falschen Ausgang erkennen?

Belege bei Änderungen an persistenten Daten das Verhalten mit vorhandenen Nutzerbeständen.
Prüfe bei Lifecycle- oder Bedienänderungen den betroffenen nativen Ablauf.

## Lokal testen

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Der lokale Vorabcheck wählt dasselbe Risikoprofil wie CI und berücksichtigt unversionierte Dateien:

```bash
./scripts/ci/check-changes.sh            # gegen origin/main
./scripts/ci/check-changes.sh --all      # Vollprüfung erzwingen
```

Die schnelle Prüfschleife ohne visuelle Goldens ist weiterhin direkt nutzbar:

```bash
./scripts/ci/check-fast.sh
```

Visuelle Verträge und installierbare Pakete laufen getrennt:

```bash
./scripts/ci/check-goldens.sh
./gradlew lintDebug assembleDebug assembleInstrumentation \
  assembleInstrumentationAndroidTest assembleRelease
```

Der vollständige lokale Quality-Gate führt alle Verträge ohne doppelte Hosttest-Aufgabe aus:

```bash
./scripts/ci/check-all.sh
```

Im vollständigen Profil führt CI die drei Lanes parallel aus. Die stabilen Aggregate verlangen
alle ausgewählten Nachweise. Das enge Today-Profil verwendet Contracts, Build/Lint und einen
API-35-Animationslauf; Details und Grenzen stehen in
[ADR-037](docs/architecture/adr-037-risikobasierte-verifikation-und-aktuelles-upgrade.md). Reguläre
Gerätetests installieren die sichtbar benannte App „Auto Secretary Test“ unter
`de.thonktank.autosecretary.test`; nur der signierte Upgrade-Probe-Pfad verwendet die echte
Produktions-ID.

Deterministische Zustände für Layout Inspector und Preview-Werkzeuge liegen im Debug-Build
unter `DebugPreviewFixtures`. Die 17 Editor-Referenzzustände werden zusätzlich als
Robolectric-Goldens geprüft. Für den Today-Screen decken Phone-Goldens unter anderem leere,
teilgefüllte, erntereife und abgeschlossene Gefäße sowie Tag, Abend und Nacht ab. Im vollständigen Profil führt CI die
Room-Migrationstests auf API 26, API 35 und API 37 aus.

Releasewirksame Änderungen werden nach ihrem grünen Main- und signierten Upgrade-Gate
automatisch als installierbarer Build veröffentlicht. Beim
ersten Wechsel von einem Debug-Build ist wegen der dauerhaft anderen Signatur eine einmalige
Neuinstallation nötig; danach bleiben Aufgaben bei Updates erhalten.

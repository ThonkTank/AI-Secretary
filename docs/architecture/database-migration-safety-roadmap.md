# Roadmap: sichere Datenbankmigrationen und reale Upgradehistorien

Status: verbindlich

Beschlossen: 2026-09-08

Ausgangsstand bei Beschluss: `ed57b466` (`origin/main`)

Ausgangsschema: Room 24

## Zielzustand

Datenbankmigrationen kopieren persistierte Werte ausschließlich über benannte Ziel- und
Quellspalten. Tests bilden neben dem logischen Room-Schema auch die reale physische
Tabellenhistorie ab. Der Releaseweg prüft den ältesten unterstützten Produktionsstand sowie
gezielte Schemaepochen mit später entstandenen Daten. Ein Geräte-Recovery ist erst nach
technischer und sichtbarer Datenabnahme geschlossen.

| Ausgangsproblem | Verbindlicher Zielzustand |
|---|---|
| Positionsabhängige `SELECT *`-Schreibkopien | Keine positionsabhängige Schreibkopie in einer Produktionsmigration |
| Schemaexport verdeckt physische Historie | Lineage-Fixtures führen die echten Zwischenmigrationen aus |
| Breite API-Matrix mit gemeinsamer Datenlücke | Plattform- und Datenhistorienrisiken werden getrennt geprüft |
| Schwer rekonstruierbare Reparaturzuordnung | Mapping ist direkt an Migration und Architekturvertrag dokumentiert |
| Produktionsfixture ohne spätere Ablaufdaten | Signierte Schema-20- und Schema-23-Regressionsfixtures ergänzen Schema 8 |
| Offene sichtbare Geräteabnahme | Recovery endet mit App-, Widget-, Protokoll- und Bestandsdatenprüfung |

## Unveränderliche Grenzen

- Room bleibt auf Schema 24, solange die Umsetzung keine tatsächliche Schemaänderung findet.
- App-, Domain- und UI-APIs bleiben unverändert.
- Der unterstützte Produktionsupgradepfad beginnt weiterhin bei Schema 8 beziehungsweise
  `forest-android-1008001`.
- Persönliche Gerätedaten werden nicht als Testfixture übernommen.
- Fixtures werden nicht pauschal mit jeder API-Lane kreuzmultipliziert. Jede Lane muss ein
  eigenes Plattform- oder Datenrisiko abdecken.
- Geräte werden weder deinstalliert noch zurückgestuft; App-Speicher wird nicht gelöscht.
- Jede Phase beginnt vom dann aktuellen `origin/main` in einem isolierten Worktree. Der aktive
  UI-Checkout bleibt unverändert.

## Verbindlicher Phasenablauf

Vor jeder Phase werden diese Roadmap, das getrennte Ausführungsprotokoll, der aktuelle
Repositoryzustand und die direkt betroffenen Verträge erneut gelesen. Der konkrete Phasenplan
wird vor der Implementierung im Ausführungsprotokoll festgehalten. Nach der Validierung folgt ein
getrennter Abgleich gegen Phasenplan und Gesamtroadmap. Abweichungen erhalten vor ihrer Korrektur
einen eigenen protokollierten Fixplan.

Jede Implementierungsphase wird committed, in einem eigenen Pull Request gegen `main` geprüft
und erst nach grünem `pull-request-gate` per Squash-Merge übernommen. Die nächste Phase beginnt
erst vom gemergten `origin/main` und nach grünem exaktem Main-Workflow. Produktwirksame Phasen
benötigen zusätzlich Packaging, signierte Upgrades auf API 26/35/37 und Publish. Eine reine
Dokumentationsphase darf keinen künstlichen Produktrelease erzeugen.

## Phase 1 – Verbindlicher Migrations- und Lineage-Vertrag

Branch: `codex/migration-lineage-contract`

1. Sämtliche `INSERT INTO ... SELECT *`-Schreibkopien in `DatabaseMigrations` werden durch
   vollständige Ziel- und Quellspaltenlisten ersetzt. Temporäre Sicherungen mit
   `CREATE TEMP TABLE ... AS SELECT *` bleiben nur zulässig, wenn jede spätere Schreibkopie
   semantisch benannt ist.
2. Ein Architekturtest verbietet positionsabhängige Schreibkopien dauerhaft, analog zum
   bestehenden Verbot von `RENAME COLUMN`.
3. Die bereits veröffentlichte Migration 23 nach 24 erhält direkt im Code eine kompakte
   Mapping-Erklärung. Ihre Erkennung und ihr Laufzeitverhalten bleiben unverändert.
4. Eine kleine Testhilfe erzeugt historische Datenbanken, sät Daten an festgelegten
   Schemaepochen, führt echte Zwischenmigrationen aus und kann die physische Spaltenreihenfolge
   mit `PRAGMA table_info` belegen.
5. Der organische Schema-20-Fall beweist ausdrücklich die abweichende physische Reihenfolge und
   prüft anschließend Text, Notiz, Mengen, Last, `targetRir`, Verzögerungsmodus und alle
   Verzögerungswerte.
6. Jede in 22 nach 23 neu aufgebaute Tabelle erhält semantisch unterscheidbare Sentinelwerte in
   ihren relevanten Spalten. Beabsichtigte Umbenennungen und Normalisierungen werden explizit
   erwartet; jede andere Wertänderung ist ein Fehler.

Abnahme:

- Kein `INSERT INTO ... SELECT *` verbleibt in `DatabaseMigrations`.
- Schemaexporte und `DatabaseContract.VERSION == 24` bleiben unverändert.
- Die Pfade 1 nach 24 und der produktive Pfad 8 nach 24 sind grün.
- Crashzeile, vollständig belegte Vertauschungszeile und korrekte Schema-23-Zeile bleiben
  vollständig abgedeckt.
- Fokussierte Migrationstests, vollständiger lokaler Android-Gate, PR-Matrix, Squash-Merge,
  exakter Main-Lauf, Packaging, API-26/35/37-Upgrades und Veröffentlichung sind grün.

## Phase 2 – Signierter Upgrade-Fixture-Korpus

Branch: `codex/release-upgrade-fixture-matrix`

1. Die fest verdrahtete Einzel-Fixture wird durch einen kleinen deklarativen Korpus ersetzt.
   Jede Fixture enthält ID, Risikobeschreibung, unveränderlichen Quelltag, Versionscode,
   Datenbankschema, vollständige Seedwerte, erwartete Zielwerte und zugeordnete API-Lanes.
2. `forest-android-1008001` mit Schema 8 bleibt die unterstützte Untergrenze auf API 26, 35 und
   37.
3. `forest-android-1013701` mit Schema 20 bildet auf API 26 eine organische Ablaufhistorie vor
   Einführung der Lastfelder.
4. `forest-android-1015801` mit Schema 23 enthält auf API 26 parallel eine eindeutig vertauschte
   und eine korrekte Ablaufzeile.
5. Upgrade-Probe und Runner erhalten die Fixture-ID als Parameter. Jede Quelle wird vor der
   Installation über Tag, Release-Metadaten, Commit, Paketname, Versionscode, SHA-256 und
   Produktionssignatur geprüft.
6. Der Produktionskandidat wird weiterhin genau einmal gebaut. Alle Fixture/API-Jobs verwenden
   dasselbe signierte Artefakt; Publish hängt von allen obligatorischen Upgradefällen ab.

Abnahme:

- Keine Fixture ist im Probe-Code fest verdrahtet.
- Verträge prüfen jede Quellspalte, Zielversion, Tag-Konsistenz und erwartete Zielspalte.
- Fehlende, doppelte oder unbekannte Fixture-IDs brechen die Prüfung ab.
- Der signierte Schema-8-Pfad ist auf API 26/35/37 grün; die signierten Schema-20- und
  Schema-23-Regressionspfade sind auf API 26 grün.
- Paket-, Signatur-, Commit-, Hash- und Bytegleichheitsnachweise bleiben erhalten.
- Lokale Werkzeugtests, PR-Gate, Squash-Merge, exakter Main-Lauf und Publish sind grün.

## Phase 3 – Dokumentation und Recovery-Vertrag

Branch: `codex/migration-safety-documentation`

1. ADR-035 hält logisches Room-Schema, physische SQLite-Historie, das Verbot positioneller
   Schreibkopien, Fixture-Arten, Schemafolge 20 nach 24 und das exakte Reparaturmapping fest.
2. ADR-005 beschreibt die tatsächliche API-26/35/37-Matrix und den Fixture-Korpus.
3. `docs/signing-and-recovery.md` erhält den datenbewahrenden Recovery-Ablauf: keine
   Deinstallation, vollständige Artefaktprüfung, ausschließlich Vorwärtsupdate, Versions- und
   Erstinstallationsprüfung, App-/Widgetstart, frisches Fehlerprotokoll und sichtbare Prüfung von
   Aufgaben, Verlauf und bestehendem Ablauf.

Abnahme:

- Aktive Dokumentation beschreibt den implementierten Vertrag im Präsens und ohne Widerspruch.
- Architekturindex verlinkt Roadmap, Execution Log und ADR-035.
- Der tatsächliche Diff wird als dokumentationsrein und `release_required=false` klassifiziert.
- Dokumentationsprüfungen, PR-Gate, Squash-Merge und exakter Main-Workflow sind grün; es entsteht
  kein Produktrelease.

## Cross-Phase- und Geräteabschluss

Nach allen drei Phasen wird der aktuelle Repositorystand erneut gegen jede Anforderung dieser
Roadmap geprüft. Nach dem letzten produktiven Release werden APK und Metadaten heruntergeladen
und vor der Installation vollständig geprüft. Das autorisierte Pixel 8 erhält ausschließlich ein
datenbewahrendes `adb install -r`. Installierte Version, Paket, Signatur, erhaltenes
Erstinstallationsdatum, Kaltstart, Widget und frisches SQLite-/Crashprotokoll werden kontrolliert.
Nach Entsperrung werden vorhandene Aufgaben, Verlauf und mindestens ein bestehender Ablauf
sichtbar bestätigt. Bei einem Fehler bleiben App und Daten installiert; es wird ausschließlich
vorwärts korrigiert.

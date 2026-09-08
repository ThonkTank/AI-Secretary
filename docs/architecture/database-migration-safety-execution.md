# Ausführung: sichere Datenbankmigrationen und reale Upgradehistorien

Kanonische Grundlage:
[Roadmap: sichere Datenbankmigrationen und reale Upgradehistorien](database-migration-safety-roadmap.md)

Dieses Protokoll ist append-only. Phasenplan, lokale Validierung, Audit, Korrekturrunden,
Pull-Request-Gate, Squash-Merge, exakter Main-Lauf, Veröffentlichung und Geräteabnahme werden
getrennt ausgewiesen.

## Phase 1 – Verbindlicher Migrations- und Lineage-Vertrag

Status: in Arbeit

Ausgangsstand: `ed57b466` (`origin/main`)

### Plan

Ergebnis: Produktionsmigrationen schreiben persistierte Werte nur noch über benannte Spalten.
Der Testvertrag unterscheidet logische Room-Exporte von organisch entstandener physischer
Tabellenreihenfolge und prüft jede in 22 nach 23 neu aufgebaute Tabelle semantisch.

Betroffene Komponenten:

- `DatabaseMigrations` mit historischen Tabellenneuaufbauten und Migration 23 nach 24;
- `ArchitectureBoundaryTest` mit dem statischen Migrationsvertrag;
- `DatabaseMigrationRobolectricTest` und eine kleine Testhilfe für physische Schemahistorien;
- kanonische Roadmap und dieses Ausführungsprotokoll.

Reihenfolge:

1. Schemaexporte, Datenbankversion und aktuelle Migrations-/Teststruktur als Baseline erfassen.
2. Alle positionsabhängigen Schreibkopien durch explizite Ziel-/Quellprojektionen ersetzen und
   das Reparaturmapping dokumentieren, ohne fachliche Transformationen zu verändern.
3. Den statischen Negativvertrag ergänzen.
4. Die Lineage-Testhilfe und organischen Schema-20-Sentinels einführen; danach den vollständigen
   22-nach-23-Tabellenverbund mit unterscheidbaren Werten prüfen.
5. Fokussierte Migrationstests auf den vorgesehenen SDK-Konfigurationen, CI-/Releaseverträge,
   vollständigen lokalen Android-Gate und `git diff --check` mit JDK 21 ausführen.
6. Implementierung getrennt gegen diesen Phasenplan und die komplette Roadmap auditieren. Vor
   jeder notwendigen Korrektur zuerst einen Fixplan hier ergänzen.

Abnahme: Kein positionelles Schreib-Insert verbleibt; Schema 24 und öffentliche APIs sind
unverändert; alle Werte- und Lineage-Verträge sind grün. Erst danach folgen Commit, Pull Request,
Squash-Merge, exakter Main-Releaseweg und der Beginn von Phase 2.

## Phase 2 – Signierter Upgrade-Fixture-Korpus

Status: wartet auf Phase 1

## Phase 3 – Dokumentation und Recovery-Vertrag

Status: wartet auf Phase 2

## Cross-Phase- und Geräteabschluss

Status: wartet auf Phase 3

### Lokale Umsetzung und Validierung – 2026-09-08

Umgesetzt wurden vollständige Ziel- und Quellspaltenlisten für sämtliche zehn verbliebenen
positionsabhängigen Schreibkopien in `DatabaseMigrations`, der statische Negativvertrag, die
Lineage-Testhilfe mit physischer `PRAGMA table_info`-Prüfung sowie semantisch unterscheidbare
Sentinels für alle von 22 nach 23 neu aufgebauten Tabellen. Die Migration 23 nach 24 wurde nur
um die explizite historische Zuordnung und die Begründung ihrer domänenscharfen Erkennung
dokumentiert; ihr Laufzeitverhalten blieb unverändert.

Validierung mit JDK 21 und Android SDK `/home/aaron/Android/Sdk`:

- fokussiert: `ArchitectureBoundaryTest` und `DatabaseMigrationRobolectricTest`, grün in 5:42;
- fokussiert nach Ergänzung der Tabellensentinels: `DatabaseMigrationSemanticCopyTest`,
  `DatabaseMigrationRobolectricTest` und `ArchitectureBoundaryTest`, grün in 2:27;
- Python-CI-Verträge: 24 von 24 grün;
- Python-Releaseverträge: 24 von 24 grün;
- vollständiger lokaler Android-Gate mit `testInstrumentationUnitTest`, `lintDebug`,
  `assembleDebug`, `assembleInstrumentationAndroidTest` und `assembleRelease`: 157 Aufgaben,
  grün in 22:53;
- `git diff --check`: grün.

### Phasenaudit – 2026-09-08

Der getrennte Abgleich gegen Phasenplan und Gesamtroadmap ergab keine zu korrigierende
Abweichung:

- kein `INSERT INTO ... SELECT *` verbleibt in `DatabaseMigrations`; temporäre
  `CREATE TEMP TABLE ... AS SELECT *`-Snapshots werden nur noch durch benannte Projektionen
  zurückgeschrieben;
- `DatabaseContract.VERSION` bleibt 24;
- Schemaexport 24 blieb bytegleich mit SHA-256
  `200e46de5c60137ea9e09524c7cb91ffaed322033c1790d0d459ace6b4cb6bf3`;
- `DatabaseContract.java` blieb bytegleich mit SHA-256
  `489c1114b1d5d3e15254a8eaa7a3a131540f10005b0faf77484dab993492dba4`;
- es gibt keine Änderung an App-, Domain- oder UI-APIs;
- die Pfade 1 nach 24 und 8 nach 24, die organische physische Folge 20 nach 21 nach 22 nach 24,
  die leere Crashzelle, die vollständig belegte Vertauschungszeile und die korrekte
  Schema-23-Zeile sind im grünen Gesamtlauf enthalten;
- jede der zehn von 22 nach 23 neu aufgebauten Tabellen wird spaltenweise nach Bedeutung
  geprüft.

Lokales Gate: erfüllt. Remote-Gate, Squash-Merge, exakter Main-Lauf und Veröffentlichung stehen
noch aus; Phase 1 und damit Phase 2 bleiben bis zu deren Nachweis offen.

### Einordnungsberichtigung – 2026-09-08

Die unmittelbar vor dieser Berichtigung stehenden Abschnitte „Lokale Umsetzung und
Validierung“ und „Phasenaudit“ protokollieren ausschließlich Phase 1. Sie wurden nach den
Platzhaltern für die späteren Phasen angehängt, damit dieses Protokoll append-only bleibt; sie
sind kein vorgezogener Cross-Phase-Abschluss.

### Remote-Abschluss Phase 1 – 2026-09-08

Statusfortschreibung: veröffentlicht

- Phasencommit: `fd33c438b785c06bc061d9ae51a0cf26566f6da5`;
- Pull Request: `#343`, vollständiger `pull-request-gate` grün im Lauf `34197947321`;
- PR-Matrix: Qualität sowie reguläre und Animationsinstrumentierung auf API 26, 35 und 37
  grün;
- Squash-Merge auf `main`: `2b3a44e32c582808311baaa44b70abf5155f9cc3`;
- exakter Main-Lauf `34199684954`: Packaging, API-26/35/37-Upgrades und Publish grün;
- veröffentlichter Release: Auto Secretary 0.2.162, Tag `forest-android-1016201`, dessen Tag und
  Release-Ziel exakt auf den Squash-Commit zeigen;
- veröffentlichte APK: `AutoSecretary.apk`, SHA-256
  `6e6b3b005b73a7a0881b0e6d71ba5537da948be8408efd49c0c9b7b94c39adfa`;
- veröffentlichte Metadaten: `release-metadata.json`, SHA-256
  `b588c5ad28581313de338c1aaa9f3f364e74fc37c9ed3f863c78d5aae50101f6`.

Phase-1-Gate: vollständig erfüllt. Phase 2 darf vom aktuellen `origin/main` beginnen.

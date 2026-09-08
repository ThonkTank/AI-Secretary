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

## Phase 2 – Signierter Upgrade-Fixture-Korpus

Statusfortschreibung: in Arbeit

Ausgangsstand: `bed757392ce02a308e0b80aaf68907d7eeb290a2` (`origin/main`)

### Plan – 2026-09-08

Ergebnis: Der Releaseweg leitet fünf gezielte Upgrade-Lanes aus einem validierten,
manifestierten Fixture-Korpus ab. Jede Lane installiert eine exakt festgelegte signierte
Produktionsquelle, sät ihre schemahistorischen Daten, aktualisiert mit demselben einmal gebauten
Kandidaten und prüft die erwarteten Zielwerte.

Betroffene Komponenten:

- `release/upgrade-fixtures` mit Manifest und vollständigen Schema-8-, Schema-20- und
  Schema-23-Fixtures;
- eine kleine Release-Hilfe samt Unit-Tests für Korpus-, Matrix- und Quellartefaktverträge;
- `UpgradePersistenceProbe`, `UpgradeProbeInstrumentation` und der Upgrade-Runner für die
  verpflichtende Fixture-ID und generische Seed-/Erwartungswerte;
- `ProductionUpgradeFixtureContractTest`, Runner-, Workflow-, Scope- und Releaseverträge;
- `.github/workflows/verify.yml` für die aus dem Korpus abgeleiteten fünf Upgrade-Lanes.

Reihenfolge:

1. Die unveränderlichen Metadaten der drei veröffentlichten Quellen erfassen und gegen Tag,
   Zielcommit, APK, Metadaten und Produktionssignatur verifizieren.
2. Einen manifestierten Korpus mit eindeutigen IDs, Risikobeschreibung, Quellidentität,
   vollständigen Seedzeilen, Zielerwartungen und genau zugeordneten API-Lanes einführen.
3. Die Korpus-Hilfe so implementieren, dass fehlende, doppelte und unbekannte IDs sowie
   unvollständige oder inkonsistente Quellen hart fehlschlagen und die Workflow-Matrix
   deterministisch erzeugt wird.
4. Probe und Runner über `fixtureId` verbinden; Seed- und Zielzeilen generisch aus dem gewählten
   Fixture lesen und die Auswahl zwischen Seed und Verify persistent gegen Verwechslung sichern.
5. Den Workflow jede Quelle einschließlich Release-Metadaten, Commit, Paket, Versionscode,
   SHA-256 und Signatur vor der Installation prüfen lassen. Der Kandidat bleibt ein einziges
   gemeinsames signiertes Artefakt, Publish hängt vom gesamten Matrixjob ab.
6. Werkzeug-, Fixture-, Runner- und fokussierte Androidtests früh ausführen, danach den
   vollständigen lokalen Android-Gate mit JDK 21 und `git diff --check`.
7. Implementierung getrennt gegen diesen Phasenplan und die Gesamtroadmap auditieren. Vor jeder
   notwendigen Korrektur zuerst einen eigenen Fixplan hier anhängen.

Abnahme: Schema-8-Upgrades sind auf API 26/35/37, Schema 20 und Schema 23 jeweils auf API 26
verpflichtend; jede Quelle und jeder Zielwert ist vertraglich geprüft; es gibt keine fest
verdrahtete Fixture in der Probe; Schema 24 und öffentliche App-/Domain-/UI-APIs bleiben
unverändert. Erst nach lokalem Gate, PR-Gate, Squash-Merge, exaktem Main-Lauf und Publish darf
Phase 3 beginnen.

### Lokale Umsetzung und Validierung – 2026-09-08

Die einzelne Schema-8-Datei wurde durch einen manifestierten Korpus aus Schema 8, 20 und 23
ersetzt. Eine neue Release-Hilfe validiert Vertrag, Eindeutigkeit und Vollständigkeit des Korpus,
erzeugt deterministisch fünf Risikolanes und prüft jede Quellveröffentlichung fail-closed gegen
Release- und Tag-Ziel, Commit, Metadatenbytes und APK-Bytes. Probe und Runner säen und prüfen nun
generisch über die obligatorische Fixture-ID; die gewählte ID wird zwischen Seed und Verify
persistiert.

Der Workflow löst die Matrix im immer laufenden Scope-Job auf, damit auch übersprungene
Packaging-Jobs keinen leeren Matrixausdruck erzeugen. Der Produktionskandidat wird weiterhin
genau einmal gebaut. Jede Lane lädt ihre eigene signierte Quelle und deren Metadaten, prüft
zusätzlich Paket, Versionsname, Versionscode und Signatur und übergibt den Fixture-Namen bis in
die Geräteprobe. Publish bleibt vom aggregierten Upgradejob abhängig.

Validierung mit JDK 21 und Android SDK `/home/aaron/Android/Sdk`:

- Python-CI-Verträge: 25 von 25 grün;
- Python-Release- und Workflowverträge: 30 von 30 grün;
- fokussierter Fixture-/Schemavertrag und Kompilierung der Android-Probe: 45 Aufgaben, grün in
  4:09;
- vollständiger lokaler Android-Gate mit `testInstrumentationUnitTest`, `lintDebug`,
  `assembleDebug`, `assembleInstrumentationAndroidTest` und `assembleRelease`: 157 Aufgaben,
  grün in 20:22;
- Debug-APK 5.683.341 Bytes, Release-APK 2.837.404 Bytes; beide innerhalb ihrer Grenzen;
- alle drei Fixture-Dateien sind in der erzeugten Upgrade-Test-APK enthalten;
- alle drei heruntergeladenen Produktionsquellen wurden live mit der neuen Hilfe sowie mit
  `aapt` und `apksigner` gegen Commit, Tag, Metadaten- und APK-SHA-256, Paket, Versionscode,
  Versionsname und Produktionssignatur verifiziert;
- `git diff --check`: grün.

### Phasenaudit – 2026-09-08

Der getrennte Abgleich gegen Phasenplan und Gesamtroadmap ergab keine offene lokale Abweichung:

- der Korpus enthält ausschließlich `schema-8-floor`, `schema-20-organic-flow` und
  `schema-23-repair-boundary` und erzeugt exakt die genehmigten fünf Lanes;
- Schema 8 läuft auf API 26, 35 und 37, Schema 20 und 23 jeweils gezielt auf API 26;
- die Probe enthält keine feste Fixture-ID oder feste fachliche Seed-/Zielzeile;
- Manifest- und Laufzeitverträge verwerfen fehlende, doppelte, verwaiste, unbekannte oder
  syntaktisch ungültige Fixtures beziehungsweise IDs;
- jede Seedzeile deckt exakt jede Spalte ihres exportierten Quellschemas ab, und jede erwartete
  Zielspalte existiert in Schema 24;
- die Schema-20-Fixture enthält getrennt die leere Crashzelle und die vollständig belegte
  stille Vertauschung; die Schema-23-Fixture enthält parallel die eindeutig reparierbare und
  die byte-/wertgleich zu erhaltende korrekte Zeile;
- Release-, Tag-, Commit-, Metadaten-, APK-, Paket-, Versions- und Signaturidentität werden vor
  jeder Installation geprüft; Manipulationstests decken jede Herkunftsgrenze ab;
- der Kandidat wird einmal gebaut und von allen Matrixjobs gemeinsam verwendet; Publish wartet
  auf den vollständigen Matrixjob;
- Schemaexport 24 blieb bytegleich mit SHA-256
  `200e46de5c60137ea9e09524c7cb91ffaed322033c1790d0d459ace6b4cb6bf3`;
- `DatabaseContract.java` blieb bytegleich mit SHA-256
  `489c1114b1d5d3e15254a8eaa7a3a131540f10005b0faf77484dab993492dba4`;
- es gibt keine Änderung an Produktions-App-, Domain- oder UI-Code und keine öffentliche
  API-Änderung.

Lokales Gate: erfüllt. Remote-Gate, Squash-Merge, exakter Main-Lauf und Veröffentlichung stehen
noch aus; Phase 2 und damit Phase 3 bleiben bis zu deren Nachweis offen.

### Fehlgeschlagener exakter Main-Lauf und Fixplan – 2026-09-08

PR `#345` war mit Qualität, sechs Instrumentierungslanes und `pull-request-gate` vollständig
grün und wurde als `2fa62299dfd4524955e6577a75b6cf303660e71b` nach `main` gemergt. Der exakte
Main-Lauf `34207682584` baute und signierte den Kandidaten einmal erfolgreich. Vier der fünf
neuen Upgrade-Lanes waren grün: Schema 8 auf API 26, 35 und 37 sowie Schema 23 auf API 26.
Schema 20 auf API 26 scheiterte deshalb korrekt vor Publish; es entstand kein neuer Release.

Diagnose: Die Fixture erwartete für organisch aus Schema 20 migrierte Ablaufzeilen
`targetRir = 0`, während die echte Migration 20 nach 21 dieses neue Pflichtfeld mit dem
historischen Default `2` anlegt. Der Gerätefehler lautete
`AssertionError: flow_run_steps.targetRir differs`. Das frische Protokoll zeigte außerdem, dass
die gesäte Aufgabe den heute ungültigen Domänenwert `missedOccurrenceMode = SKIP` trug; gültig
sind `COLLAPSE` und `ACCUMULATE`.

Fixplan:

1. Ausschließlich die Schema-20-Zielerwartungen auf den belegten Migrationsdefault `2` stellen.
2. Die künstlichen Schema-20- und Schema-23-Aufgaben mit dem gültigen neutralen Wert
   `COLLAPSE` säen, damit auch der App-Start ohne Domänenfehler abläuft.
3. Den Java-Vertrag um konkrete Assertions für den historischen `targetRir`-Default und alle
   gesäten `missedOccurrenceMode`-Werte erweitern.
4. Fixture-/Workflowverträge, fokussierten Androidtest, vollständigen lokalen Android-Gate und
   `git diff --check` erneut ausführen und getrennt auditieren.
5. Die Korrektur über einen neuen PR, Squash-Merge und einen neuen exakten Main-Lauf führen.
   Publish bleibt bis zu fünf grünen Upgrade-Lanes gesperrt; Phase 3 bleibt geschlossen.

### Lokale Validierung und Audit der Phase-2-Korrektur – 2026-09-08

Der Fixplan wurde vollständig und ohne Scope-Erweiterung umgesetzt:

- beide organisch aus Schema 20 migrierten Ablaufzeilen erwarten nun den durch Migration 20
  nach 21 belegten Pflichtfelddefault `targetRir = 2`;
- die künstlichen Aufgaben in Schema 20 und 23 verwenden `missedOccurrenceMode = COLLAPSE`;
- der Java-Vertrag prüft beide historischen `targetRir`-Werte konkret und parst jeden in einer
  Fixture vorhandenen Aufgabenmodus über den aktuellen Domänenenum;
- Python-CI-Verträge: 25 von 25 grün;
- Python-Release- und Workflowverträge: 30 von 30 grün;
- fokussierter `ProductionUpgradeFixtureContractTest`: 35 Aufgaben, grün in 2:47;
- vollständiger lokaler Android-Gate mit JDK 21: 157 Aufgaben, grün in 17:09;
- Debug-APK 5.683.341 Bytes, Release-APK 2.837.404 Bytes;
- `git diff --check`: grün.

Der getrennte Soll/Ist-Abgleich zeigt ausschließlich vier geänderte Dateien: die zwei Fixtures,
ihren Hostvertrag und dieses append-only Protokoll. Produktions-App-, Domain-, UI-, Schema- und
öffentliche API-Dateien sind unverändert. Alle fünf Lanes, die Quellidentitäten und der
Einmal-Kandidatenvertrag bleiben unverändert verpflichtend. Lokale Fix-Abnahme: erfüllt;
Remote-Gate, Squash-Merge, neuer exakter Main-Lauf und Publish stehen noch aus.

### Remote-Abschluss Phase 2 – 2026-09-08

Statusfortschreibung: veröffentlicht

- ursprünglicher Phasencommit `dbc8ff48d78724eec62ab8696c9408756e98a738`, PR `#345`,
  vollständiger `pull-request-gate` grün im Lauf `34205749440`;
- erster Squash-Merge `2fa62299dfd4524955e6577a75b6cf303660e71b`;
- erster exakter Main-Lauf `34207682584`: Packaging und vier von fünf Upgrade-Lanes grün,
  Schema 20 rot, Publish korrekt übersprungen und kein Release erzeugt;
- Korrekturcommit `ef597625e55b9f05b7e9b74231b95674086a5fd5`, PR `#346`, vollständiger
  `pull-request-gate` grün im Lauf `34210524165`;
- finaler Squash-Merge auf `main`: `0107fff4e9437f4b7d62750e393617545a41879c`;
- exakter Main-Lauf `34212299563`: Packaging, Schema 8 auf API 26/35/37, Schema 20 auf API 26,
  Schema 23 auf API 26 und Publish vollständig grün;
- veröffentlichter Release: Auto Secretary 0.2.163, Tag `forest-android-1016301`; Release-Ziel
  und Tag zeigen exakt auf den finalen Squash-Commit;
- veröffentlichte APK SHA-256:
  `1cf4da34d7054e7dfa325d1a461727a4f984c1a2f620197863c7101b61ed08eb`;
- veröffentlichte Metadaten SHA-256:
  `beca4ddeb14b9b8d9c6b996cf8c0cccf5c07d931a3bd2b60e2a108a8f75ea5c6`;
- Paket `de.thonktank.autosecretary`, Versionscode `1016301`, Versionsname `0.2.163` und
  Produktionssignatur
  `de45d94c9724beeaa2e0dff31f69f53bb0f4c9ba79a5aa419d1f29d18f4d91da` wurden nach dem
  Download erneut unabhängig geprüft.

Phase-2-Gate: vollständig erfüllt. Phase 3 darf nach grünem dokumentationsreinem Status-PR und
dessen exaktem Main-Lauf vom dann aktuellen `origin/main` beginnen.

## Phase 3 – Dokumentation und Recovery-Vertrag

Statusfortschreibung: in Arbeit

Ausgangsstand: `12ea80298d4d8c969dc6d2c7b7c28a8e63bccc7f` (`origin/main`)

### Plan – 2026-09-08

Ergebnis: Die aktive Architektur- und Betriebsdokumentation beschreibt den inzwischen
implementierten Migrations-, Upgrade- und datenbewahrenden Geräte-Recovery-Vertrag vollständig
im Präsens. Sie unterscheidet logisches Room-Schema und physische SQLite-Historie und macht die
Grenze zwischen automatisiertem Releasebeweis und sichtbarer Geräteabnahme eindeutig.

Betroffene Komponenten:

- neue ADR-035 für Migrations-Lineage, benannte Schreibkopien, Fixture-Arten, die Schemafolge 20
  nach 24 und das exakte Reparaturmapping;
- ADR-005 und `docs/releasing.md` für die tatsächlich laufende API-26/35/37- und
  Fixture-/Upgrade-Matrix;
- `docs/signing-and-recovery.md` für den vorwärtsgerichteten, datenbewahrenden
  Wiederherstellungsablauf;
- Architekturindex sowie dieses append-only Ausführungsprotokoll.

Reihenfolge:

1. Implementierte Migrationen, Fixture-Korpus, Workflowmatrix und bestehende aktive
   Release-/Recovery-Dokumente erneut als Ist-Vertrag lesen.
2. ADR-035 ergänzen und das historische Neun-Spalten-Mapping einschließlich der eng begrenzten
   Reparaturerkennung vollständig festhalten.
3. ADR-005 und Releaseanleitung auf den manifestierten Korpus mit fünf Lanes aktualisieren;
   alte Aussagen zur einzelnen 0.2.80-Fixture und Zweiermatrix entfernen.
4. Das Recovery-Runbook um Artefaktprüfung, ausschließlich `adb install -r`, erhaltenes
   `firstInstallTime`, App-/Widgetstart, frisches Fehlerprotokoll und sichtbare Bestandsprüfung
   ergänzen. Deinstallation, Datenlöschung und Downgrade werden für diesen Pfad verboten.
5. Architekturindex um Roadmap, Ausführungsprotokoll und ADR-035 ergänzen.
6. Links, widersprüchliche aktive Aussagen, Dokumentations-Scope, Releaseklassifizierung,
   relevante Hostverträge und `git diff --check` prüfen.
7. Die Dokumentation getrennt gegen diesen Plan und die Gesamtroadmap auditieren. Vor jeder
   nötigen Korrektur zuerst einen Fixplan hier anhängen.

Abnahme: Der Diff bleibt dokumentationsrein und `release_required=false`; die aktive
Dokumentation stimmt mit Schema 24, drei Fixtures, fünf Upgrade-Lanes und dem
datenbewahrenden Recovery-Vertrag überein. Erst nach lokalem Gate, PR-Gate, Squash-Merge und
grünem exaktem Main-Lauf ohne Produktrelease darf der Cross-Phase- und Geräteabschluss beginnen.

### Planpräzisierung – 2026-09-08

Die Baseline-Lektüre fand in der als aktuell bezeichneten `architecture-map.md` noch den
widersprüchlichen Persistenzstand Schema 23. Die Phase umfasst deshalb zusätzlich die reine
Aktualisierung dieser zwei aktuellen Schemaangaben auf Schema 24 und einen Verweis auf ADR-035.
Historisch datierte Baselines und Ausführungsnachweise werden nicht umgeschrieben.

### Lokale Umsetzung und Validierung – 2026-09-08

ADR-035 hält nun den Unterschied zwischen logischem Room-Schema und physischer
SQLite-Entstehungsgeschichte, das statisch gesicherte Verbot positioneller Schreibkopien, die
drei Fixture-Arten, die Folge 20 nach 24 sowie die vollständige Neun-Spalten-Hin- und
Rückzuordnung fest. ADR-005 und die Releaseanleitung beschreiben den manifestierten Korpus aus
drei signierten Quellen und seine fünf Risikolanes. Das Recovery-Runbook enthält den
ausführbaren Vorwärtsupdatepfad einschließlich Quell-, Commit-, Hash-, Paket-, Versions- und
Signaturprüfung, Signaturvergleich mit der installierten APK, `firstInstallTime`, App-/Widgetstart,
frischem Protokoll und sichtbarer Bestandsabnahme. Architekturindex und aktuelle Architekturkarte
verweisen auf den neuen Vertrag und Schema 24.

Lokale Validierung:

- Python-CI-Verträge: 25 von 25 grün;
- Python-Release- und Workflowverträge: 30 von 30 grün;
- sämtliche neu oder geändert verlinkten lokalen Ziele vorhanden;
- kein alter Verweis auf `v0.2.80.json`, eine Zweier-Upgradematrix oder Schema 23 als aktuellen
  Persistenzstand verbleibt in den aktiv geänderten Vertragsdokumenten;
- Scope-Klassifizierung des vollständigen Diffs:
  `quality_required=false`, `instrumentation_required=false`, `release_required=false`;
- `git diff --check`: grün.

### Phasenaudit – 2026-09-08

Der getrennte Abgleich gegen Phasenplan und Gesamtroadmap ergab keine zu korrigierende
Abweichung:

- ADR-035 benennt jede der neun vertauschten Spalten und die fachdomänenbasierte
  Reparaturerkennung; Crashzeile, stille Vertauschung und unverändert zu erhaltende korrekte
  Schema-23-Zeile sind voneinander abgegrenzt;
- ADR-005 und Releaseanleitung stimmen mit den drei Korpusfixtures, fünf Lanes und dem einmal
  gebauten Kandidaten überein;
- der Gerätepfad verbietet Deinstallation, Speicherlöschung, Downgrade und Signaturwechsel und
  verlangt ausschließlich das datenbewahrende `adb install -r` nach vollständiger Prüfung;
- technische Abnahme und sichtbare Prüfung von Aufgaben, Verlauf, bestehendem Ablauf und Widget
  sind getrennt und beide verpflichtend;
- alle geänderten oder neuen Dateien liegen unter `docs/`; Produktionscode, Schemaexporte,
  öffentliche APIs, Fixturedaten und Workflow bleiben unverändert.

Lokales Phase-3-Gate: erfüllt. Pull Request, Squash-Merge und exakter Main-Lauf ohne
Produktrelease stehen noch aus; der Cross-Phase- und Geräteabschluss bleibt bis dahin gesperrt.

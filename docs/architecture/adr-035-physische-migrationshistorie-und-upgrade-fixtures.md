# ADR-035: Physische Migrationshistorie und signierte Upgrade-Fixtures

- Status: angenommen
- Datum: 2026-09-08

## Kontext

Ein Room-Schemaexport beschreibt die logische Gestalt einer Datenbankversion. Er beweist nicht,
in welcher physischen Reihenfolge SQLite die Spalten einer konkreten Installation angelegt hat.
Insbesondere hängen `ALTER TABLE ... ADD COLUMN`-Migrationen neue Spalten physisch am Ende an,
auch wenn der spätere Room-Export sie an einer anderen Stelle aufführt.

Dieser Unterschied verursachte in Auto Secretary 0.2.158 (`forest-android-1015801`) einen
Startabsturz. Migration 20 nach 21 hängte Last- und `targetRir`-Spalten an
`flow_run_steps` an. Migration 22 nach 23 baute die Tabelle anschließend mit einer
positionsabhängigen Schreibkopie neu auf. Ein leeres `lastUsedDelayMillis` landete dadurch im
nicht-nullbaren `targetRir`; vollständig belegte Zeilen konnten die Migration dagegen mit
semantisch vertauschten Werten beenden.

## Entscheidung

### Logisches Schema und physische Historie

Room-Schemaexporte bleiben der Vertrag für Tabellen, Spalten, Typen, Nullbarkeit, Indizes und
Fremdschlüssel einer Version. Für jede Migration, die eine Tabelle neu aufbaut, ist zusätzlich
die physische Herkunft der Quelldaten maßgeblich. Persistierte Werte werden deshalb immer mit
vollständigen Ziel- und Quellspaltenlisten kopiert. Eine Schreibkopie der Form
`INSERT INTO ... SELECT *` ist in Produktionsmigrationen verboten und wird durch einen
Architekturtest verhindert.

Ein temporärer Snapshot mit `CREATE TEMP TABLE ... AS SELECT *` ist nur zulässig, wenn jede
spätere Rückschreiboperation die Bedeutung aller Ziel- und Quellspalten explizit benennt.
Absichtliche Umbenennungen oder Normalisierungen müssen dabei sichtbar in der Projektion stehen.

### Relevante Schemafolge 20 nach 24

| Version | Bedeutung für den Vorfall |
| --- | --- |
| 20 | `flow_run_steps` besitzt Ablauf-, Notiz- und Verzögerungswerte, aber noch keine Lastfelder. |
| 21 | `plannedLoadMode`, `plannedLoadUnit`, `plannedLoadMilli` und `targetRir` werden per `ALTER TABLE` physisch am Tabellenende ergänzt; `targetRir` erhält den historischen Default `2`. |
| 22 | Der Ablaufgraph bleibt in dieser organisch entstandenen physischen Reihenfolge erhalten. |
| 23 | Ablauf-, Ressourcen- und Ausführungsnamen werden neu aufgebaut. Die korrigierte Migration kopiert jede Spalte semantisch; die veröffentlichte 0.2.158-Fassung kopierte `flow_run_steps` positionsabhängig. |
| 24 | Eine eng begrenzte Reparatur übersetzt nur das erkennbare 0.2.158-Wertebild zurück. Bereits korrekte oder neu unter Schema 23 angelegte Zeilen bleiben unverändert. |

Die organische Schema-22-Reihenfolge der neun betroffenen Felder und ihre falsche Interpretation
durch 0.2.158 waren:

| Bedeutung in Schema 22 | Feld mit dem Wert in einer fehlerhaft migrierten Schema-23-Zeile | Rückübersetzung in 23 nach 24 |
| --- | --- | --- |
| `note` | `plannedLoadMode` | `plannedLoadMode` → `note` |
| `delayMode` | `plannedLoadUnit` | `plannedLoadUnit` → `delayMode` |
| `defaultDelayMillis` | `plannedLoadMilli` | `plannedLoadMilli` → `defaultDelayMillis` |
| `lastUsedDelayMillis` | `targetRir` | `targetRir` → `lastUsedDelayMillis` |
| `chosenDelayMillis` | `note` | `note` → `chosenDelayMillis` |
| `plannedLoadMode` | `delayMode` | `delayMode` → `plannedLoadMode` |
| `plannedLoadUnit` | `defaultDelayMillis` | `defaultDelayMillis` → `plannedLoadUnit` |
| `plannedLoadMilli` | `lastUsedDelayMillis` | `lastUsedDelayMillis` → `plannedLoadMilli` |
| `targetRir` | `chosenDelayMillis` | `chosenDelayMillis` → `targetRir` |

Migration 23 nach 24 erkennt eine reparierbare Zeile nur an der Kombination disjunkter
Fachdomänen: `plannedLoadUnit` enthält einen Verzögerungsmodus (`FIXED` oder
`REMEMBER_LAST`), `delayMode` einen Lastmodus, `defaultDelayMillis` textuell eine Lasteinheit
und `note` eine kanonische Ganzzahl. Nur dann wird die vollständige inverse Zuordnung atomar
angewandt. Das ist bewusst keine allgemeine Plausibilitätskorrektur. Eine Schema-22-Zeile mit
leerem `lastUsedDelayMillis` erreicht unter der fehlerhaften Migration wegen der
`targetRir`-Nullbarkeit Schema 23 gar nicht; der korrigierte direkte Pfad 22 nach 24 kopiert sie
semantisch und benötigt keine Reparaturheuristik.

### Drei komplementäre Fixture-Arten

1. Room-Schemaexporte prüfen den logischen Vertrag jeder gespeicherten Version.
2. Lineage-Fixtures erzeugen alte Tabellen und führen die echten Zwischenmigrationen aus. Sie
   belegen die physische Reihenfolge per `PRAGMA table_info` und prüfen alle semantischen
   Sentinelwerte nach der Migration.
3. Der signierte Produktions-Fixture-Korpus installiert unveränderliche veröffentlichte APKs,
   sät Daten über deren tatsächliches Schema und aktualisiert mit exakt dem einmal gebauten
   Produktionskandidaten.

Der Produktionskorpus enthält aktuell:

| Fixture | Signierte Quelle | Pflicht-Lanes bei `full` | Risiko |
| --- | --- | --- | --- |
| `schema-8-floor` | `forest-android-1008001` / 0.2.80 | API 26, 35 und 37 | unterstützte Untergrenze und Plattformkompatibilität |
| `schema-20-organic-flow` | `forest-android-1013701` / 0.2.137 | API 26 | organische Spaltenhistorie, leere Crashzelle und vollständig belegte Vertauschungszeile |
| `schema-22-clean-candidate` | `forest-android-1015701` / 0.2.157 | API 26 | unbenutzte Angebote ohne FK-Kaskaden entfernen; kein verdeckender Recovery-Eintrag, aktive Ressourcen und Historie erhalten |
| `schema-23-repair-boundary` | `forest-android-1015801` / 0.2.158 | API 26 | exakte Reparatur einer vertauschten Zeile bei wertgleichem Erhalt einer korrekten Zeile |

Jede Quelle wird vor der Installation gegen Release- und Tag-Ziel, Commit, Paketname,
Versionscode, Versionsname, Metadaten- und APK-SHA-256 sowie Produktionssignatur geprüft. Alle
sechs historischen Lanes verwenden denselben signierten Kandidaten. Nach [ADR-037](adr-037-risikobasierte-verifikation-und-aktuelles-upgrade.md) bleiben sie
bei vollständigen Änderungen verpflichtend; Publish hängt dann von ihrem gemeinsamen Erfolg ab.
Jeder Produktrelease ergänzt den separat gebundenen aktuellen Smoke. Dessen expliziter Vertrag 2
erlaubt gleiche Schemata bei höherer App-Version; historische Fixtures behalten Vertrag 1 mit
strikt höherem Zielschema und bleiben vollständig manifestiert.

Zielerwartungen bestehen aus `table`, einem nichtleeren `where` und genau einem Modus:
`values` mit nichtleeren Spaltenwerten verlangt genau eine passende Zeile; `absent: true`
verlangt keine passende Zeile. Beide Modi zusammen, `absent: false`, unbekannte Felder und
leere Selektoren sind ungültig. Namen sind SQL-Bezeichner, Werte werden gebunden; freie SQL-
Ausdrücke sind nicht Teil dieses Vertrags. Bestehende Anwesenheitsprüfungen behalten ihre
Bedeutung. Die Ergänzung vom 2026-09-12 schließt gezielt die Nachweislücke, dass spätere Recovery
eine frühere Waisenerzeugung trotz korrekter Kandidaten und erhaltener gültiger Zeilen verdeckt.
Python validiert das Format, Room prüft die Migration, und der tatsächliche Release-Runner
prüft beide Modi nativ einschließlich vorhandener verbotener Zeilen und ungültiger Erwartungen.

## Konsequenzen

Ein sauber erzeugter Schemaexport oder eine erfolgreiche Neuinstallation genügt nicht mehr als
Migrationsnachweis. Änderungen an Migrationen, Upgrade-Fixtures oder deren Ausführung sind
releasewirksam und müssen die zugehörigen Lineage- und Produktionspfade bestehen. Neue Fixtures
werden nur ergänzt, wenn sie ein eigenes Datenhistorien- oder Plattformrisiko abdecken; der
Korpus wird nicht pauschal über alle Android-Versionen kreuzmultipliziert.

Zum ursprünglichen Entscheidungszeitpunkt war das Zielschema 24. Maßgeblich sind heute
`DatabaseContract.VERSION` und die damit abgeglichenen Fixture-Zielversionen. Diese ADR ändert
keine App-, Domain- oder UI-API. Den
operativen, datenbewahrenden Gerätepfad beschreibt das
[Signing- und Recovery-Runbook](../signing-and-recovery.md); die phasenweise Herleitung steht in
der [Migrationssicherheits-Roadmap](database-migration-safety-roadmap.md) und ihrem
[Ausführungsprotokoll](database-migration-safety-execution.md).

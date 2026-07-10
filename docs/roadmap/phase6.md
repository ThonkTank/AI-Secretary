# Phase 6 - Task-Domaenenmodell nach `domain/model/`

## Design

Ziel dieser Phase ist eine reine Paketverschiebung des fachlichen Task-Modells:
`domain` darf nicht laenger `features.task.data.*` importieren, weil die
fachlichen Typen nicht der Persistenzschicht gehoeren. Verhalten und Room-Schema
bleiben unveraendert.

1. Modellklassen verschieben
   - Folgende Dateien ziehen von `features/task/data/` nach
     `features/task/domain/model/`:
     `Task`, `TaskCore`, `TaskSlot`, `TaskPrefSlot`,
     `TaskPrefSlotFactory`, `TaskPrerequisite`, `TaskRelation`,
     `TaskPlannedMeal`.
   - Room-Annotationen, Tabellenamen, Primary Keys, Foreign Keys und Indizes
     bleiben auf den Klassen. Es gibt keine Schema-Aenderung und keine Migration.
   - `TaskScheduleConfig`, `TaskTransitionStat`, DAOs und DAO-nahe
     Konfigurations-/Statistiktypen bleiben in `data/`.

2. Importflaeche aktualisieren
   - `TaskDao` bleibt in `data/`, gibt aber `domain.model`-Typen zurueck und
     schreibt sie. Das ist die erlaubte Richtung `data -> domain`.
   - `AppDatabase` registriert die verschobenen Entities aus `domain.model`.
   - `Converters` importiert die verschobenen nested enums
     `TaskCore.SchedulingType` und `TaskSlot.DisplacementGroupType` aus
     `domain.model`; TypeConverter-Namen und Stringwerte bleiben unveraendert.
   - Domain, Application, UI, Tests und README/Javadocs importieren die neuen
     Pakete.
   - Nach dem Umbau darf `src/main/java/com/autosecretary/features/task/domain`
     keine Imports aus `features.task.data` mehr enthalten.
   - Weil `checkArchitecture` same-feature `domain -> data` aktuell noch
     toleriert, ist der grep selbst Teil der Done-When-Kriterien.

3. Mechanik
   - Die Verschiebung erfolgt per `git mv`, damit Git-Historie und Review-Diff
     lesbar bleiben.
   - Imports werden mechanisch auf
     `com.autosecretary.features.task.domain.model.*` umgestellt.
   - Falls Javadocs auf alte Pfade zeigen, werden sie im selben Schritt
     angepasst.

4. Schema- und Verhalten/Teststrategie
   - Keine neuen Verhaltensfunktionen. Die in Phase 2 angelegten
     Checkoff-/Scheduling-Charakterisierungstests sind der Paritaetsnachweis
     fuer Task-Modell-Verhalten.
   - Schema-Paritaet bekommt einen expliziten Test in `src/test`: eine
     in-memory Room-DB wird geoeffnet und ihre SQLite-Metadaten werden fuer die
     betroffenen Tabellen abgefragt (`PRAGMA table_info`, `PRAGMA index_list`,
     `PRAGMA foreign_key_list`). Der Test sichert die bestehenden Tabellenamen,
     relevante Spalten, Indizes und Foreign-Key-Beziehungen ab. Dadurch ist die
     reine Paketverschiebung trotz `exportSchema = false` reviewbar.
   - Kein Migrationstest ist noetig, weil Phase 6 keine Schema-Aenderung,
     keinen DB-Version-Bump und keine neue Migration einfuehrt. Der Schema-
     Snapshot-Test plus unveraenderter `@Database(version = 27)` ist die
     Akzeptanzbedingung.
   - Der harte Gate-Lauf (`checkArchitecture`, `assembleDebug`,
     `testDebugUnitTest`) prueft Kompilierung, Room-Annotation-Processing und
     die bestehenden E2E-Invarianten.

5. Doku-Folgeeffekte
   - Aktualisiert werden mindestens `CLAUDE.md`,
     `src/main/java/com/autosecretary/features/task/README.md`,
     `src/main/java/com/autosecretary/features/task/data/README.md`,
     `src/main/java/com/autosecretary/features/task/domain/README.md`,
     `src/main/java/com/autosecretary/database/README.md`,
     `src/main/java/com/autosecretary/features/task/application/internal/budget/README.md`
     und Javadocs, die den alten `features.task.data`-Pfad fuer verschobene
     Modelltypen nennen.

## Vollstaendigkeits-Review

Erster Review: FAIL.

- Schema-Paritaet war als Verhaltensbeweis formuliert, aber nicht als
  schema-level Proof. Ergaenzt: expliziter Schema-Snapshot-Test ueber SQLite-
  Metadaten (`PRAGMA table_info`, `index_list`, `foreign_key_list`).
- `Converters` war nicht als schema-sensitive Flaeche benannt. Ergaenzt.
- `checkArchitecture` toleriert same-feature `domain -> data` noch; deshalb
  wurden explizite grep-Kriterien als Done-When aufgenommen.
- Doku-Flaeche war zu unkonkret; bekannte betroffene README/Javadoc-Flaechen
  wurden enumeriert.

Zweiter Review: FAIL.

- DW9-Regex war zu breit: `Task` haette erlaubte Resttypen wie `TaskDao`,
  `TaskScheduleConfig` und `TaskTransitionStat` gematcht.

Dritter Review: PASS.

- DW9 nutzt Identifier-Grenzen `([^A-Za-z0-9_]|$)` nach den verschobenen
  Typnamen und ist damit auf die moved types begrenzt.

## Done-When-Kriterien

- DW1: Die acht Task-Modellklassen liegen unter
  `src/main/java/com/autosecretary/features/task/domain/model/`.
- DW2: `src/main/java/com/autosecretary/features/task/data/` enthaelt nur noch
  DAOs, `TaskScheduleConfig`, `TaskTransitionStat` und zugehoerige DAO-nahe
  Persistenztypen.
- DW3: Kein Produktionscode unter `features/task/domain/` importiert
  `com.autosecretary.features.task.data.*`.
- DW4: `TaskDao` und `AppDatabase` kompilieren mit den verschobenen Room-
  annotierten Modelltypen; Tabellenamen und Entity-Annotationen bleiben
  unveraendert.
- DW5: `Converters` kompiliert mit den verschobenen nested enums
  `TaskCore.SchedulingType` und `TaskSlot.DisplacementGroupType`; ihre
  serialisierten Werte bleiben unveraendert.
- DW6: Ein neuer Schema-Snapshot-Test prueft fuer `task_core`,
  `task_slots`, `task_pref_slots`, `task_relation`, `task_prerequisites` und
  `task_planned_meals` die erwarteten Tabellen/Spalten sowie relevante Indizes
  und Foreign Keys.
- DW7: Alle Produktions-, Test- und Dokumentationsreferenzen auf die
  verschobenen Typen zeigen auf `features.task.domain.model`.
- DW8: Es gibt keine Room-Migration, keinen DB-Version-Bump und keine
  destruktive Migration.
- DW9: Die folgenden Suchchecks liefern nach Umsetzung keine Treffer:
  `rg "com\\.autosecretary\\.features\\.task\\.data\\.(Task|TaskCore|TaskSlot|TaskPrefSlot|TaskPrefSlotFactory|TaskPrerequisite|TaskRelation|TaskPlannedMeal)([^A-Za-z0-9_]|$)" src/main/java src/test/java`
  und
  `rg "features/task/data/(Task|TaskCore|TaskSlot|TaskPrefSlot|TaskPrefSlotFactory|TaskPrerequisite|TaskRelation|TaskPlannedMeal)([^A-Za-z0-9_]|$)|features\\.task\\.data\\.(Task|TaskCore|TaskSlot|TaskPrefSlot|TaskPrefSlotFactory|TaskPrerequisite|TaskRelation|TaskPlannedMeal)([^A-Za-z0-9_]|$)" CLAUDE.md src/main/java docs src/test/java`.
- DW10: Abschluss-Gate ist gruen:
  `./gradlew checkArchitecture`, `./gradlew assembleDebug`,
  `./gradlew testDebugUnitTest`.

## Geschuetzte Verhaltensinvarianten

- Two-Phase-Checkoff inklusive Streak/History/Adaptive bleibt unveraendert.
- Scheduling-Roundtrip erzeugt und persistiert dieselben Slots ueber Room.
- Room-Tabellen, Spalten und Foreign-Key-Beziehungen bleiben unveraendert.
- Task-Edit und Widget-Reads verwenden dieselben fachlichen Typen, nur mit neuem
  Paketnamen.

## Umsetzung

Umgesetzt:

- Die acht Task-Modellklassen wurden nach
  `src/main/java/com/autosecretary/features/task/domain/model/` verschoben und
  auf das Paket `com.autosecretary.features.task.domain.model` umgestellt.
- Imports in `AppDatabase`, `Converters`, `TaskDao`, Domain, Application, UI
  und Tests zeigen auf `domain.model`.
- `TaskDao` bleibt in `data/` und liest/schreibt die verschobenen Modelltypen.
- `data/` enthaelt nur noch `TaskDao`, `TaskScheduleConfig`,
  `TaskScheduleConfigDao`, `TaskTransitionStat`, `TaskTransitionStatDao` und
  README.
- `checkArchitecture` wurde eng angepasst: `features/task/domain/model/` darf
  `androidx.room.*` und `androidx.annotation.*` importieren, damit die in der
  Roadmap geforderte Room-annotierte Domain-Modellform nicht vom alten Harness
  blockiert wird. Andere Domain-Android/Room-Imports bleiben verboten.
- `TaskSchemaSnapshotCharacterizationTest` prueft die Task-Tabellen, Spalten,
  Indizes und Foreign Keys ueber SQLite-PRAGMAs.
- Task-/Domain-/Data-/Database-READMEs und ein historischer Roadmap-Pfad wurden
  auf `domain/model` aktualisiert.

## Erfolgs-Review

Erster Review: FAIL.

- DW10 war noch nicht belegbar, weil `checkArchitecture`/`assembleDebug` nach
  der Umsetzung noch nicht separat als Gate gelaufen waren.
- `CLAUDE.md` enthielt noch `DB version 24`, waehrend `AppDatabase` bereits
  Version 27 verwendet. Das war ein Doku-/Constraint-Drift, kein Phase-6-
  Schema-Bump.

Re-Review: PASS.

- `CLAUDE.md` und `AppDatabase` nennen beide DB-Version 27.
- Die acht Modellklassen liegen unter `domain/model`; `data/` enthaelt nur die
  erlaubten DAO-/Config-/Transition-Dateien.
- Die DW9-Suchchecks liefern keine Treffer.
- `AppDatabase`, `TaskDao` und `Converters` nutzen die verschobenen Modelltypen.
- Der Schema-Snapshot-Test prueft Task-Tabellen, Spalten, Indizes und Foreign
  Keys.
- Kein DB-Version-Bump, keine neue Migration und keine destruktive Migration.
- Gate-Evidence wurde mit Exit 0 fuer alle drei Pflichtbefehle akzeptiert.

## Abschluss-Gate

Gruen:

- `./gradlew checkArchitecture --console=plain` -> Exit 0.
- `./gradlew assembleDebug --console=plain` -> Exit 0.
- `./gradlew testDebugUnitTest --console=plain` -> Exit 0.

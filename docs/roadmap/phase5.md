# Phase 5 - Threading & DB-Lifecycle-Eigentum

## Design

Ziel dieser Phase ist Owner-Klarheit fuer lang laufende Arbeit und
DB-Lifecycle. Verhalten bleibt gleich; die bestehenden Checkoff-Tests aus Phase 2
bleiben Paritaetsnachweis. Fuer Restore/Reset wird vor dem Umbau ein enger Test
ergaenzt, der die Sequenz "DB schliessen -> Datei ersetzen/loeschen -> DB neu
oeffnen -> Graph resetten/Activity neu laden" absichert.

1. Benannte Executor in `AppCompositionRoot`
   - `sharedExecutor` wird durch `dbExecutor` und `ioExecutor` ersetzt.
   - `dbExecutor`: single-thread, alle Room-/Repository-/DAO-Pfade, Widget-DB-
     Reads, Scheduling, ViewModels, Meal/Task/Budget Application-DB-Arbeit.
   - `ioExecutor`: Netzwerk und Dateisystem: `UpdateChecker`, Import-Dateilesen
     (`ContentDocumentReader`), Backup/Restore/Factory-Reset und PDF-Parsing/
     Claude-API.
   - `Executors.new*` bleibt ausschliesslich in `AppCompositionRoot`.
   - Oeffentliche Getter heissen `getDbExecutor()` und `getIoExecutor()`.
     `getSharedExecutor()` verschwindet aus Produktionscode.

2. Import-Threading ohne DB-on-I/O
   - `ContentDocumentReader` wird synchron/executor-frei: `read(Uri)` liefert
     `DocumentContents` oder wirft `IOException`.
   - `BudgetFragment` liest Import-Dateien auf `ioExecutor` und postet das
     Ergebnis zurueck auf den Main Thread.
   - `BudgetViewModel.importFromCsv(...)` wird intern zweistufig:
     Account-Fallback und Import-DB-Pipeline laufen auf `dbExecutor`;
     PDF-/CSV-Parsing selbst laeuft nicht auf dem DB-Executor. Dafuer wird der
     heutige `BudgetImportUseCase` in eine parse-freie DB-Pipeline und einen
     parse-Aufruf getrennt:
     1. auf DB: FileHash berechnen, Import-Record anlegen, fuer PDF die aktiven
        Import-Kategorien als Snapshot laden;
     2. auf I/O: Parser mit diesem Kategorie-Snapshot ausfuehren;
     3. auf DB: Transaktionsaufbau/Persistenz/Recurring-Detection ausfuehren.
   - `StatementFileParser` ruft nach dem Umbau kein
     `BudgetImportRepository.findActiveCategoriesForImport()` mehr auf. PDF-
     Kategorien werden ausschliesslich im DB-Schritt vor dem I/O-Parse geladen
     und als `List<ImportCategory>` uebergeben.
   - Parse-/API-Fehler nach bereits angelegtem Import-Record tragen das
     `importId` zurueck in den DB-Schritt; dort wird
     `repository.markImportFailed(importId, message)` ausgefuehrt. Der
     Lifecycle `PENDING -> FAILED` bleibt damit erhalten, ohne DB-Zugriff auf
     dem I/O-Executor.
   - `ClaudeStatementApiClient` und `ClaudeApiKeyStore` bleiben synchron und
     executor-frei; sie werden nur vom I/O-Abschnitt aufgerufen.

3. Update- und Backup-I/O auf `ioExecutor`
   - `MainActivity.startUpdateCheckIfNeeded()` nutzt `getIoExecutor()`.
   - `SettingsController` bekommt `ioExecutor`; Backup/Restore/Reset laufen auf
     I/O statt DB.
   - `SettingsDataService` bleibt synchron/executor-frei.

4. DB-Lifecycle nur in `AppCompositionRoot`
   - `AppCompositionRoot` erhaelt kleine Lifecycle-Methoden:
     `runDatabaseCheckpoint()`, `closeDatabaseForFileReplacement()`,
     `openDatabaseAfterFileReplacement()` und `resetForDataReload()`.
   - Nur diese Methoden rufen `AppDatabase.getInstance(...)` oder
     `AppDatabase.closeAndReset()`.
   - `SettingsDataService` erhaelt eine `DatabaseLifecycle`-Abhaengigkeit und
     ruft nur diese Lifecycle-Methoden, nicht mehr `AppDatabase` direkt.
   - `MainActivity.reloadUiStateAfterDataReset()` triggert nur noch `recreate()`;
     Reopen/Graph-Reset ist Teil des erfolgreichen Settings-Data-Operationspfads.

5. Tests vor Umbau
   - Neuer Import-Paritaetstest: CSV-Import ueber `BudgetViewModel`/Room schuetzt
     ImportResult/Statusmessage, Summary-Zahlen, New-/Duplicate-Counts und
     Recurring-Suggestions fuer einen kleinen Datensatz; nach dem Umbau bleibt
     derselbe Test gruen.
   - Neuer Restore/Reset-Sequenztest mit Fake-`DatabaseLifecycle` schuetzt
     Checkpoint/Close/Open-Aufrufreihenfolge und Sidecar-Cleanup.

6. Doku-Folgeeffekte
   - `CLAUDE.md`, `src/main/java/com/autosecretary/app/README.md`,
     `src/main/java/com/autosecretary/database/README.md`,
     `src/main/java/com/autosecretary/features/task/README.md` und betroffene
     Klassen-Javadocs werden von "shared executor" auf
     `dbExecutor`/`ioExecutor` aktualisiert.

## Vollstaendigkeits-Review

Erster Review: FAIL. Blocker waren fehlender Import-Paritaetstest fuer die
geschuetzte Import-Invariante, unentschiedene PDF-Kategorie-Snapshot-Strategie
und ein offener Fehlerpfad fuer Parse-/API-Fehler nach angelegtem Import-Record.

Re-Review: PASS. Import-Paritaet und Restore/Reset-Sequenztests sind als
Vor-Umbau-Pflicht benannt; PDF-Kategorien werden vor dem I/O-Parse auf dem
DB-Executor als Snapshot geladen; Parse-/API-Fehler werden mit `importId`
zurueck in den DB-Schritt getragen und dort als FAILED markiert.

## Done-When-Kriterien

- DW1: Produktionscode enthaelt `Executors.new*` nur in `AppCompositionRoot`.
- DW2: Produktionscode ausser `AppCompositionRoot` enthaelt keine direkten
  `AppDatabase.getInstance(...)`- oder `AppDatabase.closeAndReset()`-Aufrufe.
- DW3: Room-/Repository-/DAO-arbeitende Feature-Pfade werden mit
  `getDbExecutor()` verdrahtet; `UpdateChecker`, `ContentDocumentReader` und
  Settings-Backup/Restore/Reset werden mit `getIoExecutor()` verdrahtet.
- DW4: `ContentDocumentReader`, `ClaudeStatementApiClient`,
  `ClaudeApiKeyStore` und `SettingsDataService` erzeugen oder besitzen keinen
  Executor.
- DW5: PDF-/Claude-Parsing laeuft nicht auf dem DB-Executor; die DB-Schritte des
  Import-Pipelines bleiben auf dem DB-Executor. `StatementFileParser` fuehrt
  keine Import-Repository-Abfrage mehr aus; PDF-Kategorien werden als DB-
  Snapshot uebergeben.
- DW6: Parse-/API-Fehler nach angelegtem Import-Record markieren den Import im
  DB-Schritt als FAILED; es gibt keinen DB-Zugriff auf dem I/O-Executor.
- DW7: Vor dem Umbau ergaenzte Import-Paritaets- und Restore/Reset-Sequenztests
  bleiben nach dem Umbau gruen; bestehende Checkoff- und Scheduling-
  Charakterisierungstests bleiben gruen.
- DW8: Doku/Javadocs beschreiben `dbExecutor`/`ioExecutor` und DB-Lifecycle-
  Eigentum korrekt.
- DW9: Abschluss-Gate ist gruen:
  `./gradlew checkArchitecture`, `./gradlew assembleDebug`,
  `./gradlew testDebugUnitTest`.

## Geschuetzte Verhaltensinvarianten

- Two-Phase-Checkoff inklusive Streak/History/Adaptive bleibt unveraendert.
- Restore/Reset schliesst die aktuelle DB vor Dateiersatz/-loeschung, entfernt
  Sidecars, oeffnet danach eine DB-Instanz neu und laesst die UI anschliessend
  neu binden.
- Manuelles Backup fuehrt weiterhin vor dem Kopieren einen WAL-Checkpoint aus.
- Import-Ergebnisse bleiben gleich: gleiche Summary-Zahlen, Duplicate-/New-
  Counts und Recurring-Suggestions fuer dieselben Eingaben.
- Update-Check und APK-Download bleiben asynchron und blockieren die UI nicht.

## Umsetzung

Umgesetzt:

- `AppCompositionRoot` besitzt jetzt zwei benannte Single-Thread-Executor:
  `dbExecutor` fuer Room/Repository/DAO-Arbeit und `ioExecutor` fuer Datei- und
  Netzwerk-I/O. Produktionscode erzeugt Executor nur noch dort.
- `MainActivity`, `SettingsController`, `UpdateChecker`, `BudgetFragment`,
  `BudgetViewModelFactory`, Task-/Budget-/Meal-Wiring und der Task-Widget-
  Toggle wurden auf `getDbExecutor()` bzw. `getIoExecutor()` verdrahtet.
- `ContentDocumentReader` ist synchron und executor-frei; `BudgetFragment`
  liest Importdateien auf `ioExecutor`.
- `BudgetViewModel.importFromCsv(...)` zerlegt Importarbeit in DB-Setup,
  I/O-Parsing und DB-Abschluss. `BudgetImportUseCase` stellt dafuer
  `beginImport`, `parse`, `completeImport` und `markImportFailed` bereit.
- `StatementFileParser` liest keine Import-Kategorien mehr aus dem Repository;
  PDF-Kategorien werden vor dem Parse als DB-Snapshot geladen und uebergeben.
- Parser-/API-Fehler nach angelegtem Import-Record werden als
  `ImportPipelineException` mit `importId` zurueckgetragen und auf dem
  DB-Executor als FAILED markiert.
- `SettingsDataService` bekommt eine `DatabaseLifecycle`-Abhaengigkeit.
  Produktionsaufrufe auf `AppDatabase.getInstance()`/`closeAndReset()` liegen
  nur noch in `AppCompositionRoot`.
- Charakterisierung erweitert:
  `csvImportKeepsSummaryCountsDuplicatesAndSuggestionsInvariant` laeuft ueber
  `BudgetViewModel.importFromCsv()` und prueft Summary, Duplicate-/New-Counts,
  leere Suggestions und sichtbare Rows; `SettingsDataServiceCharacterizationTest`
  prueft Restore-Sequenz und Sidecar-Cleanup.
- `CLAUDE.md`, App-/DB-/Task-READMEs, Import-README und betroffene Javadocs
  beschreiben `dbExecutor`/`ioExecutor` und DB-Lifecycle-Eigentum.

## Erfolgs-Review

Erster Review: FAIL.

- DW7-Blocker: Import-Paritaetstest lief direkt ueber
  `BudgetImportUseCase.execute()` statt ueber `BudgetViewModel.importFromCsv()`.
- DW8-Blocker: Import-README enthielt noch die alte UI->UseCase-Aussage.
- Roadmap-/Phasenstatus war erwartungsgemaess noch nicht finalisiert.

Re-Review: PASS.

- Import-Paritaet laeuft zweimal ueber `BudgetViewModel.importFromCsv()` und
  prueft Erstimport plus Duplikatimport.
- Import-README dokumentiert DB-Setup auf `dbExecutor`, Parsing auf
  `ioExecutor` und DB-Abschluss zurueck auf `dbExecutor`.
- `BudgetImportUseCase.parse()` verpackt Parserfehler mit `importId`; das
  ViewModel markiert Fehler im DB-Schritt als FAILED.

## Abschluss-Gate

Gruen:

- `./gradlew checkArchitecture --console=plain` -> Exit 0.
- `./gradlew assembleDebug --console=plain` -> Exit 0.
- `./gradlew testDebugUnitTest --console=plain` -> Exit 0.

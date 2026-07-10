# Phase 1 — Toten Code entfernen

Status: **designt + vollständigkeitsgeprüft**, Umsetzung ausstehend.

## Design (erweiterter Löschsatz)

Dateien löschen:
- `features/meal/application/MealTaskBridgeService.java` (209 Z., nicht verdrahtet,
  dupliziert Completion-Logik ohne Streak/History/Adaptive)
- `features/meal/domain/MealPlanGenerator.java` (314 Z., nie aufgerufen)
- `features/meal/application/internal/LegacyMealImportService.java` (691 Z.) + dessen
  begleitende `README.md`

Interface + Impl bereinigen (übrige Methoden dort bleiben live!):
- `existsTransactionByImportHash`, `isKnownCategory` aus
  `budget/domain/BudgetImportRepository.java` und
  `budget/data/repository/BudgetImportRoomRepository.java`

## Vollständigkeits-Review (Subagent) — Ergebnis

Reference-Audit: alle vier Ziele sind aus Live-Code (AppCompositionRoot, Manifest,
UI) nicht erreichbar. Sicher löschbar. Keine Reflection/ServiceLoader/String-Lookup.

**Folge-Totcode — MUSS mitgelöscht werden (sonst unvollständig):**
- `TaskDataService.writeSync(Task)` (`TaskDataService.java:109`) — einziger Aufrufer war die Bridge
- `TaskDataService.readSync(String)` (`:116`) — einziger Aufrufer war die Bridge
- `TaskDataService.deleteTaskGraphSync(String)` (`:131`) — einziger Aufrufer war die Bridge
- **behalten:** `TaskDataService.readAllSync()` (`:123`) — noch von `TaskEditViewModel.java:96` genutzt
- `BudgetTransactionDao.existsByImportHash(String)` (`BudgetTransactionDao.java:226`) — nur von gelöschter Impl genutzt
- `BudgetAccountCategoryDao.readCategory(String)` (`BudgetAccountCategoryDao.java:48`) — nur von gelöschter Impl genutzt

**Dangling-Doku — mitbereinigen:**
- `features/task/application/TaskDataService.java:107` (Javadoc nennt Bridge; löst sich mit `writeSync`-Löschung auf)
- `features/meal/README.md:5, 24` (nennt LegacyMealImportService)
- `features/meal/application/README.md:51` (verweist auf internal/README)
- `features/budget/application/importing/README.md:185` (nennt existsTransactionByImportHash)

**Optional (nicht erforderlich):** 3-arg `TaskPrefSlotFactory.create` auf `private` setzen
(nach Bridge-Löschung ruft nur noch `createDefault()` intern auf).

**Nicht löschen (verifiziert weiterhin live):** `TaskMealIntegrationService.completeMealTask`
(von `TaskCompletionEffects.java:38`), diverse Meal-Repository-`save*`-Methoden,
`MealPlanGenerator`-Abhängigkeiten (`ShelfLifeService` etc. anderweitig genutzt).

## Done-When-Kriterien

- DW1: die 3 Dateien + LegacyMealImport-`README.md` gelöscht.
- DW2: die 2 toten Budget-Methoden (Interface + Impl) entfernt.
- DW3: der Folge-Totcode aus dem Review mitgelöscht (3 TaskDataService-Methoden,
  2 DAO-Methoden); `readAllSync` behalten.
- DW4: Dangling-Doku bereinigt.
- DW5: `grep -rn` über src/main nach den gelöschten Symbolen → keine Treffer.
- DW6: `./gradlew checkArchitecture` grün, `assembleDebug` grün.
- DW7: keine Verhaltensänderung an lebendem Code (gelöschte Pfade waren unerreichbar).

## Verhaltensinvarianten

Keine — reine Entfernung unerreichbaren Codes. Deshalb keine neuen Tests in dieser
Phase (Testsuite existiert ohnehin erst ab Phase 2).

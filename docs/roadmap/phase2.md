# Phase 2 — Test-Infrastruktur & Verhaltens-Baseline

Status: **abgeschlossen**.

## Design

### Test-Infrastruktur

- JVM-Test-Sourceset `src/test/java` aktivieren; keine `src/androidTest`-Suite und
  keine neuen Gradle-Module.
- Governance-Konflikt vor Testanlage bereinigen: `CLAUDE.md` und diese Roadmap sind
  fuer Phase 2 autoritativ, weil sie die juengere Testregel setzen. Das noch alte
  `AGENTS.md`-Verbot von `src/test`/JUnit wird in dieser Phase auf die CLAUDE-Testregel
  aktualisiert; dadurch entsteht keine zweite Testpolitik.
- Bestehender DB-Doku-Konflikt: `CLAUDE.md` nennt noch DB-Version 24, `AppDatabase`
  ist bereits Version 27. Phase 2 aendert die DB-Version nicht und blockiert nicht an
  diesem vorbestehenden Doku-Drift; die Roadmap ordnet den finalen Doku-Sync Phase 8 zu.
- Gradle-Dependencies:
  - `testImplementation("junit:junit:4.13.2")`
  - `testImplementation("androidx.test:core:1.6.1")`
  - `testImplementation("androidx.arch.core:core-testing:2.2.0")`
  - `testImplementation("org.robolectric:robolectric:4.14.1")`
  - `testAnnotationProcessor("androidx.room:room-compiler:2.6.1")`
- Android-Testoptionen:
  - `testOptions.unitTests.isIncludeAndroidResources = true`, damit Robolectric
    deutsche String-Ressourcen fuer `LoadBudgetOverviewUseCase` und ViewModels sieht.
- Gemeinsame Test-Helfer in `src/test/java/com/autosecretary/testing/`:
  - `AutoSecretaryRobolectricTest`: Basisklasse mit `@RunWith(RobolectricTestRunner.class)`
    und `@Config(sdk = 35)`.
  - `TestDatabases`: erstellt `AppDatabase` ueber
    `Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class)`
    mit `allowMainThreadQueries()` und schliesst sie in `@After`.
  - `SynchronousExecutorService`: fuehrt `execute/submit` inline aus, damit
    ViewModel-/UseCase-Flows deterministisch bleiben.
  - `LiveDataTestUtil`: liest LiveData-Werte unter `InstantTaskExecutorRule`.
  - `RobolectricDrain`: ruft nach ViewModel-/Callback-Aktionen den Main-Looper
    (`ShadowLooper`) leer, damit `LiveData.postValue()` und callbackDispatcher
    deterministisch beobachtet werden.
  - `CallbackProbe`: Await-Helfer fuer Consumer/Runnable-Callbacks; bei synchronem
    Executor sofort erledigt, bei Robolectric-Callback mit Looper-Drain abgesichert.
  - `TaskFixtures`, `BudgetFixtures`, `MealFixtures`: kleine Builder fuer DB-reife
    Domain-/Entity-Objekte; keine Produktions-Abstraktion.
- Tests bauen den Objektgraphen manuell aus In-Memory-DAOs, Repositories, UseCases,
  `SynchronousExecutorService` und CallbackDispatcher. `AppCompositionRoot`,
  `AppDatabase.getInstance()` und echte Singleton-Lifecycle-Pfade werden nicht genutzt.

### Charakterisierungstests

1. `BudgetOverviewCharacterizationTest`
   - Pfad: `BudgetViewModel -> LoadBudgetOverviewUseCase -> BudgetRoomRepository -> Room`.
   - Instanziierung: `AppDatabase` in-memory; `BudgetRoomRepository` aus
     `budgetAccountCategoryDao`, `budgetTransactionDao`, `budgetLimitDao`,
     `budgetRecurringTemplateDao`; `BudgetSeedService`,
     `CalculateEffectiveBudgetLimitUseCase`, `LoadBudgetOverviewUseCase` mit
     Robolectric-Resources; Import/Recurring/Transfer-UseCases mit denselben Repositories;
     `BudgetViewModel` mit synchronem Executor und `InstantTaskExecutorRule`.
   - Setup: aktives Konto, Ausgabe-Kategorie, eine Ausgabe im aktuellen Monat.
   - Invariant: ViewModel waehlt das erste aktive Konto, liefert `BudgetUiState.CONTENT`,
     eine Transaktionszeile mit deutschem Kategorie-Label/Icon, Summary-Ausgaben und
     Balance-Chartpunkte.
   - Geschuetzt fuer Phase 4: `LoadBudgetOverviewUseCase` darf beim Entkoppeln von
     UI-State-Typen beobachtbar gleich bleiben.

2. `TaskMealCompletionCharacterizationTest`
   - Pfad: `CheckOffTaskUseCase -> TaskSlotToggleMutation -> TaskCompletionEffects ->
     TaskMealIntegrationService -> MealRoomRepository/MealRecipeRoomRepository/MealPantryRoomRepository -> Room`.
   - Instanziierung: `TaskDao`, `TaskCompletionService`, `TaskLifecycleManager`,
     `TaskTransitionRecorder`, `TaskSlotToggleMutation`, `TaskCompletionEffects`;
     Budget-Booking mit No-op-UseCase oder leerem BudgetRepository, Widget-Refresh mit
     testbarem No-op; Meal-Repositories aus Room-DAOs; callbackDispatcher synchron.
   - Setup: Task mit heutigem Slot und geplantem Meal, Rezept mit Ingredient, Pantry-Bestand.
   - Ausfuehrung: zwei Check-Off-Aufrufe gegen denselben Slot.
   - Invariant: erster Aufruf setzt nur `realStart`; zweiter Aufruf setzt `completed`,
     erzeugt genau einen Datensatz in `meal_consumption_log`, reduziert Pantry,
     markiert den MealPlan erledigt und setzt `task_planned_meals.completed`.
   - Geschuetzt fuer Phase 4: meal↔task-Port-Umbau darf Completion-Side-Effects nicht aendern.

3. `TaskCompletionCharacterizationTest`
   - Pfad: `CheckOffTaskUseCase -> TaskSlotToggleMutation -> TaskCompletionService -> Room`.
   - Instanziierung: wie Task-Meal-Test, aber Meal/Budget/Widget-Side-Effects als No-op,
     damit nur Completion-Kern und Task-DAOs gemessen werden.
   - Setup: adaptive Task mit PrefSlot, History/Streak-Ausgangswerten und heutigem Slot.
   - Ausfuehrung: erster Check-Off, dann `realStart` im DB-Slot gezielt auf eine
     nicht-quick Dauer zuruecksetzen, zweiter Check-Off.
   - Invariant: STARTED/COMPLETED-Zweiphasigkeit, `currentStreak`, Completion-History,
     Timing-Sample und adaptive PrefSlot-Anpassung bleiben erhalten.
   - Geschuetzt fuer Phase 5/6: Threading- und Paketverschiebung duerfen Kerncompletion
     und Room-Persistenz nicht aendern.

4. `TaskSchedulingRoundtripCharacterizationTest`
   - Pfad: `RegenerateScheduleUseCase -> DefaultTaskSlotGenerator -> TaskDao -> Room`.
   - Instanziierung: `TaskDao`, `TaskScheduleConfigDao`,
     `TaskScheduleConfigRepository`, `DefaultTaskSlotGenerator` mit No-op-Kalender und
     Budget-Eligibility, `RegenerateScheduleUseCase` mit synchronem Executor.
   - Setup: wiederholende Task mit PrefSlot im Planungsfenster und ScheduleConfig.
   - Invariant: `Result.createdSlots() > 0`, DB enthaelt Slots, und `TaskDao.read()`
     liest dieselben Slot-Kernfelder wieder aus. Dadurch kann der Test nicht durch den
     Exception-Fallback `createdSlots=0` falsch-gruen werden.
   - Geschuetzt fuer Phase 6: Task-Modell-Paketverschiebung muss Room-Schema und
     Scheduling-Roundtrip erhalten.

5. `MealPlannerCharacterizationTest`
   - Pfad: `MealPlannerPresenter -> MealRoomRepository/MealRecipeRoomRepository/MealPantryRoomRepository -> Room`.
   - Instanziierung: Room-Meal-DAOs, drei Meal-Repositories, `MealPlannerPresenter`
     mit synchronem workerExecutor und callbackDispatcher.
   - Setup: Rezept, MealPlan, PantryItem, ShoppingListItem und HouseholdMember.
   - Invariant: `loadHome()` aggregiert Plan/Recipe/Pantry/Shopping sortiert und
     `saveRecipe -> planRecipe -> toggleMealCompleted` persistiert eine CRUD-Kette
     mit erledigtem MealPlan.
   - Geschuetzt fuer Phase 7: Presenter-Split darf Home-Aggregation und Meal-CRUD
     nicht aendern.

### Ausdrueckliche Nicht-Ziele

- Keine Espresso-/Instrumented-Tests.
- Keine Produktcode-Abstraktionen nur fuer Tests.
- Keine neue DI-Schicht, kein Event-Bus, keine Gradle-Module.
- Keine Room-Schemaaenderung und keine Migration.

## Vollständigkeits-Review (Subagent) — Ergebnis

Erster Review-Zeitpunkt: 2026-07-10.

Ergebnis: fail; Umsetzung blockiert, bis Designluecken geschlossen sind.

Blockierende Punkte aus dem Review und Design-Reaktion:
- AGENTS/CLAUDE-Testregel-Konflikt: Design ergaenzt Governance-Bereinigung in Phase 2.
- CLAUDE/AppDatabase-DB-Version-Drift: Design stellt klar, dass Phase 2 die Version nicht
  aendert und der finale Doku-Sync Phase 8 gehoert.
- LiveData/Executor-Determinismus: `InstantTaskExecutorRule`, Looper-Drain und Await-Helfer
  sind jetzt Pflicht.
- AppCompositionRoot-Risiko: Tests bauen manuelle In-Memory-Graphen und nutzen keine Singletons.
- Scheduling-Falschgruen: Test muss `createdSlots > 0` und DB-Slots pruefen.
- Task-Meal-Side-Effects: Assertions auf MealPlan, `task_planned_meals`, ConsumptionLog
  und Pantry sind jetzt explizit.

Re-Review-Zeitpunkt: 2026-07-10.

Ergebnis: pass; keine verbleibenden Design-Blocker. Der Re-Review bestaetigt:
- AGENTS-Synchronisierung ist als erster Umsetzungsschritt und DW1 verankert.
- DB-Version-Doku-Drift ist bewusst auf Phase 8 verschoben und blockiert Phase 2 nicht.
- LiveData/Looper/Executor-Determinismus ist ausreichend entschieden.
- Tests vermeiden `AppCompositionRoot`/Singletons.
- Scheduling- und Task-Meal-Assertions verhindern falsch-gruene Tests.

## Done-When-Kriterien

- DW1: `AGENTS.md` ist mit der autoritativen CLAUDE/Roadmap-Testregel synchronisiert
  und verbietet `src/test`/JUnit nicht mehr.
- DW2: `./gradlew testDebugUnitTest` fuehrt echte JVM-Tests aus, nicht mehr `NO-SOURCE`.
- DW3: Die fuenf Charakterisierungstests existieren unter `src/test/java`, treiben die
  genannten Schichtpfade und nennen im Testnamen oder in einer knappen Testkommentierung
  die geschuetzte Invariante.
- DW4: Test-Helfer bleiben unter `src/test/java/com/autosecretary/testing/` und
  erzeugen keine Produktions-Abhaengigkeiten.
- DW5: Keine `src/androidTest`-Suite, keine neuen Gradle-Module, kein DI-Framework,
  kein Event-Bus.
- DW6: Keine produktive Room-Schemaaenderung; `AppDatabase`-Version bleibt unveraendert.
- DW7: Tests nutzen keine `AppCompositionRoot`-/`AppDatabase.getInstance()`-Singletons,
  sondern ausschliesslich in-memory Room mit `db.close()`.
- DW8: `./gradlew checkArchitecture --console=plain` Exit 0.
- DW9: `./gradlew assembleDebug --console=plain` Exit 0.
- DW10: `./gradlew testDebugUnitTest --console=plain` Exit 0 mit ausgefuehrten Tests
  aus allen fuenf Charakterisierungstestklassen.

## Verhaltensinvarianten

- Budget-Overview: Kontoauswahl, Content/Empty-State, Summary, Zeilenlabel und Chartdaten
  bleiben beim spaeteren UI/Application-Split beobachtbar gleich.
- Task↔Meal-Completion: Completion bucht Meal-Konsum, Pantry-Verbrauch und MealPlan-Status
  unveraendert.
- Task-Completion-Kern: STARTED/COMPLETED-Zweiphasigkeit, Streak/History/Adaptive bleiben
  ueber Room erhalten.
- Scheduling-Roundtrip: generierte Slots bleiben mit denselben Kernfeldern persistent.
- Meal-Planner: Home-Aggregation und CRUD-Kette bleiben beim Presenter-Split erhalten.

## Erfolgs-Review (Subagent) — Ergebnis

Review-Zeitpunkt: 2026-07-10.

Ergebnis gegen Done-When-Kriterien:
- DW1: pass — `AGENTS.md` erlaubt JVM-Tests unter `src/test` und nennt
  `testDebugUnitTest`.
- DW2: pass — Gradle-Testdependencies und Robolectric-Resources sind konfiguriert;
  XML-Reports zeigen echte Testcases statt `NO-SOURCE`.
- DW3: pass — fuenf Charakterisierungstestklassen existieren und nennen die
  geschuetzten Invarianten in den Testnamen.
- DW4: pass — Test-Helfer liegen unter `src/test/java/com/autosecretary/testing/`.
- DW5: pass — keine `src/androidTest`-Suite, keine neuen Gradle-Module, kein
  DI-Framework, kein Event-Bus.
- DW6: pass — kein Diff an `AppDatabase.java`; Version bleibt 27.
- DW7: pass — keine Nutzung von `AppCompositionRoot` oder `AppDatabase.getInstance()`
  in `src/test`; Tests verwenden in-memory Room und `db.close()`.
- DW8/DW9: im Subagenten-Review nicht ausgefuehrt; anschließend im Hauptlauf gruen geprüft.
- DW10: pass — Testreports enthalten alle fuenf `*CharacterizationTest`-Klassen mit je
  einem Test, `failures=0`, `errors=0`.

Blockierende Befunde: keine nach Abschluss-Gate.

## Abschluss-Gate

2026-07-10:
- `./gradlew checkArchitecture --console=plain` → Exit 0.
- `./gradlew assembleDebug --console=plain` → Exit 0.
- `./gradlew testDebugUnitTest --console=plain` → Exit 0; fuenf
  Charakterisierungstestklassen ausgefuehrt (`BudgetOverview`, `TaskMealCompletion`,
  `TaskCompletion`, `TaskSchedulingRoundtrip`, `MealPlanner`).

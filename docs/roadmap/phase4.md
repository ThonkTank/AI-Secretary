# Phase 4 - Schicht-Entkopplung

## Design

Ziel dieser Phase ist reine Abhaengigkeitskorrektur ohne bewusstes
Verhaltensdelta. Die Phase nutzt die in Phase 2 angelegten Charakterisierungstests
fuer Budget-Overview und Task/Meal-Completion als Paritaetsnetz. Neue Tests werden
nur ergaenzt, wenn beim Umbau eine bisher nicht abgedeckte beobachtbare
Randbedingung beruehrt wird.

1. Budget-Overview application -> UI-Abhaengigkeit entfernen
   - `LoadBudgetOverviewUseCase` liefert weiterhin ein fertiges
     `OverviewData`, aber dessen DTOs und Mapper liegen in
     `features/budget/application/overview/`.
   - Verschoben werden `BudgetChartPoint`, `BudgetSummaryData`,
     `BudgetTransactionRow`, `TimeRangeFilter` und
     `BudgetSummaryPresentationMapper`.
   - Der verschobene Application-Mapper enthaelt nur
     `toSummary(...)`, `categoryLabel(...)` und `categoriesForDirection(...)`.
     Dialog-Controller duerfen diesen Application-Mapper importieren, weil UI ->
     Application erlaubt ist.
   - `BudgetUiState`, `BudgetLimitBar` und `UiText` bleiben UI-State, weil sie
     reine Sichtzustands- bzw. Dialog-/Banner-Typen sind. Limit-Bar-Mapping wird
     in `BudgetLimitPresentationMapper` unter `features/budget/ui/internal/`
     verschoben und nutzt nur `BudgetOverviewMapper.categoryLabel(...)`.
   - UI darf die Application-Overview-DTOs binden; Application importiert danach
     keine `features.budget.ui.*` Pakete mehr.
   - `LoadBudgetOverviewUseCase` behaelt vorerst seine `Resources`-Abhaengigkeit
     fuer deutsche Row-Labels; Phase 4 entfernt gezielt UI-Package-Abhaengigkeiten,
     nicht Android-Resource-Labeling.

2. Task/Meal-Completion auf Consumer-Port umstellen
   - Der task-Konsument definiert `TaskMealCompletionService` in
     `features/task/domain/`.
   - Der Port akzeptiert ein task-domain-eigenes
     `TaskMealCompletionRequest`-Record mit `MealType`, `recipeId`,
     `completionDate`, `plannedServings` und `actualServings`. Dieses Record
     importiert keine `features/task/data.*` Klassen.
   - Der Adapter `TaskMealCompletionFromMealPlanner` liegt in
     `features/task/application/internal/meal/` und implementiert den Port gegen
     Meal-Domain-Repositories.
   - `TaskCompletionEffects` bleibt task-seitig fuer die Auswertung des heutigen
     `Task`/`TaskPlannedMeal`-Objekts verantwortlich: kein MealType oder schon
     erledigtes PlannedMeal -> kein Port-Aufruf; sonst `task.completePlannedMeal(...)`,
     Request bauen, Port ausfuehren und danach den Task erneut schreiben.
   - Die bisherige konkrete `features/meal/application/TaskMealIntegrationService`
     wird entfernt; `TaskCompletionEffects` haengt nur noch am task-eigenen Port.
   - Die vorhandene Meal-Completion-Charakterisierung bleibt unveraendert im
     Verhalten: geplantes Task-Meal wird abgeschlossen, Pantry wird verbraucht,
     ConsumptionLog wird geschrieben und der passende MealPlan wird erledigt.

3. MealPlannerFragment von Domain-Service loesen
   - `HouseholdEnergyService.calculateTdee(...)` wird nicht mehr im Fragment
     aufgerufen.
   - Der ViewModel/Presenter-Pfad liefert eine kleine UI-Zeile fuer
     Household-Management inklusive Alter und TDEE, sodass das Fragment nur noch
     rendert.

4. Direkt-Repository-Zugriffe aus ViewModels herausziehen
   - `BudgetViewModel` erhaelt schmale Application-Aufrufe fuer
     Transaktionsmutation, Account-Aufloesung und Limit-Uebersicht, statt selbst
     `BudgetRepository` zu verwenden.
   - Konkret: `BudgetTransactionMutationUseCase` fuer Add/Update/Delete,
     `ResolveBudgetAccountUseCase` fuer den Import-/Suggestion-Account-Fallback
     und `LoadBudgetLimitBarsUseCase` fuer Kategorie-Limit-Bars.
   - `TaskEditViewModel` erhaelt `TaskEditReferenceDataUseCase`, der Eltern-,
     Budget-Account- und Budget-Kategorie-Optionen in der Application-Schicht
     zusammenstellt.
   - Factory- und Composition-Root-Wiring werden entsprechend angepasst.

5. Unlock-Receiver ueber WidgetRefreshNotifier
   - `AutoSecretaryApplication` ruft beim Unlock nicht mehr direkt
     `TaskWidgetProvider.notifyWidgetUpdate(...)`, sondern den in Phase 3 nach
     `shared/` verschobenen `WidgetRefreshNotifier`.

6. Doku-/Javadoc-Folgeeffekte
   - Referenzen auf `TaskMealIntegrationService` werden auf den neuen
     task-eigenen Port/Adapter aktualisiert in `CLAUDE.md`,
     `features/README.md`, `features/meal/README.md`,
     `features/meal/application/README.md`, `TaskCore`, `MealPlan` und
     `ConsumptionLog`.

## Vollstaendigkeits-Review

Erster Review: FAIL. Blocker waren ein unvollstaendig beschriebenes
`TaskMealCompletionService`-Portdatenmodell, unentschiedene Zielorte beim
`BudgetSummaryPresentationMapper`-Split, zu schmale Test-/Done-When-Kriterien
fuer die neu beruehrten Verhalten und fehlende Doku-/Javadoc-Folgeeffekte.

Re-Review: PASS. Die Blocker sind durch `TaskMealCompletionRequest` ohne
`task.data.*`, klare Mapper-Zielorte, zusaetzliche Charakterisierungstests und
explizite Doku-/Javadoc-Flächen geschlossen.

## Done-When-Kriterien

- DW1: `LoadBudgetOverviewUseCase` und andere Klassen unter
  `features/budget/application/` importieren keine `features.budget.ui.*`
  Pakete mehr.
- DW2: Es gibt keine Produktionklasse `features/meal/application/TaskMealIntegrationService`
  mehr; `TaskCompletionEffects` referenziert stattdessen den task-eigenen
  Consumer-Port. Der Port und sein Request importieren keine `features/task/data.*`
  Klassen.
- DW3: `MealPlannerFragment` importiert oder ruft `HouseholdEnergyService` nicht
  mehr direkt auf.
- DW4: `BudgetViewModel` und `TaskEditViewModel` halten keine
  `BudgetRepository`-Felder mehr und fuehren keine direkten Budget-Repository-
  Lese-/Schreibaufrufe mehr aus.
- DW5: `AutoSecretaryApplication` importiert keinen `TaskWidgetProvider` mehr
  und nutzt `WidgetRefreshNotifier` fuer den Unlock-Refresh.
- DW6: Die Phase-2-Charakterisierungstests fuer Budget-Overview und
  Task/Meal-Completion bleiben unveraendert gruen, ausser Imports/Konstruktion
  werden an verschobene Typen angepasst.
- DW7: Vor dem Umbau werden zusaetzliche Charakterisierungstests fuer die in
  dieser Phase neu beruehrten Verhalten ergaenzt: Budget-Transaktionsmutation
  aktualisiert Overview, Budget-Limit-Bars bleiben gleich, TaskEdit-Referenzdaten
  enthalten Eltern/Budget-Optionen, Meal-Household-Management liefert Alter/TDEE,
  und Unlock-Refresh delegiert an `WidgetRefreshNotifier`.
- DW8: Doku-/Javadoc-Referenzen auf den entfernten Service sind auf den neuen
  Port/Adapter aktualisiert; `rg "TaskMealIntegrationService" CLAUDE.md src/main/java`
  findet keine veraltete Architekturbeschreibung mehr.
- DW9: Abschluss-Gate ist gruen:
  `./gradlew checkArchitecture`, `./gradlew assembleDebug`,
  `./gradlew testDebugUnitTest`.

## Geschuetzte Verhaltensinvarianten

- Budget-Overview zeigt fuer denselben Account/Monat dieselben Summary-Werte,
  Transaktionszeilen und Chart-Punkte wie vor der Entkopplung.
- Budget-Transaktionen, Transfers, Imports, Recurring-Suggestions und Limits
  fuehren nach erfolgreicher Mutation weiterhin zu einem aktualisierten Overview.
- Task-Meal-Completion bucht weiterhin Meal-Folgeeffekte und persistiert das
  aktualisierte Task-Planned-Meal.
- Household-Management zeigt weiterhin Name, Inaktiv-Markierung, Alter, Gender
  und TDEE je Haushaltsmitglied.
- Unlock aktualisiert weiterhin das Task-Widget.

## Umsetzung

Abgeschlossen.

- Budget-Overview-DTOs und `BudgetOverviewMapper` liegen in
  `features/budget/application/overview/`; der UI-interne Limit-Bar-Mapper ist
  getrennt.
- `BudgetViewModel` nutzt Application-Aufrufe fuer Transaktionsmutation,
  Account-Aufloesung und Limit-Overview; `TaskEditViewModel` nutzt
  `TaskEditReferenceDataUseCase`.
- Die Task/Meal-Kopplung laeuft ueber den task-eigenen
  `TaskMealCompletionService`-Port mit `TaskMealCompletionRequest`; der Adapter
  `TaskMealCompletionFromMealPlanner` liegt in `task/application/internal/meal/`.
- `MealPlannerFragment` rendert `HouseholdMemberRowState`; Alter/TDEE kommen aus
  ViewModel/Presenter.
- `AutoSecretaryApplication` delegiert Unlock-Refresh an `WidgetRefreshNotifier`.
- Zusätzliche Charakterisierungstests wurden vor dem Umbau angelegt und blieben
  nach dem Umbau gruen.

## Erfolgs-Review

PASS. Der Subagent bestaetigte alle Done-When-Kriterien DW1-DW8 ohne
blockierende Befunde. DW9 blieb wie vorgesehen dem separaten Abschluss-Gate
vorbehalten.

## Abschluss-Gate

Gruen:

- `./gradlew checkArchitecture --console=plain` -> Exit 0
- `./gradlew assembleDebug --console=plain` -> Exit 0
- `./gradlew testDebugUnitTest --console=plain` -> Exit 0

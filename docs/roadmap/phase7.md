# Phase 7 - Application-Schicht normalisieren

## Design

Ziel dieser Phase ist Namens- und Verantwortlichkeitsklarheit in der
Application-Schicht. Verhalten bleibt gleich; die in Phase 2 angelegten
Meal-Planner-Charakterisierungstests bleiben der Paritaetsanker.

1. `MealPlannerPresenter` splitten und entfernen
   - `features/meal/application/MealPlannerPresenter.java` verschwindet.
   - Ersatz ist kein neuer UI-Presenter, sondern eine kleine Application-
     Fassade plus fokussierte UseCases/DataServices:
     - `MealPlannerDataService`: UI-facing Application-Fassade fuer den
       Meal-Planner-ViewModel-Vertrag. Sie besitzt Executor/Callback-
       Dispatching und delegiert fachliche Arbeit.
     - `LoadMealHomeUseCase`: Home-Aggregation inkl. Sortierung,
       Demo-Rezept-Seeding, WeekPlanSnapshot, NeedProgress und ShoppingFocus.
     - `LoadMealWeeklyProgressUseCase`: Wochenziel-/Kalorien-/FoodGroup-
       Fortschritt inklusive `WeeklyProgressOverview` und
       `WeeklyProgressFoodGroup`.
     - `MealPlanMutationUseCase`: Planen, Completion togglen und MealPlan
       loeschen.
     - `MealShoppingUseCase`: Shopping-Status, Need->ShoppingItem und
       Pantry-Item-Anlage.
     - `MealManagementDataService`: Management-Loads und CRUD fuer Rezepte,
       Zutaten, Pantry, Haushalt und CookingPreferences.
   - Die oeffentlichen Callback-Methoden koennen im `MealPlannerDataService`
     den bisherigen ViewModel-Aufrufvertrag behalten, damit UI- und Test-Diff
     klein bleiben. Die innere Arbeit liegt aber in den fokussierten Klassen.
   - In `MealPlannerDataService` darf nur Executor-/Callback-Dispatching,
     no-op Dialog-Readiness (`openManageNeed`, `openManagePantry`) und
     Delegation bleiben. Keine Sortierung, kein Repository-CRUD, kein
     Demo-Seeding, keine TDEE-/Progress-Berechnung.

2. Methoden-Mapping aus dem aktuellen Presenter
   - `MealPlannerDataService`: public async facade fuer `loadHome`,
     `getWeeklyProgressOverview`, `openManagePlan`, `openManageNeed`,
     `openManagePantry`, `updateShoppingItemStatus`, `planRecipe`,
     `toggleMealCompleted`, `createShoppingItemFromNeed`, `createPantryItem`,
     Management-Loads/CRUD, `deleteMealPlan`, `loadCookingPreferences`,
     `saveCookingPreferences`. Jede Methode delegiert an genau einen fokussierten
     UseCase/DataService und dispatched den Callback.
   - `LoadMealHomeUseCase`: `loadHome`-Inhalt plus private Helper
     `loadSortedMealPlans`, `loadSortedRecipes`, `loadSortedPantryItems`,
     `loadShoppingList`, `createWeekPlanSnapshot`, `createNeedProgress`,
     `createShoppingFocus`.
   - `LoadMealWeeklyProgressUseCase`: `getWeeklyProgressOverview`-Inhalt plus
     `estimateActualFromCalories` und `toPercent`.
   - `MealPlanMutationUseCase`: `planRecipe`, `toggleMealCompleted`,
     `deleteMealPlan`.
   - `MealShoppingUseCase`: `updateShoppingItemStatus`,
     `createShoppingItemFromNeed`, `createPantryItem`.
   - `MealManagementDataService`: `openManagePlan` data load,
     `loadRecipesForManagement`, `loadIngredientsForManagement`,
     `loadPantryItemsForManagement`, `loadHouseholdMembersForManagement`,
     `loadHouseholdMemberOverviewsForManagement`, `saveRecipe`, `deleteRecipe`,
     `saveIngredient`, `deleteIngredient`, `savePantryItem`,
     `deletePantryItem`, `saveHouseholdMember`, `deleteHouseholdMember`,
     `loadCookingPreferences`, `saveCookingPreferences`.

3. DTO/Nested-Type-Umzug
   - `WeeklyProgressOverview`, `WeeklyProgressFoodGroup` und
     `HouseholdMemberOverview` werden aus dem Presenter herausgeloest.
   - Ziel: eigene package-private oder public Records/Klassen in
     `features/meal/application/`, je nach Nutzung durch ViewModel und Tests.
   - `MealPlannerViewModel` importiert keine Presenter-Typen mehr.

4. Wiring
   - `AppCompositionRoot` cached `MealPlannerDataService` statt
     `MealPlannerPresenter`.
   - `MealPlannerViewModelFactory` und `MealPlannerViewModel` nehmen
     `MealPlannerDataService`.
   - `MainActivity.showCookingPrefsDialog()` nutzt den DataService aus dem
     Composition Root.
   - `MealPlannerCharacterizationTest` wird auf `MealPlannerDataService`
     umgestellt, ohne Assertions zu lockern.

5. `TaskEditPresenter` umbenennen
   - `features/task/ui/edit/TaskEditPresenter.java` wird zu
     `TaskEditFormController.java`.
   - Konstruktor, Felder, Javadocs, `TaskEditDialog`,
     `PrefSlotSectionController`, `TaskEditSectionBinder` und README-
     Referenzen werden aktualisiert.
   - Verhalten bleibt unveraendert; es ist eine UI-Namenskorrektur, keine
     Application-Schicht-Aenderung.

6. Test-Folgeeffekte
   - Vor dem Split wird der Meal-Charakterisierungstest erweitert, damit die
     zusaetzlich betroffenen Public-Methoden abgedeckt sind:
     WeeklyProgress, Shopping-Status-Update, Need->ShoppingItem, Pantry-
     Item-Anlage mit Expiry, CookingPreferences Load/Save und MealPlan-
     Delete.
   - Die bestehenden Home-/CRUD-/Household-Assertions bleiben erhalten und
     werden nicht gelockert.

7. Doku-Folgeeffekte
   - Aktualisiert werden mindestens `CLAUDE.md`,
     `features/meal/README.md`, `features/meal/application/README.md`,
     `features/meal/ui/README.md`, `features/task/ui/edit/README.md` und
     betroffene Javadocs/XML-Kommentare (u.a.
     `src/main/res-meal/layout/meal_plan_row_item.xml`).
   - Nach der Phase darf in Produktionscode kein `MealPlannerPresenter` und kein
     `TaskEditPresenter` mehr vorkommen.
   - Historische Roadmap-Reports duerfen alte Namen nur dann behalten, wenn sie
     explizit historische Baseline/alte Pfade beschreiben. Aktuelle Architektur-
     und README-Oberflaechen muessen die neuen Namen verwenden.

## Vollstaendigkeits-Review

Erster Review: FAIL.

- Die Fassade/UseCase-Grenze war zu lose; es waere noch moeglich gewesen, den
  alten Presenter nur umzubenennen. Ergaenzt wurde ein Methoden-Mapping fuer
  jede aktuelle Public-Methode und jeden relevanten Helper sowie die Regel, dass
  `MealPlannerDataService` nur Dispatching und Delegation enthaelt.
- Die Verhaltensparitaet war zu eng auf bestehende Tests beschraenkt. Ergaenzt
  wurden verpflichtende Charakterisierungserweiterungen fuer WeeklyProgress,
  Shopping-Status, Need->ShoppingItem, Pantry-Expiry, CookingPreferences und
  MealPlan-Delete.
- Die Doku-Regel kollidierte mit historischen Roadmap-Reports und uebersah
  einen XML-Kommentar. Ergaenzt wurde eine historische-Ausnahme und die XML-
  Kommentar-Flaeche.

## Done-When-Kriterien

- DW1: `MealPlannerPresenter.java` existiert nicht mehr; Produktionscode und
  Tests verwenden `MealPlannerDataService` plus fokussierte Meal-Application-
  Klassen.
- DW2: Die Meal-Application-Schicht enthaelt fokussierte Klassen gemaess
  Methoden-Mapping oben. `MealPlannerDataService` enthaelt keine Repository-
  CRUD-/Sortier-/Seeding-/TDEE-/Progress-Logik, sondern nur Dispatching und
  Delegation.
- DW3: `MealPlannerViewModel`, `MealPlannerViewModelFactory`,
  `AppCompositionRoot`, `MainActivity` und Meal-Tests sind auf die neuen
  Application-Typen verdrahtet.
- DW4: `TaskEditPresenter.java` existiert nicht mehr; der UI-Formlogik-Typ
  heisst `TaskEditFormController`.
- DW5: Produktionscode, Tests und Doku enthalten keine alten
  `MealPlannerPresenter`- oder `TaskEditPresenter`-Referenzen, ausser
  historischen Roadmap-Baseline-Notizen, die explizit alten Zustand beschreiben.
- DW6: Meal-Planner-Charakterisierungstests bleiben unveraendert in ihrer
  Aussagekraft und decken zusaetzlich WeeklyProgress, Shopping-Status,
  Need->ShoppingItem, Pantry-Expiry, CookingPreferences und MealPlan-Delete ab.
- DW7: Keine neuen Gradle-Module, kein DI-Framework, kein Event-Bus, keine
  DB-Schema-Aenderung.
- DW8: Abschluss-Gate ist gruen:
  `./gradlew checkArchitecture`, `./gradlew assembleDebug`,
  `./gradlew testDebugUnitTest`.

## Geschuetzte Verhaltensinvarianten

- Meal-Home zeigt weiterhin dieselben Plan-, Rezept-, Pantry- und Shopping-
  Daten sowie WeekPlan/Need/Shopping-Fokus-Aggregate.
- Rezept speichern, Rezept planen und MealPlan completion togglen bleiben
  beobachtbar gleich.
- Household-Management liefert aktive Mitglieder inklusive unveraenderter
  Age/TDEE-Berechnung.
- Task-Edit-Repetition passt PrefSlots weiterhin identisch an und speichert
  denselben Task-Zustand.

## Umsetzung

Umgesetzt:

- `MealPlannerPresenter` wurde entfernt.
- Neue Meal-Application-Typen:
  `MealPlannerDataService`, `LoadMealHomeUseCase`,
  `LoadMealWeeklyProgressUseCase`, `MealPlanMutationUseCase`,
  `MealShoppingUseCase`, `MealManagementDataService`,
  `WeeklyProgressOverview`, `WeeklyProgressFoodGroup`,
  `HouseholdMemberOverview`.
- `MealPlannerDataService` enthaelt nur Executor-/Callback-Dispatching, no-op
  Dialog-Readiness und Delegation an fokussierte Klassen.
- `MealPlannerViewModel`, `MealPlannerViewModelFactory`,
  `AppCompositionRoot`, `MainActivity` und `MealPlannerCharacterizationTest`
  sind auf `MealPlannerDataService` verdrahtet.
- `TaskEditPresenter` wurde zu `TaskEditFormController` umbenannt; Dialog,
  PrefSlot-Controller, SectionBinder und Task-Edit-Doku nutzen den neuen Namen.
- Meal-Charakterisierung wurde vor dem Split erweitert um WeeklyProgress,
  Shopping-Status, Need->ShoppingItem, Pantry-Expiry, CookingPreferences und
  MealPlan-Delete.
- `CLAUDE.md`, Meal-README, Meal-Application-README, Meal-UI-README,
  Task-Edit-README und `meal_plan_row_item.xml` wurden aktualisiert.

Zwischenverifikation:

- `./gradlew testDebugUnitTest --tests com.autosecretary.features.meal.MealPlannerCharacterizationTest --console=plain`
  -> Exit 0 vor dem Split nach Test-Erweiterung.
- `./gradlew testDebugUnitTest --console=plain` -> Exit 0 nach dem Split.

## Erfolgs-Review

PASS.

- `MealPlannerPresenter.java` und `TaskEditPresenter.java` sind entfernt; die
  neuen Zieltypen sind verdrahtet.
- `MealPlannerDataService` bleibt auf Executor-/Callback-Dispatching,
  no-op Dialog-Readiness und Delegation beschraenkt. Sortierung, Seeding,
  Repository-CRUD und Progress-Berechnung liegen in den fokussierten
  Application-Klassen.
- ViewModel, Factory, Composition Root, MainActivity und Meal-Tests verwenden
  `MealPlannerDataService`.
- Die erweiterte Meal-Charakterisierung deckt WeeklyProgress,
  Shopping-Status, Need->ShoppingItem, Pantry-Expiry, CookingPreferences und
  MealPlan-Delete ab.
- Keine neuen Gradle-Module, kein DI-Framework, kein Event-Bus und keine
  DB-Schema-Aenderung.

## Abschluss-Gate

- `./gradlew checkArchitecture --console=plain` -> Exit 0.
- `./gradlew assembleDebug --console=plain` -> Exit 0.
- `./gradlew testDebugUnitTest --console=plain` -> Exit 0.

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Quick Reference

| Task | Command |
|------|---------|
| Syntax check (safe) | `./gradlew compileDebugJavaWithJavac` |
| Build + auto-push | `./gradlew assemble` |
| Run tests | `./gradlew testDebugUnitTest` |
| Widget debug | `adb logcat \| grep -iE "(widget\|RemoteViews\|autosecretary)"` |
| Dump plans | `adb shell am broadcast -a com.autosecretary.debug.DUMP_PLANS -n com.autosecretary/activities.widget.DebugBroadcastReceiver` |
| Replan all | `adb shell am broadcast -a com.autosecretary.debug.REPLAN_ALL -n com.autosecretary/activities.widget.DebugBroadcastReceiver` |

## Build Commands

```bash
# Syntax-Check ohne Nebenwirkungen (bevorzugt während der Entwicklung)
./gradlew compileDebugJavaWithJavac

# Build debug APK — ACHTUNG: pushed automatisch zu GitHub!
./gradlew assemble

# Run JUnit/Robolectric unit tests (test sources in test/)
./gradlew testDebugUnitTest

# Clean build
./gradlew clean
```

**WICHTIG — `assemble` pushed automatisch:** `assemble` → `copyToRelease` (APK + version.txt inkrementieren) → `pushToGitHub` (git add release/, commit, push). Für einen reinen Syntax-Check ohne Push immer `compileDebugJavaWithJavac` verwenden.

**WICHTIG:** Nach jeder abgeschlossenen Code-Änderung oder Bugfix MUSS `./gradlew assemble` ausgeführt werden. Das ist der korrekte Abschluss jeder Aufgabe – es baut die APK, inkrementiert die Version und pushed zu GitHub, damit das Auto-Update-System die neue Version an installierte Apps ausliefern kann.

**Typischer Workflow:** Code ändern → `compileDebugJavaWithJavac` (Fehler prüfen) → Fehler fixen → `assemble` (Release).

## Debugging (ADB)

```bash
# Widget-Fehler in Echtzeit anschauen
~/Android/Sdk/platform-tools/adb logcat | grep -iE "(widget|RemoteViews|autosecretary)"

# Logs vor Test löschen, dann Widget neu hinzufügen
~/Android/Sdk/platform-tools/adb logcat -c

# Plan-Debugging via DebugBroadcastReceiver (expliziter Component-Name für Android 8+)
adb shell am broadcast -a com.autosecretary.debug.DUMP_PLANS -n com.autosecretary/activities.widget.DebugBroadcastReceiver
adb shell am broadcast -a com.autosecretary.debug.REPLAN_ALL -n com.autosecretary/activities.widget.DebugBroadcastReceiver
adb logcat | grep DebugPlan   # Output filtern

# App muss laufen oder kürzlich gestartet worden sein! Bei Bedarf erst starten:
adb shell am start -n com.autosecretary/activities.inApp.mainActivity
```

**DUMP_PLANS Output-Format:**
```
=== MONDAY 2026-02-03 (06:00 - 18:00) ===
  06:00-06:30  Morgenroutine [GOAL, CRITICAL, score=150234]
      06:00-06:02  Tabletten nehmen [TASK, CRITICAL, score=100012]
  11:00-12:00  Haushalt [GOAL, LOW, score=100]
      11:00-12:00  Einkaufen [TASK, MODERATE, score=200] [Budget: 25.00€]
=== Zusammenfassung: 7 Tage, 23 Goal-Slots ===
```

Typische Widget-Fehler: "Error inflating RemoteViews" + "Class not allowed to be inflated" → View-Typ nicht RemoteViews-kompatibel.

## Project Layout

Non-standard Android project structure — no `app/` module, sources are at root level:
- Java sources: `src/` (not `app/src/main/java/`)
- Resources: `res/`
- Manifest: `AndroidManifest.xml` (root level)

Configured in `build.gradle.kts` via custom `sourceSets`. Java 17, compileSdk/targetSdk 35, minSdk 26. Uses `coreLibraryDesugaring` for `java.time` API on older devices. `applicationId` / `namespace` = `com.autosecretary`.

**Flat package names:** Source files use direct package names (e.g. `package activities.inApp;`, `package entities;`) — there is no root `com.autosecretary` prefix in the source. The namespace `com.autosecretary` only affects the generated `R` class (`import com.autosecretary.R;`) and manifest merging.

**Dependencies:** `androidx.core:core:1.12.0`, `coreLibraryDesugaring` (desugar_jdk_libs 2.1.4). Test: JUnit 4.13.2, Robolectric 4.14.1, `androidx.test:core:1.6.1`.

**Versionierung:**
- **versionCode:** Aus `release/version.txt` gelesen und automatisch inkrementiert bei jedem `assemble`
- **versionName:** Semantisch `$major.$minor.$patch` in `build.gradle.kts` (aktuell 1.0.0)
- `copyToRelease` schreibt nur den neuen versionCode zurück — bei reinem `compileDebugJavaWithJavac` bleibt version.txt unverändert

**Konstanten** (`data/constants.java`):
```java
DB_NAME = "autosecretary.db"
DB_VERSION = 31  // v1.0.0 Production Release
PREF_NAME = "secretary"
PREF_DB_VERSION = "db_version"
PREF_APP_MODE = "app_mode"
MODE_DEVELOPMENT = "development"
MODE_PRODUCTION = "production"
BACKUP_DIR = "backups"
MAX_BACKUPS = 5
```

**SQLrepo Singleton:** Verwende `SQLrepo.getInstance(context)` statt `new SQLrepo(context)` für Singleton-Zugriff. Alle Manager nutzen dies automatisch. Verhindert mehrfache DB-Connections und Race Conditions.

**Scheduling-Konstanten** (`scheduling/buildToDo.java`):
```java
FIXED_APPOINTMENT_PRIORITY = 10_000_000  // Feste Termine übertreffen alle
PREF_TIME_WINDOW_MINUTES = 480           // 8h-Arbeitstag für PrefTime-Penalty
CHAIN_LENGTH_BONUS_PER_ITEM = 50         // Bonus für längere Task-Ketten
SCORE_SCALE_FACTOR = 100                 // log1p-Score-Skalierung
```

## Architecture

```
src/
├── activities/inApp/     # mainActivity (Launcher, 4-Tab-UI), editItem (Create/Edit Modal), budgetView (Budget-Tab), mealPlanView (Ernährung-Tab, 5 Sub-Tabs: Woche/Rezepte/Einkauf/Vorrat/Planung)
├── activities/generic/   # ViewHelper, ViewBuilder, taskList (nutzt TaskRowRenderer)
├── activities/widget/    # TaskWidgetProvider, TaskWidgetFactory, TaskWidgetService, WidgetRefreshApp
├── entities/             # trackedItem, todoList, CalendarEvent, config, MealType (shared enum),
│                         # Account, Transaction, BudgetLimit, Import, HouseholdMember, CookingPreferences,
│                         # RecipeRating, Ingredient, Recipe, MealPlan, MealSchedule, ShoppingListItem,
│                         # PantryItem, ConsumptionLog, WeeklyFoodTarget, StorePackage
├── repository/           # Repo (Interface), SQLrepo, Table, MigrationManager,
│                         # parser/ (ParseUtils, itemParser, todoParser, accountParser, transactionParser,
│                         # budgetLimitParser, importParser, categoryParser, householdMemberParser,
│                         # cookingPreferencesParser, recipeRatingParser, ingredientParser, recipeParser,
│                         # mealPlanParser, mealScheduleParser, shoppingListParser, pantryParser,
│                         # consumptionParser, weeklyFoodTargetParser)
├── controller/           # todoManager, updateChecker, editorManager, budgetManager, mealManager,
│                         # SettingsManager, ApiKeyManager, ClaudeApiClient, ImportProcessor,
│                         # RecurringPatternDetector, WidgetUpdateManager
├── data/                 # constants, seedTestData, TaskListData, TaskRowConfig, BudgetDisplayData
├── render/               # TaskRowRenderer (einheitlicher Renderer für App + Widget)
└── scheduling/           # buildToDo, cleanToDo, CalendarReader, generateMealPlan,
                          # DailyPlanningScheduler, DailyPlanningReceiver, BootReceiver
```

**Widget-Architektur:** Kernprinzip: "App passt sich an Widget an" — RemoteViews-kompatible Layouts werden für beide Systeme verwendet.

**RemoteViews-Einschränkungen:** Nur bestimmte Views sind in Widgets erlaubt:
- Layouts: `FrameLayout`, `LinearLayout`, `RelativeLayout`, `GridLayout`
- Views: `TextView`, `ImageView`, `Button`, `ImageButton`, `ProgressBar`, `Chronometer`, `AnalogClock`
- Collections: `ListView`, `GridView`, `StackView`, `AdapterViewFlipper`

**NICHT erlaubt:** `<View>` (für Spacer/Divider stattdessen `ImageView` mit `background` verwenden), `CheckBox` (stattdessen `ImageView` mit Toggle-Icons), custom Views.

**Data flow:** `mainActivity` → `todoManager` (Controller) → `buildToDo` (UseCase) → `SQLrepo` (Repository)

**todoManager** exponiert `provideList()` → `List<TaskEntry>` (Record mit slotId, taskTitle, start/end, completed, goalTitle, progressCurrent/Target/Unit etc.). Wird von der App-UI konsumiert. Callback-Pattern via `TodoListener` Interface:
- `onListUpdated()` — Plan hat sich geändert, UI neu laden
- `onSchedulingConflicts(List<SchedulingConflict>)` — Feste Termine konnten nicht eingeplant werden (default-Methode, optional implementierbar)

Weitere Methoden:
- `replanToday()` — Plan löschen + neu generieren. Leitet Scheduling-Konflikte an Listener weiter.
- `completeSlot(slotId)` / `uncompleteSlot(slotId)` — Normale Tasks abhaken
- `incrementProgress(slotId)` / `decrementProgress(slotId)` — Progress-Tasks: setzt `slot.progressDelta`, NICHT das Item direkt
- `startTimer(slotId)` / `stopTimer(slotId)` — setzt workStart/workEnd + completes

**editorManager** verwaltet CRUD-Operationen für alle Item-Typen. Exponiert `getAllItems()` → `List<TreeEntry>` (`record TreeEntry(trackedItem item, int depth)`) für die hierarchische Baumdarstellung im Editor (DFS-Traversal). `getAvailableParents(type)` filtert typbasiert: TASK→GOAL, GOAL→PROJECT, PROJECT→null. `getActiveAccounts()` liefert alle aktiven Konten für Budget-Spinner. `createItem()` synct automatisch `parent.children`.

**budgetManager** verwaltet Budget-Daten für die UI. Exponiert Record-basierte Methoden:
- `provideAccounts()` → `List<AccountEntry>` — Alle aktiven Konten
- `provideSummary(yearMonth)` → `BudgetSummary` — Gesamtübersicht (Saldo, Einnahmen, Ausgaben, Netto)
- `provideRecentTransactions(limit)` → `List<TransactionEntry>` — Letzte N Transaktionen
- `provideTransactions(accountId, yearMonth, category)` → `List<TransactionEntry>` — Gefiltert
- `provideBudgetLimits(yearMonth)` → `List<BudgetEntry>` — Budget-Limits mit Spent/Remaining
- `provideCategories()` / `provideExpenseCategories()` → `List<CategoryOption>` — Für Dropdowns

Callback-Pattern via `BudgetListener` Interface. Write-Operationen (`createTransaction`, `setBudgetLimit`, etc.) aktualisieren automatisch Konto-Salden und benachrichtigen Listener.

**Recurring Template Operations:**
- `getAllTransactionsForAccount(accountId)` → `List<Transaction>` — Für Pattern-Erkennung
- `createRecurringTemplate(candidate, accountId)` → `Long` — Erstellt wiederkehrendes Template aus `RecurringCandidate`
- `linkTransactionsToTemplate(txIds, templateId)` — Verknüpft existierende Transaktionen mit Template

**mealManager** verwaltet Meal-Planning-Daten für die UI. Exponiert Record-basierte Methoden:
- `provideMembers()` → `List<MemberEntry>` — Alle aktiven Haushaltsmitglieder mit TDEE
- `provideAllRecipes()` / `provideRecipes(MealType)` → `List<RecipeEntry>` — Alle oder gefilterte Rezepte
- `provideIngredients()` → `List<IngredientEntry>` — Alle Zutaten für Rezept-Erstellung
- `provideMealPlan(weekStart)` → `List<MealPlanEntry>` — Wochenplan-Einträge
- `provideSchedule()` → `List<ScheduleEntry>` — Mahlzeiten-Kalender (7×4 Grid)
- `provideFoodGroupProgress(weekStart)` → `List<FoodGroupProgress>` — Fortschritt pro Lebensmittelgruppe (DGE-basiert)

Write-Operationen: `createRecipe()`, `updateRecipe()`, `deleteRecipe()`, `toggleFavorite()`, `createMember()`, `updateMember()`, `deleteMember()`, `createMealPlan()`, `updateMealPlan()`, `deleteMealPlan()`, `updateSchedule(id, time, enabled)`. `calculateRecipeNutrition()` berechnet Nährwerte basierend auf Zutaten automatisch. `findMealPlan(date, mealType)` findet existierenden Eintrag. `getMember(id)` und `getRecipe(id)` laden einzelne Entitäten.

**Shopping List Operations:**
- `provideShoppingList(weekKey)` → `List<ShoppingEntry>` — Einkaufsliste gruppiert nach FoodGroup
- `provideShoppingSummary(weekKey)` → `ShoppingSummary` — Zusammenfassung (Laden, Fortschritt, Preis)
- `generateShoppingList(weekStart)` — Aggregiert Zutaten aus MealPlans, zieht Vorrat ab, rundet auf Packungsgrößen
- `determinePreferredStore(ingredientIds)` — Scoring: Coverage (10 Punkte/Zutat) + Recency (max 30 Punkte)
- `finishShopping(weekKey, accountId, totalCents)` — Markiert gekauft, füllt Vorrat, erstellt Transaktion
- `toggleShoppingItemPurchased(itemId)` — Einzelnes Item togglen

**Meal Completion Flow:** `completeMeal(mealPlanId, actualServings)` wird von `todoManager.completeSlot()` aufgerufen wenn ein Meal-Task abgehakt wird:
1. `MealPlan.isCompleted = true`, `completedAt = now()`
2. Pantry reduzieren via `consumeFromPantry()` (FIFO nach Ablaufdatum)
3. `ConsumptionLog` erstellen pro aktivem Haushaltsmitglied

Callback-Pattern via `MealListener` Interface.

**generateMealPlan** (`scheduling/generateMealPlan.java`) — Automatische Wochenplan-Generierung in 6 Schritten:
1. `calculateWeeklyTarget()` — DGE-basierter Wochenbedarf pro FoodGroup, mit Überschuss/Defizit-Korrektur der Vorwoche
2. `calculateMealCalories()` — TDEE-basierte Kalorienverteilung (Frühstück 20%, Mittag 35%, Abend 35%, Snack 10%)
3. `planCookingSessions()` — Koch-Sessions basierend auf CookingPreferences und Kalender-Events
4. `scoreRecipe()` — Rezept-Bewertung nach 7 Kriterien: FoodGroup-Bedarf, Skalierbarkeit, Variety, Aufwand, Ratings, Pantry-Expiry, Verderblichkeit
5. `generateWeekPlan()` — Orchestriert alles, erstellt MealPlans und Meal-Tasks
6. `mealManager.generateShoppingList()` — Erstellt Einkaufsliste mit Single-Store-Optimierung

**Meal-Task-Erstellung:** Für jede Mahlzeit wird ein `trackedItem` mit `fixedAppointment` (Datum + Uhrzeit aus MealSchedule) erstellt. `mealPlanId` verknüpft Task mit MealPlan für Completion-Tracking.

**Meal UI Records** (`mealManager.java`) — Records für die Meal-UI-Datenübergabe:
- `MemberEntry(id, name, age, gender, dailyCalories, activityLabel, isActive)`
- `RecipeEntry(id, title, mealType, totalTime, calories, servings, tags, isFavorite, formattedLastUsed)`
- `IngredientEntry(id, name, foodGroup, unit, caloriesPer100)`
- `MealPlanEntry(id, date, dayName, mealType, recipeId, recipeTitle, servings, calories, isCompleted)`
- `ScheduleEntry(id, day, dayLabel, mealType, mealLabel, mealIcon, time, isEnabled, formattedTime)` — Mahlzeiten-Kalender
- `FoodGroupProgress(group, label, icon, targetGrams, plannedGrams, percent, formatted)`
- `ShoppingEntry(id, ingredientId, ingredientName, foodGroup, foodGroupIcon, amount, neededAmount, excessAmount, unit, formattedAmount, formattedExcess, suggestedStore, isPurchased, estimatedPriceCents, formattedPrice)`
- `ShoppingSummary(weekKey, suggestedStore, totalItems, purchasedItems, estimatedTotalCents, formattedTotal, isComplete)`
- `PantryEntry(id, ingredientId, name, amount, rawAmount, unit, location, locationIcon, locationType, expiryInfo, expiryDate, isExpiringSoon, isExpired)` — Vorratsartikel für Anzeige

**Pantry CRUD Operations:**
- `providePantry(filter)` → `List<PantryEntry>` — Vorrat sortiert nach Ablaufdatum, optional gefiltert nach StorageLocation
- `addToPantry(ingredientId, amount, unit, location, expiryDate)` — Neuen Artikel hinzufügen
- `updatePantryItem(item)` — Artikel aktualisieren
- `adjustPantryAmount(itemId, delta)` — Stepper-Logik, Auto-Delete bei ≤0
- `deletePantryItem(id)` — Artikel löschen
- `getPantryItem(id)` → `PantryItem` — Einzelnen Artikel laden

**Claude API Integration** (`controller/`) — Kontoauszug-Import via Anthropic Messages API:
- `ApiKeyManager` — Speichert API-Key Base64-encoded in SharedPreferences (`"secretary"`). Validierung: muss mit `"sk-ant-"` beginnen.
- `ClaudeApiClient` — HTTP-Client für `https://api.anthropic.com/v1/messages`. Model: `claude-sonnet-4-20250514`, Max 4096 Tokens, 120s Timeout für PDF-Verarbeitung.
- `ClaudePrompts` — System-Prompt mit dynamischer Kategorie-Liste aus DB. Strikte JSON-Output-Vorgabe für Transaktionen.
- `ImportProcessor` — Orchestriert Import-Workflow: SHA256-Datei-Hash (Duplikat-Check) → Import-Entity anlegen → Claude API aufrufen → Transaktionen parsen → Pro Transaktion: Hash-Check (`date_amount_payee`), Kategorie validieren, speichern via `createTransactionQuiet()` → Batch-Notifikation.

**Import-Workflow Records:**
- `ParsedStatementResult(periodStart, periodEnd, transactions[], promptTokens, responseTokens, processingTimeMs)`
- `ParsedTransaction(date, amountCents, payee, description, categoryId, hash)`
- `ImportResult(totalTransactions, newTransactions, duplicates, processingTimeMs, recurringCandidates)`

**Recurring Pattern Detection** (`RecurringPatternDetector.java`) — Erkennt wiederkehrende Muster nach Import:
- `normalizePayee()` — Entfernt Nummern/Sonderzeichen für Payee-Vergleich ("REWE #1234" → "REWE")
- `payeeSimilarity()` — Levenshtein-basiertes Fuzzy-Matching (Threshold: ≥75%)
- `detectPatterns()` — Gruppiert nach Payee, prüft Betrag-Konsistenz (±15%), erkennt Datum-Muster
- Pattern-Typen: MONTHLY_DAY (gleicher Tag ±2), MONTHLY_LAST (Monatsende), WEEKLY (gleicher Wochentag), INTERVAL (festes Tage-Intervall)
- `RecurringCandidate` Record mit Confidence-Score (0.0-1.0), automatischer Kategorisierung, Varianz-Statistiken
- Nach Import: budgetView zeigt Modal mit Kandidaten, User kann Templates erstellen via `budgetManager.createRecurringTemplate()`

**UI:** Hybrid XML + programmatisch. Hauptstruktur über XML-Layouts:
- Seiten-Layouts: `activity_main.xml`, `view_task_list.xml`, `view_edit_item.xml`, `modal_edit_item.xml`, `view_budget.xml`, `modal_transaction.xml`, `view_meal_plan.xml`, `modal_recipe.xml`, `modal_meal_plan.xml`, `modal_member.xml`, `modal_pantry.xml`
- Komponenten: `row_tree_item.xml` (Editor-Baum), `item_account_card.xml`, `item_budget_bar.xml`, `item_transaction_row.xml`, `item_recipe_card.xml`, `item_food_group_bar.xml`, `item_ingredient_row.xml`, `item_meal_slot.xml` (Wochenplan-Karte), `item_member_card.xml` (Haushaltsmitglied), `item_shopping_row.xml` (Einkaufsliste)
- Styling: `res/values/colors.xml`, `dimens.xml`, `styles.xml` — Farben/Größen als Ressourcen statt Hardcoded-Werte

**Einheitliche Layouts** (`res/layout/`): `item_task.xml`, `item_goal_header.xml`, `item_calendar.xml` — RemoteViews-kompatibel (RelativeLayout statt LinearLayout+weight, ImageView statt CheckBox). Widget-Container: `widget_list.xml`. Widget-Metadaten: `res/xml/task_widget_info.xml`.

Farben und Dimensionen liegen ausschließlich in `colors.xml`/`dimens.xml` — programmatischer Zugriff via `ContextCompat.getColor()` und `getResources().getDimension()`. Neue Views bevorzugt als XML-Layout anlegen; dynamische Listeinträge und ähnliches weiterhin programmatisch.

**Edit Modal** (`editItem.java` + `modal_edit_item.xml`) — Create/Edit-Dialog für Tasks, Goals und Projects:

**Feld-Sichtbarkeit nach ItemType:**

| Feld | TASK | GOAL | PROJECT | Bedingung |
|------|------|------|---------|-----------|
| Titel | ✅ | ✅ | ✅ | — |
| Beschreibung | ✅ | ✅ | ✅ | — |
| Priorität | ✅ | ✅ | ✅ | — |
| Min Dauer (Wert + Einheit) | ✅ | ✅ | ❌ | — |
| Max Dauer (Wert + Einheit) | ✅ | ✅ | ❌ | — |
| Parent | ✅ | ✅ | ❌ | — |
| Vorgänger | ✅ | ❌ | ❌ | — |
| Vorgänger Delay | ✅ | ❌ | ❌ | Wenn Vorgänger gewählt |
| Bevorzugte Uhrzeit | ✅ | ✅ | ❌ | Default: 09:00 bei Create |
| Cooldown | ✅ | ❌ | ❌ | — |
| Deadline | ✅ | ❌ | ❌ | RepType=NONE |
| Fester Termin | ✅ | ❌ | ❌ | RepType=NONE |
| Progress | ✅ | ✅ | ❌ | — |
| Progress per Rep | ✅ | ❌ | ❌ | Wenn Progress > 0 |
| Goal Icon/Color | ❌ | ✅ | ❌ | — |
| Budget | ✅ | ❌ | ❌ | — |
| Wiederholung | ✅ | ❌ | ❌ | — |
| Erst erledigen | ✅ | ❌ | ❌ | — |

**UI-Patterns:**
- `updateFieldVisibility()` — Zentrale Methode für typ-basierte Sichtbarkeit
- `updateButtonGroup(buttons, selectedIndex, activeColor, inactiveColor)` — Toggle-Buttons (Priority, DurationUnit)
- `refreshPredecessorSpinner()` — Lädt verfügbare Vorgänger + zeigt/versteckt Delay-Row
- TimePicker/DatePicker — Standard Android-Dialoge via `TimePickerDialog`/`DatePickerDialog`

**editItem Helper-Methoden:** `bindModal()` und `saveItem()` sind in spezialisierte Helper-Methoden aufgeteilt:
- **Binding:** `bindBasicFields()`, `bindDeadlineFields()`, `bindFixedAppointmentFields()`, `bindGoalCustomizationFields()`, `bindProgressFields()`, `bindTypeButtons()`, `bindPriorityButtons()`, `bindRepetitionButtons()`, `bindBudgetFields()`, `bindDurationFields()`, `bindPrefTimeFields()`, `bindPredecessorDelayFields()`, `bindCompleteFirstFields()`
- **Save:** `applyDurationFields()`, `applyDeadlineFields()`, `applyCooldownField()`, `applyProgressFields()`, `applyGoalFields()`, `applyParentField()`, `applyPrefTimeField()`, `applyPredecessorFields()`, `applyBudgetFields()`, `applyRepetitionFields()`, `persistItem()`
- Neue Feld-Gruppen: Entsprechende `bind*()` und `apply*()` Methode hinzufügen

**ViewHelper** (`activities/generic/ViewHelper.java`) — Zwei zentrale Utilities für programmatische UI:
- `dp(Context, int)` — Konvertiert dp zu Pixel (statischer Import überall verwendet)
- `roundedBg(Context, int color, int cornerDp)` — Erstellt `GradientDrawable` mit Farbe und abgerundeten Ecken

**Widget + Unified Renderer:** App und Widget verwenden dieselben Layouts (`item_task.xml`, `item_goal_header.xml`, `item_calendar.xml`). Zwei-Schichten-Architektur:
- `TaskRowConfig.java` — Records (TaskConfig, GoalHeaderConfig, CalendarConfig) mit Factory-Methoden, zentralisiert Business-Logik (Deadline-Rot, Streak-Farbe, etc.)
- `TaskRowRenderer.java` — Überladene `apply*(View, ...)` und `apply*(RemoteViews, ...)` Methoden, wendet Config auf Views an

Widget-Komponenten in `activities/widget/`:
- `TaskWidgetProvider` — AppWidgetProvider, verarbeitet Actions (Toggle, Timer, Refresh, CreateItem)
- `TaskWidgetFactory` — RemoteViewsFactory, liefert RemoteViews für ListView
- `TaskWidgetService` — RemoteViewsService Boilerplate
- `WidgetRefreshApp` — Custom Application, registriert Widgets beim WidgetUpdateManager + Unlock-Receiver
- `BudgetWidgetProvider` — Minimalistisches 2-Spalten-Widget (Saldo + Frei). "Frei" wird rot bei negativem Wert.
- `DebugBroadcastReceiver` — ADB-triggerbare Debug-Befehle (DUMP_PLANS, REPLAN_ALL). `exported="false"` im Manifest — nur via ADB erreichbar (System-Privilegien), nicht von externen Apps.

**WidgetUpdateManager** (`controller/WidgetUpdateManager.java`) — Decoupled Widget-Benachrichtigungen:
```java
public enum DataDomain { TODO, BUDGET, MEAL }
WidgetUpdateManager.registerWidget(DataDomain.TODO, TaskWidgetProvider::notifyWidgetUpdate);
WidgetUpdateManager.notifyUpdate(context, DataDomain.TODO);
```
Registrierung erfolgt in `WidgetRefreshApp.onCreate()`. Controller (budgetManager, todoManager, etc.) nutzen `notifyUpdate()` statt direkter Widget-Referenzen → verbesserte Testbarkeit.

**Widget-Header-Buttons:** Neben dem App-Titel befinden sich zwei Buttons:
- "+" Button → Öffnet App direkt im Create-Modal (via `ACTION_CREATE_ITEM` Intent → `mainActivity.handleWidgetIntent()`)
- "↻" Button → Refresht Widget-Daten (via `ACTION_REFRESH` Broadcast)

**Completion-Feedback:**
- **App** (`taskList.java`): `animateCompletion()` — Checkbox-Bounce (scale 1.0→1.3→1.0) + Hintergrund-Flash (`completion_flash` → `surface_complete`)
- **Widget** (`TaskWidgetProvider`/`TaskWidgetFactory`): Flash via statischer `flashingSlotId` — bei Completion wird Slot-ID gesetzt, Factory rendert mit `completion_flash`, nach 300ms wird ID gelöscht und Widget neu gerendert

**Meta-Row Badges:** Unterhalb des Task-Titels zeigt `task_meta_row` (LinearLayout) drei optionale Badges:
- Streak: "🔥 X" mit Rarity-Farbe (aus `TaskListData.getStreakRarityColorRes()`)
- Deadline: "Fällig: dd.MM.yyyy" (rot wenn überfällig)
- Remaining: "⏱ X Tage" — aus `trackedItem.remainingTime(today)`

**Background Scheduling:** `DailyPlanningScheduler` registriert AlarmManager-Trigger um 00:00 → `DailyPlanningReceiver` führt aus:
1. `cleanToDo.clean()` — Zwei Phasen: (a) Gestrige Slots auswerten → `trackedItem.update()` mit Slot-Daten + followUp-Tracking, (b) ALLE übrigen Items refreshen → `update(null,...)` für Perioden-Reset, scheduled-Bereinigung, blockedDays-Refresh. Danach alte todoLists aus DB entfernen.
2. `buildToDo.planWeek()` — Neuen 7-Tage-Plan erstellen
3. `scheduleDaily()` — Nächsten Mitternachts-Alarm registrieren
4. `WidgetUpdateManager.notifyUpdate(context, DataDomain.TODO)` — Widget aktualisieren

`BootReceiver` re-registriert den Alarm nach Geräte-Neustart.

## Key Patterns

**trackedItem** is the central entity — Tasks, Goals, and Projects all use this class with `ItemType` enum:
- `TASK` — Individual work units with `minDurationValue/maxDurationValue`, `timePerProgressUnit`, `repetition`, `prefTime`, optional `budgetRequirementCents`, optional `fixedDate`/`fixedTime` (feste Termine), optional `mealPlanId` (Meal-Task-Verknüpfung)
- `GOAL` — Containers for tasks, have `children` list and time budget
- `PROJECT` — Top-level grouping

**Hierarchy:** Project → Goal → Task

**Goal-Darstellung:** Goals haben `goalIcon` (Emoji-String, z.B. "💪") und `goalColor` (Hex-String, z.B. "#FFE53935"). Werden als farbige Header mit Icon in Task-Liste und Widget angezeigt. Editierbar im Create/Edit-Modal (Emoji-Eingabe via System-Tastatur + Farb-Grid mit 10 vordefinierten Farben).

**Streak-Rarity-System (MMO-Style):** Tasks zeigen ein farbiges Streak-Badge ("🔥 35") basierend auf Rarity-Stufen:

| Streak  | Rarity    | Farbe                      |
|---------|-----------|----------------------------|
| 0       | —         | Nichts angezeigt           |
| 1–9     | Common    | Grau `rarity_common`       |
| 10–29   | Uncommon  | Grün `rarity_uncommon`     |
| 30–59   | Rare      | Blau `rarity_rare`         |
| 60–99   | Epic      | Lila `rarity_epic`         |
| 100+    | Legendary | Gold `rarity_legendary`    |

Rarity-Farben in `colors.xml`, Streak-Wert kommt aus `trackedItem.currentStreak`. Logik in `TaskListData.getStreakRarityColorRes()`.

**Repetition Types (RepetitionType enum):**
- `NONE` — Einmalig, keine Wiederholung. Nach Completion: `isCompleted = true` permanent. Builder: `.noRepetition()`. Kann optionales `deadline`-Feld haben (siehe Deadline-Logik).
- `INTERVAL` — "alle X Tage/Wochen" (every X days/weeks)
- `REPS_PER_TIME` — "X mal pro Woche/Monat" (X times per week/month)
- `DAY_OF_TIME` — "jeden Freitag" or "jeden 10." (every Friday / every 10th)

**completeFirst-Modus:** Wenn `completeFirst = true`, werden überfällige Tasks NICHT automatisch zurückgesetzt bis sie erledigt sind:
- Streak bleibt erhalten (kein Reset bei Überfälligkeit)
- Perioden-Reset (isCompleted, completions, progressCurrent) wird übersprungen
- Task bleibt einplanbar und wartet auf Completion
- Helper-Methode `isCompleteFirstBlocked(day)` prüft: `completeFirst=true && overdue(day) > 0 && !isCompleted`
- Nach Completion läuft die normale Perioden-Logik wieder an

**config** (`config.java`) — Hält `Map<DayOfWeek, DaySchedule>` mit Start-/Endzeit pro Wochentag (z.B. Mo 06:00–18:00). Wird von `buildToDo` genutzt um verfügbare Stunden pro Tag zu bestimmen. `DaySchedule` ist eine innere Klasse mit `start`/`end` (`LocalTime`).

**CalendarEvent** (`CalendarEvent.java`) — Java Record: `record CalendarEvent(String title, LocalTime start, LocalTime end)`. `CalendarReader` liest Device-Kalender via `CalendarContract.Instances`, `CalendarProvider` ist ein Functional Interface für testbare Kalender-Abstraktion.

**Repo Interface** (`Repo.java`) — Abstraktion über SQLrepo, ermöglicht testbare Dependencies:
```java
public interface Repo {
    <T> T lookup(String table, Map<String, String> filters, String column);   // raw value
    <T> List<T> lookups(String table, Map<String, String> filters, String column); // raw values
    <T> T fetch(Table<T> table, long id);                                     // entity by ID
    <T> T fetch(Table<T> table, Map<String, String> filters);                 // entity by filter
    <T> List<T> fetchAll(Table<T> table);                                     // all entities
    <T> List<T> fetchAll(Table<T> table, Map<String, String> filters);        // filtered batch fetch
    void write(Object entity);                                                // INSERT or UPDATE
}
```

`lookup`/`lookups` use String table names and return converted primitives. `fetch` uses type-safe `Table<T>` references and returns entity objects. `fetchAll` lädt alle Einträge einer Tabelle oder gefiltert per Map (eliminiert N+1 Query-Pattern). `write` auto-detects entity type.

**Batch-Fetch Pattern:** Statt N+1 Queries (IDs laden → einzeln fetchen) immer `fetchAll(Table, filters)` verwenden:
```java
// FALSCH (N+1):
List<Long> ids = repo.lookups("items", Map.of("is_completed", "0"), "id");
for (Long id : ids) { repo.fetch(Table.ITEMS, id); }

// RICHTIG (Single Query):
List<trackedItem> items = repo.fetchAll(Table.ITEMS, Map.of("is_completed", "0"));
```

**ParseUtils** (`repository/parser/ParseUtils.java`) — Zentrale safe-parsing Utilities für alle Parser:
```java
ParseUtils.safeEnum(AccountType.class, value);    // null statt Exception bei ungültigem Enum
ParseUtils.safeLocalDate(value);                  // null statt Exception bei ungültigem Datum
ParseUtils.safeLocalTime(value);                  // null statt Exception bei ungültiger Zeit
ParseUtils.safeLong(value);                       // null statt Exception
ParseUtils.safeInt(value, defaultValue);          // Fallback bei Fehler
```
**WICHTIG:** Bei allen `Enum.valueOf()` Aufrufen in Parsern `ParseUtils.safeEnum()` verwenden um Crashes bei ungültigen DB-Werten zu vermeiden.

**Table-Referenzen:**
- Task-Management: `Table.ITEMS`, `Table.TODOS`
- Budget: `Table.ACCOUNTS`, `Table.TRANSACTIONS`, `Table.BUDGET_LIMITS`, `Table.IMPORTS`, `Table.CATEGORIES`
- Meal Planning: `Table.HOUSEHOLD_MEMBERS`, `Table.COOKING_PREFERENCES`, `Table.RECIPE_RATINGS`, `Table.INGREDIENTS`, `Table.RECIPES`, `Table.MEAL_PLANS`, `Table.MEAL_SCHEDULES`, `Table.SHOPPING_LIST_ITEMS`, `Table.PANTRY_ITEMS`, `Table.CONSUMPTION_LOGS`, `Table.WEEKLY_FOOD_TARGETS`, `Table.STORE_PACKAGES`

**TaskListData** (`data/TaskListData.java`) — Shared data transformation für App und Widget:
- `DisplayRow` sealed interface mit drei Record-Typen: `GoalHeader`, `TaskItem`, `CalendarEvent`
- `fromEntries(List<TaskEntry>)` transformiert flache TaskEntry-Liste zu DisplayRow-Liste mit eingefügten Goal-Headern
- `getStreakRarityColorRes(int streak)` gibt Rarity-Farb-Resource-ID zurück

**BudgetDisplayData** (`data/BudgetDisplayData.java`) — Shared data transformation für Budget-UI:
- `getCategoryLabel(category)` / `getCategoryIcon(category)` — Deutsche Labels und Emojis für TransactionCategory
- `formatCents(int)` / `formatCentsWithSign(int)` — "1.234,56 EUR" bzw. "+1.234,56 EUR" Format
- `formatDate(LocalDate)` / `formatDateShort(LocalDate)` — "02.02.2026" bzw. "02.02." Format
- `formatYearMonth(String)` — "Februar 2026" aus "2026-02"
- `toYearMonth(LocalDate)` / `toYearMonth(YearMonth)` — Konvertiert zu "2026-02" Format
- `isIncomeCategory(category)` — Prüft ob Kategorie mit "INCOME_" beginnt

**TaskEntry** (`todoManager.java`) — Record für die UI-Datenübergabe:
```java
public record TaskEntry(
    Long slotId,            // TimeSlot ID (zum Abhaken)
    String taskTitle,       // Titel des Tasks
    String taskDescription, // Beschreibung des Tasks
    int slotDuration,       // Berechnete Slot-Dauer in Minuten
    LocalTime start,        // Slot-Startzeit
    LocalTime end,          // Slot-Endzeit
    boolean completed,      // Checkbox-State / "heute erledigt" bei Progress
    String goalTitle,       // Titel des übergeordneten Goals
    Long goalSlotId,        // Goal-Slot ID (für Goal-Completion-Check)
    boolean isCalendarEvent,// Kalender-Termin (nicht abhakbar)
    LocalTime workStart,    // Timer gestartet? (null = nicht gestartet)
    LocalDate deadline,     // Fälligkeitsdatum (null = keine Deadline)
    String goalIcon,        // Emoji-Icon des Goals (z.B. "💪")
    String goalColor,       // Hex-Farbcode des Goals (z.B. "#FFE53935")
    int currentStreak,      // Aktuelle Streak-Länge des Tasks
    int remainingDays,      // Verbleibende Tage (Deadline/Periode)
    int progressCurrent,    // Angezeigter Progress (berechnet je nach progressPerRep-Modus)
    int progressTarget,     // Ziel-Fortschritt (0 = kein Tracking)
    String progressUnit     // Einheit (z.B. "Seiten", nullable)
)
```

**Budget UI Records** (`budgetManager.java`) — Records für die Budget-UI-Datenübergabe:
- `AccountEntry(accountId, name, icon, color, type, balanceCents, formatted, includeInTotal)`
- `TransactionEntry(id, accountId, accountName, amountCents, formatted, isIncome, date, dateFormatted, category, categoryLabel, categoryIcon, description, payee, isRecurring)`
- `BudgetEntry(limitId, category, categoryLabel, categoryIcon, yearMonth, limitCents, spentCents, rolloverCents, remainingCents, percentUsed, isOverBudget, formattedLimit, formattedSpent, formattedRemaining)`
- `BudgetSummary(totalBalanceCents, monthlyIncomeCents, monthlyExpensesCents, monthlyNetCents, formattedTotal, formattedIncome, formattedExpenses, formattedNet)`
- `CategoryOption(category, label, icon, isIncome)` — Für Dropdown/Spinner

## Testing

**Gradle Tests:** Test-Infrastruktur konfiguriert (`test/` sourceSets, JUnit 4.13.2, Robolectric 4.14.1) — aktuell keine Tests vorhanden. Kommando: `./gradlew testDebugUnitTest`.

**ADB-basiertes Debugging:** Scheduling und Pläne werden via `DebugBroadcastReceiver` getestet (siehe Debugging-Sektion). Zeigt adjustedPrio-Scores, Budget-Info, Hierarchie.

## Scheduling Algorithm

**buildToDo** — Globale Slot-Bewertung über alle 7 Tage gleichzeitig:
1. Load/create 7-day plans, sync calendar events
2. Loop: `getItems()` + `buildChains()` → `tryMatchChain(highest)` globally across all days
3. Verdrängungslogik: Higher-priority items replace lower-priority ones
4. Persist to DB

**Unified Chaining System:** Zwei Felder steuern alle Verkettungen:
- `predecessor: Long` — Welcher Task muss vorher kommen?
- `predecessorDelay: int` — Minuten Wartezeit (0 = konsekutiv, >0 = verzögert)

| `predecessorDelay` | Verhalten |
|-------------------|-----------|
| `0` | Same-Day-Chain: Items werden konsekutiv am selben Tag platziert |
| `>0` | Delayed-Chain: Item wartet X Minuten nach Predecessor-Completion |

**Scheduling-Logik:**
- `buildChains()` gruppiert nur Items mit `delay=0` zu `TaskChain` Records
- Items mit `delay>0` werden einzeln behandelt, gefiltert via `isPredecessorReady()`
- `getEarliestStart()` berechnet frühesten Slot basierend auf `predecessor.lastCompletion + delay`
- Kette bekommt die **Summe** der Prioritäten aller Mitglieder (nicht max)
- `tryMatchChain()` evaluiert alle **(Startpunkt × Chain-Länge)**-Kombinationen über alle 7 Tage
- **Atomare Chain-Verdrängung:** `TimeSlot.chainId` trackt Zugehörigkeit (nur bei `delay=0`)
- Builder-Convenience: `.chainAfter(id)` = delay 0, `.delayAfter(id, minutes)` = verzögert

**Testability:** `buildToDo` nimmt `Repo`-Interface und `CalendarProvider` (Functional Interface) als Dependencies — ermöglicht Unit-Tests ohne Android-Kontext.

**Priority** basiert auf `Priority` enum (CRITICAL: 100000, HIGH: 400, MODERATE: 200, LOW: 100), plus Overdue-Bonus. PrefTime-Matching via logarithmische Score-Funktion in `tryMatchChain()`. FollowUp-Boost via `scoreFollow()` für historische Muster.

**Prio-Boost (`buildChains()`):** Einheitliche Formel über `item.work(today)` und `item.remainingTime(today)`:
```
basePrio = priority.value + (priority.value × overdue × 0.5)
if (überfällige Deadline): basePrio × 3.0
else if (work > 0 && time > 0): basePrio × min(2.0, 1.0 + work/time)
```
- `work()` = kombinierte verbleibende Arbeit (reps × progress-units, je min 1). Gibt 0 wenn weder Reps/Progress/Deadline aktiv.
- `remainingTime()` = verbleibende Tage (Deadline → Tage bis Deadline; REPS_PER_TIME → Tage in Periode minus eingeplante; Progress-only → 7 Tage Fallback).
- Überfällige Deadline (NONE-Tasks): fester 3.0x Boost, überschreibt die work/time-Formel.

**Progress-Tracking:** Items können `progressCurrent`/`progressTarget`/`progressUnit` haben (z.B. 2/6 Seiten). Zwei Modi:
- **Global** (`progressPerRep=false`, Standard): Progress akkumuliert über Slots hinweg (z.B. "6 Seiten Hausarbeit" — Tag 1: 2 Seiten, Tag 2: weiter bei 2/6)
- **Pro Slot** (`progressPerRep=true`): Jeder Slot startet bei 0 (z.B. "3x pro Woche 5 Liegestütze" — jeder der 3 Slots zeigt 0/5 → 5/5)

**Perioden-Reset:** ALLE wiederkehrenden Tasks mit Progress-Tracking bekommen `progressCurrent=0` am Perioden-Ende (nicht nur progressPerRep). Alter Wert wird in `progressLastPeriod` gesichert.

**Persistenz-Architektur:** Progress-Änderungen werden NICHT direkt im Item gespeichert, sondern im `TimeSlot.progressDelta`:
1. User drückt [+] → `todoManager.incrementProgress()` setzt `slot.progressDelta++` und `slot.completed = true`
2. UI zeigt: Bei `progressPerRep=false`: `item.progressCurrent + slot.progressDelta`. Bei `progressPerRep=true`: nur `slot.progressDelta`
3. Um Mitternacht: `cleanToDo` → `item.update(..., progressDelta, ...)` wendet Delta auf Item an (für Statistik)

**Progress-UI:** Tasks mit Progress zeigen statt Checkbox einen kompakten Stepper `[-] 3/6 [+]`. Hintergrund wird grün bei Fortschritt ("heute erledigt"), Strikethrough erst bei vollem Progress.

**Zeit-pro-Einheit Tracking:** `timePerProgressUnit` speichert den Durchschnitt Minuten pro Fortschrittseinheit (gemessen via Timer oder User-Schätzung). Einheitliche Logik für alle Tasks:
- Mit Progress-Tracking: `Zeit / progressDelta = Min/Einheit`
- Ohne Progress-Tracking: `Zeit / 1 = Min/Completion` (jede Completion = 1 Einheit)
- `getEstimatedTime()` berechnet geschätzte Zeit: `timePerProgressUnit × verbleibende Einheiten` (0 wenn keine Messung vorhanden)

**Flexible Min/Max Duration:** Jedes Item kann separate Min- und Max-Grenzen haben, jeweils in Zeit oder Fortschritts-Einheiten:
- `minDurationValue/Unit` — Minimum pro Tag (z.B. "min 2 Seiten" oder "min 30 min")
- `maxDurationValue/Unit` — Maximum pro Tag (z.B. "max 4 Seiten" oder "max 60 min")
- `DurationUnit` enum: `MINUTES` oder `PROGRESS_UNITS` — Einheit für Min/Max-Dauer
- Builder-Convenience-Methoden:
  - `.minMinutes(30)`, `.maxMinutes(60)` — Zeit-basierte Grenzen
  - `.minProgress(2)`, `.maxProgress(4)` — Progress-basierte Grenzen
  - `.minDuration(value, unit)`, `.maxDuration(value, unit)` — Generische Methoden
  - `.budgetRequirement(cents)`, `.budgetAccount(id)`, `.budgetCategory(cat)`
  - `.prefTime(LocalTime)` — Bevorzugte Startzeit (wird vom System gelernt, aber initial setzbar)
  - `.delayAfter(predecessorId, minutes)` — Verzögerte Verkettung
  - `.completeFirst(boolean)` — Erst erledigen vor Reset
- `getMinDurationMinutes()` / `getMaxDurationMinutes()` — Konvertieren zu Minuten (bei PROGRESS_UNITS: Wert × timePerProgressUnit)
- `getSlotDuration()` — Clamps estimated auf [min, max], Fallback auf min oder 30 min wenn keine Schätzung
- Wird von `buildToDo` via `getSlotDuration()` für Slot-Sizing und Scheduling genutzt

Offener Progress (progressCurrent < progressTarget) bewirkt:
- Prio-Boost über `work()/remainingTime()` — mehr verbleibende Einheiten = höherer Boost, gedeckelt bei 2.0x
- Periodenblockierung wird übersprungen (wie REPS_PER_TIME), damit das Item täglich einplanbar bleibt
- `isCompleted` wird auf `false` gehalten solange Progress offen ist

**Deadline-Logik:** Einmalige Tasks (`RepetitionType.NONE`) können ein optionales `deadline`-Feld (`LocalDate`) haben. Wirkung:
- Prio-Boost über `work()/remainingTime()`: je näher die Deadline, desto höher der Multiplier (cap 2.0x). Bei überfälliger Deadline: fester 3.0x Boost.
- Periodenblockierung wird übersprungen (wie REPS_PER_TIME), damit das Item täglich einplanbar bleibt bis zur Erledigung.
- Anzeige in Task-Liste und Widget als "Fällig: dd.MM.yyyy", rot wenn überfällig.
- Edit-UI: DatePickerDialog, nur sichtbar bei TASK + RepetitionType.NONE.

**Feste Termine (Fixed Appointments):** Einmalige Tasks können zusätzlich zu oder anstelle von `deadline` einen festen Termin haben:
- `fixedDate` (`LocalDate`) — Muss an diesem Tag eingeplant werden
- `fixedTime` (`LocalTime`) — Muss zu dieser Uhrzeit starten
- Beide Felder müssen gemeinsam gesetzt sein oder beide null
- Utility: `isFixedAppointment()` prüft ob beide Felder gesetzt sind
- Builder: `.fixedAppointment("2026-02-10", "14:00")` oder `.fixedDate(...).fixedTime(...)`

**Scheduling-Verhalten fester Termine:**
- Werden in `buildChains()` als hochpriorisierte Einzelketten (Prio 10.000.000) behandelt
- `getItems()` überspringt blockedDays-Check — feste Termine MÜSSEN eingeplant werden
- `tryMatchChain()` evaluiert NUR den exakten Tag und Zeit (keine Alternativen)
- Können NICHT von anderen Tasks verdrängt werden (wie Calendar-Events)
- Können andere feste Termine NICHT verdrängen (Konflikt → erster gewinnt)
- SIND abhakbar (normale Task-Completion-Logik, anders als Calendar-Events)
- Edit-UI: DatePicker + TimePicker (24h), nur sichtbar bei TASK + RepetitionType.NONE

**Scheduling-Konflikt-Tracking:** Wenn feste Termine nicht eingeplant werden können, wird ein `SchedulingConflict` Record erstellt:
```java
public record SchedulingConflict(Long itemId, String itemTitle, LocalDate conflictDate, String reason) {}
// reason: "DAY_BOUNDS" | "CALENDAR_OVERLAP" | "FIXED_OVERLAP"
```
`buildToDo.getConflicts()` liefert alle Konflikte des letzten `planWeek()`-Durchlaufs. `todoManager` leitet diese via `TodoListener.onSchedulingConflicts()` an die UI weiter.

**Skip conditions in getItems():** `item.blockedDays` enthält den Tag, oder (nur Goal → Project) `parent.blockedDays` enthält den Tag, oder (bei Tasks mit `budgetRequirementCents > 0`) das freie Budget ist nicht ausreichend.

**Budget-Aware Scheduling:** Tasks können Budget-Anforderungen haben:
- `budgetRequirementCents` — Kosten in Cents (0 = kein Budget erforderlich)
- `budgetAccountId` — Spezifisches Konto (null = beliebiges aktives Konto)
- `budgetCategory` — Kategorie für die Auto-Transaction bei Completion

`buildToDo.getFreeBudgetCents()` berechnet verfügbares Budget:
1. Summe aller aktiven Konten mit `includeInTotal` (oder spezifisches Konto)
2. Minus: Wiederkehrende Ausgaben der nächsten 7 Tage (`nextDue` im Zeitfenster)
3. Minus: Bereits im Scheduling-Durchlauf committed (`committedBudgetCents`)

**Budget-Caching:** `cachedBaseBudget` wird am Anfang von `planWeek()` einmalig berechnet und für alle Budget-Tasks ohne spezifisches Konto wiederverwendet. Am Ende von `planWeek()` wird der Cache zurückgesetzt.

Nach erfolgreicher Platzierung in `assignChain()` wird `committedBudgetCents` erhöht, um Race Conditions zu vermeiden.

**Auto-Transaction bei Completion:** `todoManager.completeSlot()` erstellt automatisch eine Transaction für Budget-Tasks:
- Betrag: `-item.budgetRequirementCents` (Ausgabe)
- Konto: `item.budgetAccountId` oder erstes aktives mit `includeInTotal`
- `isConfirmed = false` — User muss in Budget-View bestätigen
- Konto-Saldo wird sofort aktualisiert
- **Atomarität:** Alle DB-Writes (Slot, Transaction, Account) werden in einer SQLite-Transaction gewrappt (`db.beginTransaction()` / `setTransactionSuccessful()` / `endTransaction()`)
- Meal-Completion (`mealManager.completeMeal()`) wird außerhalb der Transaction ausgeführt und mit try-catch geschützt

**Task vs. Goal Slot-Fitting:** Tasks müssen komplett in einen Slot passen (werden übersprungen wenn zu wenig Zeit). Goals dürfen partiell eingeplant werden (slotCoverage reduziert die Prio proportional).

**trackedItem.update()** — Zentraler Entry-Point für Tagesabschluss-Logik. Signatur:
```java
void update(Boolean completed, LocalTime workStart, LocalTime workEnd,
            Long previousItemId, Integer progressDelta, LocalDate day, Repo repo)
```
- `completed=true/false` → Slot-Daten auswerten (completions, prefTime, timePerProgressUnit, streak, followUps)
- `progressDelta` → Fortschrittsänderung aus dem Slot (wird von `incrementProgress`/`decrementProgress` im Slot gesammelt und erst hier angewendet). Auch für Zeit-pro-Einheit Berechnung genutzt.
- `completed=null` → Nur "immer"-Updates (Refresh ohne Slot-Daten)
- "Immer"-Updates (laufen IMMER, auch bei null): Perioden-Reset (`isCompleted` → false wenn neue Periode begonnen), scheduled bereinigen (vergangene Daten entfernen), blockedDays neu berechnen. Bei ALLEN Items mit Progress-Tracking: `progressCurrent` reset auf 0 mit Backup in `progressLastPeriod`.
- `followUps`: Wenn `previousItemId != null`, wird `followUps.merge(previousItemId, 1, Integer::sum)` aufgerufen — trackt welche Items direkt vor diesem erledigt wurden.

**FollowUp-System:** Drei Mechanismen für Task-Reihenfolge:
- **predecessor** (`trackedItem.predecessor`): Harte Constraint "dieser Task soll nach Task X kommen". UI: Spinner im Edit-Modal. **Scheduling:** Items mit `predecessorDelay=0` werden in `buildChains()` zu Ketten gruppiert (Same-Day). Items mit `predecessorDelay>0` warten auf Predecessor-Completion + Delay (Delayed-Chains).
- **Actual Completion Tracking** (`TimeSlot.previousCompletedItemId`): Trackt die tatsächliche Completion-Reihenfolge (nicht die geplante!). `todoManager` speichert pro Goal-Slot, welcher Task zuletzt erledigt wurde (`lastCompletedByParent`). Bei Completion wird `previousCompletedItemId` im Slot gesetzt. `cleanToDo` liest aus dem Slot statt aus der Iterations-Reihenfolge.
- **FollowUp Prio-Boost** (`trackedItem.scoreFollow(predecessorId)`): Tasks die historisch oft nach bestimmten anderen Tasks erledigt wurden, bekommen einen Planungs-Boost. Berechnung: Wenn Task A mindestens 5x nach Task B erledigt wurde UND das mindestens 5% aller FollowUps von A ausmacht, bekommt A einen Boost = Anteil (z.B. 60% der Follows von B → 60% Boost auf adjustedPrio). Angewendet in `buildToDo.tryMatchChain()` via `findPrecedingItem()`.

**blockedDays-Mechanismus:** Jedes Item hat seine **eigenen** blockedDays. `trackedItem.getBlockedDays()` berechnet aus zwei Quellen:
1. Cooldown-Fenster VOR und NACH `lastCompletion` und jedem `scheduled`-Datum (±N Tage)
2. Alle Tage zwischen `lastCompletion` und `calcNextRepetition()` (NICHT für REPS_PER_TIME, NICHT für Items mit offenem Progress, und NICHT für Items mit Deadline, da diese mehrfach pro Periode einplanbar sind)

`blockedDays` wird automatisch neu berechnet in:
- `update()` — nach Tagesabschluss
- `schedule()` — wenn Item eingeplant wird (+ propagiert scheduled-Datum zum Parent und berechnet Parent-blockedDays neu)
- `unPlan()` — wenn Item verdrängt wird

**Wichtig:** Goals erben NICHT die blockedDays ihres Parents. Stattdessen prüfen `getItems()` und `tryMatch()` separat `parent.blockedDays` (nur für Goal → Project).

## Database

SQLite mit drei Tabellen-Gruppen:

**Task-Management:**
- `items` — All tracked items (Tasks, Goals, Projects)
- `config_schedules` — Day-of-week keyed schedule (start_time, end_time)
- `todos` — Generated daily plans (id, date, start_time, end_time)
- `time_slots` — Nested TimeSlots (todo_id FK, parent_slot_id FK for hierarchy, item_id FK, completed, work_start, work_end, progress_delta, previous_completed_item_id, chain_id)

**Budget-Tracking:**
- `accounts` — Finanzkonten (Girokonto, Sparkonto, Bargeld, Kreditkarte)
- `transactions` — Buchungen (Income/Expense, einmalig/recurring in einer Tabelle)
- `budget_limits` — Monatliche Budgetlimits pro Kategorie (UNIQUE category+year_month)
- `imports` — Bank-Statement-Uploads via Claude API (file_hash für Duplikat-Check, claude_model/tokens für Tracking, status PENDING→PROCESSING→COMPLETED/FAILED)
- `categories` — Transaktionskategorien (name, icon, isIncome, sortOrder)

**Meal Planning:**
- `household_members` — Haushaltsmitglieder mit BMR/TDEE-Berechnung (name, birthDate, gender, heightCm, weightKg, activityLevel)
- `cooking_preferences` — Koch-Präferenzen (maxCookingPerWeek, allowedCookingDays, preferredMealTypes)
- `recipe_ratings` — Bewertungen pro Mitglied (recipeId, memberId, rating 1-5)
- `ingredients` — Zutaten mit FoodGroup-Enum und Nährwerten pro 100g (name, foodGroup, unit, gramsPerUnit, calories, protein, carbs, fat, fiber)
- `recipes` — Rezepte (name, description, prepEffort, mealTypes, servings, ingredients als pipe-separated String)
- `meal_plans` — Wochenplan-Einträge (date, mealType, recipeId, servings, isCompleted)
- `meal_schedules` — Mahlzeiten-Kalender 7×4 Grid (day_of_week, meal_type, scheduled_time, is_enabled)
- `shopping_list_items` — Einkaufslisteneinträge (ingredientId, amount, unit, needed_amount, excess_amount, suggested_store, isPurchased)
- `pantry_items` — Vorratsartikel (ingredientId, amount, unit, expiryDate, storageLocation)
- `consumption_logs` — Verbrauchsprotokoll für Nährwert-Tracking (date, memberId, recipeId/ingredientId, calories, protein, carbs, fat)
- `weekly_food_targets` — Berechneter Wochenbedarf pro FoodGroup (weekKey, grainGrams, vegetableGrams, fruitGrams, ...)

**Relations in items:** `parent` (single ID), `children` (comma-separated IDs), `followups` ("id:count" pairs, e.g. "5:3,8:1"), `scheduled` (comma-separated ISO dates), `blocked_days` (comma-separated ISO dates, computed), `required_predecessor` (single ID, optionale Vorgänger-Constraint).

**Feste Termine in items:** `fixed_date` (TEXT, ISO date), `fixed_time` (TEXT, ISO time). Beide Felder müssen gemeinsam gesetzt sein oder beide null. Nur für einmalige Tasks (RepetitionType.NONE) relevant.

**Darstellung in items:** `goal_icon` (TEXT, Emoji), `goal_color` (TEXT, Hex-Farbcode). Nur für Goals relevant.

**Budget in items:** `budget_requirement_cents` (INTEGER, 0=kein Budget), `budget_account_id` (INTEGER FK, null=beliebig), `budget_category` (TEXT). Nur für Tasks relevant.

**Meal-Tasks in items:** `meal_plan_id` (INTEGER FK, null=kein Meal-Task). Verknüpft Meal-Tasks mit MealPlan-Einträgen. Bei Completion wird `mealManager.completeMeal()` aufgerufen.

**Budget-Entities:**
- `Account` — AccountType enum: CHECKING, SAVINGS, CASH, CREDIT. Felder: name, icon, color, initialBalanceCents, currentBalanceCents, includeInTotal
- `Transaction` — Unified für alle Transaktionstypen:
  - Typ-Flags: `isIncome`, `isRecurring`
  - `categoryId` → FK zu `categories` Tabelle (user-controlled, nicht hardcoded)
  - `RecurringType` enum: MONTHLY_DAY, MONTHLY_LAST, WEEKLY, INTERVAL
  - `RepUnits` enum: DAY (1), WEEK (7), MONTH (30) — für Intervall-basierte Wiederholungen
  - Varianz-Tracking: amountMinCents, amountMaxCents, amountAvgCents, occurrenceCount
  - `parentRecurringId` — Links zu wiederkehrendem Template (für Pattern-Detection)
  - Utility: `calcNextOccurrence(from)` berechnet nächstes Fälligkeitsdatum, `updateStats(amount)` aktualisiert Varianz-Statistiken
  - Builder-Pattern: `new Transaction.Builder(accountId, amountCents, date, categoryId).monthlyOnDay(15).build()`
- `Category` — User-controlled Transaktionskategorien:
  - Felder: name, icon (Emoji), color (Hex), isIncome, isBuiltIn, sortOrder, isActive
  - Built-in Kategorien werden via `seedTestData.java` angelegt (~20 Default-Kategorien)
  - Einnahmen: Gehalt, Bonus, Erstattung, Sonstiges Einkommen
  - Ausgaben: Miete, Nebenkosten, Lebensmittel, Restaurant, ÖPNV, Auto, Gesundheit, etc.
  - Claude-Prompt wird dynamisch mit User-Kategorien generiert (`ClaudePrompts.buildSystemPrompt(categories)`)
- `BudgetLimit` — Pro Kategorie + yearMonth (UNIQUE Constraint). Felder: limitCents, spentCents, rolloverCents
- `Import` — Kontoauszug-Import via Claude API. Felder: fileHash (SHA256), periodStart/End, Statistiken (total/new/autoCategorized), Claude-Metadata (model, promptTokens, responseTokens, processingTimeMs), Status-Workflow (PENDING→PROCESSING→COMPLETED/FAILED)

**MealType** (`entities/MealType.java`) — **WICHTIG:** Zentraler Enum für Mahlzeit-Typen:
```java
public enum MealType {
    BREAKFAST("Frühstück", "🍳"),
    LUNCH("Mittagessen", "🍽️"),
    DINNER("Abendessen", "🍲"),
    SNACK("Snack", "🍎");

    public final String label;
    public final String icon;
}
```
**IMMER** `entities.MealType` verwenden — NICHT `Recipe.MealType`, `MealPlan.MealType` oder `MealSchedule.MealType` (diese existieren nicht!). Bei Compile-Fehlern: Import `import entities.MealType;` prüfen.

**Meal-Planning-Entities:**
- `HouseholdMember` — Gender enum (MALE, FEMALE, OTHER), ActivityLevel enum (SEDENTARY, LIGHT, MODERATE, ACTIVE, VERY_ACTIVE) mit factor und label. Methoden: `calculateBMR()` (Mifflin-St Jeor), `calculateTDEE()`, `getAge()`, `getAgeFactor()`, `getActivityFoodFactor()`, `getFoodFactor()` (für DGE-Skalierung)
- `CookingPreferences` — Felder: maxCookingPerWeek, allowedCookingDays (Set<DayOfWeek>), preferredMealTypes (Set<MealType>)
- `RecipeRating` — Bewertung 1-5 Sterne pro Mitglied und Rezept
- `Ingredient` — FoodGroup enum mit DGE-Wochen-Empfehlungen (GRAIN: 1750g, VEGETABLE: 2800g, FRUIT: 1750g, DAIRY: 1750g, MEAT: 400g, FISH: 140g, EGG: 210g, FAT: 210g, LEGUME: 560g, NUT: 175g, POTATO: 1400g, OTHER: 0g). Nährwerte pro 100g gespeichert. `isWholeUnit` (nur ganze Einheiten kaufbar), `isPerishable` (sollte bis Wochenende verbraucht werden).
- `Recipe` — PrepEffort enum (QUICK, MEDIUM, ELABORATE). RecipeIngredient record für Zutaten-Referenz. Ingredients als pipe-separated String: "id|name|amount|unit;...". Skalierungsfelder: `minServings`, `maxServings`, `ScalingPrecision` enum (EXACT für Backen, ROUGH für Pfannengerichte, NONE für Eintöpfe).
- `StorePackage` — Verknüpft Zutaten mit Ladengrößen (ingredientId, storeName, packageSize, unit, priceCents)
- `MealPlan` — Wochenplan-Einträge mit date, mealType, recipeId, servings, isCompleted
- `MealSchedule` — Mahlzeiten-Kalender: 7 Tage × 4 Mahlzeiten = 28 Einträge. Felder: dayOfWeek, mealType (→ MealType enum), scheduledTime (LocalTime), isEnabled
- `ShoppingListItem` — Einkaufslisteneinträge mit `neededAmount` (exakt benötigt), `excessAmount` (Überschuss durch Packungsgrößen), `suggestedStore` (empfohlener Laden), `isPurchased`-Tracking
- `PantryItem` — StorageLocation enum (PANTRY, FRIDGE, FREEZER). Felder: expiryDate, purchaseDate
- `ConsumptionLog` — Nährwert-Protokoll: entweder rezeptbasiert (recipeId + servingsConsumed) oder Einzelzutat (ingredientId + amount)
- `WeeklyFoodTarget` — Pro FoodGroup: Zielmengen (*Grams) und geplante Mengen (*Planned) für Wochenfortschritt

**Beträge in Cents:** Alle Geldbeträge als `int` in Cents gespeichert (z.B. 1250 = 12.50 EUR) um Floating-Point-Probleme zu vermeiden.

**DB-Strategie (v1.0.0+):** Production-Mode mit Migrations-Support.

**Migration-System:**
- `DB_VERSION` in `constants.java` (aktuell: **31**)
- `MigrationManager.java` verwaltet Backups und Migrationen
- `SQLrepo.onUpgrade()` ruft `MigrationManager.migrate()` auf
- Backup wird VOR Migration erstellt: `getFilesDir()/backups/backup_vX_timestamp.db`
- Max 5 Backups (Rotation in MigrationManager)
- `_migrations` Tabelle trackt angewendete Migrationen

**Konsolidierte Migrationen:**
- v20–v29: Fall-through zu v30 (Konsolidierung)
- v30: `migrateV30_SchemaConsolidation()` — Fügt alle neuen Spalten hinzu (fixed_date/time, predecessor/delay, budget, meal_plan_id, etc.) + `createPerformanceIndexes()`
- v31: `migrateV31_ProductionCleanup()` — Bereinigt Testdaten, behält Referenzdaten (categories, ingredients, config_schedules)

**Performance-Indexes** (erstellt in v30):
```sql
idx_items_budget  ON items(budget_requirement_cents) WHERE budget_requirement_cents > 0
idx_items_fixed   ON items(fixed_date) WHERE fixed_date IS NOT NULL
idx_items_meal    ON items(meal_plan_id) WHERE meal_plan_id IS NOT NULL
idx_items_open    ON items(is_completed, type) WHERE is_completed = 0
```

**Bei Schema-Änderungen:**
1. `DB_VERSION` in `constants.java` hochzählen
2. Neuen `case` in `MigrationManager.runMigration()` hinzufügen:
   ```java
   case 32:
       db.execSQL("ALTER TABLE items ADD COLUMN new_field TEXT");
       break;
   ```
3. Schema in `SQLrepo.onCreate()` für Neuinstallationen anpassen
4. Helper: `addColumnIfNotExists(db, table, column, type)` prüft `PRAGMA table_info` vor `ALTER TABLE`

**Referenzdaten:** `categories`, `ingredients`, `config_schedules` werden bei Migration beibehalten.

**seedTestData.java:** Wird NICHT mehr automatisch aufgerufen — bleibt für Entwicklung/Debugging verfügbar.

## Settings UI

**Overflow-Menü (⋮)** rechts in der Tab-Bar (`activity_main.xml`). Click-Handler in `mainActivity.buildUI()` öffnet `SettingsManager`.

**SettingsManager** (`controller/SettingsManager.java`) bietet:
- Backup wiederherstellen (Liste verfügbarer Backups, formatierte Anzeige)
- Manuelles Backup erstellen
- Factory Reset (alle Daten löschen mit Sicherheitsbackup)
- Über AutoSecretary (Version + DB-Schema anzeigen)

Bei Datenänderung (Restore/Reset) wird `Activity.recreate()` aufgerufen.

## Auto-Update System

GitHub dient als CDN. `release/version.txt` enthält den aktuellen Integer-versionCode, `release/AutoSecretary.apk` die aktuelle APK. Repository: `ThonkTank/AI-Secretary`.

`updateChecker.java` prüft beim App-Start → fetcht `version.txt` → vergleicht mit lokalem `versionCode` → bei neuer Version: Dialog → Download → FileProvider → System-Installer. Die UI wird erst nach abgeschlossenem Update-Check aufgebaut (Callback-Pattern).

## Language

German documentation, comments and variable names are preferred.

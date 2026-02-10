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

## Critical Rules

- **SQLrepo:** IMMER `SQLrepo.getInstance(context)` — NIEMALS `new SQLrepo(context)`
- **MealType:** IMMER `import entities.MealType` — NICHT `Recipe.MealType` etc. (existieren nicht)
- **Enum-Parsing:** IMMER `ParseUtils.safeEnum()` statt `Enum.valueOf()` in Parsern
- **Batch-Fetch:** IMMER `fetchAll(Table, filters)` statt N+1 (IDs laden → einzeln fetchen)
- **Geldbeträge:** IMMER `int` in Cents (1250 = 12.50 EUR), keine Floats
- **assemble pushed automatisch:** `assemble` → `copyToRelease` (APK + version.txt inkrementieren) → `pushToGitHub`. Für Syntax-Check ohne Push: `compileDebugJavaWithJavac`
- **Nach jeder Aufgabe:** `./gradlew assemble` ausführen (baut APK, inkrementiert Version, pushed zu GitHub für Auto-Update)
- **Kein Legacy-Support im Code:** Es gibt keine bestehenden Nutzerdaten — Parser, Entities und Business-Logik dürfen IMMER vom aktuellen Format ausgehen. KEIN Backward-Compat-Code (alte Enum-Namen, veraltete Feld-Formate, etc.). Der EINZIGE Legacy-Support-Mechanismus sind DB-Migrationen (`MigrationManager`), die Schema-Änderungen nach App-Updates durchführen.

**Typischer Workflow:** Code ändern → `compileDebugJavaWithJavac` (Fehler prüfen) → Fehler fixen → `assemble` (Release).

## Code Style & Eleganz-Regeln

Konkrete Arbeitsweisen für saubere Implementierungen:

**Parsing aus EditText — IMMER `ViewHelper.parseInt()` / `ViewHelper.parseDouble()` verwenden:**
```java
// RICHTIG:
int prepTime = parseInt(inputPrepTime, 0);
double amount = parseDouble(inputAmount, 100.0);

// FALSCH (5 Zeilen Boilerplate):
int prepTime = 0;
try { String s = input.getText().toString().trim();
    if (!s.isEmpty()) prepTime = Integer.parseInt(s);
} catch (NumberFormatException ignored) {}
```

**Enum ↔ Spinner-Index — `ordinal()` statt manuelles Switch-Mapping:**
```java
// RICHTIG:
spinnerMealType.setSelection(recipe.mealType.ordinal());
MealType type = MealType.values()[spinnerMealType.getSelectedItemPosition()];

// FALSCH (redundantes Mapping das aus-dem-Sync geraten kann):
private int getMealTypeIndex(MealType type) {
    return switch (type) { case BREAKFAST -> 0; case LUNCH -> 1; ... };
}
```
Gilt nur wenn Spinner-Reihenfolge = Enum-Reihenfolge (Standard in diesem Projekt).

**Ressourcen-Management — IMMER try-with-resources für Streams:**
```java
// RICHTIG:
try (InputStream is = getContentResolver().openInputStream(uri)) {
    byte[] bytes = is.readAllBytes();
}

// FALSCH:
InputStream is = getContentResolver().openInputStream(uri);
byte[] bytes = is.readAllBytes();
is.close();
```

**Farb-Alpha — `ColorUtils.setAlphaComponent()` statt Bitmanipulation:**
```java
// RICHTIG:
import androidx.core.graphics.ColorUtils;
int semiTransparent = ColorUtils.setAlphaComponent(color, 0x30);

// FALSCH (unleserlich):
int semiTransparent = (color & 0x00FFFFFF) | 0x30000000;
```

**Tab-Indizes — IMMER benannte Konstanten, keine Magic Numbers:**
```java
// RICHTIG:
private static final int SUB_TAB_WEEK = 0;
private static final int SUB_TAB_RECIPES = 1;
switch (currentTab) { case SUB_TAB_WEEK -> ... }

// FALSCH:
switch (currentTab) { case 0 -> ... case 1 -> ... }
```

**Dead Code vermeiden — keine doppelten Zuweisungen vor Methodenaufrufen die dasselbe tun:**
```java
// RICHTIG:
updateCategorySpinner();  // setzt categoriesList intern

// FALSCH:
categoriesList = manager.provideCategories().stream().filter(...).toList();
updateCategorySpinner();  // überschreibt categoriesList sofort wieder
```

**Fehler-Fallbacks — bei Parse-Fehlern konsistenten Zustand herstellen, nicht stillschweigend ignorieren:**
```java
// RICHTIG:
try { int color = Color.parseColor(hex); applyColor(card, color); }
catch (IllegalArgumentException e) { applyDefaultColor(card); }

// FALSCH (Card in inkonsistentem Zustand):
try { ... applyColor(card, color); }
catch (IllegalArgumentException ignored) {}
```

**PrefSlot-Konstruktion — IMMER Factory-Methoden, NIEMALS `new PrefSlot()`:**
```java
// RICHTIG:
PrefSlot.weekly(DayOfWeek.MONDAY, LocalTime.of(9, 0));
PrefSlot.monthly(15, LocalTime.of(14, 0));
PrefSlot.of(dayKey, time, count, monthly);  // Parser/Deserialisierung

// FALSCH (keine dayKey-Validierung):
new TrackedItem.PrefSlot(1, LocalTime.of(9, 0), 0);
```

**Enum-Vergleiche — IMMER typsicher, NIEMALS über String-Label:**
```java
// RICHTIG (wenn Record MealType-Feld hat):
if (entry.mealType() == MealType.BREAKFAST) { ... }

// FALSCH (fragil, mehrere String-Formate nötig):
if (entry.mealType().equals(type.label)) { ... }
```

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
adb shell am start -n com.autosecretary/activities.inApp.MainActivity
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

**Flat package names:** Source files use direct package names (e.g. `package activities.inApp;`, `package entities;`) — there is no root `com.autosecretary` prefix in the source. The namespace `com.autosecretary` only affects the generated `R` class (`import com.autosecretary.R;`) and manifest merging. Tab-Subdirectories verwenden eigene Sub-Packages: `activities.inApp.tasksTab`, `activities.inApp.budgetTab`, `activities.inApp.ernaehrungTab`.

**Dependencies:** `androidx.core:core:1.12.0`, `coreLibraryDesugaring` (desugar_jdk_libs 2.1.4). Test: JUnit 4.13.2, Robolectric 4.14.1, `androidx.test:core:1.6.1`.

**Versionierung:**
- **versionCode:** Aus `release/version.txt` gelesen und automatisch inkrementiert bei jedem `assemble`
- **versionName:** Semantisch `$major.$minor.$patch` in `build.gradle.kts` (aktuell 1.0.0)
- `copyToRelease` schreibt nur den neuen versionCode zurück — bei reinem `compileDebugJavaWithJavac` bleibt version.txt unverändert

**Konstanten** (`data/Constants.java`):
```java
DB_NAME = "autosecretary.db"
DB_VERSION = 34  // Meal-Type auf Items, Item-ID auf MealPlans
PREF_NAME = "secretary"
PREF_DB_VERSION = "db_version"
PREF_APP_MODE = "app_mode"
MODE_DEVELOPMENT = "development"
MODE_PRODUCTION = "production"
BACKUP_DIR = "backups"
MAX_BACKUPS = 5
```

**Scheduling-Konstanten** (`scheduling/BuildToDo.java`):
```java
FIXED_APPOINTMENT_PRIORITY = 10_000_000  // Feste Termine übertreffen alle
PREF_TIME_WINDOW_MINUTES = 480           // 8h-Arbeitstag für PrefTime-Penalty
CHAIN_LENGTH_BONUS_PER_ITEM = 50         // Bonus für längere Task-Ketten
SCORE_SCALE_FACTOR = 100                 // log1p-Score-Skalierung
NO_PREF_DAY_PENALTY = 0.3               // Score-Multiplikator wenn kein PrefSlot fuer den Tag
DAY_WEIGHT_BASE = 0.5                   // Basis-Anteil der Tages-Gewichtung
```

## Architecture

```
src/
├── activities/inApp/     # MainActivity (Launcher, 3-Tab-UI: Tasks/Budget/Ernährung)
│   ├── tasksTab/         # TaskView (Delegator, 2 Sub-Tabs), WeekPlanView (Tagesplan), TaskManagerView (Verwalten, Baum + Editor-Modal) + editorModal/ (ItemEditorModal [public Orchestrator]) + editorModal/fields/ (FieldGroup [interface], VisibilityFlags [record], CoreFields, SchedulingFields, RepetitionFields, DurationProgressFields, HierarchyFields, BudgetFields, GoalAppearanceFields, PrefScheduleEditor)
│   ├── budgetTab/        # BudgetView (Delegator, BudgetListener), TransactionModal (Create/Edit), ImportModal (Claude API), RecurringSuggestionsModal
│   └── ernaehrungTab/    # MealPlanView (Delegator, 4 Sub-Tabs), WeekPlanTab, RecipesTab, ShoppingTab, PantryTab, FoodGroupHeader, MemberTab, MealTabListener
├── activities/generic/   # ViewHelper, DateTimeHelper, ViewBuilder
├── activities/widget/    # TaskWidgetProvider, TaskWidgetFactory, TaskWidgetService, WidgetRefreshApp
├── entities/             # TrackedItem, TodoList, CalendarEvent, Config, MealType (shared enum),
│                         # Account, Transaction, BudgetLimit, Import, HouseholdMember, CookingPreferences,
│                         # RecipeRating, Ingredient, Recipe, MealPlan, ShoppingListItem,
│                         # PantryItem, ConsumptionLog, WeeklyFoodTarget, StorePackage
├── repository/           # Repo (Interface), SQLrepo, Table, MigrationManager,
│                         # parser/ (ParseUtils + je ein {Entity}Parser.java pro Entity)
├── controller/           # TodoManager, UpdateChecker, EditorManager, BudgetManager, MealManager,
│                         # SettingsManager, ApiKeyManager, ClaudeApiClient, ImportProcessor,
│                         # RecurringPatternDetector, WidgetUpdateManager
├── data/                 # Constants, SeedTestData, TaskListData, TaskRowConfig, BudgetDisplayData, ClaudePrompts
├── render/               # TaskRowRenderer (einheitlicher Renderer für App + Widget)
└── scheduling/           # BuildToDo, CleanToDo, CalendarReader, GenerateMealPlan,
                          # DailyPlanningScheduler, DailyPlanningReceiver, BootReceiver
```

**Widget-Architektur:** Kernprinzip: "App passt sich an Widget an" — RemoteViews-kompatible Layouts werden für beide Systeme verwendet.

**RemoteViews-Einschränkungen:** Nur bestimmte Views sind in Widgets erlaubt:
- Layouts: `FrameLayout`, `LinearLayout`, `RelativeLayout`, `GridLayout`
- Views: `TextView`, `ImageView`, `Button`, `ImageButton`, `ProgressBar`, `Chronometer`, `AnalogClock`
- Collections: `ListView`, `GridView`, `StackView`, `AdapterViewFlipper`

**NICHT erlaubt:** `<View>` (für Spacer/Divider stattdessen `ImageView` mit `background` verwenden), `CheckBox` (stattdessen `ImageView` mit Toggle-Icons), custom Views.

**Tab-Architektur:** 3 Haupt-Tabs (Tasks, Budget, Ernährung). Jeder Tab ist ein schlanker Delegator (`ViewBuilder`-Interface) der an extrahierte Sub-Komponenten delegiert:
- `TaskView` (`tasksTab/`) — 2 Sub-Tabs (Tagesplan/Verwalten), Constructor `(Context, TodoManager)`, erstellt EditorManager intern. Delegiert an `WeekPlanView` + `TaskManagerView`, exponiert `openCreateModal()` für Widget-Intent
- `BudgetView` (`budgetTab/`) — Budget-Übersicht, Konten, Budget-Limits, Transaktionen. Implementiert `BudgetListener`. Delegiert Modals an `TransactionModal` (Create/Edit), `ImportModal` (Kontoauszug-Import via Claude API, `FilePickerCallback`), `RecurringSuggestionsModal` (wiederkehrende Muster). Import-Erfolg triggert Recurring-Modal via Callback-Chain.
- `MealPlanView` (`ernaehrungTab/`) — 4 Sub-Tabs (Woche/Rezepte/Einkauf/Vorrat), delegiert an `WeekPlanTab`, `RecipesTab`, `ShoppingTab`, `PantryTab`, `FoodGroupHeader`. `WeekPlanTab` delegiert Haushalt-Verwaltung an `MemberTab`. Implementiert `MealListener`. Hält `currentWeekStart` als shared State für WeekPlanTab und ShoppingTab — Navigation-Callbacks via `navigateWeek(delta)`.
- **Sub-Tab-Pattern:** Tabs haben `render(FrameLayout)`, `initModals(FrameLayout rootContainer)`, `setListener(MealTabListener)`. Wochen-basierte Tabs (WeekPlanTab, ShoppingTab) bekommen zusätzlich `weekStart` + `onPrevWeek`/`onNextWeek` Callbacks in `render()`. Jeder Sub-Tab inflated und besitzt seine eigenen Modals (via `LayoutInflater.inflate()` + `rootContainer.addView()`). `MealTabListener` ist ein `@FunctionalInterface` in `ernaehrungTab/`.
- **Delegator-Pattern:** `selectSubTab(int)` toggled Farben + ruft `tab.render(container)` auf. FAB-Actions delegieren an `tab.showAutoGenerateDialog()`, `tab.openCreateModal()`, etc.

**Data flow:** `MainActivity` → `TodoManager` (Controller) → `BuildToDo` (UseCase) → `SQLrepo` (Repository)

**TodoManager** exponiert `provideList()` → `List<TaskEntry>` (Record mit slotId, taskTitle, start/end, completed, goalTitle, progressCurrent/Target/Unit etc.). Wird von der App-UI konsumiert. Callback-Pattern via `TodoListener` Interface:
- `onListUpdated()` — Plan hat sich geändert, UI neu laden
- `onSchedulingConflicts(List<SchedulingConflict>)` — Feste Termine konnten nicht eingeplant werden (default-Methode, optional implementierbar)

Weitere Methoden:
- `replanToday()` — Plan löschen + neu generieren. Leitet Scheduling-Konflikte an Listener weiter.
- `completeSlot(slotId)` / `uncompleteSlot(slotId)` — Normale Tasks abhaken
- `incrementProgress(slotId)` / `decrementProgress(slotId)` — Progress-Tasks: setzt `slot.progressDelta`, NICHT das Item direkt
- `startTimer(slotId)` / `stopTimer(slotId)` — setzt workStart/workEnd + completes

**EditorManager** verwaltet CRUD-Operationen für alle Item-Typen. Exponiert `getAllItems()` → `List<TreeEntry>` (`record TreeEntry(TrackedItem item, int depth)`) für die hierarchische Baumdarstellung im Editor (DFS-Traversal). `getAvailableParents(type)` filtert typbasiert: TASK→GOAL, GOAL→PROJECT, PROJECT→null. `getActiveAccounts()` liefert alle aktiven Konten für Budget-Spinner. `createItem()` synct automatisch `parent.children`.

**BudgetManager** verwaltet Budget-Daten für die UI. Pattern: Record-basierte `provide*()` Methoden für UI-Datenübergabe (z.B. `provideAccounts()` → `List<AccountEntry>`, `provideSummary(yearMonth)` → `BudgetSummary`). Callback via `BudgetListener` Interface. Write-Operationen aktualisieren automatisch Konto-Salden und benachrichtigen Listener. Recurring-Template-Erstellung via `createRecurringTemplate(candidate, accountId)`.

**MealManager** verwaltet Meal-Planning-Daten. Gleicher `provide*()`-Pattern wie BudgetManager (z.B. `provideAllRecipes()`, `provideMealPlan(weekStart)`, `provideSchedule()`). Write-Operationen für Rezepte, Mitglieder, MealPlans. `calculateRecipeNutrition()` berechnet Nährwerte basierend auf Zutaten. Shopping-List: `generateShoppingList()` aggregiert Zutaten, zieht Vorrat ab, rundet auf Packungsgrößen; `finishShopping()` markiert gekauft + füllt Vorrat + erstellt Transaktion. Pantry-CRUD mit `adjustPantryAmount(itemId, delta)` (Auto-Delete bei ≤0). Callback via `MealListener` Interface.

**Meal Completion Flow:** `completeMeal(mealPlanId, actualServings)` wird von `TodoManager.completeSlot()` aufgerufen: MealPlan→completed, Pantry reduzieren (FIFO nach Ablaufdatum), ConsumptionLog erstellen.

**GenerateMealPlan** (`scheduling/GenerateMealPlan.java`) — Automatische Wochenplan-Generierung: DGE-basierter Wochenbedarf → TDEE-Kalorienverteilung (20/35/35/10%) → Koch-Sessions aus Preferences + Kalender → Rezept-Scoring (7 Kriterien: FoodGroup, Skalierbarkeit, Variety, Aufwand, Ratings, Pantry-Expiry, Verderblichkeit) → MealPlans + Meal-Tasks erstellen → Einkaufsliste mit Single-Store-Optimierung.

**Meal-Task-Erstellung:** Für jede Mahlzeit wird ein `TrackedItem` mit `fixedAppointment` (Datum + Uhrzeit) und `mealType` erstellt. `MealPlan.itemId` verknüpft MealPlan mit dem TrackedItem für Completion-Tracking (Lookup via `MealManager.findMealPlanForItem(date, itemId)`).

**Claude API Integration** (`controller/`) — Kontoauszug-Import via Anthropic Messages API. Workflow: `ApiKeyManager` (Base64-encoded in SharedPrefs, muss mit `"sk-ant-"` beginnen) → `ClaudeApiClient` (`claude-sonnet-4-20250514`, 4096 Tokens, 120s Timeout) → `ImportProcessor` (SHA256-Datei-Hash Duplikat-Check → Claude API → Transaktionen parsen + Hash-Check `date_amount_payee` → speichern via `createTransactionQuiet()`). `ClaudePrompts` generiert System-Prompt mit dynamischer Kategorie-Liste aus DB.

**Recurring Pattern Detection** (`RecurringPatternDetector.java`) — Erkennt wiederkehrende Muster nach Import: Fuzzy-Payee-Matching (Levenshtein ≥75%), Betrag-Konsistenz (±15%), Datum-Pattern (MONTHLY_DAY, MONTHLY_LAST, WEEKLY, INTERVAL). `RecurringCandidate` Record mit Confidence-Score (0.0-1.0). BudgetView zeigt Modal mit Kandidaten nach Import.

**UI:** Hybrid XML + programmatisch. Hauptstruktur über XML-Layouts:
- Seiten-Layouts: `activity_main.xml`, `view_tasks.xml` (Sub-Tabs), `view_task_list.xml`, `view_edit_item.xml`, `modal_edit_item.xml`, `view_budget.xml`, `modal_transaction.xml`, `view_meal_plan.xml` (Sub-Tabs), `modal_recipe.xml`, `modal_meal_plan.xml`, `modal_member.xml`, `modal_pantry.xml`
- Komponenten: `row_tree_item.xml` (Editor-Baum), `item_account_card.xml`, `item_budget_bar.xml`, `item_transaction_row.xml`, `item_recipe_card.xml`, `item_food_group_bar.xml`, `item_ingredient_row.xml`, `item_pref_slot_row.xml` (PrefSlot Tag+Uhrzeit), `item_meal_slot.xml` (Wochenplan-Karte), `item_member_card.xml` (Haushaltsmitglied), `item_shopping_row.xml` (Einkaufsliste)
- Styling: `res/values/colors.xml`, `dimens.xml`, `styles.xml` — Farben/Größen als Ressourcen statt Hardcoded-Werte

**Einheitliche Layouts** (`res/layout/`): `item_task.xml`, `item_goal_header.xml`, `item_calendar.xml` — RemoteViews-kompatibel (RelativeLayout statt LinearLayout+weight, ImageView statt CheckBox). Widget-Container: `widget_list.xml`. Widget-Metadaten: `res/xml/task_widget_info.xml`.

Farben und Dimensionen liegen ausschließlich in `colors.xml`/`dimens.xml` — programmatischer Zugriff via `ContextCompat.getColor()` und `getResources().getDimension()`. Neue Views bevorzugt als XML-Layout anlegen; dynamische Listeinträge und ähnliches weiterhin programmatisch.

**Accessibility:** Alle interaktiven XML-Elemente (Spinner, Buttons mit kryptischem Text wie L/M/H/C) brauchen `android:contentDescription="@string/cd_*"`. String-Ressourcen in `strings.xml` unter `<!-- Content Descriptions -->`. EditTexts mit `android:hint` brauchen KEIN contentDescription (Hint dient als Accessibility-Label). Dekorative `<View>`-Spacer bekommen `android:importantForAccessibility="no"`.

**Edit Modal** (`tasksTab/TaskManagerView.java` + `editorModal/` + `modal_edit_item.xml`) — Create/Edit-Dialog für Tasks, Goals und Projects. `TaskManagerView` vereint Baum-Darstellung und Editor-Delegation in einer Klasse:
- **Baum** (in `TaskManagerView`) — Hierarchischer Baum (Expand/Collapse, Suche, Typ-Filter)
- `ItemEditorModal` (`editorModal/`, **public**) — Schlanker Orchestrator. Erstellt alle 8 Field-Gruppen, koordiniert `populate()`/`save()`/`updateVisibility()`. Konstruktor: `(Context, EditorManager, View modalOverlay, Runnable onSaved)`. Hält `suppressListeners` Flag (ersetzt SyncGuard).
- `FieldGroup` (`editorModal/fields/`, **public interface**) — `populate(TrackedItem)`, `apply(TrackedItem.Builder)`, `updateVisibility(VisibilityFlags)`. Jede Gruppe liest/schreibt Views direkt.
- `VisibilityFlags` (`editorModal/fields/`, **public record**) — Immutable Sichtbarkeits-Flags. Statische Factory `compute(type, schedulingType, repType, repUnit, repValue, progressEnabled, budgetEnabled, predecessorIdx)`. Konstanten: `SCHED_TERMIN=0`, `SCHED_AUFGABE=1`, `SCHED_ANGEWOHNHEIT=2`. 8 Kontext-Inputs + 18 berechnete boolean-Flags.
- **Field-Gruppen** (`editorModal/fields/`, **public**) — Je eine Klasse pro Domain: `CoreFields`, `SchedulingFields`, `RepetitionFields`, `DurationProgressFields`, `HierarchyFields`, `BudgetFields`, `GoalAppearanceFields`, `PrefScheduleEditor`. Konstruktor: `(Context, View root, BooleanSupplier suppressCheck, Runnable onVisibilityTrigger [, EditorManager])`. Keine TextWatcher, keine State-Updates — nur visibility-affektierende Listener (7 Stueck gesamt).

**SchedulingType-System (UI-only, kein DB-Feld):** Neben dem ItemType (TASK/GOAL/PROJECT) steuert ein **SchedulingType-Dropdown** die Feld-Sichtbarkeit:
- `SCHED_TERMIN` (0) — Fester Zeitpunkt (nur TASK). Erzwingt `RepetitionType.NONE` + `fixedDate`/`fixedTime`
- `SCHED_AUFGABE` (1) — Einmalig, flexibel (TASK + GOAL). Erzwingt `RepetitionType.NONE`, optionale Deadline
- `SCHED_ANGEWOHNHEIT` (2) — Wiederkehrend (TASK + GOAL). Erzwingt `RepetitionType != NONE`, PrefSlots + CompleteFirst

Mapping: TASK-Spinner zeigt alle 3, GOAL-Spinner zeigt nur Aufgabe+Angewohnheit, PROJECT bekommt kein Dropdown.
Ableitung bei Edit: `isFixedAppointment()` → Termin, `repType != NONE` → Angewohnheit, sonst → Aufgabe.

**Feld-Sichtbarkeit nach ItemType + SchedulingType:**

| Feld | TASK+Termin | TASK+Aufgabe | TASK+Angew. | GOAL+Aufgabe | GOAL+Angew. | PROJECT |
|------|-------------|-------------|-------------|-------------|-------------|---------|
| SchedulingType | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Priorität | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Fixed Date/Time | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Deadline | ❌ | ✅ | ❌ | ✅ | ❌ | ❌ |
| Repetition-Sektion | ❌ | ❌ | ✅ | ❌ | ✅ | ❌ |
| Cooldown | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ |
| [CB] Fortschritt | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Dauer | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| [CB] Budget | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Parent | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Predecessor | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |

`[CB]` = CheckBox-Toggle: Progress-Felder und Budget-Felder sind hinter CheckBoxen (`cb_completion`, `cb_budget`) versteckt.
Repetition-Sektion enthält: RepType-Buttons (INTERVAL/REPS_PER_TIME/DAY_OF_TIME, kein NONE), Wert+Einheit+Wochentag, CompleteFirst-Toggle, PrefSlots.

**UI-Patterns:**
- `VisibilityFlags.compute()` — Deterministische Sichtbarkeitsberechnung aus aktuellen Gruppen-Gettern
- `updateButtonGroup(buttons, selectedIndex)` / `updateToggleButton(button, enabled)` — Toggle-Buttons (Priority, DurationUnit, RepType, CompleteFirst, ProgressPerRep)
- `suppressListeners` — Boolean in ItemEditorModal, verhindert Listener-Kaskaden während `populate()`. Gruppen prüfen via `BooleanSupplier`.
- TimePicker/DatePicker — Standard Android-Dialoge via `TimePickerDialog`/`DatePickerDialog`

**Editor-Modal-Pattern (Direct Field Groups):**
- **Caller:** `new ItemEditorModal(ctx, manager, overlay, onSaved)` + `init()` + `showModal(item)` / `openCreateModal()`
- **Show-Flow:** `suppressListeners = true` → `for (g : allGroups) g.populate(item)` → `suppressListeners = false` → `updateVisibility()`
- **Save-Flow:** `coreFields.getTitle()` validieren → `new Builder(type, title, priority)` → `for (g : allGroups) g.apply(builder)` (jede Gruppe guardet sich selbst, z.B. RepetitionFields setzt `noRepetition()` wenn unsichtbar) → `build()` → `preserveState()` → `create/update`
- **Neue Feld-Gruppe hinzufügen:** (1) Klasse in `fields/` mit `implements FieldGroup`, Constructor `(Context, View root, BooleanSupplier suppressCheck, Runnable onVisibilityTrigger [, EditorManager])`. (2) `populate(item)`, `apply(builder)`, `updateVisibility(flags)` implementieren. (3) In `ItemEditorModal` Konstruktor erstellen + in `allGroups` Array aufnehmen. (4) Neue Visibility-Regeln in `VisibilityFlags.compute()`.
- **Spinner-Daten:** Field-Gruppen halten Referenz-Listen (z.B. `HierarchyFields.availableParents`). Index-Auflösung direkt in `apply()`.
- Beim Edit übernimmt `TrackedItem.preserveState(source)` alle Laufzeit-/Historien-Felder (id, created, children, streaks, completions, followUps, timing, mealType etc.) — neue State-Felder dort ergänzen.

**DateTimeHelper** (`activities/generic/DateTimeHelper.java`) — Statische Utilities für Datum/Zeit-Operationen (Pure Java, keine Android-Dependencies):
- `getMonday(LocalDate)` — Montag der Woche für ein Datum
- `getWeekKey(LocalDate)` — Wochen-Key im Format "2026-W05" (zentralisiert `WeekFields.of(Locale.GERMANY)`)
- `formatTime(LocalTime)` — Formatiert als "HH:mm"
- `getWeekNumber(LocalDate)` — Extrahiert Wochennummer (deutsche Wochenberechnung)

**ViewHelper** (`activities/generic/ViewHelper.java`) — Zentrale Utilities für programmatische UI:
- `dp(Context, int)` — Konvertiert dp zu Pixel (statischer Import überall verwendet)
- `roundedBg(Context, int color, int cornerDp)` — Erstellt `GradientDrawable` mit Farbe und abgerundeten Ecken
- `showEmptyState(ViewGroup container, String message)` — Einheitliche "Keine X vorhanden"-Meldung (`text_secondary`, Padding 16/32)
- `spinnerAdapter(Context, String[]/List<String>)` — Erstellt `ArrayAdapter` mit `simple_spinner_item` + `setDropDownViewResource`. Verwendung: `spinner.setAdapter(spinnerAdapter(ctx, items))`
- `buildWeekHeader(Context, LocalDate, Runnable onPrev, Runnable onNext)` — Baut "< KW X (dd.-dd.MM.) >" Navigation, gibt `LinearLayout` zurück (Caller kann extra Buttons anhängen)
- `setupModalOverlay(View overlay, Runnable onDismiss)` — Konfiguriert Modal-Overlay: Klick ausserhalb schliesst, Klick auf Card wird absorbiert. Sucht `R.id.modal_card`, Fallback auf `getChildAt(0)`
- `parseInt(EditText, int fallback)` / `parseDouble(EditText, double fallback)` — Safe-Parsing aus EditText-Feldern (leerer/ungültiger Wert → fallback)

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
Registrierung erfolgt in `WidgetRefreshApp.onCreate()`. Controller (BudgetManager, TodoManager, etc.) nutzen `notifyUpdate()` statt direkter Widget-Referenzen → verbesserte Testbarkeit.

**Widget-Header-Buttons:** Neben dem App-Titel befinden sich zwei Buttons:
- "+" Button → Öffnet App direkt im Create-Modal (via `ACTION_CREATE_ITEM` Intent → `MainActivity.handleWidgetIntent()`)
- "↻" Button → Refresht Widget-Daten (via `ACTION_REFRESH` Broadcast)

**Completion-Feedback:**
- **App** (`WeekPlanView.java`): `animateCompletion()` — Checkbox-Bounce (scale 1.0→1.3→1.0) + Hintergrund-Flash (`completion_flash` → `surface_complete`)
- **Widget** (`TaskWidgetProvider`/`TaskWidgetFactory`): Flash via statischer `flashingSlotId` — bei Completion wird Slot-ID gesetzt, Factory rendert mit `completion_flash`, nach 300ms wird ID gelöscht und Widget neu gerendert

**Meta-Row Badges:** Unterhalb des Task-Titels zeigt `task_meta_row` (LinearLayout) drei optionale Badges:
- Streak: "🔥 X" mit Rarity-Farbe (aus `TaskListData.getStreakRarityColorRes()`)
- Deadline: "Fällig: dd.MM.yyyy" (rot wenn überfällig)
- Remaining: "⏱ X Tage" — aus `TrackedItem.remainingTime(today)`

**Background Scheduling:** `DailyPlanningScheduler` registriert AlarmManager-Trigger um 00:00 → `DailyPlanningReceiver` führt aus:
1. `CleanToDo.clean()` — Zwei Phasen: (a) Gestrige Slots auswerten → `TrackedItem.update()` mit Slot-Daten + followUp-Tracking, (b) ALLE übrigen Items refreshen → `update(null,...)` für Perioden-Reset, scheduled-Bereinigung, blockedDays-Refresh. Danach alte TodoLists aus DB entfernen.
2. `BuildToDo.planWeek()` — Neuen 7-Tage-Plan erstellen
3. `scheduleDaily()` — Nächsten Mitternachts-Alarm registrieren
4. `WidgetUpdateManager.notifyUpdate(context, DataDomain.TODO)` — Widget aktualisieren

`BootReceiver` re-registriert den Alarm nach Geräte-Neustart.

## Key Patterns

**TrackedItem** is the central entity — Tasks, Goals, and Projects all use this class with `ItemType` enum:
- `TASK` — Individual work units with `minDurationValue/maxDurationValue`, `timePerProgressUnit`, `repetition`, `prefSlots` (per-weekday preferred times), optional `budgetRequirementCents`, optional `fixedDate`/`fixedTime` (feste Termine), optional `mealType` (Meal-Item-Identifikation)
- `GOAL` — Containers for tasks, have `children` list and time budget. Können jetzt auch Repetition (Angewohnheit) haben.
- `PROJECT` — Top-level grouping

**Meal-Items:** `TrackedItem.mealType` (BREAKFAST/LUNCH/DINNER/SNACK, null = kein Meal-Item) identifiziert recurring Meal-Items. `MealPlan.itemId` verknüpft MealPlan mit dem zugehörigen TrackedItem. MealType wird nicht im Edit-Modal gesetzt, sondern vom Ernährungs-Tab.

**PrefSlot-System (Per-Weekday/Monthly Preferences):** Jedes Item hat `prefSlots: List<PrefSlot>` mit:
```java
public static record PrefSlot(int dayKey, LocalTime time, int completionCount) {
    static PrefSlot weekly(DayOfWeek day, LocalTime time);     // dayKey 1-7, count=0
    static PrefSlot monthly(int dayOfMonth, LocalTime time);   // dayKey 1-31, count=0
    static PrefSlot of(int dayKey, LocalTime time, int count, boolean monthly); // validiert + count
}
```
`dayKey` ist kontextabhängig: 1-7 (Wochentag, Mo=1) für die meisten Items, 1-31 (Monatstag) für REPS_PER_TIME/DAY_OF_TIME + MONTH. **IMMER Factory-Methoden statt `new PrefSlot()` verwenden** — Factory validiert dayKey-Range.

**Statische Helpers (Single Source of Truth):**
- `isMonthlyDayMode(RepetitionType, RepUnits)` — true nur für REPS_PER_TIME/DAY_OF_TIME + MONTH (NICHT INTERVAL+MONTH)
- `isValidDayKey(int dayKey, boolean monthly)` — Validiert Range (weekly: 1-7, monthly: 1-31)

**Instanz-Methoden:**
- `getPrefDayKey(LocalDate)` → dayKey für ein Datum (kontextabhängig)
- `getPrefSlotForDate(LocalDate)` → PrefSlot oder null (Convenience über getPrefDayKey)
- `getDayKeyWeightForDate(LocalDate)` → 0.0-1.0 (Completion-Gewichtung)
- `getPrefSlotForDay(DayOfWeek)` / `getDayWeight(DayOfWeek)` → Convenience für Wochentag-Lookup
- `hasPrefSlots()` → true wenn Slots vorhanden
- `updatePrefSlot(day, workStart)` — Running Average bei Completion, nutzt `getPrefDayKey()` intern

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

Rarity-Farben in `colors.xml`, Streak-Wert kommt aus `TrackedItem.currentStreak`. Logik in `TaskListData.getStreakRarityColorRes()`.

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

**Config** (`Config.java`) — Hält `Map<DayOfWeek, DaySchedule>` mit Start-/Endzeit pro Wochentag (z.B. Mo 06:00–18:00). Wird von `BuildToDo` genutzt um verfügbare Stunden pro Tag zu bestimmen. `DaySchedule` ist eine innere Klasse mit `start`/`end` (`LocalTime`).

**CalendarEvent** (`CalendarEvent.java`) — Java Record: `record CalendarEvent(String title, LocalTime start, LocalTime end)`. `CalendarReader` liest Device-Kalender via `CalendarContract.Instances`, `CalendarProvider` ist ein Functional Interface für testbare Kalender-Abstraktion.

**Repo Interface** (`Repo.java`) — Abstraktion über SQLrepo, ermöglicht testbare Dependencies. Zwei Zugriffsarten: `lookup`/`lookups` (String table, raw primitives) vs `fetch`/`fetchAll` (type-safe `Table<T>`, entity objects). `write` auto-detects entity type. Batch-Fetch-Regel: Siehe Critical Rules oben. Table-Referenzen in `Table.java` (z.B. `Table.ITEMS`, `Table.ACCOUNTS`, `Table.RECIPES`).

**ParseUtils** (`repository/parser/ParseUtils.java`) — Safe-parsing Utilities: `safeEnum()`, `safeLocalDate()`, `safeLocalTime()`, `safeLong()`, `safeInt()`. Siehe Critical Rules oben.

**TaskListData** (`data/TaskListData.java`) — Shared data transformation für App und Widget:
- `DisplayRow` sealed interface mit drei Record-Typen: `GoalHeader`, `TaskItem`, `CalendarEvent`
- `fromEntries(List<TaskEntry>)` transformiert flache TaskEntry-Liste zu DisplayRow-Liste mit eingefügten Goal-Headern
- `getStreakRarityColorRes(int streak)` gibt Rarity-Farb-Resource-ID zurück

**BudgetDisplayData** (`data/BudgetDisplayData.java`) — Shared data transformation für Budget-UI: Formatierung (`formatCents` → "1.234,56 EUR", `formatDate`/`formatYearMonth`), Kategorie-Labels/Icons, `toYearMonth()`-Konvertierung, `isIncomeCategory()`-Check.

**TaskEntry** (`TodoManager.java`) — Record für die UI-Datenübergabe. Enthält slotId, taskTitle, start/end, completed, goalTitle/Icon/Color, currentStreak, deadline, progressCurrent/Target/Unit etc. Vollständige Signatur im Source-Code.

**Budget UI Records** (`BudgetManager.java`) — `AccountEntry`, `TransactionEntry`, `BudgetEntry`, `BudgetSummary`, `CategoryOption`. Felder siehe Source-Code.

## Testing

**Keine automatisierten Tests.** Der Code ändert sich zu schnell — Tests verursachen unnötigen Wartungs-Overhead. Verifizierung erfolgt ausschließlich über `compileDebugJavaWithJavac` (Syntax) und manuelles Testen via ADB. KEINE Unit-Tests, Integrationstests oder Test-Dateien anlegen.

**ADB-basiertes Debugging:** Scheduling und Pläne werden via `DebugBroadcastReceiver` getestet (siehe Debugging-Sektion). Zeigt adjustedPrio-Scores, Budget-Info, Hierarchie.

## Scheduling Algorithm

**BuildToDo** — Globale Slot-Bewertung über alle 7 Tage gleichzeitig:
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

**Testability:** `BuildToDo` nimmt `Repo`-Interface und `CalendarProvider` (Functional Interface) als Dependencies — ermöglicht Unit-Tests ohne Android-Kontext.

**Priority** basiert auf `Priority` enum (CRITICAL: 100000, HIGH: 400, MODERATE: 200, LOW: 100), plus Overdue-Bonus. Per-weekday PrefSlot-Matching via `calculateItemScore(item, itemStart, precedingItemId, date)` in `tryMatchChain()`: nutzt `item.getPrefSlotForDate(date)` und `item.getDayKeyWeightForDate(date)`. Items mit PrefSlot für den Tag bekommen Zeit-Matching (quadratische Penalty) + Tages-Gewichtung (0.5-1.0x basierend auf Completion-Häufigkeit); Items ohne Slot für den Tag werden mit 0.3x bestraft. FollowUp-Boost via `scoreFollow()` für historische Muster.

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
1. User drückt [+] → `TodoManager.incrementProgress()` setzt `slot.progressDelta++` und `slot.completed = true`
2. UI zeigt: Bei `progressPerRep=false`: `item.progressCurrent + slot.progressDelta`. Bei `progressPerRep=true`: nur `slot.progressDelta`
3. Um Mitternacht: `CleanToDo` → `item.update(..., progressDelta, ...)` wendet Delta auf Item an (für Statistik)

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
  - `.prefSlots(List<PrefSlot>)` — Per-weekday bevorzugte Zeiten
  - `.delayAfter(predecessorId, minutes)` — Verzögerte Verkettung
  - `.completeFirst(boolean)` — Erst erledigen vor Reset
- `getMinDurationMinutes()` / `getMaxDurationMinutes()` — Konvertieren zu Minuten (bei PROGRESS_UNITS: Wert × timePerProgressUnit)
- `getSlotDuration()` — Clamps estimated auf [min, max], Fallback auf min oder 30 min wenn keine Schätzung
- Wird von `BuildToDo` via `getSlotDuration()` für Slot-Sizing und Scheduling genutzt

Offener Progress (progressCurrent < progressTarget) bewirkt:
- Prio-Boost über `work()/remainingTime()` — mehr verbleibende Einheiten = höherer Boost, gedeckelt bei 2.0x
- Periodenblockierung wird übersprungen (wie REPS_PER_TIME), damit das Item täglich einplanbar bleibt
- `isCompleted` wird auf `false` gehalten solange Progress offen ist

**Deadline-Logik:** Einmalige Tasks (`RepetitionType.NONE`) können ein optionales `deadline`-Feld (`LocalDate`) haben. Wirkung:
- Prio-Boost über `work()/remainingTime()`: je näher die Deadline, desto höher der Multiplier (cap 2.0x). Bei überfälliger Deadline: fester 3.0x Boost.
- Periodenblockierung wird übersprungen (wie REPS_PER_TIME), damit das Item täglich einplanbar bleibt bis zur Erledigung.
- Anzeige in Task-Liste und Widget als "Fällig: dd.MM.yyyy", rot wenn überfällig.
- Edit-UI: DatePickerDialog, nur sichtbar bei SchedulingType=AUFGABE.

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
- Edit-UI: DatePicker + TimePicker (24h), nur sichtbar bei SchedulingType=TERMIN

**Scheduling-Konflikt-Tracking:** Wenn feste Termine nicht eingeplant werden können, wird ein `SchedulingConflict` Record erstellt:
```java
public record SchedulingConflict(Long itemId, String itemTitle, LocalDate conflictDate, String reason) {}
// reason: "DAY_BOUNDS" | "CALENDAR_OVERLAP" | "FIXED_OVERLAP"
```
`BuildToDo.getConflicts()` liefert alle Konflikte des letzten `planWeek()`-Durchlaufs. `TodoManager` leitet diese via `TodoListener.onSchedulingConflicts()` an die UI weiter.

**Skip conditions in getItems():** `item.blockedDays` enthält den Tag, oder (nur Goal → Project) `parent.blockedDays` enthält den Tag, oder (bei Tasks mit `budgetRequirementCents > 0`) das freie Budget ist nicht ausreichend.

**Budget-Aware Scheduling:** Tasks können Budget-Anforderungen haben:
- `budgetRequirementCents` — Kosten in Cents (0 = kein Budget erforderlich)
- `budgetAccountId` — Spezifisches Konto (null = beliebiges aktives Konto)
- `budgetCategory` — Kategorie für die Auto-Transaction bei Completion

`BuildToDo.getFreeBudgetCents()` berechnet verfügbares Budget:
1. Summe aller aktiven Konten mit `includeInTotal` (oder spezifisches Konto)
2. Minus: Wiederkehrende Ausgaben der nächsten 7 Tage (`nextDue` im Zeitfenster)
3. Minus: Bereits im Scheduling-Durchlauf committed (`committedBudgetCents`)

**Budget-Caching:** `cachedBaseBudget` wird am Anfang von `planWeek()` einmalig berechnet und für alle Budget-Tasks ohne spezifisches Konto wiederverwendet. Am Ende von `planWeek()` wird der Cache zurückgesetzt.

Nach erfolgreicher Platzierung in `assignChain()` wird `committedBudgetCents` erhöht, um Race Conditions zu vermeiden.

**Auto-Transaction bei Completion:** `TodoManager.completeSlot()` erstellt automatisch eine Transaction für Budget-Tasks:
- Betrag: `-item.budgetRequirementCents` (Ausgabe)
- Konto: `item.budgetAccountId` oder erstes aktives mit `includeInTotal`
- `isConfirmed = false` — User muss in Budget-View bestätigen
- Konto-Saldo wird sofort aktualisiert
- **Atomarität:** Alle DB-Writes (Slot, Transaction, Account) werden in einer SQLite-Transaction gewrappt (`db.beginTransaction()` / `setTransactionSuccessful()` / `endTransaction()`)
- Meal-Completion (`MealManager.completeMeal()`) wird außerhalb der Transaction ausgeführt und mit try-catch geschützt

**Task vs. Goal Slot-Fitting:** Tasks müssen komplett in einen Slot passen (werden übersprungen wenn zu wenig Zeit). Goals dürfen partiell eingeplant werden (slotCoverage reduziert die Prio proportional).

**TrackedItem.update()** — Zentraler Entry-Point für Tagesabschluss-Logik. Signatur:
```java
void update(Boolean completed, LocalTime workStart, LocalTime workEnd,
            Long previousItemId, Integer progressDelta, LocalDate day, Repo repo)
```
- `completed=true/false` → Slot-Daten auswerten (completions, prefSlot, timePerProgressUnit, streak, followUps)
- `progressDelta` → Fortschrittsänderung aus dem Slot (wird von `incrementProgress`/`decrementProgress` im Slot gesammelt und erst hier angewendet). Auch für Zeit-pro-Einheit Berechnung genutzt.
- `completed=null` → Nur "immer"-Updates (Refresh ohne Slot-Daten)
- "Immer"-Updates (laufen IMMER, auch bei null): Perioden-Reset (`isCompleted` → false wenn neue Periode begonnen), scheduled bereinigen (vergangene Daten entfernen), blockedDays neu berechnen. Bei ALLEN Items mit Progress-Tracking: `progressCurrent` reset auf 0 mit Backup in `progressLastPeriod`.
- `followUps`: Wenn `previousItemId != null`, wird `followUps.merge(previousItemId, 1, Integer::sum)` aufgerufen — trackt welche Items direkt vor diesem erledigt wurden.

**FollowUp-System:** Drei Mechanismen für Task-Reihenfolge:
- **predecessor** (`TrackedItem.predecessor`): Harte Constraint "dieser Task soll nach Task X kommen". UI: Spinner im Edit-Modal. **Scheduling:** Items mit `predecessorDelay=0` werden in `buildChains()` zu Ketten gruppiert (Same-Day). Items mit `predecessorDelay>0` warten auf Predecessor-Completion + Delay (Delayed-Chains).
- **Actual Completion Tracking** (`TimeSlot.previousCompletedItemId`): Trackt die tatsächliche Completion-Reihenfolge (nicht die geplante!). `TodoManager` speichert pro Goal-Slot, welcher Task zuletzt erledigt wurde (`lastCompletedByParent`). Bei Completion wird `previousCompletedItemId` im Slot gesetzt. `CleanToDo` liest aus dem Slot statt aus der Iterations-Reihenfolge.
- **FollowUp Prio-Boost** (`TrackedItem.scoreFollow(predecessorId)`): Tasks die historisch oft nach bestimmten anderen Tasks erledigt wurden, bekommen einen Planungs-Boost. Berechnung: Wenn Task A mindestens 5x nach Task B erledigt wurde UND das mindestens 5% aller FollowUps von A ausmacht, bekommt A einen Boost = Anteil (z.B. 60% der Follows von B → 60% Boost auf adjustedPrio). Angewendet in `BuildToDo.tryMatchChain()` via `findPrecedingItem()`.

**blockedDays-Mechanismus:** Jedes Item hat seine **eigenen** blockedDays. `TrackedItem.getBlockedDays()` berechnet aus zwei Quellen:
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
- `accounts` — Finanzkonten. AccountType enum: CHECKING, SAVINGS, CASH, CREDIT. Felder: name, icon, color, initialBalanceCents, currentBalanceCents, includeInTotal
- `transactions` — Unified einmalig + recurring. Builder: `new Transaction.Builder(accountId, amountCents, date, categoryId).monthlyOnDay(15).build()`. `calcNextOccurrence(from)` für Fälligkeitsberechnung
- `budget_limits` — Monatliche Budgetlimits pro Kategorie (UNIQUE category+year_month). Felder: limitCents, spentCents, rolloverCents
- `imports` — Bank-Statement-Uploads via Claude API (file_hash für Duplikat-Check, status PENDING→PROCESSING→COMPLETED/FAILED)
- `categories` — User-controlled Transaktionskategorien (name, icon, isIncome). ~20 Built-in via `SeedTestData.java`

**Meal Planning:**
- `household_members` — Gender/ActivityLevel enums. BMR (Mifflin-St Jeor), TDEE, DGE-Skalierung via `getFoodFactor()`
- `cooking_preferences` — maxCookingPerWeek, allowedCookingDays, preferredMealTypes
- `recipe_ratings` — Bewertungen pro Mitglied (recipeId, memberId, rating 1-5)
- `ingredients` — FoodGroup enum mit DGE-Wochen-Empfehlungen. Nährwerte pro 100g. `isWholeUnit`, `isPerishable`
- `recipes` — PrepEffort enum (QUICK/MEDIUM/ELABORATE). Ingredients als pipe-separated: `"id|name|amount|unit;..."`. Skalierung: `minServings`/`maxServings`
- `meal_plans` — Wochenplan-Einträge (date, mealType, recipeId, servings, isCompleted, itemId FK zu TrackedItem)
- `shopping_list_items` — ingredientId, amount, needed/excess, suggested_store, isPurchased
- `pantry_items` — StorageLocation enum (PANTRY, FRIDGE, FREEZER). expiryDate, purchaseDate
- `consumption_logs` — Nährwert-Tracking (date, memberId, recipeId/ingredientId, calories, protein, carbs, fat)
- `weekly_food_targets` — Berechneter Wochenbedarf pro FoodGroup (weekKey, diverse Gramm-Felder)

**Relations in items:** `parent` (single ID), `children` (comma-separated IDs), `followups` ("id:count" pairs, e.g. "5:3,8:1"), `scheduled` (comma-separated ISO dates), `blocked_days` (comma-separated ISO dates, computed), `required_predecessor` (single ID, optionale Vorgänger-Constraint).

**Per-weekday PrefSlots in items:** `pref_schedule` (TEXT, Format: `"1;08:30;15,3;08:00;12"` — numerischer dayKey;Uhrzeit;completionCount, Semikolon trennt Felder, Komma trennt Einträge).

**Feste Termine in items:** `fixed_date` (TEXT, ISO date), `fixed_time` (TEXT, ISO time). Beide Felder müssen gemeinsam gesetzt sein oder beide null. Nur für einmalige Tasks (RepetitionType.NONE) relevant.

**Darstellung in items:** `goal_icon` (TEXT, Emoji), `goal_color` (TEXT, Hex-Farbcode). Nur für Goals relevant.

**Budget in items:** `budget_requirement_cents` (INTEGER, 0=kein Budget), `budget_account_id` (INTEGER FK, null=beliebig), `budget_category` (TEXT). Nur für Tasks relevant.

**Meal-Items in items:** `meal_type` (TEXT, null=kein Meal-Item, BREAKFAST/LUNCH/DINNER/SNACK). Identifiziert recurring Meal-Items. Verknüpfung mit MealPlan läuft über `MealPlan.itemId` (nicht über items-Spalte). Bei Completion wird `MealManager.completeMeal()` aufgerufen.

**DB-Strategie (v1.0.0+):** Production-Mode mit Migrations-Support. Keine Backward-Compat im Code — Migrationen sind der einzige Mechanismus für Schema-Änderungen nach Updates.

**Migration-System:**
- `DB_VERSION` in `Constants.java` (aktuell: **34**)
- `MigrationManager.java` verwaltet Backups und Migrationen
- `SQLrepo.onUpgrade()` ruft `MigrationManager.migrate()` auf
- Backup wird VOR Migration erstellt: `getFilesDir()/backups/backup_vX_timestamp.db`
- Max 5 Backups (Rotation in MigrationManager)
- `_migrations` Tabelle trackt angewendete Migrationen

**Konsolidierte Migrationen:**
- v20–v29: Fall-through zu v30 (Konsolidierung)
- v30: `migrateV30_SchemaConsolidation()` — Fügt alle neuen Spalten hinzu (fixed_date/time, predecessor/delay, budget, meal_plan_id, etc.) + `createPerformanceIndexes()`
- v31: `migrateV31_ProductionCleanup()` — Bereinigt Testdaten, behält Referenzdaten (categories, ingredients, config_schedules)
- v32: `migrateV32_FreeformMealSchedule()` — Free-form Meal Schedule (beliebig viele pro Tag)
- v33: `migrateV33_PrefSchedule()` — Per-weekday PrefSlots (`pref_schedule` Spalte)
- v34: `migrateV34_MealTypeConsolidation()` — `meal_type` auf items, `item_id` auf meal_plans. meal_schedules eliminiert zugunsten recurring TrackedItems

**Performance-Indexes** (erstellt in v30, ergaenzt in v34):
```sql
idx_items_budget      ON items(budget_requirement_cents) WHERE budget_requirement_cents > 0
idx_items_fixed       ON items(fixed_date) WHERE fixed_date IS NOT NULL
idx_items_meal        ON items(meal_plan_id) WHERE meal_plan_id IS NOT NULL
idx_items_open        ON items(is_completed, type) WHERE is_completed = 0
idx_meal_plans_item   ON meal_plans(item_id) WHERE item_id IS NOT NULL   -- v34
```

**Bei Schema-Änderungen:**
1. `DB_VERSION` in `Constants.java` hochzählen
2. Neuen `case` in `MigrationManager.runMigration()` hinzufügen:
   ```java
   case 34:
       db.execSQL("ALTER TABLE items ADD COLUMN new_field TEXT");
       break;
   ```
3. Schema in `SQLrepo.onCreate()` für Neuinstallationen anpassen
4. Helper: `addColumnIfNotExists(db, table, column, type)` prüft `PRAGMA table_info` vor `ALTER TABLE`

**Referenzdaten:** `categories`, `ingredients`, `config_schedules` werden bei Migration beibehalten.

**SeedTestData.java:** Wird NICHT mehr automatisch aufgerufen — bleibt für Entwicklung/Debugging verfügbar.

## Settings UI

**Overflow-Menü (⋮)** rechts in der Tab-Bar (`activity_main.xml`). Click-Handler in `MainActivity.buildUI()` öffnet `SettingsManager`.

**SettingsManager** (`controller/SettingsManager.java`) bietet:
- Backup wiederherstellen (Liste verfügbarer Backups, formatierte Anzeige)
- Manuelles Backup erstellen
- Factory Reset (alle Daten löschen mit Sicherheitsbackup)
- Über AutoSecretary (Version + DB-Schema anzeigen)

Bei Datenänderung (Restore/Reset) wird `Activity.recreate()` aufgerufen.

## Auto-Update System

GitHub dient als CDN. `release/version.txt` enthält den aktuellen Integer-versionCode, `release/AutoSecretary.apk` die aktuelle APK. Repository: `ThonkTank/AI-Secretary`.

`UpdateChecker.java` prüft beim App-Start → fetcht `version.txt` → vergleicht mit lokalem `versionCode` → bei neuer Version: Dialog → Download → FileProvider → System-Installer. Die UI wird erst nach abgeschlossenem Update-Check aufgebaut (Callback-Pattern).

## Code Conventions

**Keine Magic Numbers:** Wiederkehrende Werte als Konstanten definieren. Defaults gehören in die Entity-Klasse als Single Source of Truth, nicht in Parser oder Migrationen duplizieren.

**ViewHelper-Utilities nutzen (nicht duplizieren):**
- `ViewHelper.showEmptyState(container, message)` — Einheitliche "Keine X vorhanden"-Meldung
- `ViewHelper.spinnerAdapter(ctx, items)` — Spinner-Adapter erstellen
- `ViewHelper.setupModalOverlay(overlay, onDismiss)` — Modal-Overlay mit Click-Absorption konfigurieren
- `ViewHelper.buildWeekHeader(ctx, weekStart, onPrev, onNext)` — Wochen-Navigation
- `ViewHelper.parseInt(EditText, fallback)` / `parseDouble(EditText, fallback)` — Safe-Parsing aus EditText-Feldern
- `ViewHelper.afterTextChanged(Runnable)` — TextWatcher ohne beforeTextChanged/onTextChanged-Boilerplate
- `DateTimeHelper.*` — Datum/Zeit-Operationen (getMonday, getWeekKey, formatTime)

**Sortier-/Filter-Logik zentralisieren:** Wenn dieselbe Sortierung an 2+ Stellen gebraucht wird → statische Methode in der Entity-Klasse, nicht inline duplizieren.

**XML-Layouts:**
- Overlay-Farbe: `@color/overlay_dim` — NICHT hardcoded `#80000000`
- Farben/Dimensionen: Ausschließlich via `@color/*` / `@dimen/*` Ressourcen
- Buttons: `<TextView>` mit Click-Handler (konsistent mit restlicher App), nicht `<Button>`
- Accessibility: Alle interaktiven Elemente (Spinner, Buttons, clickable TextViews) brauchen `android:contentDescription`. EditTexts mit `android:hint` brauchen KEIN contentDescription

**Migrationen:** Bei destruktiven Operationen (Records löschen, Spalten droppen) vorher `Log.w()` mit Anzahl betroffener Datensätze.

## Language

German documentation, comments and variable names are preferred.

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> [!WARNING]
> `copyToRelease` and `publishReleaseArtifact` have side effects:
> - `copyToRelease` copies the debug APK to `ops/release/` **and increments** `ops/release/version.txt`.
> - `publishReleaseArtifact` runs `copyToRelease` then `pushToGitHub`: `git add ops/release/`, `git commit --allow-empty`, `git push`.
>
> **Safe local build (no side effects):** `./gradlew assembleDebug`

## Build Commands

- `./gradlew assembleDebug` — builds debug APK, no side effects.
- `./gradlew installDebug` — builds and installs to a connected device/emulator.
- `./gradlew copyToRelease` — copies APK to `ops/release/` and bumps version.
- `./gradlew publishReleaseArtifact` — runs `copyToRelease` then pushes to GitHub.

**No automated tests.** Do not write tests, suggest tests, or add test dependencies. Validation is done via `./gradlew assembleDebug` and manual scripts (`ops/check_only.sh`, `ops/test_schedule.sh`) with a connected device.

## Glossary

- **Task**: Main work item (`Task` / `TaskCore`).
- **Slot**: One scheduled execution window (`TaskSlot`, table `task_slots`).
- **PrefSlot**: Preferred day/time pattern (`TaskPrefSlot`, table `task_pref_slots`).
- **Repetition**: How often a task repeats in a period window (`TaskCore.Repetition`).
- **Period**: Unit of repetition — day, week, or month (`Period` enum).
- **Streak**: Consecutive successful periods (`TaskCore.History.currentStreak`).
- **Adaptive**: Auto-adjust preferred times from real completion data (`TaskCore.adaptive`).
- **Checklist mode**: Today's scheduled slots only, sorted by time.
- **Manage mode**: Today's tasks including unscheduled, grouped by task hierarchy, sorted by title.
- **TransactionDirection**: `INCOME` or `EXPENSE` — direction of a budget transaction (`features/budget/domain/TransactionDirection`).
- **CarryoverDebt**: `TaskCore.Repetition.carryoverDebt` — unpaid reps from missed periods, accumulated when `completeFirst=true`.

## Architecture

**MVVM + Room**, feature-based packages, layered UI → Application → Domain → Data.

Top-level packages under `src/main/java/com/autosecretary/`:
- **`features/task/`** — scheduling, slot generation, task lifecycle
- **`features/budget/`** — transactions, CSV/PDF import, recurring pattern detection, balance chart, home screen widget
- **`app/`** — `AppCompositionRoot` (DI root), `MainActivity`, `AutoSecretaryApplication`, `UpdateChecker`, settings
- **`shared/`** — cross-feature enums: `Priority` (values: LOW=100, MEDIUM=200, HIGH=400, CRITICAL=10000), `Period`
- **`database/`** — `AppDatabase` (Room DB class) + `Converters` (type converters for `LocalDate`, `LocalTime`, `LocalDateTime`, `DayOfWeek`, all domain enums, and `Set<DayOfWeek>` as comma-separated string)
- **`util/`** — `TreeBuilder<T>` generic depth-first tree traversal utility used by both task hierarchy and slot hierarchy views

Both features share a single-threaded `ExecutorService` wired in `AppCompositionRoot` (`app/`). All DB access runs on this executor; results post to main via `Handler`. **`AppCompositionRoot` is the manual DI root** — read it to understand wiring. It also exposes `resetForDataReload()` to re-wire after a data import.

### Key non-obvious design choices

**`Task` is a Room POJO, not a `@Entity`.** Room assembles it via `@Embedded` + `@Relation` from five tables: `task_core`, `task_slots`, `task_relation`, `task_pref_slots`, `task_prerequisites`. `TaskCore` uses `@Embedded` for three inner classes (`Repetition`, `Progress`, `History`) with column prefixes (`repetition_`, `progress_`, `history_`).

**`TaskListItem`** (application layer) is a flat read model produced by `TaskListItemMapper`. **`ViewSlot`** (presentation layer, in `ui/list/state/`) wraps it for RecyclerView and adds `depth` for tree indentation.

**Two-phase checkoff:** First checkbox tap sets `slot.realStart` (STARTED → green row background). Second tap sets `slot.realEnd` + `slot.completed = true` (COMPLETED). Adaptive tasks update `TaskPrefSlot.start` via EMA on completion.

**Slot scoring** lives in `TaskScorer` (`features/task/domain/internal/scheduling/`). Composite score: priority base → child priority inheritance → day constraint → preferred-time fit → urgency → aging. Call `scorer.maintenance(task)` once before the scoring loop to pre-compute and cache constants; `scorer.reset()` at the start of each daily run.

**Task → Budget integration:** `TaskCore` has three optional fields — `budgetRequiredCents`, `budgetAccountId`, `budgetCategoryId`. When a task completes with `budgetRequiredCents > 0`, `CheckOffTaskUseCase` calls `BookTaskCompletionExpenseUseCase` to auto-book an expense against the linked account.

**Budget recurring templates:** `BudgetRecurringTemplateEntity` (table `budget_recurring_template`) stores payee/amount/schedule patterns detected by `RecurringPatternDetector`. `BudgetCategory` has `icon` and `colorHex` fields for display. `BudgetTransactionEntity` has `transactionKind` (`STANDARD` / `INTERNAL_TRANSFER`) and `linkedTransactionId` for transfer pairs.

**Budget import pipeline:** `StatementFileParser` (`features/budget/application/importing/`) routes by file type. CSV is parsed locally (columns: `date,amountCents,payee,description,[categoryId],[importHash]`). PDF files are base64-encoded and sent to the Claude API via `ClaudeStatementApiClient` (model `claude-sonnet-4-20250514`, 30s connect / 120s read timeout). `ClaudeApiKeyStore` (`features/budget/data/api/`) stores the key in SharedPreferences — the user must configure it in budget settings before PDF import works.

**Self-update:** `UpdateChecker` (`app/update/`) fetches `ops/release/version.txt` from the GitHub raw URL on each app start, compares it against the version code baked into the build, and prompts the user to install the new APK if the remote is higher. This is why `publishReleaseArtifact` increments `ops/release/version.txt`.

**Android alarm/boot integration:** `BootReceiver` re-registers daily planning on `BOOT_COMPLETED`. `DailyPlanningReceiver` handles the custom daily alarm action, calls `RegenerateScheduleUseCase` via `goAsync()`, and updates widgets on completion. `DailyPlanningScheduler` uses `AlarmManager.setExactAndAllowWhileIdle()` with fallback to `setAndAllowWhileIdle()` when exact alarms are not permitted.

### Conventions

- **Package layout:** Public entry points stay in stable packages (`features/task/ui/list/`, `features/task/application/*UseCase`). Implementation helpers go in `internal/` sub-packages.
- **`task/application/` sub-packages** (see `features/task/application/README.md`):
  - Root — top-level entry-point use-cases and `TaskDataService`.
  - `calendar/` — `TaskCalendarService` contract and DTOs.
  - `config/` — `TaskScheduleConfigRepository`/`Service` abstractions.
  - `listmodel/` — `TaskListItem` and `TaskListItemMapper` (never `model/`).
  - `internal/` — Android/infrastructure implementations: `calendar/CalendarReader`, `mutations/TaskSlotToggleMutation`, `scheduling/` receivers.
- **`budget/domain/timeline/`** — `AccountBalanceTimelineService` and balance chart data structures (`BalanceTimelinePoint`, `DailyDeltaPoint`, `MonthlyDeltaPoint`).
- **`budget/domain/importing/`** — `ImportCategory` (uses `TransactionDirection`) and `ImportTransactionRecord`.
- **Layout naming:** `<feature>_<surface>_<kind>` — e.g. `task_row_item.xml`, `budget_add_transaction_dialog.xml`. Kind is one of: `activity`, `fragment`, `item`, `widget`, `dialog`.
- **UI language:** All user-facing text in **German** — "Generieren", "Speichern", "Neue Task", etc.

## Rules

- **DB version 21**, `exportSchema = false`. Schema changes: bump version only. Room uses `fallbackToDestructiveMigration()`. **Manual migrations (`Migration` subclasses, `.addMigrations(...)`) are strictly forbidden.**
- **`android.nonTransitiveRClass=true`** — use the app's own R class for all resource references.
- Java 17, Room 2.6.1 (annotation processor, not KSP), AGP 8.7.3, Gradle 8.10.2 (`./gradlew` wrapper only).
- All `@PrimaryKey` fields are `String` UUIDs.

## Not Yet Implemented

- `SchedulingType.TERMIN` and `fixedDate`/`fixedStart`/`fixedEnd`/`fixedDuration` exist in the data model but are not exposed in `TaskEditDialog`.
- `BudgetLimitDao` limit-based tracking exists but is not fully surfaced in UI.
- `TaskDAO.deleteCore()` and `Task.setParentId()` exist but are never called.

## Commit Conventions

Short imperative subject lines. Optional prefixes: `fix(scope):`, `feat(scope):`, `refactor(scope):`. Common scopes: `ui`, `build`, `domain`, `data`, `scheduling`.

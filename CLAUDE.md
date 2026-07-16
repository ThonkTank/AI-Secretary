# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> [!WARNING]
> `copyToRelease` and `publishReleaseArtifact` have side effects:
> - `copyToRelease` copies the debug APK to `ops/release/` **and increments** `ops/release/version.txt`.
> - `publishReleaseArtifact` runs `copyToRelease` then publishes `AutoSecretary.apk` plus `version.txt` as a GitHub Release through `gh release create`.
>
> **Safe local build (no side effects):** `./gradlew assembleDebug`

## Build Commands

- `./gradlew checkArchitecture` — runs the ArchUnit architecture rules (alias onto `testDebugUnitTest`).
- `./gradlew assembleDebug` — builds debug APK, no side effects.
- `./gradlew installDebug` — builds and installs to a connected device/emulator.
- `./gradlew copyToRelease` — copies APK to `ops/release/` and bumps version.
- `./gradlew publishReleaseArtifact` — runs `copyToRelease` then publishes a GitHub Release.

**Testing policy.** Every required behavior invariant must be cleanly and findably documented and covered by tight end-to-end tests (JVM tests under `src/test`, Robolectric + in-memory Room, driving UI ViewModel/DataService → application → domain → data and asserting observable outcomes). A test names the invariant it protects. Run with `./gradlew testDebugUnitTest`. This replaces the former "no automated tests" rule. Additional validation via `./gradlew assembleDebug` and manual scripts (`ops/check_only.sh`, `ops/test_schedule.sh`) with a connected device.

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

**MVVM + Room**, feature-based packages, layered UI -> Application -> Domain -> Data. The layer and feature boundaries are enforced by ArchUnit rules on real bytecode (`ArchitectureRulesTest` in `src/test`; see the Conventions section) rather than a custom build-script linter.

Top-level packages under `src/main/java/com/autosecretary/`:
- **`features/task/`** — scheduling, slot generation, task lifecycle
- **`features/budget/`** — transactions, CSV/PDF import, recurring pattern detection, balance chart, home screen widget
- **`features/meal/`** — meal planning, recipe management, pantry, shopping lists, weekly food targets; backed by Room (same as task/budget). `MealPlannerDataService` is the application-layer facade (UI obtains it via `AutoSecretaryApplication.from(context)`), and delegates to focused meal use cases (`LoadMealHomeUseCase`, `LoadMealWeeklyProgressUseCase`, `MealPlanMutationUseCase`, `MealShoppingUseCase`). Data layer uses `Meal*Entity` classes in `data/entity/`, `Meal*Dao` in `data/dao/`, and `Meal*RoomRepository` in `data/repository/`.
- **`features/assistant/`** — the multi-domain Claude chat tab (over task, meal, budget). Own feature with `application/` (chat engine + tool registry/handlers in `internal/`) and `ui/`; no domain/data of its own. Reaches other features only through their domain layer plus a narrow task seam (see the Multi-domain assistant note below). Resources live in `res-assistant/` (`assistant_*` identifiers).
- **`app/`** — `AppCompositionRoot` (DI root), `MainActivity`, `AutoSecretaryApplication`, `UpdateChecker`, settings
- **`shared/`** — cross-feature enums/contracts and neutral utilities: `Priority` (values: LOW=100, MEDIUM=200, HIGH=400, CRITICAL=10000), `Period`, `MealType`, `WidgetRefreshNotifier`, `ContentDocumentReader`; and `WidgetConfiguration` (shared update-period constant for task and budget widgets)
- **`database/`** — `AppDatabase` (Room DB class) + `Converters` (type converters for `LocalDate`, `LocalTime`, `LocalDateTime`, `YearMonth`, `DayOfWeek`, all domain enums, and `Set<DayOfWeek>` as comma-separated string)
- **`util/`** — `TreeBuilder<T>` generic depth-first tree traversal utility used by both task hierarchy and slot hierarchy views

`AppCompositionRoot` (`app/`) owns two named executors: `dbExecutor` for all Room/repository/DAO work and `ioExecutor` for file and network work. I/O classes stay synchronous and executor-free; callers choose the executor. Results post to main via `Handler`. **`AppCompositionRoot` is the manual DI root** — read it to understand wiring. Feature code must not import `app/`. The root also owns `AppDatabase.getInstance()` / `AppDatabase.closeAndReset()` calls and exposes `resetForDataReload()` to re-wire after restore/reset. Feature entry points obtain their dependencies via `AutoSecretaryApplication.from(context)`, which exposes the wiring from `AppCompositionRoot`; feature code imports only `AutoSecretaryApplication` from `app/`, never `AppCompositionRoot` or other app internals.

### Key non-obvious design choices

**`Task` is a Room POJO, not a `@Entity`.** Room assembles it via `@Embedded` + `@Relation` from six tables: `task_core`, `task_slots`, `task_relation`, `task_pref_slots`, `task_prerequisites`, `task_planned_meals`. `TaskCore` uses `@Embedded` for three inner classes (`Repetition`, `Progress`, `History`) with column prefixes (`repetition_`, `progress_`, `history_`). `TaskPlannedMeal` (table `task_planned_meals`, composite PK `taskId`+`day`) links a task to a planned meal for a given date; `TaskCore.mealType` identifies the associated meal type. On completion, `TaskCompletionEffects` calls `TaskMealCompletionFromMealPlanner`, which records meal consumption directly against the meal-domain repositories (cross-feature access goes through the foreign domain layer).

**`TaskListItem`** (application layer) is a flat read model produced by `TaskListItemMapper`. **`ViewSlot`** (presentation layer, in `ui/list/state/`) wraps it for RecyclerView and adds `depth` for tree indentation.

**Two-phase checkoff:** Logic lives in `TaskCompletionService` (`features/task/domain/`). First checkbox tap sets `slot.realStart` (STARTED → green row background). Second tap sets `slot.realEnd` + `slot.completed = true` (COMPLETED). Duration under 3 s ("quick tap") or over 24 h ("stale") is excluded from history statistics. Adaptive tasks update `TaskPrefSlot.start` via EMA (α=0.2) on completion via `TaskLifecycleManager.adaptPrefSlot()`.

**`TaskLifecycleManager`** (`features/task/domain/`) is a stateless domain service for period advancement (`advancePeriods`), streak tracking (`updateStreakForCompletion`), and adaptive pref-slot adjustment (`adaptPrefSlot`, `adaptPrerequisiteGap`). Called by `TaskScorer` during maintenance and by `CheckOffTaskUseCase` on completion.

**`TaskTreeOperations`** (`features/task/domain/`) — static utility wrapping `TreeBuilder` to build and flatten task trees. Used by `RegenerateScheduleUseCase` before bulk DB writes.

**Slot scoring** lives in `TaskScorer` (`features/task/domain/internal/scheduling/`). Composite score: priority base → day constraint → preferred-time fit → urgency (capped ramp; overdue grows per day instead of a cliff) → follow-up boost → aging → spread penalty. **Placement selection is priority-tier-first** (`DefaultTaskSlotGenerator.placementPreferred`): every net-positive placement of a higher priority tier is applied before any lower-tier one, and a chain can never displace a slot of a strictly higher tier — the score orders placements only *within* a tier. Tasks without repetition (`reps == 0`) never pass the hard gate (`repsPerDay() == 0`) and are not auto-scheduled. **Buffers:** global `SchedulingTuning` (pause between movable slots, lead time before appointments — device-calendar events and TERMINE), stored in `SchedulingSettings` (SharedPreferences), applied as effective interval bounds in the competitive loop only; TERMIN pinning uses raw bounds. Call `scorer.maintenance(task, day, state)` once per task before scoring to pre-compute and cache constants; `scorer.reset()` at the start of each daily run; `scorer.setTransitionStats(stats)` once per run to load learned A→B transition patterns. **Public contracts in `domain/scheduling/`:** `TaskSlotGenerator` (main scheduling interface), `TaskPlanningState` (mutable cross-day state tracking which days each task was placed, enabling evenly distributed repetitions), `TaskSlotGenerationResult` (created-slot count + `List<SchedulingConflict>`), `SchedulingConflict` (reason codes: `OUTSIDE_WINDOW`, `CALENDAR_OVERLAP`, `PREREQUISITE_BLOCKED`, `NO_MATCHING_GAP`), `TaskBudgetEligibilityService` (domain interface for budget-feasibility gating during scheduling; implemented by `TaskBudgetEligibilityFromBudgetLookup` in `application/internal/budget/`). **Transition stats:** DB entity `TaskTransitionStat` (table `task_transition_stats`, composite PK `fromTaskId`+`toTaskId`) stores learned A→B sequences; `TaskTransitionStatLoader` maps them to `TransitionStat` domain records fed into the scorer for follow-up boosts.

**Task → Budget integration:** `TaskCore` has three optional fields — `budgetRequiredCents`, `budgetAccountId`, `budgetCategoryId`. When a task completes with `budgetRequiredCents > 0`, `CheckOffTaskUseCase` calls `BookTaskCompletionExpenseUseCase` to auto-book an expense against the linked account.

**Multi-domain assistant** (`features/assistant/`): its own feature — a full Claude Messages chat over the task, meal, and budget domains. `AssistantChatUseCase` (`application/`) is a lean loop engine: it drives the Messages loop, the per-model ADAPTIVE→ENABLED→NONE thinking-fallback ladder, and conversation bookkeeping, but owns no tool logic. Tools live in `application/internal/` as an `AssistantToolRegistry` of `AssistantTool` records — one object bundles a tool's name, schema, progress label, and handler; its `Kind` (READ/PROPOSAL) drives wire ordering (all reads advertised before all proposals). Per-domain handlers (`TaskTools`, `MealTools`, `BudgetTools`) each co-locate a tool's JSON schema with the parser it must match; `AssistantJson` and `DbCalls` are shared helpers. Read tools (`get_*`) answer from the live DB on the `dbExecutor`; write tools (`propose_*`) never write — they park a validated `PendingProposal` (`AssistantProposals`) shown as a confirmation card, and only `ConfirmAssistantProposalUseCase` executes it on user tap. **Failed-turn rollback:** a turn appends messages as it loops; on any failure `AssistantChatUseCase` truncates `AssistantConversation` back to its pre-turn size (`size()`/`rollbackTo(int)`), so a dangling assistant `tool_use` with no `tool_result` can't 400 the next request. **UI** (`ui/`): the whole screen renders from one immutable `AssistantUiState` on a single `LiveData`; the history is a RecyclerView + `AssistantHistoryAdapter` diffing immutable `ExchangeItem`s (items by id, contents by reference equality) so a streamed tick rebinds only the pending row. **Cross-feature boundary:** the assistant reaches meal and budget only through their domain layers via gateways in `application/internal/` (`AssistantMealGateway`, `AssistantBudgetGateway`, `AssistantTransactionImportExecutor`); the task feature it touches through a narrow, ArchUnit-enforced seam — the `TaskDao`/`TaskCategoryDao`/`TaskCategoryWindowDao` read DAOs (task has no repository; its POJOs are the domain model) plus `ApplyTaskChangesUseCase`/`UndoTaskChangesUseCase` (for the undo snapshot). The task-domain proposal types (`ChangeOp`, `TaskAssistantProposal`, `TaskSnapshot`) stay in `features/task/domain/assistant/`; `TaskAssistantProposal` also carries `windowChanges` so `get_category_windows`/`propose_category_window_changes` (reserved per-weekday category time blocks, `TaskCategoryWindow`) reuse the same apply/undo path — the apply step invalidates the shared `TaskCategoryWindowRepository` scheduler cache. Model (`ClaudeModelStore`), endpoint (`ClaudeEndpointStore`) and thinking are user-configurable in the chat; PDF/txt/md attachments are supported, and an attached bank statement imports through the same dedup/fingerprint pipeline as the budget file import (fingerprint kept in sync with `BudgetImportUseCase.buildTransactionFingerprint`).

**Budget recurring templates:** `BudgetRecurringTemplateEntity` (table `budget_recurring_template`) stores payee/amount/schedule patterns detected by `RecurringPatternDetector`. Schedule type is encoded via `RecurringType` enum (`MONTHLY_DAY` — fixed day-of-month; `MONTHLY_LAST` — last day; `WEEKLY` — fixed day-of-week; `INTERVAL` — N-day interval), with `recurringValue` (day or interval) and `recurringDayOfWeek` as companions. `BudgetCategory` has `icon` and `colorHex` fields for display. `BudgetLimit` (table `budget_limit`, unique index on `categoryId`+`yearMonth`) stores per-category-per-month spending caps with optional rollover configuration (carryover adjustment, surplus/deficit caps); used by `CalculateEffectiveBudgetLimitUseCase`. `BudgetTransactionEntity` has `transactionKind` (`STANDARD` / `INTERNAL_TRANSFER`) and `linkedTransactionId` for transfer pairs. `TransferDetails` (`features/budget/domain/`) is a value record (sourceAccountId, targetAccountId, amountCents, bookingDate, note) used by `BudgetRepository.createTransfer()` and `updateTransfer()` to avoid long parameter lists.

**Budget import pipeline:** `StatementFileParser` (`features/budget/application/importing/`) routes by file type. CSV is parsed locally (columns: `date,amountCents,payee,description,[categoryId],[importHash]`). PDF files are base64-encoded and sent to the Claude API via `ClaudeStatementApiClient`, which delegates HTTP transport (30s connect / 120s read timeout) to the shared `ClaudeMessagesClient` and reads the user-selectable model from `ClaudeModelStore` (default `claude-sonnet-5`) and the endpoint from `ClaudeEndpointStore` — the same configuration the assistant chat uses; no model is hardcoded. `ClaudeStatementApiClient` lives in `features/budget/data/api/`; the shared `ClaudeMessagesClient` transport, its wire-shape records, and the `ClaudeApiKeyStore`/`ClaudeModelStore`/`ClaudeEndpointStore` config stores live in `shared/` (the assistant and the budget importer both use them). `ClaudeApiKeyStore` encrypts the key via the Android Keystore — the user must configure it before PDF import or the assistant works. The domain contract for import operations is `BudgetImportRepository` (separate from `BudgetRepository`), implemented by `BudgetImportRoomRepository` in `data/repository/`. `BudgetImportRepository` covers: import lifecycle (create/complete/fail records), batch transaction persistence, recurring template creation, and `synchronizeRecurringTemplateState()`. Each import session is tracked by `BudgetImportEntity` (table `budget_import`), which stores a `fileHash` for deduplication and transitions through `ImportStatus` states (PENDING → COMPLETED/FAILED); managed by `BudgetImportDao`.

**Self-update:** `UpdateChecker` (`app/update/`) reads the latest published GitHub Release on each app start and can also be triggered manually from Settings. The release must contain `version.txt` (integer `versionCode`) and `AutoSecretary.apk`; if the remote version is higher than the build, the app offers a direct APK install through the Android system installer.

**Android alarm/boot integration:** `BootReceiver` re-registers daily planning on `BOOT_COMPLETED`. `DailyPlanningReceiver` handles the custom daily alarm action, calls `RegenerateScheduleUseCase` via `goAsync()`, and updates widgets on completion. `DailyPlanningScheduler` uses `AlarmManager.setExactAndAllowWhileIdle()` with fallback to `setAndAllowWhileIdle()` when exact alarms are not permitted. All three live in `features/task/application/internal/alarms/`. `DeviceCalendarBlockedIntervalProvider` (implements `CalendarBlockedIntervalProvider` from `domain/scheduling/`) lives in `features/task/application/internal/calendar/`.

**Responsive re-planning:** `ScheduleReplanCoordinator` (`features/task/application/`) is the single, coalescing entry point for schedule regeneration — every scheduling-input change funnels through `requestReplan()` instead of calling `RegenerateScheduleUseCase` directly. It collapses a burst of requests into one run plus at most one follow-up, refreshes widgets, and notifies an optional listener (the open `TaskViewModel`) so the list refreshes once the schedule is rebuilt — re-planning happens even when the task tab is not visible. Triggers: manual task save/delete (`TaskEditViewModel`), assistant apply/undo (`ApplyTaskChangesUseCase`/`UndoTaskChangesUseCase`), reserved category windows (`TaskCategoryWindowViewModel`), per-weekday windows + buffer/tuning + the enable toggle (`TaskScheduleConfigViewModel`), and external device-calendar edits (a `ContentObserver` on `CalendarContract.Events` registered in `AppCompositionRoot`). **Check-off/undo/progress deliberately do NOT re-plan** — the open day stays stable during execution. Re-planning never replaces started/completed work: `RegenerateScheduleUseCase`'s delete filter skips `completed`/`realStart` slots and the generator treats them as non-displaceable.

### Conventions

- **Package layout:** Public entry points stay in stable packages (`features/task/ui/list/`, `features/task/application/*UseCase`). Implementation details usually live in `internal/` sub-packages.
- **Architecture check:** the rules live in `src/test` as ArchUnit rules on real bytecode (`ArchitectureRulesTest`), run by `./gradlew checkArchitecture` (alias onto `testDebugUnitTest`) and by `check`. They enforce the import matrix / layer boundaries, domain purity, UI-host discipline, ViewModel view/infrastructure bans, cross-feature-only-via-domain, executor ownership, and the application-no-`Presenter` naming rule. The assistant has two dedicated rules: it may reach other features only via their domain layer plus a named task seam (the three task read DAOs — `TaskDao`/`TaskCategoryDao`/`TaskCategoryWindowDao` — + `Apply`/`UndoTaskChangesUseCase`), and no feature may depend back on the assistant; a companion test asserts the seam classes exist so a rename can't silently widen the allowlist. Adding a rule = adding a test method (verify it fails on a deliberate violation). The widget/launcher XML validators still run on `preBuild`.
- **UI conventions (not machine-checked):** `Fragment`/`DialogFragment` `observe(...)` calls must pass `getViewLifecycleOwner()`; `registerForActivityResult(...)` must be declared in a host field initializer or in `onCreate()`. `TaskListFragment` calls `vm.refreshList()` in `onViewCreated` so the list reflects any external mutation (e.g. an assistant task change/undo) on the next show — the fragment is recreated on every tab switch while the ViewModel is activity-scoped, so no cross-ViewModel wiring is needed.
- **Repository interfaces:** one-model features (task — its Room POJOs *are* the domain model) use the DAO directly and have no repository interface; two-model features (budget, meal) keep a domain `*Repository` interface implemented by a `*RoomRepository` that maps entities to domain types and is the seam cross-feature callers depend on.
- **`task/application/` sub-packages:**
  - Root — top-level entry-point use-cases and `TaskDataService`.
  - `calendar/` — `TaskCalendarService` contract and DTOs.
  - `config/` — `TaskScheduleConfigRepository` (implements `SchedulingWindowProvider`; lazy-cached per-day scheduling windows).
  - `listmodel/` — `TaskListItem` and `TaskListItemMapper` (never `model/`).
  - `internal/` — Android/infrastructure implementations: `alarms/` (receivers), `budget/` (`TaskBudgetEligibilityFromBudgetLookup` implements `TaskBudgetEligibilityService` + `BookTaskCompletionExpenseUseCase`), `calendar/` (`CalendarReader`, `CalendarQueryHelper`, `DeviceCalendarBlockedIntervalProvider`), `mutations/TaskSlotToggleMutation`.
- **`budget/domain/timeline/`** — `AccountBalanceTimelineService` and balance chart data structures (`BalanceTimelinePoint`, `DailyDeltaPoint`, `MonthlyDeltaPoint`).
- **`budget/domain/importing/`** — `ImportCategory` (uses `TransactionDirection`), `ImportTransactionRecord`, `ImportTransactionType` (INCOME/EXPENSE/TRANSFER; maps to `TransactionDirection`+`TransactionKind`), `ImportStatus`, `ParsedStatement`, `ParsedTransaction`.
- **`budget/domain/recurring/`** — recurring pattern domain types: `RecurringBudgetTransaction`, `RecurringPatternDetector`, `RecurringScheduleParams`, `RecurringSuggestion`, `RecurringTemplateScheduler`, `RecurringType`, `TemplateStatusUpdate`; implementation helpers in `recurring/internal/`.
- **Layout naming:** `<feature>_<surface>_<kind>` — e.g. `task_row_item.xml`, `budget_add_transaction_dialog.xml`. Kind is one of: `activity`, `fragment`, `item`, `widget`, `dialog`.
- **UI language:** All user-facing text in **German** — "Generieren", "Speichern", "Neue Task", etc.

## Project Status

**The app is in active production use.** The task feature is live; real user data is in the DB. The backend/domain layer is stable and not expected to grow. Do not propose new features, new abstractions "for future extensibility", or speculative infrastructure. Changes should focus on:
- **Bug fixes** in existing behavior
- **Simplification** — removing unnecessary complexity, dead code, redundant abstractions
- **Code quality** — readability, consistency, correctness
- **UI polish** — the frontend may still receive refinements

Do not suggest adding: new modules, new domain entities, new integration points, caching layers, dependency injection frameworks, migration infrastructure, or any other "investment" that only pays off if the codebase grows further. It won't.

Extracting shared constants, small utility methods, or deduplicating repeated code into a common location is always welcome — that is simplification, not new infrastructure.

## Rules

- **DB version:** the single source of truth is `@Database(version = …)` in `AppDatabase.java`. `exportSchema = false`. Schema changes require a version bump and compatible Room migrations. Destructive fallback is not the default because user data must be preserved.
- **`android.nonTransitiveRClass=true`** — use the app's own R class for all resource references.
- Java 17, Room 2.6.1 (annotation processor, not KSP), AGP 8.7.3, Gradle 8.10.2 (`./gradlew` wrapper only).
- New entity `@PrimaryKey` fields must be `String` UUIDs. Existing exceptions: `TaskTransitionStat` and `TaskPlannedMeal` use composite PKs; `TaskScheduleConfig` uses `DayOfWeek` as PK.

## Commit Conventions

Short imperative subject lines. Optional prefixes: `fix(scope):`, `feat(scope):`, `refactor(scope):`. Common scopes: `ui`, `build`, `domain`, `data`, `scheduling`.

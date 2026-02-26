# Structure Review: `features/task/application` (developer mode)

Scope reviewed: `src/main/java/com/autosecretary/features/task/application`.

## Summary
- The package already trends toward a **clear, feature-local application layer** with useful subpackages (`config`, `calendar`, `listmodel`, `internal/*`).
- The main mental load comes from a few **boundary leaks at the top-level**: some files are entry use-cases while others are generic async services, making "where to start" less obvious.
- Internal implementation grouping is generally good, but there is one notable discoverability issue where **broadcast receiver/scheduler infrastructure sits inside `application/internal`** instead of a clearer runtime boundary package.
- Naming is mostly concrete, but a couple names (`TaskAsyncDataService`, `internal/actions`) require reading code before intent is clear.
- Overall, the structure is **more coherent than fragmented**, with targeted reorganization likely enough rather than broad churn.

## Findings

### [keep] Top-level use-case entry points are easy to scan
- **Path(s) involved**: `application/AdjustTaskProgressUseCase.java`, `application/CheckOffTaskUseCase.java`, `application/RegenerateScheduleUseCase.java`
- **What makes it hard to read/navigate today**: Minor ambiguity only—other top-level classes are not all use-cases.
- **Proposed structural change**: Keep current placement for the three use-case entry classes.
- **Why it reduces mental load**: New contributors can quickly identify user-triggered operations by class names at the package root.
- **Tradeoffs / risks**: None if preserved.

### [rename] Clarify the purpose of `TaskAsyncDataService`
- **Path(s) involved**: `application/TaskAsyncDataService.java`
- **What makes it hard to read/navigate today**: The name is broad and sounds infrastructural; it does not signal whether it is an entry API, orchestration service, or technical adapter.
- **Proposed structural change**: Rename to a task-specific orchestration name such as `TaskQueryService` or `LoadTaskListDataUseCase` (based on real responsibility).
- **Why it reduces mental load**: Better name-to-content alignment lowers lookup time and reduces "open file to confirm purpose" loops.
- **Tradeoffs / risks**: Import churn and references updates.

### [move] Separate runtime scheduling infrastructure from application orchestration
- **Path(s) involved**: `application/internal/scheduling/BootReceiver.java`, `DailyPlanningReceiver.java`, `DailyPlanningScheduler.java`
- **What makes it hard to read/navigate today**: Android runtime concerns (broadcast receivers/alarms) live under `application/internal`, which blurs application orchestration vs platform wiring.
- **Proposed structural change**: Move these files under a clearer runtime/integration boundary within the feature, e.g. `features/task/runtime/scheduling/` (or `features/task/data/scheduling/android/` if matching repo conventions).
- **Why it reduces mental load**: Readers can infer "Android lifecycle wiring" from tree location instead of discovering it only by class internals.
- **Tradeoffs / risks**: Package rename churn and manifest/import updates.

### [rename] Replace vague `internal/actions` with a behavior-oriented package name
- **Path(s) involved**: `application/internal/actions/*`
- **What makes it hard to read/navigate today**: `actions` is generic and can become a dumping ground.
- **Proposed structural change**: Rename package to a concrete intent like `internal/taskmutation` or split by behavior (`internal/progress`, `internal/completion`) if it grows.
- **Why it reduces mental load**: Concrete names improve scanning and make placement of future related files more obvious.
- **Tradeoffs / risks**: Package rename and import churn.

### [consider] Co-locate budget bridge closer to cross-feature integration boundary
- **Path(s) involved**: `application/internal/budget/BookTaskCompletionExpenseUseCase.java`, `TaskBudgetEligibilityFromBudgetLookup.java`
- **What makes it hard to read/navigate today**: Budget integration is nested under task application internals; this is workable but hides an explicit cross-feature boundary.
- **Proposed structural change**: Consider `features/task/integration/budget/` (or equivalent) if more budget adapters appear.
- **Why it reduces mental load**: Makes cross-feature touchpoints explicit and easier to audit.
- **Tradeoffs / risks**: Might be unnecessary overhead if only two stable classes remain.

### [keep] README-backed subpackage map is a strong onboarding asset
- **Path(s) involved**: `application/README.md`, subpackages `calendar/`, `config/`, `listmodel/`, `internal/`
- **What makes it hard to read/navigate today**: No significant issue.
- **Proposed structural change**: Keep and maintain as structure evolves.
- **Why it reduces mental load**: Gives a quick architecture legend and placement rules for contributors.
- **Tradeoffs / risks**: Needs periodic updates to avoid drift.

## Verdict
**Adequate**

- The current layout already communicates a mostly coherent application-layer story.
- A small set of naming and boundary adjustments would noticeably improve discoverability.
- The package is not fragmented enough to require large refactors.
- Primary gains are from clarifying runtime-vs-application and reducing generic naming.

## Suggested target structure
```text
features/task/
  application/
    AdjustTaskProgressUseCase.java
    CheckOffTaskUseCase.java
    RegenerateScheduleUseCase.java
    TaskQueryService.java          # renamed from TaskAsyncDataService
    calendar/
    config/
    listmodel/
    internal/
      completion/                  # optional rename/split from actions
      progress/
  runtime/
    scheduling/
      BootReceiver.java
      DailyPlanningReceiver.java
      DailyPlanningScheduler.java
  integration/
    budget/
      BookTaskCompletionExpenseUseCase.java
      TaskBudgetEligibilityFromBudgetLookup.java
```

EnterPlanMode

## Implementation plan (actionable findings only)

### Context
- Naming ambiguity (`TaskAsyncDataService`, `internal/actions`) increases file-open churn before intent is clear.
- Runtime Android wiring in `application/internal/scheduling` makes boundaries less obvious for new contributors.
- Cross-feature budget adapters are functional where they are, but discoverability could improve with an explicit integration boundary.

### Step-by-step changes
1. **Rename async service for intent clarity**
   - Files: `src/main/java/com/autosecretary/features/task/application/TaskAsyncDataService.java` (+ all imports/usages).
   - Change: Rename class/file to a responsibility-revealing name (`TaskQueryService` or `LoadTaskListDataUseCase`).
   - Update package README examples to match.

2. **Rename or split `internal/actions` package**
   - Files: `src/main/java/com/autosecretary/features/task/application/internal/actions/TaskProgressAdjustAction.java`, `TaskSlotToggleAction.java`, `TaskTransitionRecorder.java`.
   - Change: Either:
     - rename package to `internal/taskmutation`, or
     - split into `internal/progress` and `internal/completion` if desired for stronger co-location.
   - Update all imports from `application/*UseCase` callers.

3. **Move scheduling runtime wiring out of `application/internal`**
   - Files: `src/main/java/com/autosecretary/features/task/application/internal/scheduling/*`.
   - Change: Move to `src/main/java/com/autosecretary/features/task/runtime/scheduling/` (or team-preferred runtime boundary).
   - Update package declarations and AndroidManifest receiver class paths.

4. **(Optional) Create explicit budget integration boundary**
   - Files: `src/main/java/com/autosecretary/features/task/application/internal/budget/*`.
   - Change: Move to `src/main/java/com/autosecretary/features/task/integration/budget/` if budget adapters are expected to grow.
   - Update imports and README map.

5. **Update documentation map after structural edits**
   - Files: `src/main/java/com/autosecretary/features/task/application/README.md`.
   - Change: Refresh package map to reflect final folder names and placement rules.

### Verification
- Build: `./gradlew assembleDebug`.
- Runtime smoke checks:
  - Open task list, verify load and refresh interactions still work.
  - Check off a task and verify completion flow (including budget side-effect if enabled).
  - Trigger/resume daily planning scheduling path (boot/scheduler receiver behavior).
- Navigation sanity:
  - From feature root, verify a new contributor can find entry use-cases, runtime wiring, and budget integration without reading implementation details.

ExitPlanMode

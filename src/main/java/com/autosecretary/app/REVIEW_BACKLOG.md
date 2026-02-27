# Review Backlog — App & Layout

> Note: REVIEW_BACKLOG.md files cannot live inside `src/main/res/` (Android build rejects non-XML files there).
> Issues from `src/main/res/layout/` are tracked here as the nearest valid location.

## Open Issues — `app/`

### [consider] `getTaskViewModelFactory` mixes construction with side-effect field assignments
**File:** `AppCompositionRoot.java:75–154`

The method constructs and returns a `TaskViewModelFactory` but also silently assigns `regenerateScheduleUseCase` and `taskSlotToggleMutation` as side effects. Callers of `getRegenerateScheduleUseCase()` and `getTaskSlotToggleMutation()` trigger full task-graph initialization as a hidden side effect via delegation to this method. *Note: method was renamed `create→get` in a prior review cycle; naming inconsistency is resolved.*

**Suggested alternative:** Give `taskSlotToggleMutation` and `regenerateScheduleUseCase` their own lazy-init logic in their respective getters, separated from the factory construction path.

**Tradeoff:** Splitting out the getters duplicates some DB/handler setup. Lower-effort alternative: extract a private `initTaskGraph()` that populates all three fields, and have all three public getters call it.

## Open Issues — `src/main/res/layout/`

### [warning] `budget_overview_fragment.xml` — BudgetTransactionList inflates into LinearLayout, not RecyclerView
**File:** `src/main/res/layout/budget_overview_fragment.xml:296–301`
**Smell:** All transaction rows are always inflated into a vertical LinearLayout inside a ScrollView. As the number of transactions grows this causes progressively worse memory and layout-pass overhead. RecyclerView would recycle views.
**Fix:** Replace the LinearLayout inflation pattern in `BudgetOverviewLoader` with a RecyclerView + adapter. Requires Java code changes.

### [warning] `budget_overview_fragment.xml` — Summary card row pattern duplicated 4×
**File:** `src/main/res/layout/budget_overview_fragment.xml:136–227`
**Smell:** The "label left + value right" horizontal LinearLayout pattern is copy-pasted four times (income, expense, free budget, net). If spacing, textAppearance, or gravity needs changing it must be applied in all four places.
**Fix:** Extract into a reusable `budget_summary_row_item.xml` and inflate in code, or at minimum add a comment grouping them so future changes are obvious.

### [nit] Label TextViews without IDs in budget transfer and limit dialogs
**Files:** `src/main/res/layout/budget_transfer_dialog.xml:8–12`, `budget_transfer_dialog.xml:19–24`, `budget_edit_limit_dialog.xml:8–13`
**Smell:** Three label TextViews have no `android:id`, making them unreferenceable from code. If a section ever needs programmatic show/hide, the label can't be targeted.
**Fix:** Add IDs: `BudgetTransferSourceAccountLabel`, `BudgetTransferTargetAccountLabel`, `BudgetLimitCategoryLabel`.

### [nit] ID naming inconsistency — `task_row_widget.xml` uses snake_case, all others use PascalCase
**File:** `src/main/res/layout/task_row_widget.xml`
**Smell:** Widget row IDs (`widget_row_start`, `widget_row_end`, `widget_row_checkbox`, `widget_row_title`, `widget_row_streak`) use snake_case while every other layout uses PascalCase. RemoteViews don't require this distinction.
**Fix:** Rename to PascalCase (`WidgetRowStart`, etc.) and update all usages in the widget provider Java class.

# Layout Directory — `res/layout/`

## File Naming Convention

All layout files follow `<feature>_<surface>_<kind>.xml`:

| Feature  | Surfaces                                                  |
|----------|-----------------------------------------------------------|
| `app_`   | `main_activity`                                           |
| `task_`  | `list_fragment`, `editor_dialog`, `row_item`, `row_widget`, `list_widget`, `schedule_config_dialog`, `schedule_day_item` |
| `budget_`| `overview_fragment`, `widget`, `add_transaction_dialog`, `edit_limit_dialog`, `transfer_dialog`, `recurring_suggestions_dialog`, `recurring_suggestion_item`, `transaction_item`, `limit_bar_item` |
| `meal_`  | `overview_fragment`, `plan_create_dialog`, `need_create_dialog`, `pantry_create_dialog`, `plan_row_item`, `text_row_item` |

**Kind** is one of: `activity`, `fragment`, `item`, `widget`, `dialog`.

## ID Naming Convention

View IDs use **PascalCase** throughout (e.g., `@+id/BudgetDialogAmount`, `@+id/TaskTitle`).
This is intentional and consistent — do not rename to `snake_case`.

IDs are typically prefixed by feature area:
- `Budget*`, `BudgetDialog*`, `BudgetLimit*`, `BudgetTransfer*`, `BudgetWidget*`
- `Task*`, `Edit*` (task editor fields), `Widget*` (task widget)
- `Meal*`, `MealDialog*`, `MealPlan*`, `MealPantry*`

## Common Patterns

### Dynamic content containers

Several layouts use empty `LinearLayout` containers whose children are inflated
programmatically in Java code (not via adapters). These look like dead code in the XML
but are populated at runtime. Look for XML comments like
`<!-- items inflated programmatically by XxxController -->`.

Examples: `BudgetTransactionList`, `BudgetLimitBarsContainer`, `BudgetRecurringSuggestionList`,
`PrefSlotContainer`, `ScheduleConfigContainer`, `MealWeekList`, `MealRecipeList`.

### Widget layouts (`*_widget.xml`)

Widget layouts are rendered via Android's `RemoteViews` API, which only supports a limited
set of platform views (no `RecyclerView`, no custom views). `ListView` is used instead of
`RecyclerView` in `task_list_widget.xml` — this is correct and intentional.

Each widget layout has a mandatory companion **AppWidgetProviderInfo** XML file in `../xml/`:
- `task_list_widget.xml` → `../xml/widget_task_info.xml` (min size, update period, resize mode)
- `budget_widget.xml` → `../xml/widget_budget_info.xml`

When adding or editing a widget layout, check the companion info file too — update period,
minimum dimensions, and initial layout are all declared there, not in the layout XML.

See: https://developer.android.com/develop/ui/views/appwidgets#remoteviews

### Visibility toggling

Many views start with `android:visibility="gone"` and are shown/hidden at runtime based on
state (e.g., loading spinners, empty states, conditional sections in the task editor).
Comments in the XML explain when each hidden view becomes visible.

## Quality Tiers

All three features (task, budget, meal) use `@dimen`, `@color`, `@style`, and
`@string` resource references throughout. When adding new layouts, follow the same convention.

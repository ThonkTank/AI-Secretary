# REVIEW_BACKLOG — src/main/res

## Open issues

---

### [inconsistent] Widget layout view IDs use snake_case; all other layouts use PascalCase

**Concept / area:** View ID naming convention

**Observed patterns:**
- PascalCase (dominant, all feature layouts): `StartTime`, `TaskCheckBox`, `DayNavPrev`, `BudgetTitle`, `BudgetDialogAmount`, `EditTitle`, etc. — across `task_row_item.xml`, `task_list_fragment.xml`, `task_editor_fragment.xml`, `budget_overview_fragment.xml`, `budget_add_transaction_dialog.xml`, `budget_limit_bar_item.xml`, `budget_recurring_suggestion_item.xml`, `task_schedule_day_item.xml`
- snake_case (minority, widget layouts only): `widget_prev_day`, `widget_date_label`, `widget_row_start`, `widget_row_checkbox`, `budget_widget_total_value`, `budget_widget_open_button` — in `task_list_widget.xml`, `task_row_widget.xml`, `budget_widget.xml`

**Canonical recommendation:** PascalCase for all view IDs, matching every non-widget layout in the project.

**Impact:** ~20 view IDs across 3 widget layouts, plus corresponding Java references in `TaskWidgetProvider.java`, `TaskWidgetFactory.java`, `BudgetWidgetProvider.java`. Requires coordinated res + Java changes — deferred.

---

### [inconsistent] Budget widget text styling uses raw attributes; task widgets use textAppearance styles

**Concept / area:** Text styling in widget layouts

**Observed patterns:**
- Task widgets (`task_list_widget.xml`, `task_row_widget.xml`): `android:textAppearance="@style/TextAppearance.Task.Widget.NavAction"`, `@style/TextAppearance.Task.Metadata"`, etc.
- Budget widget (`budget_widget.xml`): raw `android:textSize`, `android:textColor`, `android:textStyle` on each TextView — no `textAppearance` style used

**Canonical recommendation:** Define `TextAppearance.Budget.Widget.Header` and `TextAppearance.Budget.Widget.Value` styles in `styles.xml` and apply them in `budget_widget.xml`. Requires adding new styles — deferred.

**Impact:** 5 TextViews in `budget_widget.xml`.

---

### [warning] `budget_overview_fragment.xml` — BudgetTransactionList inflates into LinearLayout, not RecyclerView
**File:** `src/main/res/layout/budget_overview_fragment.xml:296–301`
**Smell:** All transaction rows are always inflated into a vertical LinearLayout inside a ScrollView. As the number of transactions grows this causes progressively worse memory and layout-pass overhead. RecyclerView would recycle views.
**Fix:** Replace the LinearLayout inflation pattern in `BudgetOverviewLoader` with a RecyclerView + adapter. Requires Java code changes. Deferred.

*(Promoted from `app/REVIEW_BACKLOG.md`)*

---

### [warning] `budget_overview_fragment.xml` — Summary card row pattern duplicated 4×
**File:** `src/main/res/layout/budget_overview_fragment.xml:136–227`
**Smell:** The "label left + value right" horizontal LinearLayout pattern is copy-pasted four times (income, expense, free budget, net). If spacing, textAppearance, or gravity needs changing it must be applied in all four places.
**Fix:** Extract into a reusable `budget_summary_row_item.xml` and inflate in code, or at minimum add a comment grouping them so future changes are obvious. Deferred.

*(Promoted from `app/REVIEW_BACKLOG.md`)*

---

### [nit] Label TextViews without IDs in budget transfer and limit dialogs
**Files:** `src/main/res/layout/budget_transfer_dialog.xml:8–12`, `budget_transfer_dialog.xml:19–24`, `budget_edit_limit_dialog.xml:8–13`
**Smell:** Three label TextViews have no `android:id`, making them unreferenceable from code. If a section ever needs programmatic show/hide, the label can't be targeted.
**Fix:** Add IDs: `BudgetTransferSourceAccountLabel`, `BudgetTransferTargetAccountLabel`, `BudgetLimitCategoryLabel`. Deferred.

*(Promoted from `app/REVIEW_BACKLOG.md`)*

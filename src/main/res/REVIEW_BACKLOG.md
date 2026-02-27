# REVIEW_BACKLOG — src/main/res

## Open issues

---

### [consider] Budget overview LinearLayout inflation instead of RecyclerView

**File:** `layout/budget_overview_fragment.xml:296–301`
**What makes it hard today:** All transaction rows inflate into a LinearLayout inside a ScrollView. As transaction count grows, this causes progressively worse memory and layout-pass overhead.
**Proposed change:** Replace with RecyclerView + adapter.
**Why it reduces mental load:** Standard Android pattern; easier to reason about performance.
**Tradeoffs:** Requires Java adapter/ViewHolder code in `BudgetFragment` — significant scope. Deferred.

---

### [consider] Budget overview summary card row pattern duplicated 4×

**File:** `layout/budget_overview_fragment.xml:136–227`
**What makes it hard today:** The "label left + value right" horizontal LinearLayout pattern is copy-pasted four times (income, expense, free budget, net). Changes must be applied in all four places.
**Proposed change:** Extract into a reusable `budget_summary_row_item.xml`.
**Why it reduces mental load:** Single source of truth for the row pattern.
**Tradeoffs:** Requires Java inflate logic changes. Deferred.


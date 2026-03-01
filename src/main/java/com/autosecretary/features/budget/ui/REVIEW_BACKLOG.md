# Budget UI – Review Backlog

## Open Issues

### [warning] `renderLimitBars` + `renderTransactions` — full reinflation on every reload @skill:review-performance
**File:** `BudgetFragment.java` (renderLimitBars / renderTransactions methods)

Full inflate-and-addView loops with `removeAllViews()` on every LiveData emission. For 50+ transactions this is significant main-thread work. Consider RecyclerView or diffing to avoid reinflation when content is unchanged.

### [platform] RadioGroup with plain RadioButtons for time range filter — should use Material Chips or SegmentedButton
**File:** `res/layout/budget_overview_fragment.xml` lines 39–66

The chart time range selector (30d / 3m / 12m) uses three plain `RadioButton` elements in a `RadioGroup`. Material Design 3 provides Segmented Buttons (`com.google.android.material.button.MaterialButtonToggleGroup`) or Filter Chips for mutually exclusive option selection — both have better visual affordance and match platform conventions more closely.

**Deferred:** Requires layout and Java listener changes; scope exceeds a single-file fix.

### [a11y] Chart canvas label text size hardcoded as `dp(10f)` — bypasses text scale system
**File:** `BudgetBalanceChartView.java` line 63

```java
labelPaint.setTextSize(dp(10f));
```

Canvas text uses `dp` not `sp`, so it does not respect Android's user font size preference. The value is also smaller than the app's minimum token (`text_xs = 12sp`). Since this is a custom canvas view the limitation is intrinsic to the API; however, the size could be read from a dimen resource so it's at least a single-source-of-truth for the value.

**Deferred:** Changing Canvas text to sp requires converting via `DisplayMetrics.scaledDensity`; warrants a separate targeted fix.

### [friction] Date fields require manual ISO typing — no date picker
**File:** `budget_add_transaction_dialog.xml`, `budget_transfer_dialog.xml`

Both dialogs use a plain `TextInputEditText` with `inputType="date"` and hint `"Datum (YYYY-MM-DD)"`. On Android, `inputType="date"` does not launch a native date picker — it only suggests a numeric keyboard. Users must type the exact ISO format; German-locale users who instinctively write "01.03.2026" will get a validation error with no guidance.

**Deferred:** Replacing with `MaterialDatePicker` requires new picker logic and fragment manager integration in both dialog controllers — multi-file change warranting a dedicated fix cycle.

### [consider] `UiText.resolve()` — two-branch `formatArgs` check could be one line
**File:** `UiText.java` lines 36–39

```java
if (formatArgs.length > 0) {
    return context.getString(resId, formatArgs);
}
return context.getString(resId);
```

Could collapse to `return context.getString(resId, formatArgs)` since passing an empty array to the varargs overload is equivalent when the format string has no `%` specifiers. However, if any resource string ever contains a literal `%` (e.g. "100%"), calling the format overload with zero args would throw `MissingFormatArgumentException`. The current defensive split avoids this class of bug with no meaningful cost.

**Deferred:** Risk outweighs the ~2-line saving; keep current form.

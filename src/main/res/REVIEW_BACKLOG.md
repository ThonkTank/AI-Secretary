# Review Backlog — src/main/res

## Open Issues

---

### [warning] Path data duplication between launcher icon files
**Files:** `drawable/ic_launcher_foreground.xml` (paths in lines 7–18) and `drawable/ic_launcher_monochrome.xml` (line 9)
**Smell:** Both files contain the same geometric path definitions, split differently. Foreground uses 4 `<path>` elements for color; monochrome combines them in one.
**Why it will cause problems:** Icon design changes require updating both files in sync. Missing one causes the monochrome icon to drift. Mitigation comment already added to `ic_launcher_monochrome.xml`.
**Future fix:** Generate the monochrome version from the foreground programmatically in the build pipeline.

---

### [consider] Hardcoded text sizes in `budget_widget.xml`
**File:** `layout/budget_widget.xml` (title and value TextViews)
TextViews use raw `sp` values (`15sp`, `12sp`, `18sp`, `16sp`) rather than `TextAppearance` styles used everywhere else. Widget RemoteViews cannot reference Material theme attributes, so full parity is not possible, but app-specific `TextAppearance` styles without theme-attr references would be cleaner.
**Tradeoff:** Widget RemoteView limitations make this harder; low urgency.

---

### [consider] `task_deadline_*` aliases duplicate `task_urgency_*` colors with no current differentiation
**File:** `values/task_colors.xml` lines 36–38; consumer `ListRowAdapter.java`
**Why complex:** Three aliases (`task_deadline_overdue/soon/future`) point to the same values as `task_urgency_overdue/soon/future`. Doubles the name surface for the same colours.
**Simpler alternative:** Remove deadline aliases; reference `task_urgency_*` directly.
**Tradeoff:** Removes the ability to evolve deadline and urgency colors independently in the future without Java changes. Moderate justification for keeping them.

---

### [consider] Double alias in widget title colours
**File:** `values/task_colors.xml` lines 68, 70
**Why complex:** `task_widget_title_default` → `task_widget_text_primary` → `task_color_on_surface` (two hops). `task_widget_title_completed` → `task_widget_text_muted` → `task_color_on_surface_muted` (two hops). The `task_widget_text_*` tier is justified (direct layout use). The extra `title_*` hop could point to `task_color_*` directly.
**Simpler alternative:** Change `task_widget_title_default` to reference `@color/task_color_on_surface` and `task_widget_title_completed` to reference `@color/task_color_on_surface_muted`.
**Tradeoff:** Loses the expressive link "completed title = muted widget text".

---

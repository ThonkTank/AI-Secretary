# Review Backlog — src/main/res

## Open Issues

---

### [warning] Path data duplication between launcher icon files
**Files:** `drawable/ic_launcher_foreground.xml` (paths in lines 7–18) and `drawable/ic_launcher_monochrome.xml` (line 9)
**Smell:** Both files contain the same geometric path definitions, split differently. Foreground uses 4 `<path>` elements for color; monochrome combines them in one.
**Why it will cause problems:** Icon design changes require updating both files in sync. Missing one causes the monochrome icon to drift. Mitigation comment already added to `ic_launcher_monochrome.xml`.
**Future fix:** Generate the monochrome version from the foreground programmatically in the build pipeline.

---

### [warning] `task_strings.xml` naming lie — file contains budget and nav strings
**File:** `values/task_strings.xml` lines 103–188
**Smell:** `task_strings.xml` also contains all `budget_*` strings (~85 entries) and `nav_tasks` / `nav_budget`. The convention in `strings.xml` says "feature-specific resources belong in feature-prefixed files", but budget strings are in a task-named file.
**Why it will cause problems:** As the budget feature grows, more budget strings get added to a file that says "task". Developers will not find budget strings by searching `budget_strings.xml`. The convention violation grows with every new budget string.
**Fix:** Create `budget_strings.xml` and move all `budget_*` strings and nav strings there. No Java changes needed — R.string keys are global across XML files.

---

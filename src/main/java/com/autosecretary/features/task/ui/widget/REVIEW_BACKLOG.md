# Review Backlog — task/ui/widget

## Open Issues

---

### [nit] `new TaskListItemMapper()` in `TaskWidgetFactory` constructor bypasses composition root
**File:** `TaskWidgetFactory.java` line 31

`TaskListItemMapper` is a stateless object; creating a fresh instance is functionally correct. However, `AppCompositionRoot` owns all DI wiring for this mapper elsewhere. Since the root does not currently expose the mapper, this is low risk — worth noting as a future injection candidate if `TaskListItemMapper` ever gains state or dependencies.

**Fix:** Add a getter to `AppCompositionRoot` and pass the mapper via `TaskWidgetService`. Not worth the churn now given the mapper is stateless.

---

### [nit] Raw `new Thread(...)` in `handleToggle` vs. shared executor
**File:** `TaskWidgetProvider.java` lines 161–171

The project wires a shared `ExecutorService` in `AppCompositionRoot`. This widget spawns a raw `Thread` for background DB work, which is inconsistent with that convention. Using the shared executor would ensure consistent threading behaviour and allow potential future monitoring.

**Fix:** Pass the executor through the composition root and submit the Runnable there instead. Low priority — `goAsync()` with a raw thread is a correct and common Android widget pattern.

---

### [warning] `taskDao.readAll()` loads all tasks for in-memory filtering
**File:** `TaskWidgetFactory.java` line 50

`onDataSetChanged` fetches every task from the DB, builds all `TaskListItem`s, then discards all except those matching the selected date. For large task datasets this wastes memory and CPU on every widget refresh.

**Fix:** Add a date-scoped query to `TaskDAO` (e.g. `readAllForDate(LocalDate)`) and use it here. Requires changes outside this directory — defer.


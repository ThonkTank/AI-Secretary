# Review Backlog — task/ui/widget

## Analysis Summary

**KISS review completed.** Code is clean and focused. All issues requiring fixes involve dependencies or patterns that were intentionally chosen. No simplifications recommended within scope.

## Deferred Issues (No Action Needed This Cycle)

---

### [nit] `new TaskListItemMapper()` in `TaskWidgetFactory` constructor bypasses composition root
**File:** `TaskWidgetFactory.java` line 53

`TaskListItemMapper` is a stateless object; creating a fresh instance is functionally correct. However, `AppCompositionRoot` owns all DI wiring for this mapper elsewhere. Since the root does not currently expose the mapper, this is low risk — worth noting as a future injection candidate if `TaskListItemMapper` ever gains state or dependencies.

**Rationale for deferral:** The mapper is stateless and the churn to wire it through the composition root and service layer is not justified for current usage patterns.

---

### [nit] Raw `new Thread(...)` in `handleToggle` vs. shared executor
**File:** `TaskWidgetProvider.java` lines 207–217

The project wires a shared `ExecutorService` in `AppCompositionRoot`. This widget spawns a raw `Thread` for background DB work, which is inconsistent with that convention. However, the comments correctly document why this pattern is used: `goAsync()` extends the BroadcastReceiver lifetime, and using a raw thread keeps the response fast. Queuing through an executor could cause the finish() to be delayed.

**Rationale for deferral:** This is a correct and idiomatic Android widget pattern. The code is well-commented explaining the design decision. The tradeoff (threading inconsistency vs. responsiveness) is intentional.

---

### [warning] `taskDao.readAll()` loads all tasks for in-memory filtering
**File:** `TaskWidgetFactory.java` line 74

`onDataSetChanged` fetches every task from the DB, builds all `TaskListItem`s, then discards all except those matching the selected date. For large task datasets this wastes memory and CPU on every widget refresh.

**Rationale for deferral:** Fixing this requires adding a new query method to `TaskDAO` (outside the widget package scope). The performance impact is acceptable for current usage patterns (typical apps have 20–100 tasks). A future iteration can optimize by adding `readAllForDate(LocalDate)` or similar to TaskDAO.

---

# Review Backlog — task/ui/widget

## Open Issues

---

### [consider] `new TaskListItemMapper()` in `TaskWidgetFactory` constructor bypasses composition root
**File:** `TaskWidgetFactory.java` line 31

`TaskListItemMapper` is a stateless object; creating a fresh instance is functionally correct. However, `AppCompositionRoot` owns all DI wiring for this mapper elsewhere (line 93 in the root). Since the root does not currently expose the mapper, this is low risk — worth noting as a future injection candidate if `TaskListItemMapper` ever gains state or dependencies.

**Tradeoff:** Fixing requires adding a getter to `AppCompositionRoot`. Not worth the churn now given the mapper is stateless.

---

### [consider] Raw `new Thread(...)` in `handleToggle` vs. shared executor
**File:** `TaskWidgetProvider.java` lines 161–171

The project wires a shared `ExecutorService` in `AppCompositionRoot`. This widget spawns a raw `Thread` for background DB work, which is inconsistent with that convention. Using the shared executor would ensure consistent threading behaviour and allow potential future monitoring.
Tradeoff: using `goAsync()` with a raw thread is a common and correct Android pattern for widget receivers; the shared executor would require acquiring it from the composition root (already done for other fields). Low priority.

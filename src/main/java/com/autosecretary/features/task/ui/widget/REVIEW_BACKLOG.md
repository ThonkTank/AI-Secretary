# Review Backlog — task/ui/widget

## Open Issues

---

### [consider] Raw `new Thread(...)` in `handleToggle` vs. shared executor
**File:** `TaskWidgetProvider.java` lines 161–171

The project wires a shared `ExecutorService` in `AppCompositionRoot`. This widget spawns a raw `Thread` for background DB work, which is inconsistent with that convention. Using the shared executor would ensure consistent threading behaviour and allow potential future monitoring.
Tradeoff: using `goAsync()` with a raw thread is a common and correct Android pattern for widget receivers; the shared executor would require acquiring it from the composition root (already done for other fields). Low priority.

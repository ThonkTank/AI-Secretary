# Review Backlog — task/ui/edit

## Open Issues

### [warning] Four parallel field walks across FormInput → TaskEditState → TaskEditStateMapper

**Files:** `TaskEditPresenter.java:203-237` (FormInput), `TaskEditPresenter.java:97-121` (applyForm), `TaskEditStateMapper.java:42-90` (fromTask), `TaskEditStateMapper.java:92-148` (toTask), `TaskEditState.java:23-68`

**Concern:** `FormInput` declares ~25 fields, `applyForm()` copies them one-to-one into `TaskEditState`, `toTask()` walks the same list again, `fromTask()` walks it in reverse. Every new field requires four synchronized edits; a missed one silently drops the value on save. Already diverging: `deadline` is mutated via `presenter.setEditableDeadline()` and bypasses `FormInput` entirely, making `FormInput` an incomplete representation of the form despite its name.

**Why it matters:** Field-count is already ~25 and growing (budget fields were recently added). Silent data loss from a missed field is hard to detect without automated tests.

**Suggested alternative:** Merge `FormInput` fields into `TaskEditState` directly so the form writes into the edit state and the mapper only walks one flat structure. Would reduce four walks to two (fromTask/toTask). Deferred — high-touch refactor across 5+ files with regression risk.

---

### [nit] TaskEditSessionController.java:21 — mapper instantiated outside `internal`

**File:** `TaskEditSessionController.java:21`

**Concern:** `TaskEditSessionController` hardcodes `new TaskEditStateMapper()` directly, while `TaskEditDialog` injects the mapper through `TaskEditPresenter`'s constructor. The mapper therefore escapes the `internal/mapper` package in two different styles: once via constructor injection (good) and once via direct instantiation (bypasses any wiring point). This is mild drift, not a violation — the project uses manual DI without a container, so hardcoded instantiation of stateless helpers is the project norm.

**Why it matters:** If the mapper ever needs a collaborator injected, `TaskEditSessionController`'s hardcoded instantiation would need a separate update from the `TaskEditDialog` wiring. Today the mapper is stateless so the risk is minimal.

**Suggested alternative:** Accept `TaskEditStateMapper` as a constructor parameter in `TaskEditSessionController`, wired from `AppCompositionRoot` or `TaskViewModel`. Deferred — stateless mapper makes the coupling harmless today.



# Review Backlog — task/domain/internal/scheduling

## Open Issues

### [warning] 1/3 Bundle mutable scheduling-run state into context object @skill:review-simplicity
**File:** `DefaultTaskSlotGenerator.java:248–253`

Four mutable instance fields (`newSlots`, `allTasksById`, `planningState`, `lastConflicts`) are re-initialized at the start of each public scheduling call via `initSchedulingRun`. This is an implicit, invisible per-call contract: miss one field in a new code path and the state corrupts silently.

**Action:** Bundle them into a per-call context object and pass it through private methods instead.

**Why it matters:** Any future public entry point must replicate the same init sequence or risk stale state from a previous call.

---

### [warning] 2/3 Replace telescoping constructors in DefaultTaskSlotGenerator with builder @skill:review-conventions
**File:** `DefaultTaskSlotGenerator.java:258–292`

The class has 5 telescoping constructor variants. Adding a new option requires touching all constructors.

**Action:** Replace with builder or config object pattern (consistent with other types in the codebase).

**Note:** TaskScorer.java has a similar pattern (2 constructors); see issue 3/3.

---

### [warning] 3/3 Replace telescoping constructors in TaskScorer with builder @skill:review-conventions
**File:** `TaskScorer.java:79–93`

The class has 2 telescoping constructor variants. Adding a new option requires touching both constructors.

**Action:** Replace with builder or config object pattern (consistent with other types in the codebase).

**Note:** This is the companion to issue 2/3 (DefaultTaskSlotGenerator); both may benefit from a shared builder infrastructure.

---

### [critical] Separate state mutation from read-only snapshot in scorer.maintenance() @skill:review-architecture
**File:** `DefaultTaskSlotGenerator.java:663`

`scorer.maintenance(task, ...)` is called inside `tryPlaceChain` during the evaluation loop. `maintenance()` mutates task state via `lifecycleManager.advancePeriods()` and `syncPeriodCompletions()`. This means evaluating a candidate placement has side effects on the task domain object, and repeated evaluations of the same task at different start times see mutated state. The day path avoids this by calling maintenance eagerly upfront.

**Why it matters:** Evaluation should be side-effect-free; the current pattern makes scoring order-dependent in the window path.

**Suggested approach:** Separate the state-mutating lifecycle advance from the read-only snapshot computation, or ensure maintenance is idempotent for repeated calls with the same day.

---

### [violation] DefaultTaskSlotGenerator is public but lives in internal/ package
**File:** `DefaultTaskSlotGenerator.java:70`

The class is `public`, allowing direct import from `app/AppCompositionRoot`. The `internal/` package convention exists precisely to mark implementation details that should not be referenced externally — consumers should only see the `TaskSlotGenerator` interface. This boundary is bypassed today in `AppCompositionRoot.java:43`.

**Why it matters:** `internal/` visibility is the codebase's boundary enforcement. A `public` class there is discoverable and easily imported, making future internal refactoring (e.g., renaming/splitting the class) more costly.

**Suggested fix:** Make `DefaultTaskSlotGenerator` package-private. Move the `new DefaultTaskSlotGenerator(...)` instantiation into a factory method or builder that stays inside the domain layer, exposing only `TaskSlotGenerator` to callers outside.

---

### [consider] passesHardConstraintGate uses 10 single-use one-liner private methods
**File:** `TaskScorer.java:497–530`

`passesHardConstraintGate` delegates each of its 10 conditions to a dedicated private method (`isAlreadyCompleteForCurrentCycle`, `isBudgetInsufficient`, etc.), all of which are called exactly once and have bodies of 1–3 lines. The named methods do aid readability of the gate method, but the tradeoff is 10 extra methods scattered through the file for trivial logic that could be written inline without losing comprehension.

**Simpler alternative:** Inline the conditions directly into `passesHardConstraintGate`, using well-aligned formatting to keep the 10-condition list scannable:
```java
if (snapshot.completionState().isComplete()) return false;
if (!snapshot.budgetEligible()) return false;
// ...
```

**Why safe:** The condition logic is identical; the only change is removing one level of indirection. Method names are largely self-explanatory from the condition expression alone.

---


# Budget Integration Review Backlog

## Deferred Issues

### [keep] Single-implementation interface is architecturally justified
**File:** TaskBudgetEligibilityFromBudgetLookup.java:13 (implements TaskBudgetEligibilityService)
**Interface definition:** features/task/domain/scheduling/TaskBudgetEligibilityService.java

**Analysis:**
`TaskBudgetEligibilityService` is an interface with exactly one implementation (`TaskBudgetEligibilityFromBudgetLookup`). By strict KISS principles, single-implementation interfaces should be removed. However, this interface serves a legitimate architectural purpose:

1. **Clean layering:** Separates domain scheduling logic (task prioritization, slot scoring) from budget implementation details. The schedulers (`TaskScorer`, `DefaultTaskSlotGenerator`) depend on the interface, not the concrete implementation.
2. **Low cost:** One interface + one implementation has minimal overhead.
3. **Existing design:** Not a speculative abstraction. CLAUDE.md explicitly documents this as a "domain contract used by task scheduling."
4. **Plausible future use:** Budget eligibility could reasonably be implemented differently (calendar-based constraints, external API, AI prediction) without changing scheduling code.

**KISS principle applies to NEW abstractions without clear justification. This abstraction provides real architectural value (decoupling domain from implementation) at minimal cost.**

**Verdict:** KEEP. This is not over-engineering; it's appropriate layering for a scheduling domain.

---

### [nit] Nascent duplication: Budget requirement guard clause
**Files:** BookTaskCompletionExpenseUseCase.java:19, TaskBudgetEligibilityFromBudgetLookup.java:23
**Pattern:** `if (task == null || !task.hasBudgetRequirement()) { return ... }`
**Why:** The same null/budget-requirement check appears in two unrelated classes. Currently stable at 2 instances, but will compound if more budget-related use cases are added (e.g., budget preview, budget impact calculator).

**Current status:** Low priority since only 2 instances and unlikely to diverge immediately. Each class has a distinct return behavior (different return types), so extraction would require a callback or separate handler.

**Monitoring:** If a third use case adds this check, extract to a utility method or create a `BudgetCheckRequired` helper.

**Status:** Monitor, defer extraction until pattern stabilizes (3+ instances).

---

## Clean Findings (No Issues)

### Code Quality Assessment
This module demonstrates excellent KISS design:

- **BookTaskCompletionExpenseUseCase:** Minimal, focused, single responsibility. Account resolution logic (`resolveAccountId()`) is necessary and well-justified. Defensive null checks are cheap and provide safety at negligible cost.
- **TaskBudgetEligibilityFromBudgetLookup:** Three lines of logic, no over-engineering. The return of `BudgetEligibility.passWithoutBudgetRequirement()` is appropriately semantic.
- **BudgetEligibility record + factory method:** The convenience factory method provides semantic clarity without unnecessary complexity.

**No additional KISS violations found.**

---


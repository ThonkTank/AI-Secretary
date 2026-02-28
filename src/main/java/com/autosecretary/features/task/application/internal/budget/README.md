# Budget Integration — Task-Budget Lifecycle

This module bridges task scheduling and budget constraints. It answers two questions:
1. **Can we afford to schedule this task?** (eligibility check during scheduling)
2. **Record the expense when the task completes.** (booking use-case at completion time)

## Architecture: Two Separate Concerns

Budget integration splits into two services because scheduling and completion have different responsibilities:

### `TaskBudgetEligibilityService` — Scheduling Gate
**Location:** `domain/scheduling/TaskBudgetEligibilityService.java`
**Implementation:** `TaskBudgetEligibilityFromBudgetLookup.java` (this directory)

**Purpose:** Determines whether a task's budget requirement can be met.

**When it's called:** During slot scoring and scheduling algorithms, to gate feasibility.
- Scheduler asks: "Does the user have enough funds to execute this task?"
- Decision impacts task prioritization and scheduling decisions

**Returns:** `BudgetEligibility` record — passes the feasibility check plus available balance.

**Example:**
```
Task "Therapist Session" requires $150.
Current account balance: $200.
→ Eligibility: PASS (enough budget)

Task "Flight" requires $1500.
Current balance: $200.
→ Eligibility: FAIL (insufficient budget) — deprioritize or skip
```

### `BookTaskCompletionExpenseUseCase` — Completion Recording
**Location:** `BookTaskCompletionExpenseUseCase.java` (this directory)

**Purpose:** Records an expense transaction when a task completes.

**When it's called:** During task completion, after slot state is finalized.
- Called by `CheckOffTaskUseCase` if the task has `budgetRequiredCents > 0`
- Records the transaction and deducts from budget balance

**Behavior:**
- If task has no budget requirement → no-op (return false)
- If task has budget requirement → create expense transaction and deduct balance
- If no specific account linked → use default active account (see `resolveAccountId()`)

**Example:**
```
User completes "Therapist Session" task (requires $150).
→ Create expense transaction: -$150
→ Deduct from linked account (or default)
→ Return true (success)
```

## Why Two Services?

**Eligibility** is a scheduling concern — it informs decision algorithms before execution.
**Booking** is a completion concern — it records what actually happened.

This separation allows:
- Scheduling to run independently without triggering budget writes
- Completion to be controlled separately (e.g., user cancels a scheduled task → no booking)
- Budget requirements to be optional (task can run even if budget check disabled)

## How They Connect

```
1. User creates task with budgetRequiredCents = $150

2. Scheduling runs:
   TaskBudgetEligibilityService.eligibilityFor(task)
   → Check: Is user balance >= $150?
   → Decision: Should this task be prioritized/scheduled?

3. Task is scheduled and appears in checklist

4. User checks off the task:
   CheckOffTaskUseCase calls BookTaskCompletionExpenseUseCase.execute()
   → Record: -$150 expense transaction
   → Update: Account balance = balance - $150
   → Success: return true
```

## Key Design Decision: Account Resolution

When a task completes, if no specific account is linked (`budgetAccountId` is null/blank):
- Fall back to the **default active account** (see `resolveAccountId()`)
- This allows task templates to be reused across different budget configurations

For example:
- Team member A has "Groceries" (default: personal account)
- Team member B has "Groceries" (default: shared household account)
- Same task template, different account buckets

## Public Resources

- [Task Budget Integration in CLAUDE.md](../../../../../../CLAUDE.md) — Product decisions and integration overview
- [BudgetRepository interface](../../../../../budget/domain/BudgetRepository.java) — Budget data access contract
- [Task Budget Fields](../../../../../data/Task.java) — TaskCore.budgetRequiredCents, budgetAccountId, budgetCategoryId

## Implementation Notes

- Both use-cases guard against null tasks defensively
- Account ID resolution is lenient (null/blank → default) to support task reuse
- Eligibility check does not reserve funds; actual booking happens at completion
- If booking fails (missing account), the task completion still succeeds (one-way operation)

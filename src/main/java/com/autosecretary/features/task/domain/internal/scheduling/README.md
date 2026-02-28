# Task Scheduling Implementation (`features/task/domain/internal/scheduling`)

> **Do not depend on this package directly.**
> Application-layer code (use cases, etc.) must use the public contracts in
> [`domain/scheduling/`](../scheduling/) instead.

## Overview

This package contains the **internal scheduling algorithm** — the implementation behind the
[`TaskSlotGenerator`](../scheduling/TaskSlotGenerator.java) interface. It has two files:

| File | Role |
|---|---|
| [`DefaultTaskSlotGenerator`](DefaultTaskSlotGenerator.java) | Main orchestrator. Builds prerequisite chains, evaluates candidate placements, applies displacement logic. ~1150 lines. |
| [`TaskScorer`](TaskScorer.java) | Composite scoring function. Computes a numeric priority for each (task, time slot) pair. Package-private; only used by `DefaultTaskSlotGenerator`. ~665 lines. |

Both files are large. Read the orientation below before opening them.

---

## Two Scheduling Modes

### Single-Day (`generateSlotsForDay`)
Schedules one calendar day from end to end.
1. Calls `scorer.maintenance()` for **all tasks upfront** before the placement loop.
2. Collects occupied intervals (existing slots + calendar blocks).
3. Runs `assignGlobalBestFit` (competitive placement loop) for the day.

### Multi-Day Window (`generateSlotsForWindow`)
Schedules several consecutive days in one pass, distributing repetitions intelligently.
1. Builds a `DaySchedulingContext` per day (window bounds + occupied intervals).
2. Pins fixed-time (`TERMIN`) tasks per day first.
3. Runs `assignGlobalBestFitAcrossWindow` — a single competitive loop that picks the
   globally best (day, chain, start-time) triple on each iteration.

> **Key difference:** In window mode, `scorer.maintenance()` is called lazily inside
> `tryPlaceChain` rather than upfront. This means scoring has side effects on the task
> domain object during evaluation (see pre-existing backlog note on coupling).

---

## The Competitive Displacement Algorithm

On each iteration of the placement loop:

1. **Build chains** — `buildTaskChains` groups tasks into prerequisite chains
   (A → B → C must be scheduled in order). Each chain is a candidate for atomic placement.

2. **Evaluate candidates** — For every (chain, day, start-time) triple, `tryPlaceChain`:
   - Checks prerequisites are met.
   - Finds any occupied intervals that overlap the proposed placement.
   - Identifies whether those intervals can be **displaced** (evicted) or are hard blocks.
   - Computes `gainScore` (sum of scores for the incoming chain) and
     `lossScore` (sum of scores for evicted slots).
   - Returns `null` if infeasible; returns a `ChainPlacement` with `netScore = gain − loss`.

3. **Pick the global winner** — the `ChainPlacement` with the highest positive `netScore`
   across all days and chains.

4. **Apply the placement** — evict displaced slots, insert new slot(s), update counters.

5. **Repeat** until no net-positive placement exists or the safety cap fires
   (`MAX_PLACEMENT_ITERATIONS = 10 000`).

---

## `TaskScorer` Lifecycle

```
// Once per generation run:
scorer.reset()                        // clear per-task caches
scorer.setTransitionStats(stats)      // load learned A→B patterns

// Once per task (before scoring any placement for that task):
scorer.maintenance(task, day, state)  // advance periods, build snapshot

// For each candidate placement:
int score = scorer.score(task, start, end, previousTaskId)

// After a slot is placed:
scorer.onSlotAssigned(task, assignedStart)  // mark pref slot consumed, increment scheduledToday
```

### Scoring Layers (applied in order)

| Layer | What it does |
|---|---|
| **Hard constraints** | Returns 0 if task cannot be scheduled (complete, budget insufficient, daily limit reached, cooldown active, inter-day spacing violated, period quota full, carryover debt, slot too short, deadline expired with `closeOnMiss`) |
| **Priority base** | `task.core.priority.scoringWeight` (LOW=100 … CRITICAL=10000) |
| **Child influence** | Parent inherits highest child priority if it exceeds its own |
| **Day constraint** | Returns 0 if task has day-constrained pref slots but none match today |
| **Preferred-time fit** | Linear decay from 1.0 (exact match) to 0.0 at configured deviation hours |
| **Urgency** | `1 + requiredDays / remainingDays`; overdue tasks use `OVERDUE_URGENCY_FACTOR = 100` |
| **Follow-up boost** | Additive + multiplicative boost when task historically follows the previous slot |
| **Aging** | `min(1 + daysSinceLastCompletion / 10, maxAgingMultiplier)` — rescues long-neglected tasks |
| **Spread penalty** | Discourages placing a task too soon after a prior placement in the same window |

---

## Key Internal Types

| Type | Purpose |
|---|---|
| `OccupiedInterval` | A time interval that is already claimed (task slot or calendar block). `candidate == null` → hard block (never displaceable). |
| `DisplacementCandidate` | Metadata for a placed slot that *may* be evicted. Holds `lossScore` and `atomicGroupId` (chain siblings must be evicted together). |
| `ChainNode` | One task in a prerequisite chain, plus the minimum gap after the preceding task. |
| `ChainPlacement` | A fully evaluated placement proposal: chain + start times + slots to displace + net score. |
| `DaySchedulingContext` | One day's scheduling state (window bounds + mutable occupied-interval list). |
| `TaskScoringSnapshot` | Immutable per-task scoring cache built during `maintenance()`. |

---

## Known Design Issues (see REVIEW_BACKLOG.md)

- **Mutable instance state** — five fields are re-initialised per call via `initSchedulingRun`; missing one in a new code path silently corrupts state.
- **Side-effecting evaluation** — `scorer.maintenance()` in the window path mutates task state during chain evaluation (not purely read-only).
- **Child prerequisite chains** — `buildTaskChains` only iterates tree roots; prerequisites on child tasks are silently ignored.

---

## Public Resources

- Java `java.time` (intervals, `ChronoUnit`, `LocalDateTime`): https://docs.oracle.com/javase/tutorial/datetime/
- Greedy scheduling algorithms (general background): https://en.wikipedia.org/wiki/Greedy_algorithm
- Exponential moving average (used for adaptive pref-slot and prerequisite-gap learning):
  https://en.wikipedia.org/wiki/Moving_average#Exponential_moving_average

## Related Modules

- **Public contracts**: [`features/task/domain/scheduling/`](../scheduling/)
- **Callers**: [`features/task/application/RegenerateScheduleUseCase`](../../application/) and widget providers
- **Lifecycle / completion**: [`features/task/domain/TaskLifecycleManager`](../TaskLifecycleManager.java)

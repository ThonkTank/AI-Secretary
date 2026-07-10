# Task Scheduling Implementation (`features/task/domain/internal/scheduling`)

> **Do not depend on this package directly.**
> Application-layer code (use cases, etc.) must use the public contracts in
> [`domain/scheduling/`](../scheduling/) instead.

## Overview

This package contains the **internal scheduling algorithm** — the implementation behind the
[`TaskSlotGenerator`](../scheduling/TaskSlotGenerator.java) interface. It has two files:

| File | Role |
|---|---|
| [`DefaultTaskSlotGenerator`](DefaultTaskSlotGenerator.java) | Main orchestrator. Builds prerequisite chains, evaluates candidate placements, applies displacement logic. ~1300 lines. |
| [`TaskScorer`](TaskScorer.java) | Composite scoring function. Computes a numeric priority for each (task, time slot) pair. Package-private; only used by `DefaultTaskSlotGenerator`. ~720 lines. |

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
The scheduler assumes the window is **7 days** when computing how many total repetitions
a task should have across the window (e.g. a task with 3 reps/week in a 7-day window has
a quota of 3; a daily task has a quota of 7).

1. Builds a `DaySchedulingContext` per day (window bounds + occupied intervals).
2. Pins fixed-time (`TERMIN`, i.e. `TaskCore.SchedulingType.TERMIN`) tasks per day first.
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

   > **Why start times include occupied-interval starts:** `collectStartPoints` adds the start
   > of every *displaceable* already-placed slot as a candidate start point — not just free-gap
   > starts. This is what enables displacement: the algorithm proposes placing a new chain at
   > exactly the same time as a lower-scoring existing slot and, if the net score is positive,
   > evicts it. Without this, displacement would never trigger.

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
| `DisplacementCandidate` | Metadata for a placed slot that *may* be evicted. Holds `lossScore` and `atomicGroupId` (chain siblings must be evicted together). Also has `protectedFromNormalTasks`: when `true` (TERMIN slots, i.e. `TaskCore.SchedulingType.TERMIN`), the slot can only be displaced by another TERMIN slot — normal scored tasks cannot evict it even if their score is higher. |
| `ChainNode` | One task in a prerequisite chain, plus the minimum gap after the preceding task. |
| `ChainPlacement` | A fully evaluated placement proposal: chain + start times + slots to displace + net score. |
| `DaySchedulingContext` | One day's scheduling state (window bounds + mutable occupied-interval list). |
| `TaskScoringSnapshot` | Immutable per-task scoring cache built during `maintenance()`. |

---

## Design Notes

- **Per-run scheduling state** now lives behind a dedicated run context instead of being spread across mutable instance fields.
- **Window-mode scorer maintenance** uses an effective repetition snapshot so candidate evaluation stays read-only with respect to task repetition state.
- **Child prerequisite chains** still deserve caution when touching `buildTaskChains`; verify nested prerequisite paths explicitly if you extend the chain builder.

---

## Log Output

Log messages are produced via the `Consumer<String> logger` passed to the constructor
(null in production, wired to `Log.d` or similar in debug builds). The messages mix
**German** user-facing strings with **English** field names, for example:

```
=== Zusammenfassung 2025-06-01 ===      # day summary header
  Einkaufen: 1 slots [09:00-09:30(400)] # task "Einkaufen" scheduled at 09:00
  Sport: unscheduled                    # no slot placed for this task
[GLOBAL-COMPETE] day=… winner=… verdrängt=keine  # "displaced: none"
[SCHED_CONFLICT] {taskId=…, reasonCode=NO_MATCHING_GAP, …}
```

If you see unfamiliar German words in log output: "Gesamt" = total, "Zusammenfassung" = summary,
"verdrängt" = displaced, "Lücke" = gap, "Termin" = appointment/fixed task.

## Public Resources

- Java `java.time` (intervals, `ChronoUnit`, `LocalDateTime`): https://docs.oracle.com/javase/tutorial/datetime/
- Greedy scheduling algorithms (general background): https://en.wikipedia.org/wiki/Greedy_algorithm
- Exponential moving average (used for adaptive pref-slot and prerequisite-gap learning):
  https://en.wikipedia.org/wiki/Moving_average#Exponential_moving_average

## Related Modules

- **Public contracts**: [`features/task/domain/scheduling/`](../scheduling/)
- **Callers**: [`features/task/application/RegenerateScheduleUseCase`](../../application/) and widget providers
- **Lifecycle / completion**: [`features/task/domain/TaskLifecycleManager`](../TaskLifecycleManager.java)

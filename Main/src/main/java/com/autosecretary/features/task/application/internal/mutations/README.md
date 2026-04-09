# Mutations — Low-Level Write Operations

Mutations are isolated write-side operations that coordinate multi-step database changes
while maintaining consistency across related entities. This is where complex state transitions
live when they require atomic writes to multiple DAOs.

## Why This Directory Exists

The `application/internal/mutations/` layer sits between application use-cases and data DAOs.
It encapsulates logic that:
- Coordinates writes across multiple DAOs (e.g., task, slot, transition stats)
- Enforces atomic transactions (all-or-nothing consistency)
- Implements complex state machines (e.g., two-phase task completion)
- Keeps use-cases focused on orchestration, not low-level detail

## Canonical Example: TaskSlotToggleMutation

[TaskSlotToggleMutation](TaskSlotToggleMutation.java) implements the two-phase task completion flow:

1. **First tap (STARTED phase):** Record `realStart` time and transition to the scheduler
2. **Second tap (COMPLETED phase):** Record `realEnd`, mark slot done, update streaks, adapt if needed

This requires atomic writes to:
- `task_core` — streak/history updates
- `task_slots` — real start/end and completion flag
- `task_transition_stat` — transition recording for scheduler learning

The mutation wraps these in a single `RoomDatabase.runInTransaction()` call.

### What are transition stats?

A transition stat records that the user moved from task A to task B (e.g., finished "Duschen", then started "Frühstück"). Over time, the scheduler learns which task sequences the user naturally follows and uses this to score candidate slots higher when they fit the user's observed flow. Completed transitions are weighted 2× more than started transitions, so the scheduler learns from tasks the user actually finishes rather than just begins.

To see how transition stats influence scheduling, read `TaskScorer` in `features/task/domain/internal/scheduling/`.

## When to Add New Mutations

Add a new file here when you have:
- A multi-step state transition (not just CRUD)
- Writes to 2+ DAOs that must succeed or fail together
- Logic that's too complex for a single use-case method
- A distinct "operation" that other use-cases might want to reuse

Examples:
- Cascading deletes (task + child tasks + slots)
- Bulk state updates (mark multiple slots as scheduled)
- Cross-feature operations (task completion affecting budget)

## When NOT to Add Here

- Single-DAO reads or writes → put in the DAO
- Filtering or mapping logic → put in the use-case
- UI state management → put in the UI layer
- No transactions needed → put in the use-case directly

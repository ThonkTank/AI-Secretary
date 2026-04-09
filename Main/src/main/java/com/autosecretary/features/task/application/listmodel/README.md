# Task List Presentation Models

This package provides immutable data transfer objects (DTOs) for rendering tasks in the UI list views. It decouples the UI layer from domain changes.

## What Is This Package?

- **TaskListItem**: A flat, immutable presentation model that combines task and slot information into a single record optimized for list rendering.
- **TaskListItemMapper**: A stateless transformer that converts domain `Task` objects into flat `TaskListItem` records.

These classes are part of the **application layer** — they are not domain logic and should not be used in scheduling, calculations, or persistence.

## When to Use TaskListItem

Use `TaskListItem` when:
- Rendering tasks in a RecyclerView or list adapter
- You need a single flat record per scheduled occurrence or unscheduled task
- You want to avoid coupling the UI directly to domain entities

Do **not** use `TaskListItem` for:
- Scheduling or task logic (use domain `Task` instead)
- Database operations (use domain `Task` and repositories)
- Any business rule that affects task state

## Field Semantics

`TaskListItem` handles three types of items with different field patterns:

| Item Type | When | Fields Populated | Null Fields |
|-----------|------|------------------|------------|
| **Scheduled Task** | Task has slots | All except calendar-only | None |
| **Unscheduled Task** | Task has no slots | Title, description, identifiers, goal, progress | `start`, `end`, `slotId`, `score` |
| **Calendar Event** | Device calendar entry | `itemType`, `taskId`, `slotId` (both = eventId), `title`, `day`, `start`, `end` | Everything else |

### Critical: Calendar Event IDs

For **calendar events**, both `taskId` and `slotId` hold the same synthetic event ID (not a database ID). Always check `isCalendarEvent()` before using these fields in queries.

## Example: Creating a List Adapter

```java
// In the UI layer (e.g., TaskListFragment or ViewModel)
TaskListItemMapper mapper = new TaskListItemMapper();
List<TaskListItem> items = mapper.map(tasks);  // tasks from the repository

// In the RecyclerView adapter
@Override
public void onBindViewHolder(ViewHolder holder, int position) {
    TaskListItem item = items.get(position);

    if (item.isCalendarEvent()) {
        // Render calendar event (title, time only)
        holder.title.setText(item.title);
        holder.time.setText(formatTime(item.start, item.end));
    } else {
        // Render task
        holder.title.setText(item.title);
        holder.deadline.setText(item.deadlineUrgency().toString());
        if (item.hasProgressTarget()) {
            holder.progress.setProgress(item.progressCurrent);
            holder.progress.setMax(item.progressTarget);
        }
    }
}
```

## Reading Order

1. Read the **class javadoc** of `TaskListItem` for field grouping and null semantics.
2. Read **ItemType enum javadoc** to understand the types and when each is used.
3. Read the **factory method `calendarEvent()`** javadoc to understand the dual-ID pattern.
4. Read `TaskListItemMapper` class javadoc to understand the transformation.
5. Review the source code once you understand the contracts.

## Design Notes

- **Why flat?** A RecyclerView renders a flat list. Flattening reduces null checks in the adapter and keeps presentation logic out of the domain.
- **Why immutable?** Immutability makes the list thread-safe and prevents accidental mutations that would confuse the UI.
- **Why not sealed classes?** The project targets Java 17 without preview features; sealed classes provide limited value here without pattern matching.

## Related Documentation

- **Application Package Map**: See `application/README.md` for the role of listmodel in the application layer.
- **Domain Task**: See `domain/scheduling/` for the actual task and slot entities and scheduling logic.
- **List UI**: See `ui/list/` for the RecyclerView adapters and ViewModels that consume TaskListItem.

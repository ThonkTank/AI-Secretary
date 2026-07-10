# Task Widget

## Purpose

Home-screen widget that displays a task schedule for a selected day (today or earlier/later). Users can:
- View all scheduled tasks for a day
- Navigate between days (backward/forward) with navigation arrows
- Toggle task checkboxes **only on today** to mark completion
- Create a new task via the "+" button

## Architecture

The widget uses Android's **RemoteViews** pattern to render a scrollable list of tasks. This pattern is necessary because widgets run in a separate process and cannot directly observe app state changes; instead, they poll data on-demand via a RemoteViewsService.

### Three-class pattern:

1. **TaskWidgetProvider** (`AppWidgetProvider`)
   - Entry point for the Android widget framework
   - Handles lifecycle (onUpdate, onReceive)
   - Responds to user clicks: day navigation, checkbox toggles, refresh
   - Persists selected day offset in SharedPreferences
   - Routes checkbox toggles to the app's TaskSlotToggleMutation via broadcast

2. **TaskWidgetService** (`RemoteViewsService`)
   - Instantiates TaskWidgetFactory on-demand
   - Obtains `LoadTaskWidgetItemsUseCase` from `TaskWidgetDependencies`

3. **TaskWidgetFactory** (`RemoteViewsFactory`)
   - Loads the widget read model through the application layer
   - Converts TaskListItem objects to RemoteViews (layout binding)
   - Configures click intents for checkboxes (only enabled on today)
   - Renders visual state: color, streak badge, completion status

### Data flow

```
App start
  ↓
TaskWidgetProvider.onUpdate()
  → Create RemoteViews layout with ServiceIntent pointing to TaskWidgetService
  → Build PendingIntents for buttons (prev day, next day, refresh, add task)
  → Build fill-in intent template for checkbox toggles
  ↓
User clicks checkbox
  → PendingIntent fires ACTION_TOGGLE broadcast
  → TaskWidgetProvider.onReceive() → handleToggle()
  → goAsync() extends broadcast lifetime
  → TaskWidgetDependencies.getDbExecutor() runs TaskSlotToggleMutation
  → Notify widget update when done
  ↓
AppWidgetManager calls TaskWidgetService.onGetViewFactory()
  → TaskWidgetFactory loads widget items and builds list of RemoteViews
```

## Key Design Constraints

1. **Only today is interactive:**
   - Widget shows checkboxes for all days, but clicking only works on today (offset = 0)
   - Past/future days are read-only to prevent accidental updates
   - Completed items never get interactive checkboxes

2. **No date-scoped database query:**
   - Currently, the widget use case fetches **all** tasks and filters them in memory
   - For large task lists, this is inefficient but acceptable for now
   - Future optimization: add `readAllForDate(LocalDate)` to TaskDAO

3. **German locale:**
   - Widget displays day names and dates in German ("Montag, 28. Feb") per project convention
   - See CLAUDE.md: all user-facing text is in German

4. **Two-phase checkout not exposed:**
   - The app's task completion is two-phase (first tap: start, second tap: complete)
   - The widget only supports one-phase toggle (click to complete)
   - This simplification works because the widget is for quick completion, not detailed tracking

## Public References

- [Android RemoteViews](https://developer.android.com/guide/topics/appwidgets/overview)
- [RemoteViewsService and RemoteViewsFactory](https://developer.android.com/guide/topics/appwidgets/overview#RemoteViewsService)
- [AppWidgetProvider lifecycle](https://developer.android.com/reference/android/appwidget/AppWidgetProvider)
- [Pending intents and widget click handling](https://developer.android.com/guide/topics/appwidgets/overview#Intents)

## Entry Points for Reading

1. Start with `TaskWidgetProvider.updateWidget()` — see how the layout is built and intents are wired
2. Then `TaskWidgetProvider.onReceive()` — understand the action dispatch
3. Then `TaskWidgetFactory` — see how task data becomes UI
4. TaskWidgetService is just a bridge; it exists because RemoteViewsService is a required Android contract

## See Also

- `CLAUDE.md` — Project glossary (Slot, Streak, Period, Task, etc.)
- `src/main/java/com/autosecretary/features/task/ui/README.md` — UI package conventions
- `src/main/java/com/autosecretary/features/task/application/internal/mutations/TaskSlotToggleMutation.java` — What happens when user toggles

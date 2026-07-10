# Alarms Module

This module manages the daily task scheduling alarm system that triggers schedule regeneration and widget updates.

## Overview

The app uses Android's `AlarmManager` to schedule a daily alarm at midnight that regenerates the task schedule for the upcoming day. This ensures:
- New task slots are generated each day based on task preferences and constraints
- Preferred times are adapted from recent completion history (adaptive scheduling)
- Widgets are updated with current scheduling data
- The app continues to work reliably even after device restart

## Architecture: Two-Receiver Pattern

### `BootReceiver`
Triggered when the device boots (`ACTION_BOOT_COMPLETED`).
- **Purpose:** Re-register the daily alarm after device restart (alarms don't survive reboots)
- **Calls:** `DailyPlanningScheduler.scheduleDaily()`
- **Why needed:** Without this, the alarm system would stop working if the user restarts their phone

### `DailyPlanningScheduler`
Utility class that sets up the next daily alarm using `AlarmManager`.
- **When:** Called by `BootReceiver` (on boot) and `DailyPlanningReceiver` (after each alarm)
- **How:** Schedules an alarm for next midnight (converted to device timezone)
- **Permissions:** Requires `android.permission.SCHEDULE_EXACT_ALARM` (see fallback behavior below)
- **Android S+ (API 31+):** Checks if `SCHEDULE_EXACT_ALARM` is granted; falls back to less-precise `setAndAllowWhileIdle()` if not available

### `DailyPlanningReceiver`
Triggered when the daily alarm fires.
- **Purpose:** Execute the schedule regeneration and update widgets
- **Workflow:**
  1. Re-schedule the next day's alarm (keeps the cycle going)
  2. Call `RegenerateScheduleUseCase` to generate new task slots for today
  3. Update all widgets with the refreshed schedule
  4. Handle both success and failure cases gracefully
- **Key pattern:** Uses `goAsync()` to hold the receiver alive while async work completes (see "Broadcast Receiver Lifecycle" below)

## Workflow Example

```
Device boots
  ↓
BootReceiver.onReceive() triggered
  ↓
DailyPlanningScheduler.scheduleDaily() → sets alarm for next midnight
  ↓
[time passes...]
  ↓
Alarm fires at midnight
  ↓
DailyPlanningReceiver.onReceive() triggered
  ↓
1. Re-schedule next alarm (DailyPlanningScheduler.scheduleDaily())
2. Execute RegenerateScheduleUseCase → generates new task slots
3. Update widgets (TaskWidgetProvider.notifyWidgetUpdate())
4. Call pendingResult.finish() to release broadcast receiver
  ↓
[time passes...]
  ↓
Next midnight: alarm fires again (cycle continues)
```

## Permissions and Android Version Handling

**Required permissions:**
- `android.permission.SCHEDULE_EXACT_ALARM` — allows precise midnight alarms (Android 12+)

**Behavior:**
- **Android 11 and earlier:** Always uses `setExactAndAllowWhileIdle()` (exact timing)
- **Android 12+ without permission:** Falls back to `setAndAllowWhileIdle()` (less precise, still reliable)
- **Android 12+ with permission:** Uses `setExactAndAllowWhileIdle()` (exact timing)

The app declares the permission in `AndroidManifest.xml` but does NOT request it at runtime. Users who deny the permission will still get functioning daily alarms, just at a slightly less predictable time.

## Broadcast Receiver Lifecycle: `goAsync()` Pattern

The `DailyPlanningReceiver` uses an Android pattern that novices often find confusing:

```java
PendingResult pendingResult = goAsync();
try {
    // Do async work (useCase.execute)
    useCase.execute(result -> {
        // Callback runs later, asynchronously
        pendingResult.finish();  // Tell system we're done
    });
} catch (Exception e) {
    pendingResult.finish();  // Safety fallback
}
```

**Why this pattern?**
- Without `goAsync()`, Android kills the receiver immediately after `onReceive()` returns
- But `useCase.execute()` is asynchronous — the work hasn't actually completed yet
- `goAsync()` extends the receiver's lifetime until you call `pendingResult.finish()`
- This ensures the system doesn't kill the app while the schedule is being regenerated

**Key invariant:** `pendingResult.finish()` MUST be called exactly once, whether success or failure.

## Key Concepts from the Domain Layer

See `CLAUDE.md` **Glossary** for context:
- **Task**: Main work item
- **Slot**: One scheduled execution window; multiple slots per task
- **PrefSlot**: Preferred day/time pattern for a task
- **Adaptive**: Auto-adjust preferred times from real completion data
- **Period**: Unit of repetition (day, week, or month)

The daily alarm regenerates `TaskSlot`s based on `TaskPrefSlot` preferences, adapting from historical completion times.

## Public Resources

Learn more about the Android APIs used here:
- [AlarmManager documentation](https://developer.android.com/reference/android/app/AlarmManager)
  - Methods: `setExactAndAllowWhileIdle()`, `setAndAllowWhileIdle()`, `canScheduleExactAlarms()`
- [PendingIntent documentation](https://developer.android.com/reference/android/app/PendingIntent)
  - Note: Use `FLAG_IMMUTABLE` for security (Android 6+)
- [BroadcastReceiver documentation](https://developer.android.com/reference/android/content/BroadcastReceiver)
  - Lifecycle and timeout constraints
- [BroadcastReceiver.goAsync()](https://developer.android.com/reference/android/content/BroadcastReceiver#goAsync())
  - The async pattern explained in detail
- [Build.VERSION API levels](https://developer.android.com/reference/android/os/Build.VERSION)
  - Device capability checks and version-specific APIs

## Testing and Debugging

**Local testing:**
- `./gradlew testDebugUnitTest` — runs JVM characterization tests
- `./gradlew assembleDebug` — builds the app
- `./gradlew installDebug` — installs to a connected device/emulator
- Manually verify: set device time to 11:55 PM, watch for the daily regeneration at midnight

**Common issues:**
- **Alarm never fires:** Check that `BootReceiver` is registered in `AndroidManifest.xml` with `ACTION_BOOT_COMPLETED`
- **Alarm fires but no updates:** Verify `RegenerateScheduleUseCase` is wired in `AppCompositionRoot`
- **Widgets don't update:** Check `TaskWidgetProvider.notifyWidgetUpdate()` is working

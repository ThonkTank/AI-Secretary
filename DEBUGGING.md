# AI Secretary - Debugging Guide

**Last Updated:** 2025-11-17
**Version:** v0.3.61 (Build 361)

---

## Table of Contents

1. [Quick Start Debugging](#quick-start-debugging)
2. [Debugging Tools](#debugging-tools)
3. [Common Issues & Solutions](#common-issues--solutions)
4. [Layer-Specific Debugging](#layer-specific-debugging)
5. [Performance Debugging](#performance-debugging)
6. [Testing & Validation](#testing--validation)

---

## Quick Start Debugging

### Development Environment Setup

**Prerequisites:**
- Fedora Linux 42 (or compatible Linux)
- Android SDK installed (`~/Android/Sdk`)
- ADB configured and device connected
- Java 21 (OpenJDK 21.0.8)

**Quick Debug Cycle:**
```bash
cd "/home/aaron/Schreibtisch/Neuer Ordner/ai-secretary"

# Build debug APK
./gradlew assembleDebug

# Install to device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch app
adb shell am start -n com.secretary/.app.MainActivity

# Monitor logs in real-time
adb logcat | grep -E "Secretary|ERROR|FATAL"
```

---

## Debugging Tools

### 1. ADB Logcat (Primary Tool)

**View all app logs:**
```bash
adb logcat | grep Secretary
```

**Filter by severity:**
```bash
# Errors only
adb logcat | grep -E "ERROR|Exception|FATAL"

# Specific tag
adb logcat -s TaskActivity

# Multiple tags
adb logcat -s TaskActivity:D TaskListViewModel:D
```

**Save logs to file:**
```bash
adb logcat > debug.log
```

**Clear logs before test:**
```bash
adb logcat -c
```

### 2. HTTP Log Server (Built-in)

**Access from laptop (if device connected):**

```bash
# Forward port from device to laptop
adb forward tcp:8080 tcp:8080

# View logs in browser or curl
curl http://localhost:8080/logs
```

**Features:**
- In-memory circular buffer (500 lines)
- Structured log format with timestamps
- Accessible even when ADB is disconnected

**Code reference:**
- Server: `core/logging/HttpLogServer.kt`
- Logger: `core/logging/AppLogger.kt`

### 3. Android Studio Debugger

**Attach debugger to running app:**

1. Open project in Android Studio
2. Run → Attach Debugger to Android Process
3. Select `com.secretary` process
4. Set breakpoints in Kotlin code

**Useful breakpoints:**
- `TaskActivity.kt:467` - Task checkbox changed
- `CompleteTaskUseCase.kt:34` - Task completion logic
- `TaskRepositoryImpl.kt:43` - Database updates

### 4. Gradle Build Logs

**Verbose build output:**
```bash
./gradlew assembleDebug --stacktrace --debug
```

**Common build errors:**
- Kotlin version mismatch
- Room schema export issues
- KSP annotation processing failures

---

## Common Issues & Solutions

### Issue 1: App Crashes on Launch

**Symptoms:**
- App force closes immediately after launch
- Logcat shows `FATAL EXCEPTION`

**Debug Steps:**
```bash
# Clear logs and relaunch
adb logcat -c
adb shell am start -n com.secretary/.app.MainActivity
adb logcat | grep -A 20 FATAL
```

**Common Causes:**

1. **Database Migration Failure**
   ```
   ERROR: Migration didn't properly handle
   ```
   **Solution:** Check `TaskDatabase.kt:40+` for migration logic

2. **Missing Dependency Initialization**
   ```
   ERROR: lateinit property ... has not been initialized
   ```
   **Solution:** Check `TaskActivity.onCreate()` dependency setup

3. **Room Schema Mismatch**
   ```
   ERROR: Room cannot verify the data integrity
   ```
   **Solution:** Increment database version or clear app data:
   ```bash
   adb shell pm clear com.secretary
   ```

### Issue 2: Tasks Not Saving

**Symptoms:**
- Add task dialog closes
- Task doesn't appear in list
- No error shown

**Debug Steps:**

1. **Check ViewModel logs:**
   ```bash
   adb logcat -s TaskDetailViewModel
   ```

2. **Check Repository calls:**
   ```bash
   adb logcat | grep "TaskRepositoryImpl"
   ```

3. **Verify Room DAO execution:**
   ```bash
   adb logcat | grep "RoomDatabase"
   ```

**Common Causes:**

1. **Validation Exception Not Caught**
   - Check `CreateTaskUseCase.kt:24` for validation logic
   - Add logging in catch blocks

2. **Coroutine Not Launched**
   - Verify `viewModelScope.launch` is used
   - Check for coroutine cancellation

3. **LiveData Not Observed**
   - Verify `observe(this)` in Activity
   - Check lifecycle state

### Issue 3: Streak Not Updating

**Symptoms:**
- Task completed successfully
- Streak counter doesn't increment
- No visible error

**Debug Steps:**

1. **Check StreakService logic:**
   ```kotlin
   // Add logging in StreakService.kt:33
   fun updateStreak(task: Task, completionTime: Long): Task {
       Log.d("StreakService", "Updating streak: current=${task.currentStreak}, last=${task.lastStreakDate}")
       // ...
   }
   ```

2. **Verify timestamp normalization:**
   ```kotlin
   // StreakService.kt:103
   private fun getStartOfDay(timestamp: Long): Long {
       Log.d("StreakService", "Normalizing timestamp: $timestamp")
       // ...
   }
   ```

3. **Check task update persistence:**
   ```bash
   adb logcat | grep "updateTask"
   ```

**Common Causes:**

1. **Time Zone Issues**
   - `Calendar.getInstance()` uses device timezone
   - Midnight boundary might be off

2. **Task Not Persisted**
   - Check `TaskRepositoryImpl.updateTask()` is called
   - Verify Room DAO update query

### Issue 4: Recurrence Not Working

**Symptoms:**
- Task marked complete but doesn't reset
- FREQUENCY counter doesn't increment
- Due date not updated

**Debug Steps:**

1. **Check RecurrenceService logic:**
   ```bash
   adb logcat -s RecurrenceService
   ```

2. **Verify task recurrence type:**
   ```kotlin
   // Add logging in handleRecurringCompletion
   Log.d("RecurrenceService", "Recurrence: type=${task.recurrenceType}, amount=${task.recurrenceAmount}")
   ```

3. **Check period boundary detection:**
   ```kotlin
   // RecurrenceService.kt:119
   fun isInCurrentPeriod(...) {
       Log.d("RecurrenceService", "Period check: start=$periodStart, now=$now, unit=$unit")
       // ...
   }
   ```

**Common Causes:**

1. **Period Start Not Initialized**
   - FREQUENCY tasks need `currentPeriodStart` set
   - Check task creation in AddTaskDialog

2. **Week Boundary Issues**
   - `Calendar.firstDayOfWeek` might differ by locale
   - Verify week start calculation

### Issue 5: Dialogs Not Showing

**Symptoms:**
- Button click has no effect
- Dialog doesn't appear
- No error in logs

**Debug Steps:**

1. **Check FragmentManager:**
   ```kotlin
   // TaskActivity.kt:425
   fun showAddTaskDialog() {
       Log.d("TaskActivity", "Showing AddTaskDialog")
       val dialog = AddTaskDialog.newInstance(...)
       dialog.show(supportFragmentManager, AddTaskDialog.TAG)
   }
   ```

2. **Verify FragmentResult listener:**
   ```kotlin
   // TaskActivity.kt:168
   supportFragmentManager.setFragmentResultListener(...)
   ```

**Common Causes:**

1. **FragmentManager State Loss**
   - Don't show dialogs in `onSaveInstanceState()`
   - Use `commitAllowingStateLoss()` if needed

2. **Wrong TAG Used**
   - Verify TAG matches in `show()` and listener

### Issue 6: Build Failures

**Symptoms:**
- `./gradlew assembleDebug` fails
- Compilation errors
- Dependency resolution issues

**Common Errors:**

1. **Kotlin Version Mismatch**
   ```
   ERROR: Module was compiled with an incompatible version of Kotlin
   ```
   **Solution:** Check `build.gradle.kts` Kotlin version matches

2. **Room Schema Export Error**
   ```
   ERROR: Room cannot find schema export directory
   ```
   **Solution:** Create directory:
   ```bash
   mkdir -p app/schemas
   ```

3. **KSP Annotation Processing Failure**
   ```
   ERROR: [ksp] failed to process
   ```
   **Solution:** Clean and rebuild:
   ```bash
   ./gradlew clean assembleDebug
   ```

---

## Layer-Specific Debugging

### Debugging Presentation Layer

**Key Files:**
- `TaskActivity.kt` - Main UI
- `TaskListViewModel.kt` - List management
- `TaskDetailViewModel.kt` - Task operations
- `AddTaskDialog.kt` - Task creation

**Common Patterns:**

1. **ViewModel not updating UI:**
   ```kotlin
   // Check LiveData is mutable and exposed
   private val _tasks = MutableLiveData<List<Task>>()
   val tasks: LiveData<List<Task>> = _tasks

   // Verify observer is registered
   viewModel.tasks.observe(this) { tasks ->
       Log.d("TaskActivity", "Tasks updated: ${tasks.size}")
       adapter.notifyDataSetChanged()
   }
   ```

2. **Dialog not receiving result:**
   ```kotlin
   // Verify setFragmentResult is called
   setFragmentResult(RESULT_KEY, bundleOf("success" to true))

   // Check listener is registered BEFORE dialog shown
   supportFragmentManager.setFragmentResultListener(RESULT_KEY, this) { ... }
   ```

### Debugging Domain Layer

**Key Files:**
- `CompleteTaskUseCase.kt` - Task completion
- `StreakService.kt` - Streak calculation
- `RecurrenceService.kt` - Recurrence logic

**Testing Strategy:**

1. **Run unit tests:**
   ```bash
   ./gradlew test --tests "*StreakServiceTest"
   ```

2. **Add temporary logging:**
   ```kotlin
   // StreakService.kt
   fun updateStreak(task: Task, completionTime: Long): Task {
       println("DEBUG: current=${task.currentStreak}, last=${task.lastStreakDate}")
       // ...
   }
   ```

3. **Verify business logic:**
   - Domain layer has NO Android dependencies
   - All logic should be testable in JUnit

### Debugging Data Layer

**Key Files:**
- `TaskRepositoryImpl.kt` - Repository implementation
- `TaskDao.kt` - Room DAO
- `TaskDatabase.kt` - Database setup

**Common Issues:**

1. **Query not returning results:**
   ```kotlin
   // Add logging to DAO
   @Query("SELECT * FROM tasks")
   suspend fun getAllTasks(): List<TaskEntity> {
       Log.d("TaskDao", "getAllTasks called")
       // Room generates implementation
   }
   ```

2. **Entity conversion errors:**
   ```kotlin
   // TaskRepositoryImpl.kt
   override suspend fun getAllTasks(): List<Task> {
       val entities = taskDao.getAllTasks()
       Log.d("Repository", "Fetched ${entities.size} entities")
       return entities.map { it.toDomain() }
   }
   ```

3. **Database corruption:**
   ```bash
   # Pull database from device
   adb pull /data/data/com.secretary/databases/task_database.db

   # Inspect with sqlite3
   sqlite3 task_database.db
   sqlite> .schema
   sqlite> SELECT * FROM tasks;
   ```

---

## Performance Debugging

### Identifying Slow Operations

**Use ADB systrace:**
```bash
# Record 10 seconds of app activity
adb shell am start com.secretary/.app.MainActivity
adb shell am profile start com.secretary /sdcard/trace.txt
sleep 10
adb shell am profile stop com.secretary
adb pull /sdcard/trace.txt
```

**Analyze with Perfetto:**
- Upload trace.txt to https://ui.perfetto.dev/

### Common Performance Issues

1. **Main Thread Database Queries**
   - **Symptom:** UI freezes when loading tasks
   - **Solution:** Ensure all Repository calls use `suspend` functions

2. **Inefficient List Updates**
   - **Symptom:** Lag when scrolling task list
   - **Solution:** Use `DiffUtil` in RecyclerView adapter

3. **Excessive Logging**
   - **Symptom:** App stutters when logging enabled
   - **Solution:** Disable verbose logging in production builds

---

## Testing & Validation

### Running Unit Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "*StreakServiceTest"

# Run with coverage
./gradlew testDebugUnitTestCoverage

# View HTML report
firefox app/build/reports/tests/testDebugUnitTest/index.html
```

### Manual Test Scenarios

**Test 1: Streak Continuity**
1. Create non-recurring task
2. Complete today
3. Change device date to tomorrow
4. Complete again
5. **Expected:** Streak = 2

**Test 2: FREQUENCY Recurrence**
1. Create task: "3 times per week"
2. Complete 3 times same week
3. **Expected:** Task marked complete
4. Change date to next week
5. **Expected:** Task resets to incomplete

**Test 3: Task Filtering**
1. Create 5 tasks (2 completed, 3 active)
2. Set filter to "Active Only"
3. **Expected:** Only 3 tasks shown

---

## Debugging Checklist

Before reporting an issue, verify:

- [ ] Latest version installed (`adb shell dumpsys package com.secretary | grep versionCode`)
- [ ] Device logs captured (`adb logcat > issue.log`)
- [ ] Steps to reproduce documented
- [ ] Expected vs actual behavior described
- [ ] Database state inspected (if relevant)
- [ ] Unit tests passing (`./gradlew test`)

---

## Related Documentation

- **[ARCHITECTURE.md](ARCHITECTURE.md)** - System architecture and design patterns
- **[ROADMAP.md](ROADMAP.md)** - Known issues and future improvements
- **[CLAUDE.md](CLAUDE.md)** - Development environment setup

---

**Need Help?**

- Check [GitHub Issues](https://github.com/ThonkTank/AI-Secretary/issues)
- Review test cases in `app/src/test/`
- Consult inline documentation in source code

**Debug Philosophy:** "Log early, log often, but disable in production."

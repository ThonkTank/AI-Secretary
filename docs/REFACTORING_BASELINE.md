# Refactoring Baseline - System Behavior Documentation

**Created:** 2025-11-13
**Version:** v0.3.26 (Build 326)
**Purpose:** Document current system behavior BEFORE Phase 4.5 refactoring for regression testing

---

## Critical User Flows (MUST work after refactoring)

### 1. Task Creation
**Steps:**
1. User opens app → MainActivity
2. User clicks "Open Tasks" button → TaskActivity
3. User clicks "+" button → AddTaskDialog appears
4. User enters: Title, Description, Category, Priority, Due Date, Recurrence
5. User clicks "Add" → Task saved to database
6. Task appears in list

**Expected Behavior:**
- Task appears immediately in task list
- Task has correct priority color/indicator
- If recurring: recurrence info visible
- Database entry created in `tasks` table

**Test Verification:**
```bash
curl http://localhost:8080/logs | grep "Task created"
```

---

### 2. Task Completion (Non-Recurring)
**Steps:**
1. User clicks checkbox on task
2. CompletionDialog appears
3. User enters: Time Spent, Difficulty, Notes (optional)
4. User clicks "Complete"

**Expected Behavior:**
- Task marked as completed (isCompleted = true)
- Task moves to bottom of list (or filtered out if "Active" filter)
- Completion record created in `completions` table
- Streak NOT affected (only recurring tasks have streaks)

**Database Changes:**
- `tasks.is_completed` = 1
- `tasks.last_completed_date` = current timestamp
- `completions` table: new row inserted

---

### 3. Task Completion (INTERVAL Recurrence)
**Example:** "Every 3 days"

**Steps:**
1. User completes recurring task (checkbox)
2. CompletionDialog appears → User submits

**Expected Behavior:**
- Task UNCOMPLETES automatically (is_completed = 0)
- Due date advances by interval (e.g., +3 days)
- Current streak increments by 1
- Longest streak updates if current > longest
- Completion record created

**Database Changes:**
- `tasks.is_completed` = 0 (reset!)
- `tasks.due_date` = old_due_date + (interval * unit)
- `tasks.last_completed_date` = current timestamp
- `tasks.current_streak` += 1
- `tasks.longest_streak` = max(current_streak, longest_streak)
- `completions` table: new row

**Code Location:**
- `TaskDatabaseHelper.java:handleRecurringTaskCompletion()`
- `TaskDatabaseHelper.java:resetIntervalTask()`

---

### 4. Task Completion (FREQUENCY Recurrence)
**Example:** "3 times per week"

**Steps:**
1. User completes frequency task (checkbox)
2. CompletionDialog appears → User submits

**Expected Behavior:**
- Task REMAINS ACTIVE (is_completed = 0)
- Completions counter increments
- If period boundary crossed → reset counter, update period start
- If target reached (3/3) → ???  (TODO: verify exact behavior)
- Streak increments

**Database Changes:**
- `tasks.is_completed` = ??? (verify)
- `tasks.completions_this_period` += 1
- If new period: `tasks.current_period_start` = now
- `tasks.current_streak` += 1
- `completions` table: new row

**Code Location:**
- `TaskDatabaseHelper.java:incrementFrequencyProgress()`

---

### 5. Task Edit
**Steps:**
1. User long-presses task → Edit dialog
2. User modifies fields
3. User clicks "Save"

**Expected Behavior:**
- Task updates in database
- List refreshes showing new data
- Recurrence logic NOT reset (keeps streak, period)

---

### 6. Task Delete
**Steps:**
1. User long-presses task → Delete option
2. Confirmation dialog → User confirms

**Expected Behavior:**
- Task removed from `tasks` table
- Associated completions remain in `completions` table (historical data)
- List updates immediately

---

### 7. Task Filtering
**Filters Available:**
- Search (title/description substring)
- Status: All / Active / Completed
- Priority: All / Low / Medium / High / Urgent
- Category: All / 10 categories
- Sort: Priority, Due Date, Category, Created Date, Title

**Expected Behavior:**
- Filters combine (AND logic)
- List updates immediately
- Empty state shown if no matches

---

### 8. Statistics Display
**Current Implementation:**
- Total tasks count
- Completed tasks count
- Active tasks count
- Today's completions (from `completions` table WHERE date = today)

**Location:** TaskActivity statistics panel

---

### 9. Streak Tracking
**Logic:**
- Only for recurring tasks
- Increments on each completion
- Current streak vs Longest streak
- Displayed as "🔥 X" in task list

**Calculation:**
- `TaskStatistics.java:calculateStreak()`
- `TaskDatabaseHelper.java:completeTask()` updates streaks

---

### 10. Auto-Update System
**Flow:**
1. User opens Settings menu → "Check for Updates"
2. UpdateChecker queries GitHub Releases API
3. If new version available → prompt user
4. User clicks "Update" → UpdateInstaller downloads APK
5. User installs APK manually (Android system prompt)

**Expected Behavior:**
- HTTP request to: https://api.github.com/repos/ThonkTank/AI-Secretary/releases/latest
- Compares versionCode in manifest vs release
- Downloads APK using DownloadManager
- Broadcasts intent when download completes

---

## Database Schema (v4)

### tasks table (17 columns)
```sql
CREATE TABLE tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    description TEXT,
    category TEXT,
    created_at INTEGER NOT NULL,
    due_date INTEGER,
    is_completed INTEGER DEFAULT 0,
    priority INTEGER DEFAULT 0,
    recurrence_type TEXT,
    recurrence_amount INTEGER,
    recurrence_unit TEXT,
    last_completed_date INTEGER,
    completions_this_period INTEGER DEFAULT 0,
    current_period_start INTEGER,
    current_streak INTEGER DEFAULT 0,
    longest_streak INTEGER DEFAULT 0,
    last_streak_date INTEGER
)
```

### completions table (6 columns)
```sql
CREATE TABLE completions (
    completion_id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id INTEGER NOT NULL,
    completed_at INTEGER NOT NULL,
    time_spent INTEGER,
    difficulty INTEGER,
    notes TEXT,
    FOREIGN KEY (task_id) REFERENCES tasks(id)
)
```

---

## Known Behaviors (May or may not be bugs)

1. **Frequency Tasks:** Unclear if task marks as "completed" when target reached
2. **Streak Breaking:** No logic to detect broken streaks (e.g., missed interval task)
3. **Period Boundary Detection:** Frequency tasks rely on `isInCurrentPeriod()` - verify correctness
4. **Time Zone Handling:** All dates are Unix timestamps (milliseconds) - no explicit TZ handling

---

## Logging Behavior

### Current Logging System (After Phase 4.5.1 Cleanup)
- **AppLogger:** In-memory only (500 lines max, auto-trimmed)
- **SimpleHttpServer:** localhost:8080, `/logs` endpoint
- **No file-based logging** (removed in cleanup)

### Access Methods
```bash
curl http://localhost:8080/logs                    # All logs
curl -s http://localhost:8080/logs | tail -50      # Last 50
curl -s http://localhost:8080/logs | grep ERROR    # Errors only
```

---

## Regression Test Checklist

After Phase 4.5 refactoring, verify ALL of these behaviors:

- [ ] Task creation works (all fields saved correctly)
- [ ] Task completion works (non-recurring)
- [ ] Task completion works (INTERVAL recurrence - task resets, due date advances)
- [ ] Task completion works (FREQUENCY recurrence - counter increments, period resets)
- [ ] Task edit works (changes persist)
- [ ] Task delete works (task removed, completions remain)
- [ ] Task filtering works (search, status, priority, category, sort)
- [ ] Statistics display correct counts
- [ ] Streak tracking works (increments, longest streak updates)
- [ ] Auto-update system works (check, download, install flow)
- [ ] Database migrations work (existing data preserved)
- [ ] HTTP logging accessible via localhost:8080
- [ ] App doesn't crash on launch
- [ ] All UI layouts render correctly

---

## Test Data Scenarios

### Scenario 1: Daily Task
- Title: "Daily Exercise"
- Recurrence: INTERVAL, 1 DAY
- Priority: High
- Expected: Resets every day after completion

### Scenario 2: Weekly Goals
- Title: "Gym 3x per week"
- Recurrence: FREQUENCY, 3 WEEK
- Priority: Medium
- Expected: Counter resets every Sunday (or period start day)

### Scenario 3: One-Time Task
- Title: "Buy milk"
- Recurrence: None
- Expected: Marks as completed permanently

### Scenario 4: Mixed Priorities
- 10 tasks with varying priorities (Low, Medium, High, Urgent)
- Expected: Sorted by priority DESC when filter applied

---

## Dependencies

**Current:** NONE (no external libraries)
**After Phase 4.5:** Room, LiveData, ViewModel (androidx.*)

**Critical:** Ensure backward compatibility with existing database (migration from raw SQLite to Room)

---

## Performance Baselines

- **Task List Load:** <100ms for 100 tasks
- **Task Creation:** <50ms
- **Task Completion:** <100ms (includes recurrence logic)
- **Filter Application:** <50ms
- **Statistics Calculation:** <100ms

**Measurement:** Use AppLogger timestamps in logs

---

**End of Baseline Documentation**

This document will be used to validate that Phase 4.5 refactoring does NOT break existing functionality.

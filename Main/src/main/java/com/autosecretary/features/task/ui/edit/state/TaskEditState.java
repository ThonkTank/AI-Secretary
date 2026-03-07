package com.autosecretary.features.task.ui.edit.state;

import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable UI POJO for an active editing session in
 * {@link com.autosecretary.features.task.ui.edit.TaskEditDialog TaskEditDialog}.
 * Not persisted — exists only during active editing sessions.
 * <p>
 * Most fields are directly user-editable via form widgets. Three fields —
 * {@code periodCompletions}, {@code periodStart}, and {@code carryoverDebt} — are
 * not exposed in any UI widget. They are <b>scheduler state</b> that must be preserved
 * for round-trip fidelity: when the user saves without changing the repetition period,
 * the mapper writes these values back unchanged so the scheduler's active period progress
 * counters are not silently reset. These values come from the loaded task's scheduler
 * history (see {@link com.autosecretary.features.task.data.TaskCore.History TaskCore.History}).
 */
public class TaskEditState {
    // Basic metadata
    public String id;
    public String title;
    public String description;
    public String parentTaskId;  // nullable; null means no parent

    // Scheduling & priority — user-editable
    public Priority priority = TaskEditDefaults.PRIORITY;
    public TaskCore.SchedulingType schedulingType = TaskEditDefaults.SCHEDULING_TYPE;
    // Earliest scheduling date for normal tasks (TASK). Null = no lower bound.
    public LocalDate startDate;
    public LocalDate deadline;
    // Fixed-date fields — shown in the UI only when SchedulingType.TERMIN is selected.
    public LocalDate fixedDate;
    public LocalTime fixedStart;
    public LocalTime fixedEnd;
    public Integer fixedDuration;
    public boolean closeOnMiss = TaskEditDefaults.CLOSE_ON_MISS;
    public int minDuration = TaskEditDefaults.MIN_DURATION;  // minutes
    public int maxDuration = TaskEditDefaults.MAX_DURATION;  // minutes
    public int cooldown = TaskEditDefaults.COOLDOWN;  // days (minimum gap between slots)
    public boolean adaptive;  // if true, preferred times auto-adjust from real completion times

    // Budget integration (optional) — if set, task completion auto-books an expense
    public Integer budgetRequiredCents;  // required amount in cents; null means no budget link
    public String budgetAccountId;  // which budget account to charge against
    public String budgetCategoryId;  // which budget category to use; see CLAUDE.md "Task → Budget integration"

    // Repetition — user-editable fields define the repetition pattern
    public int reps = TaskEditDefaults.REPETITION_REPS;  // total repetitions
    public int perPeriod = TaskEditDefaults.REPETITION_PER_PERIOD;  // how many per period
    public Period periodUnit = TaskEditDefaults.REPETITION_PERIOD_UNIT;  // day/week/month
    public boolean completeFirst;  // if true, complete all reps in a period before moving to next
    // Scheduler-managed state (not editable in UI): current period's completion counters
    public int periodCompletions;  // completions in the current period
    public LocalDate periodStart;  // when the current period started
    public int carryoverDebt;  // unpaid reps from missed periods (carried forward)

    // Progress tracking (optional) — for tasks with numeric progress (e.g., reading chapters)
    public String unit = TaskEditDefaults.UNIT;  // label (e.g., "pages", "chapters"); empty means no tracking
    public int target = TaskEditDefaults.TARGET;  // goal value
    public int current = TaskEditDefaults.CURRENT;  // current value
    public boolean resetPerRep;  // if true, current resets to 0 after each repetition
    public int minPerRep = TaskEditDefaults.MIN_PER_REP;  // minimum progress per repetition
    public int maxPerRep = TaskEditDefaults.MAX_PER_REP;  // maximum progress per repetition

    // Related state
    public List<PrefSlotEditState> prefSlots = new ArrayList<>();
}

package com.autosecretary.features.task.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.autosecretary.shared.Priority;
import com.autosecretary.shared.Period;
import com.autosecretary.shared.MealType;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * Room {@code @Entity} for the {@code task_core} table. Holds task metadata,
 * scheduling parameters, and three {@code @Embedded} sub-objects
 * ({@link Repetition}, {@link Progress}, {@link History}) whose fields are
 * flattened into columns with corresponding prefixes.
 */
@Entity (tableName = "task_core")
public class TaskCore {
    public static final String DEFAULT_GOAL_ICON = "🎯";
    public static final String DEFAULT_GOAL_COLOR_HEX = "#FF5E35B1";

    // Basic
    @PrimaryKey() @NonNull
    public String id = UUID.randomUUID().toString();
    public String title;
    public String description;
    @NonNull public String goalIcon = DEFAULT_GOAL_ICON;
    @NonNull public String goalColorHex = DEFAULT_GOAL_COLOR_HEX;
    public Integer budgetRequiredCents;
    public String budgetAccountId;
    public String budgetCategoryId;
    /**
     * Optional grouping into a {@link TaskCategory}. Plain nullable string (no
     * {@code @ForeignKey}); null means uncategorised. ON DELETE SET NULL is enforced in
     * {@code TaskCategoryDao.clearCategoryFromTasks} rather than by a DB foreign key.
     */
    public String categoryId;
    /**
     * Optional cross-feature meal association. When set, completing this task triggers
     * a meal consumption record via {@code TaskMealCompletionFromMealPlanner}. See
     * {@code features/meal/} for the meal feature domain. Null when not meal-related.
     */
    public MealType mealType;

    //scheduling
    public Priority priority = Priority.MEDIUM;
    /**
     * Determines how the task is scheduled.
     * <ul>
     *   <li>{@code TASK} — standard repeating/one-off task; scheduler places it freely.</li>
     *   <li>{@code TERMIN} — appointment with a fixed date/time; uses {@code fixedDate},
     *       {@code fixedStart}, {@code fixedDuration}.</li>
     * </ul>
     */
    public SchedulingType schedulingType = SchedulingType.TASK;
    /** Minimum gap between consecutive executions, in <strong>days</strong>. Default 1 (no gap). */
    public int cooldown = 1;
    /** Earliest day this task may be scheduled. Null means immediately schedulable. */
    public LocalDate startDate;
    public LocalDate deadline;
    /** Fixed date for TERMIN tasks. Null for standard tasks. */
    public LocalDate fixedDate;
    /** Fixed start time for TERMIN tasks. Null for standard tasks. */
    public LocalTime fixedStart;
    /** Fixed end time for TERMIN tasks. Null for standard tasks (optional when duration is set). */
    public LocalTime fixedEnd;
    /** Fixed duration override for TERMIN tasks, in <strong>minutes</strong>. Null for standard tasks. */
    public Integer fixedDuration;
    public LocalDate created = LocalDate.now();
    /**
     * Controls task behavior when a deadline or repetition period end is exceeded without
     * completion. If {@code true}, the task is marked as completed (closed) automatically
     * when the deadline passes or period ends. If {@code false}, the task remains open
     * and available for completion even after the deadline/period has passed.
     */
    public boolean closeOnMiss = true;

    /** When true, {@code TaskLifecycleManager.adaptPrefSlot()} nudges preferred times toward actual completion times (EMA α=0.2). */
    public boolean adaptive;
    /**
     * When true, this is a leisure/hobby item (relaxing, meals, games): it is still scheduled, but
     * carries no metrics — no streak, history, adaptive-time learning, or deadline/urgency pressure.
     * Two-phase check-off still records realStart/realEnd/completed; only the statistics are skipped.
     */
    public boolean leisure;
    /** Minimum planned slot duration, in <strong>minutes</strong>. */
    public int minDuration = 5;
    /** Maximum planned slot duration, in <strong>minutes</strong>. 0 means uncapped. */
    public int maxDuration = 10;

    @Embedded(prefix = "history_")
    public History history = new History();
    public boolean completed = false;

    @Embedded(prefix = "repetition_")
    public Repetition repetition = new Repetition();

    //completion tracking
    @Embedded(prefix = "progress_")
    public Progress progress = new Progress();

    /**
     * Tracks how often a task repeats within a configurable time window.
     * E.g. "5 times per 2 weeks" is expressed as reps=5, perPeriod=2, periodUnit=WEEK.
     */
    public static class Repetition {
        public int reps;
        /** Number of completions recorded in the current period window. Reset by {@code TaskLifecycleManager.advancePeriods()}. */
        public int periodCompletions = 0;
        /** Start date of the current repetition period. Initialized to {@code created} on first advance. */
        public LocalDate periodStart;
        /**
         * If {@code true}, all repetitions in the current period must be completed before
         * slots are generated for the next period. If {@code false}, slots may be generated
         * for future periods even if reps in the current period are incomplete.
         * When {@code true}, uncompleted reps from a missed period are tracked in
         * {@link #carryoverDebt} and added to the next period's rep count.
         */
        public boolean completeFirst;
        /** Accumulated unmet reps from past periods when {@code completeFirst=true}. Added to the next period's target. */
        public int carryoverDebt;

        public int remainingReps() {return reps - periodCompletions;}

        // Defaults keep a freshly constructed Repetition valid (reps=0 → non-schedulable, never null):
        // the assistant CREATE path leaves these untouched for one-off tasks, so a null periodUnit here
        // used to NPE the whole scheduler. See periodInDays().
        public int perPeriod = 1;
        public Period periodUnit = Period.DAY;

        /**
         * Length of one repetition period in days, or {@code 0} when the repetition is unset/invalid
         * (null {@code periodUnit} or non-positive {@code perPeriod}). Returning 0 makes such a task
         * non-schedulable ({@link #repsPerDay()} == 0) instead of throwing — a malformed task must
         * never crash schedule generation.
         */
        public int periodInDays() {
            if (periodUnit == null || perPeriod <= 0) return 0;
            return periodUnit.dayCount * perPeriod;
        }
        public int repsPerDay() {
            int days = periodInDays();
            return (reps <= 0 || days <= 0) ? 0 : (int) Math.ceil((double) reps / days);
        }
        public LocalDate periodEnd() {
            int days = periodInDays();
            return (periodStart != null && days > 0) ? periodStart.plusDays(days) : null;
        }
    }

    /**
     * Tracks incremental progress toward a target value (e.g. pages read, chapters completed).
     */
    public static class Progress {
        private static final int DEFAULT_FALLBACK_MINUTES = 10;

        public String unit;             // Unit of measurement (e.g. "pages", "chapters")
        public boolean resetPerRep;          // True = progress resets each repetition (requires repetition)

        public int target = 0;              // Target value (e.g. 6), 0 = no tracking
        public int current;             // Current progress (e.g. 3)
        public int remaining() {return target - current;}

        /** Minimum progress units to record per repetition (lower bound on step size). */
        public int minPerRep;
        /** Maximum progress units per repetition (upper bound on step size). */
        public int maxPerRep;

        // Learning statistics for adaptive slot sizing:
        // totalProgress = observed progress units, totalTime = observed minutes.
        public int totalProgress;
        public int totalTime = DEFAULT_FALLBACK_MINUTES;

        /**
         * Uses an implicit 1-unit completion for tasks without explicit progress tracking
         * to keep legacy completion-duration learning behavior.
         */
        public int completionProgressUnits() {
            return target > 0 ? Math.max(1, minPerRep) : 1;
        }

        public void recordTimingSample(int durationMinutes) {
            int boundedDuration = Math.max(1, durationMinutes);
            totalTime += boundedDuration;
            totalProgress += completionProgressUnits();
        }

        public double timePerProgress() {
            int safeTime = totalTime > 0 ? totalTime : DEFAULT_FALLBACK_MINUTES;
            return totalProgress <= 0 ? safeTime : (double) safeTime / totalProgress;
        }

        public int requiredTimePerRep() {
            return (int) Math.ceil(completionProgressUnits() * timePerProgress());
        }
    }

    /** Tracks completion statistics, streaks, and cumulative duration. */
    public static class History {
        /** Total number of times this task has been completed (all-time). */
        public int completions;
        /** Completions with a meaningful duration recorded (excludes quick-taps and stale sessions). */
        public int trackedCompletions;
        /** Current consecutive streak (incremented when the period's rep goal is exactly met). */
        public int currentStreak;
        /** Number of distinct streak runs started. Used to compute {@link #averageStreak()}. */
        public int nrStreaks = 1;
        /** Cumulative task duration in <strong>minutes</strong>, summed from tracked completions. */
        public int totalDuration;

        public int averageStreak() { return nrStreaks > 0 ? completions / nrStreaks : 0; }
        public int averageDuration() { return trackedCompletions > 0 ? totalDuration / trackedCompletions : 0; }
    }


    public enum SchedulingType {
        /** Standard repeating or one-off task. The scheduler places it freely within the window. */
        TASK,
        /**
         * Fixed-time appointment (German: "Termin"). Uses {@code fixedDate}/{@code fixedStart}/{@code fixedEnd}.
         */
        TERMIN
    }

    public int plannedDurationMinutes() {
        int floor = Math.max(minDuration, 1);
        int duration = Math.max(floor, progress.requiredTimePerRep());
        return maxDuration > 0 ? Math.min(maxDuration, duration) : duration;
    }
}

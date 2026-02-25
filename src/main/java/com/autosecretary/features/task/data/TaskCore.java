package com.autosecretary.features.task.data;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.autosecretary.shared.Priority;
import com.autosecretary.shared.Period;
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

    //scheduling
    public Priority priority = Priority.MEDIUM;
    public int cooldown = 1;
    public LocalDate deadline;
    public LocalDate created = LocalDate.now();
    public boolean closeOnMiss = true; // When deadline/period end is exceeded, close the task instead of keeping it open?

    public boolean adaptive;  // Adapt preferred times to user behavior?
    public int minDuration = 5;
    public int maxDuration = 10;

    @Embedded(prefix = "history_")
    public History history = new History();
    public boolean completed = false;

    @Embedded(prefix = "repetition_")
    public Repetition repetition = new Repetition();
    public int repsPerDay() {
        if (repetition != null) {
                return repetition.repsPerDay();
        } else {
            return 1;
        }
    }

    //completion tracking
    @Embedded(prefix = "progress_")
    public Progress progress = new Progress();

    /**
     * Tracks how often a task repeats within a configurable time window.
     * E.g. "5 times per 2 weeks" is expressed as reps=5, perPeriod=2, periodUnit=WEEK.
     */
    public static class Repetition {
        public int reps;
        public int periodCompletions = 0;
        public LocalDate periodStart;

        public double remainingReps() {return reps - periodCompletions;}

        public int perPeriod;
        public Period periodUnit;
        public int periodInDays() {return periodUnit.value * perPeriod;}
        public int repsPerDay() {return (int) Math.ceil( (double) reps / (double) periodInDays());}
        public double daysPerRep() {return (double) periodInDays() / (double) reps;}
        public double requiredDays() {return daysPerRep() * remainingReps();}

        public LocalDate periodEnd() {
            return periodStart != null ? periodStart.plusDays(periodInDays()) : null;
        }
        public double remainingDays() {
            LocalDate end = periodEnd();
            if (end == null) return periodInDays();
            return (double) ChronoUnit.DAYS.between(LocalDate.now(), end);
        }
    }

    /**
     * Tracks incremental progress toward a target value (e.g. pages read, chapters completed).
     */
    public static class Progress {
        public String unit;             // Unit of measurement (e.g. "pages", "chapters")
        public boolean resetPerRep;          // True = progress resets each repetition (requires repetition)

        public int target = 0;              // Target value (e.g. 6), 0 = no tracking
        public int current;             // Current progress (e.g. 3)
        public int remaining() {return target - current;}

        public int minPerRep;
        public int maxPerRep;

        public int totalProgress;
        public int totalTime = 10;

        public double repsRequired(double minDuration) {return resetPerRep ? target : Math.min(minPerRep, minDuration/timePerProgress());}
        public double timePerProgress() {return totalProgress == 0 ? totalTime : (double) totalTime / (double) totalProgress;}
        public int requiredTimePerRep() {return (int) (minPerRep * timePerProgress());}
    }

    /** Tracks completion statistics, streaks, and cumulative duration. */
    public static class History {
        public int completions;
        public int trackedCompletions;
        public int currentStreak;
        public int nrStreaks = 1;
        public int totalDuration;

        public int averageStreak() { return nrStreaks > 0 ? completions / nrStreaks : 0; }
        public int averageDuration() { return trackedCompletions > 0 ? totalDuration / trackedCompletions : 0; }
    }


}

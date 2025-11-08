package com.aisecretary.taskmaster.utils;

import com.aisecretary.taskmaster.database.TaskEntity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * TaskScheduler - Intelligent task sorting and scheduling
 *
 * Calculates optimal task ordering based on multiple factors:
 * - Priority (numeric weight)
 * - Due date urgency (overdue > today > soon)
 * - Estimated duration
 * - Preferred time of day
 * - Difficulty level
 * - Streak preservation
 * Phase 5: Intelligente Sortierung & Tagesplan
 */
public class TaskScheduler {

    // Weighting factors for scoring (total: 100)
    private static final float WEIGHT_PRIORITY = 25.0f;
    private static final float WEIGHT_DUE_DATE = 30.0f;
    private static final float WEIGHT_STREAK = 20.0f;
    private static final float WEIGHT_TIME_PREFERENCE = 10.0f;
    private static final float WEIGHT_DIFFICULTY = 10.0f;
    private static final float WEIGHT_DURATION = 5.0f;

    /**
     * Data class for task with calculated score
     */
    public static class ScoredTask {
        public TaskEntity task;
        public float score;
        public String scoreBreakdown; // For debugging/display

        public ScoredTask(TaskEntity task, float score, String breakdown) {
            this.task = task;
            this.score = score;
            this.scoreBreakdown = breakdown;
        }
    }

    /**
     * Sort tasks by intelligent scoring algorithm
     *
     * @param tasks List of incomplete tasks
     * @return Sorted list (highest score first = most important/urgent)
     */
    public static List<TaskEntity> sortTasks(List<TaskEntity> tasks) {
        List<ScoredTask> scoredTasks = new ArrayList<>();

        // Calculate score for each task
        for (TaskEntity task : tasks) {
            if (!task.completed) {
                ScoredTask scoredTask = scoreTask(task);
                scoredTasks.add(scoredTask);
            }
        }

        // Sort by score (descending - highest first)
        Collections.sort(scoredTasks, new Comparator<ScoredTask>() {
            @Override
            public int compare(ScoredTask a, ScoredTask b) {
                return Float.compare(b.score, a.score);
            }
        });

        // Extract sorted tasks
        List<TaskEntity> sortedTasks = new ArrayList<>();
        for (ScoredTask scoredTask : scoredTasks) {
            sortedTasks.add(scoredTask.task);
        }

        return sortedTasks;
    }

    /**
     * Calculate score for a single task
     */
    public static ScoredTask scoreTask(TaskEntity task) {
        float priorityScore = calculatePriorityScore(task);
        float dueDateScore = calculateDueDateScore(task);
        float streakScore = calculateStreakScore(task);
        float timePreferenceScore = calculateTimePreferenceScore(task);
        float difficultyScore = calculateDifficultyScore(task);
        float durationScore = calculateDurationScore(task);

        float totalScore = priorityScore + dueDateScore + streakScore +
                timePreferenceScore + difficultyScore + durationScore;

        String breakdown = String.format(
                "P:%.1f D:%.1f S:%.1f T:%.1f Df:%.1f Du:%.1f = %.1f",
                priorityScore, dueDateScore, streakScore,
                timePreferenceScore, difficultyScore, durationScore, totalScore
        );

        return new ScoredTask(task, totalScore, breakdown);
    }

    /**
     * Priority score (0-25)
     * Priority 4 = 25, Priority 3 = 18.75, Priority 2 = 12.5, Priority 1 = 6.25
     */
    private static float calculatePriorityScore(TaskEntity task) {
        return (task.priority / 4.0f) * WEIGHT_PRIORITY;
    }

    /**
     * Due date score (0-30)
     * Overdue: 30, Today: 25, Tomorrow: 20, Within 3 days: 15, Within 7 days: 10, Later: 5, No due: 2
     */
    private static float calculateDueDateScore(TaskEntity task) {
        if (task.dueAt == 0) {
            return 2.0f; // No due date = low urgency
        }

        long now = System.currentTimeMillis();
        long timeUntilDue = task.dueAt - now;
        long daysUntilDue = timeUntilDue / (24 * 60 * 60 * 1000);

        if (timeUntilDue < 0) {
            // Overdue - scale by how overdue (more overdue = higher score)
            long daysOverdue = Math.abs(daysUntilDue);
            if (daysOverdue > 7) {
                return WEIGHT_DUE_DATE; // Max score for very overdue
            }
            return WEIGHT_DUE_DATE - (daysOverdue * 1.0f); // Decrease slightly with age
        } else if (daysUntilDue == 0) {
            // Due today
            return WEIGHT_DUE_DATE * 0.83f; // 25
        } else if (daysUntilDue == 1) {
            // Due tomorrow
            return WEIGHT_DUE_DATE * 0.67f; // 20
        } else if (daysUntilDue <= 3) {
            // Within 3 days
            return WEIGHT_DUE_DATE * 0.5f; // 15
        } else if (daysUntilDue <= 7) {
            // Within 7 days
            return WEIGHT_DUE_DATE * 0.33f; // 10
        } else {
            // Later
            return WEIGHT_DUE_DATE * 0.17f; // 5
        }
    }

    /**
     * Streak score (0-20)
     * Active streak at risk: 20, Active streak: 15, Recurring without streak: 5, Non-recurring: 0
     */
    private static float calculateStreakScore(TaskEntity task) {
        if (!task.isRecurring) {
            return 0.0f; // No streak for non-recurring tasks
        }

        if (task.currentStreak > 0) {
            boolean atRisk = StreakManager.isStreakAtRisk(task);
            if (atRisk) {
                // Streak at risk - very high priority to preserve it
                return WEIGHT_STREAK;
            } else {
                // Active streak but not at risk yet
                return WEIGHT_STREAK * 0.75f;
            }
        } else {
            // Recurring task without streak - moderate priority to start one
            return WEIGHT_STREAK * 0.25f;
        }
    }

    /**
     * Time preference score (0-10)
     * Matches current time of day: 10, Different time: 0
     */
    private static float calculateTimePreferenceScore(TaskEntity task) {
        if (task.preferredTimeOfDay == null || task.preferredTimeOfDay.isEmpty()) {
            return WEIGHT_TIME_PREFERENCE * 0.5f; // Neutral if no preference
        }

        Calendar now = Calendar.getInstance();
        int currentHour = now.get(Calendar.HOUR_OF_DAY);

        String currentTimeOfDay;
        if (currentHour >= 5 && currentHour < 12) {
            currentTimeOfDay = "morning";
        } else if (currentHour >= 12 && currentHour < 18) {
            currentTimeOfDay = "afternoon";
        } else {
            currentTimeOfDay = "evening";
        }

        if (currentTimeOfDay.equals(task.preferredTimeOfDay)) {
            return WEIGHT_TIME_PREFERENCE; // Perfect match
        } else {
            return 0.0f; // Wrong time of day
        }
    }

    /**
     * Difficulty score (0-10)
     * Hard tasks get higher score in the morning (fresh mind)
     * Easy tasks get higher score in evening (tired)
     */
    private static float calculateDifficultyScore(TaskEntity task) {
        if (task.averageDifficulty == 0) {
            return WEIGHT_DIFFICULTY * 0.5f; // Neutral if unknown
        }

        Calendar now = Calendar.getInstance();
        int currentHour = now.get(Calendar.HOUR_OF_DAY);

        boolean isMorning = currentHour >= 5 && currentHour < 12;

        // Normalize difficulty to 0-1 scale (assuming 1-5 rating)
        float normalizedDifficulty = task.averageDifficulty / 5.0f;

        if (isMorning) {
            // Morning: Harder tasks get higher score
            return normalizedDifficulty * WEIGHT_DIFFICULTY;
        } else {
            // Afternoon/Evening: Easier tasks get higher score
            return (1.0f - normalizedDifficulty) * WEIGHT_DIFFICULTY;
        }
    }

    /**
     * Duration score (0-5)
     * Short tasks: 5 (quick wins), Medium tasks: 3, Long tasks: 1
     */
    private static float calculateDurationScore(TaskEntity task) {
        if (task.averageCompletionTime == 0) {
            return WEIGHT_DURATION * 0.5f; // Neutral if unknown
        }

        long durationMinutes = task.averageCompletionTime / (60 * 1000);

        if (durationMinutes <= 15) {
            // Short task - quick win
            return WEIGHT_DURATION;
        } else if (durationMinutes <= 60) {
            // Medium task
            return WEIGHT_DURATION * 0.6f;
        } else {
            // Long task
            return WEIGHT_DURATION * 0.2f;
        }
    }

    /**
     * Get next recommended task (highest scored task)
     *
     * @param tasks List of incomplete tasks
     * @return Next recommended task, or null if no tasks
     */
    public static TaskEntity getNextTask(List<TaskEntity> tasks) {
        List<TaskEntity> sortedTasks = sortTasks(tasks);
        if (sortedTasks.isEmpty()) {
            return null;
        }
        return sortedTasks.get(0);
    }

    /**
     * Get task score explanation (for UI display)
     */
    public static String getScoreExplanation(TaskEntity task) {
        ScoredTask scored = scoreTask(task);
        return String.format("Score: %.1f/100\n%s", scored.score, scored.scoreBreakdown);
    }

    /**
     * Sort tasks for today (filters + sorts)
     *
     * @param allTasks All tasks
     * @return Sorted list of today's tasks
     */
    public static List<TaskEntity> getTodaysSortedTasks(List<TaskEntity> allTasks) {
        List<TaskEntity> todayTasks = new ArrayList<>();
        long now = System.currentTimeMillis();
        long todayEnd = now + (24 * 60 * 60 * 1000);

        for (TaskEntity task : allTasks) {
            if (!task.completed) {
                // Include if: no due date, overdue, or due today
                if (task.dueAt == 0 || task.dueAt <= todayEnd) {
                    todayTasks.add(task);
                }
            }
        }

        return sortTasks(todayTasks);
    }
}

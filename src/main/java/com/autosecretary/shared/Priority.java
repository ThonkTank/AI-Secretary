package com.autosecretary.shared;

/**
 * Task priority levels with multiplicative scoring weights for daily schedule planning.
 * <p>
 * Each task in the app has a priority that influences which tasks get scheduled earlier
 * in the day during daily planning. Priority affects the scheduler's decision about when
 * to slot a task into the user's calendar.
 * <p>
 * <strong>Scoring weights:</strong> LOW=100 (baseline), MEDIUM=200 (2×), HIGH=400 (4×),
 * CRITICAL=10000 (100×). These weights are multiplied by other factors (age, preferred time fit,
 * urgency) in the task scoring algorithm. The multiplicative design ensures critical tasks
 * will appear early in the schedule regardless of other factors.
 * <p>
 * <strong>Primary consumer:</strong> {@link com.autosecretary.features.task.domain.internal.scheduling.TaskScorer}
 * uses these weights to compute overall priority scores during daily planning. Generally
 * accessed via domain logic, not directly by UI code.
 * <p>
 * <strong>When to use each level:</strong>
 * <ul>
 *   <li>{@code LOW} — Routine, non-urgent work with no time pressure (e.g., passive reading, filing)
 *   <li>{@code MEDIUM} — Normal tasks with standard importance (default for most tasks)
 *   <li>{@code HIGH} — Important or time-sensitive work that should be scheduled sooner
 *   <li>{@code CRITICAL} — Urgent blockers or top-priority items; should always appear near the top of daily schedule
 * </ul>
 */
public enum Priority {
    LOW(100),
    MEDIUM(200),
    HIGH(400),
    CRITICAL(10000);

    /**
     * Numeric weight used by task scheduler for priority comparison.
     * Higher values indicate higher priority; used multiplicatively in scoring.
     */
    public final int scoringWeight;

    Priority(int scoringWeight) {
        this.scoringWeight = scoringWeight;
    }
}

package com.autosecretary.shared;

/**
 * Task priority levels with multiplicative scoring weights for scheduler ordering.
 * <p>
 * Weights scale multiplicatively: LOW=100 (baseline), MEDIUM=2×, HIGH=4×, CRITICAL=100×.
 * This scaling ensures critical tasks dominate the scheduler even with aging multipliers.
 * Used by {@link com.autosecretary.features.task.domain.internal.scheduling.TaskScorer}
 * to compute task priority scores during daily planning.
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

package com.autosecretary.features.task.domain.internal.scoring;

public final class MultiDayStateSnapshot {
    private final int totalScheduledReps;
    private final int totalRepsInPeriod;
    private final int minDayDistance;
    private final double expectedDayGap;

    public MultiDayStateSnapshot(int totalScheduledReps, int totalRepsInPeriod, int minDayDistance, double expectedDayGap) {
        this.totalScheduledReps = totalScheduledReps;
        this.totalRepsInPeriod = totalRepsInPeriod;
        this.minDayDistance = minDayDistance;
        this.expectedDayGap = expectedDayGap;
    }

    public int totalScheduledReps() {
        return totalScheduledReps;
    }

    public int totalRepsInPeriod() {
        return totalRepsInPeriod;
    }

    public int minDayDistance() {
        return minDayDistance;
    }

    public double expectedDayGap() {
        return expectedDayGap;
    }
}

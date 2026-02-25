package com.autosecretary.features.task.domain.internal.scoring;

public final class TaskScoringSnapshot {
    private final CompletionState completionState;
    private final UrgencyState urgencyState;
    private final PreferenceFitState preferenceFitState;
    private final MultiDayStateSnapshot multiDayStateSnapshot;
    private final int sinceLast;
    private final double agingForce;
    private final int repsPerDay;
    private final int maxChildPriority;

    public TaskScoringSnapshot(CompletionState completionState,
                               UrgencyState urgencyState,
                               PreferenceFitState preferenceFitState,
                               MultiDayStateSnapshot multiDayStateSnapshot,
                               int sinceLast,
                               double agingForce,
                               int repsPerDay,
                               int maxChildPriority) {
        this.completionState = completionState;
        this.urgencyState = urgencyState;
        this.preferenceFitState = preferenceFitState;
        this.multiDayStateSnapshot = multiDayStateSnapshot;
        this.sinceLast = sinceLast;
        this.agingForce = agingForce;
        this.repsPerDay = repsPerDay;
        this.maxChildPriority = maxChildPriority;
    }

    public CompletionState completionState() {
        return completionState;
    }

    public UrgencyState urgencyState() {
        return urgencyState;
    }

    public PreferenceFitState preferenceFitState() {
        return preferenceFitState;
    }

    public MultiDayStateSnapshot multiDayStateSnapshot() {
        return multiDayStateSnapshot;
    }

    public int sinceLast() {
        return sinceLast;
    }

    public double agingForce() {
        return agingForce;
    }

    public int repsPerDay() {
        return repsPerDay;
    }

    public int maxChildPriority() {
        return maxChildPriority;
    }

    public TaskScoringSnapshot withIncrementedScheduledToday() {
        return new TaskScoringSnapshot(
                completionState.withIncrementedScheduledToday(),
                urgencyState,
                preferenceFitState,
                multiDayStateSnapshot,
                sinceLast,
                agingForce,
                repsPerDay,
                maxChildPriority
        );
    }
}

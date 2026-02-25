package com.autosecretary.features.task.domain.internal.scoring;

import com.autosecretary.features.task.domain.internal.scoring.ScoringOutputs.CompletionState;
import com.autosecretary.features.task.domain.internal.scoring.ScoringOutputs.PreferenceFitState;
import com.autosecretary.features.task.domain.internal.scoring.ScoringOutputs.UrgencyState;

public final class ScoringInputs {
    private ScoringInputs() {
    }

    public static final class MultiDayStateSnapshot {
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

    public static final class TaskScoringSnapshot {
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
}

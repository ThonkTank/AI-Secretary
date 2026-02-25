package com.autosecretary.features.task.domain.internal.scoring;

import com.autosecretary.features.task.data.TaskPrefSlot;
import java.time.LocalDate;
import java.util.List;

public final class ScoringOutputs {
    private ScoringOutputs() {
    }

    public static final class CompletionState {
        private final int completions;
        private final LocalDate lastCompletion;
        private final int periodCompletions;
        private final boolean complete;
        private final int scheduledToday;

        public CompletionState(int completions, LocalDate lastCompletion, int periodCompletions, boolean complete, int scheduledToday) {
            this.completions = completions;
            this.lastCompletion = lastCompletion;
            this.periodCompletions = periodCompletions;
            this.complete = complete;
            this.scheduledToday = scheduledToday;
        }

        public int completions() {
            return completions;
        }

        public LocalDate lastCompletion() {
            return lastCompletion;
        }

        public int periodCompletions() {
            return periodCompletions;
        }

        public boolean isComplete() {
            return complete;
        }

        public int scheduledToday() {
            return scheduledToday;
        }

        public CompletionState withIncrementedScheduledToday() {
            return new CompletionState(completions, lastCompletion, periodCompletions, complete, scheduledToday + 1);
        }
    }

    public static final class UrgencyState {
        private final double remainingDays;
        private final double requiredDays;
        private final boolean deadlineExpired;

        public UrgencyState(double remainingDays, double requiredDays, boolean deadlineExpired) {
            this.remainingDays = remainingDays;
            this.requiredDays = requiredDays;
            this.deadlineExpired = deadlineExpired;
        }

        public double remainingDays() {
            return remainingDays;
        }

        public double requiredDays() {
            return requiredDays;
        }

        public boolean isDeadlineExpired() {
            return deadlineExpired;
        }
    }

    public static final class PreferenceFitState {
        private final List<TaskPrefSlot> todayPrefSlots;
        private final boolean hasDayConstraints;

        public PreferenceFitState(List<TaskPrefSlot> todayPrefSlots, boolean hasDayConstraints) {
            this.todayPrefSlots = List.copyOf(todayPrefSlots);
            this.hasDayConstraints = hasDayConstraints;
        }

        public List<TaskPrefSlot> todayPrefSlots() {
            return todayPrefSlots;
        }

        public boolean hasDayConstraints() {
            return hasDayConstraints;
        }
    }
}

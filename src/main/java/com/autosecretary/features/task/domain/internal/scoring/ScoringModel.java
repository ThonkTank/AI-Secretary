package com.autosecretary.features.task.domain.internal.scoring;

import com.autosecretary.features.task.data.TaskPrefSlot;
import java.time.LocalDate;
import java.util.List;

public final class ScoringModel {
    private ScoringModel() {
    }

    public record CompletionState(int completions,
                                  LocalDate lastCompletion,
                                  int periodCompletions,
                                  boolean isComplete,
                                  int scheduledToday) {
        public CompletionState withIncrementedScheduledToday() {
            return new CompletionState(completions, lastCompletion, periodCompletions, isComplete, scheduledToday + 1);
        }
    }

    public record UrgencyState(double remainingDays,
                               double requiredDays,
                               boolean isDeadlineExpired) {
    }

    public record PreferenceFitState(List<TaskPrefSlot> todayPrefSlots,
                                     boolean hasDayConstraints) {
        public PreferenceFitState {
            todayPrefSlots = List.copyOf(todayPrefSlots);
        }
    }

    public record MultiDayStateSnapshot(int totalScheduledReps,
                                        int totalRepsInPeriod,
                                        int minDayDistance,
                                        double expectedDayGap) {
    }

    public record TaskScoringSnapshot(CompletionState completionState,
                                      UrgencyState urgencyState,
                                      PreferenceFitState preferenceFitState,
                                      MultiDayStateSnapshot multiDayStateSnapshot,
                                      int sinceLast,
                                      double agingForce,
                                      int repsPerDay,
                                      int maxChildPriority) {
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

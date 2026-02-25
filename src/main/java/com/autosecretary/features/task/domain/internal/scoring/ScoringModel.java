package com.autosecretary.features.task.domain.internal.scoring;

import com.autosecretary.features.task.data.TaskPrefSlot;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
                                     boolean hasDayConstraints,
                                     Set<String> consumedPrefSlotIds) {
        public PreferenceFitState {
            todayPrefSlots = List.copyOf(todayPrefSlots);
            consumedPrefSlotIds = Set.copyOf(consumedPrefSlotIds);
        }

        public PreferenceFitState(List<TaskPrefSlot> todayPrefSlots, boolean hasDayConstraints) {
            this(todayPrefSlots, hasDayConstraints, Set.of());
        }

        public PreferenceFitState withConsumedPrefSlot(String prefSlotId) {
            Set<String> newConsumed = new HashSet<>(consumedPrefSlotIds);
            newConsumed.add(prefSlotId);
            return new PreferenceFitState(todayPrefSlots, hasDayConstraints, newConsumed);
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

        public TaskScoringSnapshot withConsumedPrefSlot(String prefSlotId) {
            return new TaskScoringSnapshot(
                    completionState.withIncrementedScheduledToday(),
                    urgencyState,
                    preferenceFitState.withConsumedPrefSlot(prefSlotId),
                    multiDayStateSnapshot,
                    sinceLast,
                    agingForce,
                    repsPerDay,
                    maxChildPriority
            );
        }
    }
}

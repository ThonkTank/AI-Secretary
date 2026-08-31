package de.thonktank.autosecretary.domain.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Template-only guardrails. Device increments deliberately are not part of the policy. */
public final class TrainingAssistantPolicy {
    public final int minSets;
    public final int maxSets;
    public final int minRepetitions;
    public final int maxRepetitions;
    public final int automaticWeeklySetCeiling;
    public final TrainingMuscleGroup primaryMuscle;
    public final Set<TrainingMuscleGroup> secondaryMuscles;

    public TrainingAssistantPolicy(int minSets, int maxSets, int minRepetitions,
                                   int maxRepetitions, int automaticWeeklySetCeiling,
                                   TrainingMuscleGroup primaryMuscle,
                                   Set<TrainingMuscleGroup> secondaryMuscles) {
        if (minSets < 1 || maxSets < minSets || minRepetitions < 1
                || maxRepetitions < minRepetitions || automaticWeeklySetCeiling < 1)
            throw new IllegalArgumentException("Invalid training assistant guardrails");
        this.minSets = minSets;
        this.maxSets = maxSets;
        this.minRepetitions = minRepetitions;
        this.maxRepetitions = maxRepetitions;
        this.automaticWeeklySetCeiling = automaticWeeklySetCeiling;
        this.primaryMuscle = primaryMuscle;
        EnumSet<TrainingMuscleGroup> muscles = secondaryMuscles == null
                || secondaryMuscles.isEmpty() ? EnumSet.noneOf(TrainingMuscleGroup.class)
                : EnumSet.copyOf(secondaryMuscles);
        muscles.remove(primaryMuscle);
        this.secondaryMuscles = Collections.unmodifiableSet(muscles);
    }

    public static TrainingAssistantPolicy defaults(TrainingMuscleGroup primary) {
        return new TrainingAssistantPolicy(2, 3, 8, 12, 10, primary,
                Collections.emptySet());
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof TrainingAssistantPolicy)) return false;
        TrainingAssistantPolicy value = (TrainingAssistantPolicy) other;
        return minSets == value.minSets && maxSets == value.maxSets
                && minRepetitions == value.minRepetitions
                && maxRepetitions == value.maxRepetitions
                && automaticWeeklySetCeiling == value.automaticWeeklySetCeiling
                && primaryMuscle == value.primaryMuscle
                && secondaryMuscles.equals(value.secondaryMuscles);
    }

    @Override public int hashCode() {
        return Objects.hash(minSets, maxSets, minRepetitions, maxRepetitions,
                automaticWeeklySetCeiling, primaryMuscle, secondaryMuscles);
    }
}

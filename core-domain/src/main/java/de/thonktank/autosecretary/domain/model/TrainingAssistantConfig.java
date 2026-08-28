package de.thonktank.autosecretary.domain.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Per-template guardrails copied from the global profile when the assistant is enabled. */
public final class TrainingAssistantConfig {
    public final boolean enabled;
    public final int minSets;
    public final int maxSets;
    public final int minRepetitions;
    public final int maxRepetitions;
    public final int targetRir;
    public final long loadIncrementMilli;
    public final int automaticWeeklySetCeiling;
    public final ResistanceLoad load;
    public final TrainingMuscleGroup primaryMuscle;
    public final Set<TrainingMuscleGroup> secondaryMuscles;

    public TrainingAssistantConfig(boolean enabled, int minSets, int maxSets,
                                   int minRepetitions, int maxRepetitions, int targetRir,
                                   long loadIncrementMilli, int automaticWeeklySetCeiling,
                                   ResistanceLoad load, TrainingMuscleGroup primaryMuscle,
                                   Set<TrainingMuscleGroup> secondaryMuscles) {
        if (minSets < 1 || maxSets < minSets || minRepetitions < 1
                || maxRepetitions < minRepetitions || targetRir < 0 || targetRir > 5
                || loadIncrementMilli < 0 || automaticWeeklySetCeiling < 1)
            throw new IllegalArgumentException("Invalid training assistant guardrails");
        if (enabled && (load == null || load.mode == ResistanceLoad.Mode.UNSPECIFIED))
            throw new IllegalArgumentException("Enabled assistant needs a resistance mode");
        this.enabled = enabled;
        this.minSets = minSets;
        this.maxSets = maxSets;
        this.minRepetitions = minRepetitions;
        this.maxRepetitions = maxRepetitions;
        this.targetRir = targetRir;
        this.loadIncrementMilli = loadIncrementMilli;
        this.automaticWeeklySetCeiling = automaticWeeklySetCeiling;
        this.load = load == null ? ResistanceLoad.unspecified() : load;
        this.primaryMuscle = primaryMuscle;
        EnumSet<TrainingMuscleGroup> muscles = secondaryMuscles == null
                || secondaryMuscles.isEmpty() ? EnumSet.noneOf(TrainingMuscleGroup.class)
                : EnumSet.copyOf(secondaryMuscles);
        muscles.remove(primaryMuscle);
        this.secondaryMuscles = Collections.unmodifiableSet(muscles);
    }

    public static TrainingAssistantConfig disabled() {
        return new TrainingAssistantConfig(false, 2, 3, 8, 12, 2, 2_500, 10,
                ResistanceLoad.unspecified(), null, Collections.emptySet());
    }

    public static TrainingAssistantConfig defaults(ResistanceLoad load,
                                                   TrainingMuscleGroup primary) {
        return new TrainingAssistantConfig(true, 2, 3, 8, 12, 2,
                load.unit == ResistanceLoad.Unit.LB ? 5_000 : 2_500,
                10, load, primary, Collections.emptySet());
    }

    public TrainingAssistantConfig withLoad(ResistanceLoad value) {
        return new TrainingAssistantConfig(enabled, minSets, maxSets, minRepetitions,
                maxRepetitions, targetRir, loadIncrementMilli, automaticWeeklySetCeiling,
                value, primaryMuscle, secondaryMuscles);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof TrainingAssistantConfig)) return false;
        TrainingAssistantConfig value = (TrainingAssistantConfig) other;
        return enabled == value.enabled && minSets == value.minSets && maxSets == value.maxSets
                && minRepetitions == value.minRepetitions
                && maxRepetitions == value.maxRepetitions && targetRir == value.targetRir
                && loadIncrementMilli == value.loadIncrementMilli
                && automaticWeeklySetCeiling == value.automaticWeeklySetCeiling
                && load.equals(value.load) && primaryMuscle == value.primaryMuscle
                && secondaryMuscles.equals(value.secondaryMuscles);
    }

    @Override public int hashCode() {
        return Objects.hash(enabled, minSets, maxSets, minRepetitions, maxRepetitions,
                targetRir, loadIncrementMilli, automaticWeeklySetCeiling, load,
                primaryMuscle, secondaryMuscles);
    }
}

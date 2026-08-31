package de.thonktank.autosecretary.domain.training;

import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TrainingAssistantConfig;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.TrainingDecision;
import de.thonktank.autosecretary.domain.model.TrainingObservation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure, explainable double-progression engine without assumed device increments. */
public final class TrainingAdaptationEngine {
    public static final int CALIBRATION_OBSERVATIONS = 3;
    public static final int REQUIRED_STREAK = 2;
    public static final int MAXIMUM_LOAD_JUMP_PERCENT = 10;

    public enum Signal { INELIGIBLE, READY_UP, HOLD, TOO_HARD, SAFETY_PAUSE }

    public TrainingDecision evaluate(StepAmount.SetsReps current,
                                     TrainingAssistantConfig config,
                                     TrainingAssistantState state, List<SetResult> sets,
                                     double projectedPrimaryWeeklySets) {
        if (current == null || config == null || state == null || sets == null)
            throw new IllegalArgumentException("Complete adaptation input is required");
        if (!config.enabled || state.status == TrainingAssistantState.Status.DISABLED)
            return decision(TrainingDecision.Action.HOLD, TrainingDecision.Reason.NONE,
                    current, config.load, TrainingAssistantState.disabled(), null);
        Signal signal = classify(current, config, sets);
        if (signal == Signal.INELIGIBLE)
            return decision(TrainingDecision.Action.HOLD, TrainingDecision.Reason.NONE,
                    current, config.load, state, null);
        if (signal == Signal.SAFETY_PAUSE)
            return decision(TrainingDecision.Action.PAUSE, TrainingDecision.Reason.SAFETY_PAUSE,
                    current, config.load, new TrainingAssistantState(
                            TrainingAssistantState.Status.PAUSED,
                            state.eligibleObservations, 0, 0), null);

        int observations = state.eligibleObservations + 1;
        int ready = signal == Signal.READY_UP ? state.readyStreak + 1 : 0;
        int hard = signal == Signal.TOO_HARD ? state.hardStreak + 1 : 0;
        TrainingAssistantState.Status status = observations >= CALIBRATION_OBSERVATIONS
                ? TrainingAssistantState.Status.ACTIVE
                : TrainingAssistantState.Status.CALIBRATING;
        TrainingAssistantState next = new TrainingAssistantState(status, observations, ready, hard);
        if (status != TrainingAssistantState.Status.ACTIVE)
            return decision(TrainingDecision.Action.HOLD, TrainingDecision.Reason.CALIBRATING,
                    current, config.load, next, null);
        if (state.status == TrainingAssistantState.Status.CALIBRATING)
            return decision(TrainingDecision.Action.HOLD, TrainingDecision.Reason.CALIBRATING,
                    current, config.load,
                    new TrainingAssistantState(status, observations, 0, 0), null);
        if (ready >= REQUIRED_STREAK)
            return progress(current, config, next, projectedPrimaryWeeklySets);
        if (hard >= REQUIRED_STREAK) return regress(current, config, next);
        return decision(TrainingDecision.Action.HOLD, TrainingDecision.Reason.NONE,
                current, config.load, next, null);
    }

    public Signal classify(StepAmount.SetsReps current, TrainingAssistantConfig config,
                           List<SetResult> sets) {
        if (sets.size() != current.sets) return Signal.INELIGIBLE;
        List<Integer> rirs = new ArrayList<>();
        int missed = 0;
        for (SetResult set : sets) {
            TrainingObservation observation = set.training;
            if (observation == null || observation.origin != TrainingObservation.Origin.USER
                    || observation.rir == null || !observation.load.equals(config.load))
                return Signal.INELIGIBLE;
            if (observation.safety == TrainingObservation.Safety.PAIN_OR_TECHNIQUE)
                return Signal.SAFETY_PAUSE;
            if (set.repetitions < current.repetitions) missed++;
            rirs.add(observation.rir);
        }
        Collections.sort(rirs);
        int median = rirs.get(rirs.size() / 2);
        if (missed * 2 > sets.size() || median == 0) return Signal.TOO_HARD;
        boolean allReached = missed == 0;
        boolean anyFailure = rirs.get(0) == 0;
        return allReached && median >= config.targetRir && !anyFailure
                ? Signal.READY_UP : Signal.HOLD;
    }

    private TrainingDecision progress(StepAmount.SetsReps current,
                                      TrainingAssistantConfig config,
                                      TrainingAssistantState state, double projectedSets) {
        TrainingAssistantState reset = resetStreaks(state);
        if (current.repetitions < config.maxRepetitions)
            return decision(TrainingDecision.Action.APPLY,
                    TrainingDecision.Reason.REPETITIONS_INCREASED,
                    setsReps(current.sets, current.repetitions + 1), config.load, reset, null);
        if (config.load.adjustable())
            return decision(TrainingDecision.Action.REQUEST_NEXT_LOAD,
                    TrainingDecision.Reason.NEXT_LOAD_REQUIRED, current, config.load, reset,
                    TrainingDecision.LoadDirection.PROGRESS);
        return progressSets(current, config, reset, projectedSets);
    }

    private TrainingDecision progressSets(StepAmount.SetsReps current,
                                          TrainingAssistantConfig config,
                                          TrainingAssistantState state, double projectedSets) {
        if (current.sets < config.maxSets && config.primaryMuscle != null
                && projectedSets + 1 <= config.automaticWeeklySetCeiling)
            return decision(TrainingDecision.Action.APPLY, TrainingDecision.Reason.SET_ADDED,
                    setsReps(current.sets + 1, config.minRepetitions), config.load, state, null);
        TrainingDecision.Reason reason = current.sets < config.maxSets
                && config.primaryMuscle != null ? TrainingDecision.Reason.VOLUME_LIMIT
                : TrainingDecision.Reason.BOUNDARY_REACHED;
        return decision(TrainingDecision.Action.HOLD, reason,
                current, config.load, state, null);
    }

    private TrainingDecision regress(StepAmount.SetsReps current,
                                     TrainingAssistantConfig config,
                                     TrainingAssistantState state) {
        TrainingAssistantState reset = resetStreaks(state);
        if (current.sets > config.minSets)
            return decision(TrainingDecision.Action.APPLY, TrainingDecision.Reason.SET_REMOVED,
                    setsReps(current.sets - 1, current.repetitions), config.load, reset, null);
        if (current.repetitions > config.minRepetitions)
            return decision(TrainingDecision.Action.APPLY,
                    TrainingDecision.Reason.REPETITIONS_REDUCED,
                    setsReps(current.sets, current.repetitions - 1), config.load, reset, null);
        if (config.load.adjustable())
            return decision(TrainingDecision.Action.REQUEST_NEXT_LOAD,
                    TrainingDecision.Reason.LOWER_LOAD_REQUIRED, current, config.load, reset,
                    TrainingDecision.LoadDirection.REGRESS);
        return decision(TrainingDecision.Action.PAUSE,
                TrainingDecision.Reason.BOUNDARY_REACHED, current, config.load,
                new TrainingAssistantState(TrainingAssistantState.Status.PAUSED,
                        state.eligibleObservations, 0, 0), null);
    }

    public TrainingDecision progressSetsAfterUnavailableLoad(
            StepAmount.SetsReps current, TrainingAssistantConfig config,
            TrainingAssistantState state, double projectedSets) {
        if (current == null || config == null || state == null)
            throw new IllegalArgumentException("Complete load fallback input is required");
        return progressSets(current, config, resetStreaks(state), projectedSets);
    }

    private static TrainingAssistantState resetStreaks(TrainingAssistantState value) {
        return new TrainingAssistantState(value.status, value.eligibleObservations, 0, 0);
    }

    private static TrainingDecision decision(TrainingDecision.Action action,
                                             TrainingDecision.Reason reason,
                                             StepAmount.SetsReps prescription,
                                             ResistanceLoad load,
                                             TrainingAssistantState state,
                                             TrainingDecision.LoadDirection direction) {
        return new TrainingDecision(action, reason, prescription, load, state, direction);
    }

    private static StepAmount.SetsReps setsReps(int sets, int repetitions) {
        return (StepAmount.SetsReps) StepAmount.setsReps(sets, repetitions);
    }
}

package de.thonktank.autosecretary.domain.training;

import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TrainingAssistantConfig;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.model.TrainingObservation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure, explainable double-progression engine. */
public final class TrainingAdaptationEngine {
    public static final int CALIBRATION_OBSERVATIONS = 3;
    public static final int REQUIRED_STREAK = 2;
    public static final int MAXIMUM_LOAD_JUMP_PERCENT = 10;

    public enum Signal { INELIGIBLE, READY_UP, HOLD, TOO_HARD, SAFETY_PAUSE }
    public enum Reason {
        NONE, CALIBRATING, REPETITIONS_INCREASED, LOAD_INCREASED, SET_ADDED,
        REPETITIONS_REDUCED, LOAD_REDUCED, SET_REMOVED, BOUNDARY_REACHED, SAFETY_PAUSE
    }

    public static final class Result {
        public final StepAmount.SetsReps prescription;
        public final TrainingAssistantConfig config;
        public final TrainingAssistantState state;
        public final Signal signal;
        public final Reason reason;

        Result(StepAmount.SetsReps prescription, TrainingAssistantConfig config,
               TrainingAssistantState state, Signal signal, Reason reason) {
            this.prescription = prescription;
            this.config = config;
            this.state = state;
            this.signal = signal;
            this.reason = reason;
        }

        public boolean changedFrom(StepAmount.SetsReps before,
                                   TrainingAssistantConfig oldConfig) {
            return !prescription.equals(before) || !config.load.equals(oldConfig.load);
        }
    }

    public Result evaluate(StepAmount.SetsReps current, TrainingAssistantConfig config,
                           TrainingAssistantState state, List<SetResult> sets,
                           double projectedPrimaryWeeklySets) {
        if (current == null || config == null || state == null || sets == null)
            throw new IllegalArgumentException("Complete adaptation input is required");
        if (!config.enabled || state.status == TrainingAssistantState.Status.DISABLED)
            return result(current, config, TrainingAssistantState.disabled(),
                    Signal.INELIGIBLE, Reason.NONE);
        Signal signal = classify(current, config, sets);
        if (signal == Signal.INELIGIBLE)
            return result(current, config, state, signal, Reason.NONE);
        if (signal == Signal.SAFETY_PAUSE)
            return result(current, config, new TrainingAssistantState(
                    TrainingAssistantState.Status.PAUSED, state.eligibleObservations, 0, 0),
                    signal, Reason.SAFETY_PAUSE);

        int observations = state.eligibleObservations + 1;
        int ready = signal == Signal.READY_UP ? state.readyStreak + 1 : 0;
        int hard = signal == Signal.TOO_HARD ? state.hardStreak + 1 : 0;
        TrainingAssistantState.Status status = observations >= CALIBRATION_OBSERVATIONS
                ? TrainingAssistantState.Status.ACTIVE
                : TrainingAssistantState.Status.CALIBRATING;
        TrainingAssistantState next = new TrainingAssistantState(status, observations, ready, hard);
        if (status != TrainingAssistantState.Status.ACTIVE)
            return result(current, config, next, signal, Reason.CALIBRATING);
        if (state.status == TrainingAssistantState.Status.CALIBRATING) {
            TrainingAssistantState activated = new TrainingAssistantState(status, observations, 0, 0);
            return result(current, config, activated, signal, Reason.CALIBRATING);
        }
        if (ready >= REQUIRED_STREAK)
            return progress(current, config, next, projectedPrimaryWeeklySets, signal);
        if (hard >= REQUIRED_STREAK) return regress(current, config, next, signal);
        return result(current, config, next, signal, Reason.NONE);
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

    private Result progress(StepAmount.SetsReps current, TrainingAssistantConfig config,
                            TrainingAssistantState state, double projectedSets, Signal signal) {
        TrainingAssistantState reset = resetStreaks(state);
        if (current.repetitions < config.maxRepetitions)
            return result(setsReps(current.sets, current.repetitions + 1), config,
                    reset, signal, Reason.REPETITIONS_INCREASED);
        if (config.load.progressionWithinPercent(config.loadIncrementMilli,
                MAXIMUM_LOAD_JUMP_PERCENT))
            return result(setsReps(current.sets, config.minRepetitions),
                    config.withLoad(config.load.progress(config.loadIncrementMilli)), reset,
                    signal, Reason.LOAD_INCREASED);
        if (current.sets < config.maxSets && config.primaryMuscle != null
                && projectedSets + 1 <= config.automaticWeeklySetCeiling)
            return result(setsReps(current.sets + 1, config.minRepetitions), config,
                    reset, signal, Reason.SET_ADDED);
        return result(current, config, reset, signal, Reason.BOUNDARY_REACHED);
    }

    private Result regress(StepAmount.SetsReps current, TrainingAssistantConfig config,
                           TrainingAssistantState state, Signal signal) {
        TrainingAssistantState reset = resetStreaks(state);
        if (current.sets > config.minSets)
            return result(setsReps(current.sets - 1, current.repetitions), config,
                    reset, signal, Reason.SET_REMOVED);
        if (config.load.adjustable())
            return result(current, config.withLoad(config.load.regress(config.loadIncrementMilli)),
                    reset, signal, Reason.LOAD_REDUCED);
        if (current.repetitions > config.minRepetitions)
            return result(setsReps(current.sets, current.repetitions - 1), config,
                    reset, signal, Reason.REPETITIONS_REDUCED);
        return result(current, config, new TrainingAssistantState(
                TrainingAssistantState.Status.PAUSED, state.eligibleObservations, 0, 0),
                signal, Reason.BOUNDARY_REACHED);
    }

    private static TrainingAssistantState resetStreaks(TrainingAssistantState value) {
        return new TrainingAssistantState(value.status, value.eligibleObservations, 0, 0);
    }

    private static Result result(StepAmount.SetsReps prescription,
                                 TrainingAssistantConfig config,
                                 TrainingAssistantState state, Signal signal, Reason reason) {
        return new Result(prescription, config, state, signal, reason);
    }

    private static StepAmount.SetsReps setsReps(int sets, int repetitions) {
        return (StepAmount.SetsReps) StepAmount.setsReps(sets, repetitions);
    }
}

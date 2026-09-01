package de.thonktank.autosecretary.domain.training;

import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepPrescription;
import de.thonktank.autosecretary.domain.model.TrainingAssistantPolicy;
import de.thonktank.autosecretary.domain.model.TrainingAssistantProfile;
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

    public TrainingDecision evaluate(StepPrescription current,
                                     TrainingAssistantProfile profile, List<SetResult> sets,
                                     double projectedPrimaryWeeklySets) {
        if (current == null || !(current.amount instanceof StepAmount.SetsReps)
                || current.training == null || profile == null || sets == null)
            throw new IllegalArgumentException("Complete adaptation input is required");
        StepAmount.SetsReps amount = (StepAmount.SetsReps) current.amount;
        TrainingAssistantPolicy policy = profile.policy;
        TrainingAssistantState state = profile.state;
        Signal signal = classify(current, profile, sets);
        if (signal == Signal.INELIGIBLE)
            return decision(TrainingDecision.Action.HOLD, TrainingDecision.Reason.NONE,
                    current, state, null);
        if (signal == Signal.SAFETY_PAUSE)
            return decision(TrainingDecision.Action.PAUSE, TrainingDecision.Reason.SAFETY_PAUSE,
                    current, new TrainingAssistantState(
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
                    current, next, null);
        if (state.status == TrainingAssistantState.Status.CALIBRATING)
            return decision(TrainingDecision.Action.HOLD, TrainingDecision.Reason.CALIBRATING,
                    current,
                    new TrainingAssistantState(status, observations, 0, 0), null);
        if (ready >= REQUIRED_STREAK)
            return progress(current, policy, next, projectedPrimaryWeeklySets);
        if (hard >= REQUIRED_STREAK) return regress(current, policy, next);
        return decision(TrainingDecision.Action.HOLD, TrainingDecision.Reason.NONE,
                current, next, null);
    }

    public Signal classify(StepPrescription current, TrainingAssistantProfile profile,
                           List<SetResult> sets) {
        if (current == null || !(current.amount instanceof StepAmount.SetsReps)
                || current.training == null || profile == null || sets == null)
            throw new IllegalArgumentException("Complete classification input is required");
        StepAmount.SetsReps amount = (StepAmount.SetsReps) current.amount;
        if (sets.size() != amount.sets) return Signal.INELIGIBLE;
        List<Integer> rirs = new ArrayList<>();
        int missed = 0;
        for (SetResult set : sets) {
            TrainingObservation observation = set.training;
            if (observation == null || observation.origin != TrainingObservation.Origin.USER
                    || observation.rir == null
                    || !observation.load.equals(current.training.load))
                return Signal.INELIGIBLE;
            if (observation.safety == TrainingObservation.Safety.PAIN_OR_TECHNIQUE)
                return Signal.SAFETY_PAUSE;
            if (set.repetitions < amount.repetitions) missed++;
            rirs.add(observation.rir);
        }
        Collections.sort(rirs);
        int median = rirs.get(rirs.size() / 2);
        if (missed * 2 > sets.size() || median == 0) return Signal.TOO_HARD;
        boolean allReached = missed == 0;
        boolean anyFailure = rirs.get(0) == 0;
        return allReached && median >= current.training.targetRir && !anyFailure
                ? Signal.READY_UP : Signal.HOLD;
    }

    private TrainingDecision progress(StepPrescription current,
                                      TrainingAssistantPolicy policy,
                                      TrainingAssistantState state, double projectedSets) {
        StepAmount.SetsReps amount = (StepAmount.SetsReps) current.amount;
        TrainingAssistantState reset = resetStreaks(state);
        if (amount.repetitions < policy.maxRepetitions)
            return decision(TrainingDecision.Action.APPLY,
                    TrainingDecision.Reason.REPETITIONS_INCREASED,
                    withAmount(current, amount.sets, amount.repetitions + 1), reset, null);
        if (current.training.load.adjustable())
            return decision(TrainingDecision.Action.REQUEST_NEXT_LOAD,
                    TrainingDecision.Reason.NEXT_LOAD_REQUIRED, current, reset,
                    TrainingDecision.LoadDirection.PROGRESS);
        return progressSets(current, policy, reset, projectedSets);
    }

    private TrainingDecision progressSets(StepPrescription current,
                                          TrainingAssistantPolicy policy,
                                          TrainingAssistantState state, double projectedSets) {
        StepAmount.SetsReps amount = (StepAmount.SetsReps) current.amount;
        if (amount.sets < policy.maxSets && policy.primaryMuscle != null
                && projectedSets + 1 <= policy.automaticWeeklySetCeiling)
            return decision(TrainingDecision.Action.APPLY, TrainingDecision.Reason.SET_ADDED,
                    withAmount(current, amount.sets + 1, policy.minRepetitions), state, null);
        TrainingDecision.Reason reason = amount.sets < policy.maxSets
                && policy.primaryMuscle != null ? TrainingDecision.Reason.VOLUME_LIMIT
                : TrainingDecision.Reason.BOUNDARY_REACHED;
        return decision(TrainingDecision.Action.HOLD, reason,
                current, state, null);
    }

    private TrainingDecision regress(StepPrescription current,
                                     TrainingAssistantPolicy policy,
                                     TrainingAssistantState state) {
        StepAmount.SetsReps amount = (StepAmount.SetsReps) current.amount;
        TrainingAssistantState reset = resetStreaks(state);
        if (amount.sets > policy.minSets)
            return decision(TrainingDecision.Action.APPLY, TrainingDecision.Reason.SET_REMOVED,
                    withAmount(current, amount.sets - 1, amount.repetitions), reset, null);
        if (amount.repetitions > policy.minRepetitions)
            return decision(TrainingDecision.Action.APPLY,
                    TrainingDecision.Reason.REPETITIONS_REDUCED,
                    withAmount(current, amount.sets, amount.repetitions - 1), reset, null);
        if (current.training.load.adjustable())
            return decision(TrainingDecision.Action.REQUEST_NEXT_LOAD,
                    TrainingDecision.Reason.LOWER_LOAD_REQUIRED, current, reset,
                    TrainingDecision.LoadDirection.REGRESS);
        return decision(TrainingDecision.Action.PAUSE,
                TrainingDecision.Reason.BOUNDARY_REACHED, current,
                new TrainingAssistantState(TrainingAssistantState.Status.PAUSED,
                        state.eligibleObservations, 0, 0), null);
    }

    public TrainingDecision progressSetsAfterUnavailableLoad(
            StepPrescription current, TrainingAssistantProfile profile, double projectedSets) {
        if (current == null || !(current.amount instanceof StepAmount.SetsReps)
                || current.training == null || profile == null)
            throw new IllegalArgumentException("Complete load fallback input is required");
        return progressSets(current, profile.policy, resetStreaks(profile.state), projectedSets);
    }

    private static TrainingAssistantState resetStreaks(TrainingAssistantState value) {
        return new TrainingAssistantState(value.status, value.eligibleObservations, 0, 0);
    }

    private static TrainingDecision decision(TrainingDecision.Action action,
                                             TrainingDecision.Reason reason,
                                             StepPrescription prescription,
                                             TrainingAssistantState state,
                                             TrainingDecision.LoadDirection direction) {
        return new TrainingDecision(action, reason, prescription, state, direction);
    }

    private static StepPrescription withAmount(StepPrescription current,
                                               int sets, int repetitions) {
        return new StepPrescription(StepAmount.setsReps(sets, repetitions), current.rest,
                current.training);
    }
}

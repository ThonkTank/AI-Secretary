package de.thonktank.autosecretary.domain.training;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepPrescription;
import de.thonktank.autosecretary.domain.model.TrainingAssistantPolicy;
import de.thonktank.autosecretary.domain.model.TrainingAssistantProfile;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup;
import de.thonktank.autosecretary.domain.model.TrainingDecision;
import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.model.TrainingObservation;
import de.thonktank.autosecretary.domain.model.TrainingPrescription;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TrainingAdaptationEngineTest {
    private final TrainingAdaptationEngine engine = new TrainingAdaptationEngine();

    @Test public void calibrationRequiresThreeEligibleObservationsAndTwoReadySignals() {
        ResistanceLoad load = ResistanceLoad.numeric(ResistanceLoad.Mode.EXTERNAL,
                ResistanceLoad.Unit.KG, 50_000);
        TrainingAssistantPolicy policy = TrainingAssistantPolicy.defaults(
                TrainingMuscleGroup.CHEST);
        StepPrescription prescription = prescription(3, 8, load);
        TrainingAssistantState state = TrainingAssistantState.calibrating();

        TrainingDecision first = engine.evaluate(prescription, profile(policy, state),
                ready(3, 8, load), 6);
        assertEquals(TrainingDecision.Reason.CALIBRATING, first.reason);
        TrainingDecision second = engine.evaluate(prescription,
                profile(policy, first.nextState), ready(3, 8, load), 6);
        assertEquals(TrainingDecision.Reason.CALIBRATING, second.reason);
        TrainingDecision third = engine.evaluate(prescription,
                profile(policy, second.nextState), ready(3, 8, load), 6);

        assertEquals(TrainingDecision.Reason.CALIBRATING, third.reason);
        assertEquals(8, amount(third).repetitions);
        assertEquals(TrainingAssistantState.Status.ACTIVE, third.nextState.status);
        assertEquals(0, third.nextState.readyStreak);

        TrainingDecision fourth = engine.evaluate(prescription,
                profile(policy, third.nextState), ready(3, 8, load), 6);
        assertEquals(TrainingDecision.Reason.NONE, fourth.reason);
        TrainingDecision fifth = engine.evaluate(prescription,
                profile(policy, fourth.nextState), ready(3, 8, load), 6);
        assertEquals(TrainingDecision.Reason.REPETITIONS_INCREASED, fifth.reason);
        assertEquals(9, amount(fifth).repetitions);
    }

    @Test public void topOfRangeRequestsTheConcreteNextLoadWithoutChangingPrescription() {
        ResistanceLoad load = ResistanceLoad.numeric(ResistanceLoad.Mode.EXTERNAL,
                ResistanceLoad.Unit.KG, 50_000);
        TrainingAssistantPolicy policy = TrainingAssistantPolicy.defaults(
                TrainingMuscleGroup.BACK);
        TrainingAssistantState state = new TrainingAssistantState(
                TrainingAssistantState.Status.ACTIVE, 4, 1, 0);

        TrainingDecision result = engine.evaluate(prescription(3, 12, load),
                profile(policy, state),
                ready(3, 12, load), 6);

        assertEquals(TrainingDecision.Action.REQUEST_NEXT_LOAD, result.action);
        assertEquals(TrainingDecision.Reason.NEXT_LOAD_REQUIRED, result.reason);
        assertEquals(TrainingDecision.LoadDirection.PROGRESS, result.loadDirection);
        assertEquals(load, result.nextPrescription.training.load);
        assertEquals(12, amount(result).repetitions);
    }

    @Test public void bodyweightAddsOneSetOnlyBelowConfiguredVolumeCeiling() {
        ResistanceLoad load = ResistanceLoad.bodyweight();
        TrainingAssistantPolicy policy = new TrainingAssistantPolicy(2, 4, 8, 12, 10,
                TrainingMuscleGroup.QUADRICEPS, Collections.emptySet());
        TrainingAssistantState state = new TrainingAssistantState(
                TrainingAssistantState.Status.ACTIVE, 4, 1, 0);

        StepPrescription prescription = prescription(3, 12, load);
        TrainingDecision added = engine.evaluate(prescription, profile(policy, state),
                ready(3, 12, load), 8);
        assertEquals(TrainingDecision.Reason.SET_ADDED, added.reason);
        assertEquals(4, amount(added).sets);

        TrainingDecision held = engine.evaluate(prescription, profile(policy, state),
                ready(3, 12, load), 10);
        assertEquals(TrainingDecision.Reason.VOLUME_LIMIT, held.reason);
        assertEquals(3, amount(held).sets);
    }

    @Test public void repeatedHardSignalRegressesOneDimension() {
        ResistanceLoad load = ResistanceLoad.numeric(ResistanceLoad.Mode.ASSISTED_BODYWEIGHT,
                ResistanceLoad.Unit.KG, 20_000);
        TrainingAssistantPolicy policy = TrainingAssistantPolicy.defaults(
                TrainingMuscleGroup.BACK);
        TrainingAssistantState state = new TrainingAssistantState(
                TrainingAssistantState.Status.ACTIVE, 5, 0, 1);

        TrainingDecision result = engine.evaluate(prescription(3, 10, load),
                profile(policy, state),
                hard(3, 7, load), 6);
        assertEquals(TrainingDecision.Reason.SET_REMOVED, result.reason);
        assertEquals(2, amount(result).sets);
        assertEquals(10, amount(result).repetitions);
        assertEquals(Long.valueOf(20_000), result.nextPrescription.training.load.milliUnits);
    }

    @Test public void safetyFlagPausesWithoutChangingPrescription() {
        ResistanceLoad load = ResistanceLoad.bodyweight();
        TrainingAssistantPolicy policy = TrainingAssistantPolicy.defaults(
                TrainingMuscleGroup.CORE);
        List<SetResult> sets = ready(2, 10, load);
        sets.set(1, new SetResult(10, new TrainingObservation(load, 2,
                TrainingObservation.Safety.PAIN_OR_TECHNIQUE,
                TrainingObservation.Origin.USER)));

        StepPrescription prescription = prescription(2, 10, load);
        TrainingDecision result = engine.evaluate(prescription,
                profile(policy, TrainingAssistantState.calibrating()), sets, 2);
        assertEquals(TrainingDecision.Reason.SAFETY_PAUSE, result.reason);
        assertEquals(TrainingAssistantState.Status.PAUSED, result.nextState.status);
        assertFalse(result.changedFrom(prescription));
    }

    @Test public void legacyAndMismatchedLoadAreNotLearningEvidence() {
        ResistanceLoad planned = ResistanceLoad.numeric(ResistanceLoad.Mode.EXTERNAL,
                ResistanceLoad.Unit.KG, 20_000);
        ResistanceLoad actual = ResistanceLoad.numeric(ResistanceLoad.Mode.EXTERNAL,
                ResistanceLoad.Unit.KG, 22_500);
        TrainingAssistantProfile profile = profile(TrainingAssistantPolicy.defaults(
                TrainingMuscleGroup.SHOULDERS), TrainingAssistantState.calibrating());
        List<SetResult> values = ready(2, 10, actual);
        assertEquals(TrainingAdaptationEngine.Signal.INELIGIBLE,
                engine.classify(prescription(2, 10, planned), profile, values));
        assertEquals(TrainingMuscleGroup.SHOULDERS, profile.policy.primaryMuscle);
    }

    private static StepAmount.SetsReps sets(int sets, int repetitions) {
        return (StepAmount.SetsReps) StepAmount.setsReps(sets, repetitions);
    }

    private static StepPrescription prescription(int sets, int repetitions,
                                                 ResistanceLoad load) {
        return new StepPrescription(sets(sets, repetitions), null,
                new TrainingPrescription(load, 2));
    }

    private static TrainingAssistantProfile profile(TrainingAssistantPolicy policy,
                                                    TrainingAssistantState state) {
        return new TrainingAssistantProfile(policy, state);
    }

    private static StepAmount.SetsReps amount(TrainingDecision decision) {
        return (StepAmount.SetsReps) decision.nextPrescription.amount;
    }

    private static List<SetResult> ready(int sets, int reps, ResistanceLoad load) {
        List<SetResult> result = new ArrayList<>();
        for (int index = 0; index < sets; index++)
            result.add(new SetResult(reps, TrainingObservation.user(load, 2)));
        return result;
    }

    private static List<SetResult> hard(int sets, int reps, ResistanceLoad load) {
        List<SetResult> result = new ArrayList<>();
        for (int index = 0; index < sets; index++)
            result.add(new SetResult(reps, TrainingObservation.user(load, 0)));
        return result;
    }
}

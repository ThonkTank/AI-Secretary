package de.thonktank.autosecretary.domain.training;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TrainingAssistantConfig;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup;
import de.thonktank.autosecretary.domain.model.TrainingDecision;
import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.model.TrainingObservation;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TrainingAdaptationEngineTest {
    private final TrainingAdaptationEngine engine = new TrainingAdaptationEngine();

    @Test public void calibrationRequiresThreeEligibleObservationsAndTwoReadySignals() {
        ResistanceLoad load = ResistanceLoad.numeric(ResistanceLoad.Mode.EXTERNAL,
                ResistanceLoad.Unit.KG, 50_000);
        TrainingAssistantConfig config = TrainingAssistantConfig.defaults(load,
                TrainingMuscleGroup.CHEST);
        StepAmount.SetsReps prescription = sets(3, 8);
        TrainingAssistantState state = TrainingAssistantState.calibrating();

        TrainingDecision first = engine.evaluate(prescription, config, state,
                ready(3, 8, load), 6);
        assertEquals(TrainingDecision.Reason.CALIBRATING, first.reason);
        TrainingDecision second = engine.evaluate(prescription, config,
                first.state, ready(3, 8, load), 6);
        assertEquals(TrainingDecision.Reason.CALIBRATING, second.reason);
        TrainingDecision third = engine.evaluate(prescription, config,
                second.state, ready(3, 8, load), 6);

        assertEquals(TrainingDecision.Reason.CALIBRATING, third.reason);
        assertEquals(8, third.prescription.repetitions);
        assertEquals(TrainingAssistantState.Status.ACTIVE, third.state.status);
        assertEquals(0, third.state.readyStreak);

        TrainingDecision fourth = engine.evaluate(prescription, config,
                third.state, ready(3, 8, load), 6);
        assertEquals(TrainingDecision.Reason.NONE, fourth.reason);
        TrainingDecision fifth = engine.evaluate(prescription, config,
                fourth.state, ready(3, 8, load), 6);
        assertEquals(TrainingDecision.Reason.REPETITIONS_INCREASED, fifth.reason);
        assertEquals(9, fifth.prescription.repetitions);
    }

    @Test public void topOfRangeRequestsTheConcreteNextLoadWithoutChangingPrescription() {
        ResistanceLoad load = ResistanceLoad.numeric(ResistanceLoad.Mode.EXTERNAL,
                ResistanceLoad.Unit.KG, 50_000);
        TrainingAssistantConfig config = TrainingAssistantConfig.defaults(load,
                TrainingMuscleGroup.BACK);
        TrainingAssistantState state = new TrainingAssistantState(
                TrainingAssistantState.Status.ACTIVE, 4, 1, 0);

        TrainingDecision result = engine.evaluate(sets(3, 12), config, state,
                ready(3, 12, load), 6);

        assertEquals(TrainingDecision.Action.REQUEST_NEXT_LOAD, result.action);
        assertEquals(TrainingDecision.Reason.NEXT_LOAD_REQUIRED, result.reason);
        assertEquals(TrainingDecision.LoadDirection.PROGRESS, result.loadDirection);
        assertEquals(load, result.load);
        assertEquals(12, result.prescription.repetitions);
    }

    @Test public void bodyweightAddsOneSetOnlyBelowConfiguredVolumeCeiling() {
        ResistanceLoad load = ResistanceLoad.bodyweight();
        TrainingAssistantConfig config = new TrainingAssistantConfig(true, 2, 4, 8, 12, 2,
                10, load, TrainingMuscleGroup.QUADRICEPS, Collections.emptySet());
        TrainingAssistantState state = new TrainingAssistantState(
                TrainingAssistantState.Status.ACTIVE, 4, 1, 0);

        TrainingDecision added = engine.evaluate(sets(3, 12), config, state,
                ready(3, 12, load), 8);
        assertEquals(TrainingDecision.Reason.SET_ADDED, added.reason);
        assertEquals(4, added.prescription.sets);

        TrainingDecision held = engine.evaluate(sets(3, 12), config, state,
                ready(3, 12, load), 10);
        assertEquals(TrainingDecision.Reason.VOLUME_LIMIT, held.reason);
        assertEquals(3, held.prescription.sets);
    }

    @Test public void repeatedHardSignalRegressesOneDimension() {
        ResistanceLoad load = ResistanceLoad.numeric(ResistanceLoad.Mode.ASSISTED_BODYWEIGHT,
                ResistanceLoad.Unit.KG, 20_000);
        TrainingAssistantConfig config = TrainingAssistantConfig.defaults(load,
                TrainingMuscleGroup.BACK);
        TrainingAssistantState state = new TrainingAssistantState(
                TrainingAssistantState.Status.ACTIVE, 5, 0, 1);

        TrainingDecision result = engine.evaluate(sets(3, 10), config, state,
                hard(3, 7, load), 6);
        assertEquals(TrainingDecision.Reason.SET_REMOVED, result.reason);
        assertEquals(2, result.prescription.sets);
        assertEquals(10, result.prescription.repetitions);
        assertEquals(Long.valueOf(20_000), result.load.milliUnits);
    }

    @Test public void safetyFlagPausesWithoutChangingPrescription() {
        ResistanceLoad load = ResistanceLoad.bodyweight();
        TrainingAssistantConfig config = TrainingAssistantConfig.defaults(load,
                TrainingMuscleGroup.CORE);
        List<SetResult> sets = ready(2, 10, load);
        sets.set(1, new SetResult(10, new TrainingObservation(load, 2,
                TrainingObservation.Safety.PAIN_OR_TECHNIQUE,
                TrainingObservation.Origin.USER)));

        TrainingDecision result = engine.evaluate(sets(2, 10), config,
                TrainingAssistantState.calibrating(), sets, 2);
        assertEquals(TrainingDecision.Reason.SAFETY_PAUSE, result.reason);
        assertEquals(TrainingAssistantState.Status.PAUSED, result.state.status);
        assertFalse(result.changedFrom(sets(2, 10), config.load));
    }

    @Test public void legacyAndMismatchedLoadAreNotLearningEvidence() {
        ResistanceLoad planned = ResistanceLoad.numeric(ResistanceLoad.Mode.EXTERNAL,
                ResistanceLoad.Unit.KG, 20_000);
        ResistanceLoad actual = ResistanceLoad.numeric(ResistanceLoad.Mode.EXTERNAL,
                ResistanceLoad.Unit.KG, 22_500);
        TrainingAssistantConfig config = TrainingAssistantConfig.defaults(planned,
                TrainingMuscleGroup.SHOULDERS);
        List<SetResult> values = ready(2, 10, actual);
        assertEquals(TrainingAdaptationEngine.Signal.INELIGIBLE,
                engine.classify(sets(2, 10), config, values));
        assertTrue(config.enabled);
    }

    private static StepAmount.SetsReps sets(int sets, int repetitions) {
        return (StepAmount.SetsReps) StepAmount.setsReps(sets, repetitions);
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

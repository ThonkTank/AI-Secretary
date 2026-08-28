package de.thonktank.autosecretary.domain.training;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TrainingAssistantConfig;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup;
import de.thonktank.autosecretary.domain.model.TrainingSetResult;

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

        TrainingAdaptationEngine.Result first = engine.evaluate(prescription, config, state,
                ready(3, 8, load), 6);
        assertEquals(TrainingAdaptationEngine.Reason.CALIBRATING, first.reason);
        TrainingAdaptationEngine.Result second = engine.evaluate(prescription, config,
                first.state, ready(3, 8, load), 6);
        assertEquals(TrainingAdaptationEngine.Reason.CALIBRATING, second.reason);
        TrainingAdaptationEngine.Result third = engine.evaluate(prescription, config,
                second.state, ready(3, 8, load), 6);

        assertEquals(TrainingAdaptationEngine.Reason.CALIBRATING, third.reason);
        assertEquals(8, third.prescription.repetitions);
        assertEquals(TrainingAssistantState.Status.ACTIVE, third.state.status);
        assertEquals(0, third.state.readyStreak);

        TrainingAdaptationEngine.Result fourth = engine.evaluate(prescription, config,
                third.state, ready(3, 8, load), 6);
        assertEquals(TrainingAdaptationEngine.Reason.NONE, fourth.reason);
        TrainingAdaptationEngine.Result fifth = engine.evaluate(prescription, config,
                fourth.state, ready(3, 8, load), 6);
        assertEquals(TrainingAdaptationEngine.Reason.REPETITIONS_INCREASED, fifth.reason);
        assertEquals(9, fifth.prescription.repetitions);
    }

    @Test public void topOfRangeUsesSmallLoadIncrementAndResetsRepetitions() {
        ResistanceLoad load = ResistanceLoad.numeric(ResistanceLoad.Mode.EXTERNAL,
                ResistanceLoad.Unit.KG, 50_000);
        TrainingAssistantConfig config = TrainingAssistantConfig.defaults(load,
                TrainingMuscleGroup.BACK);
        TrainingAssistantState state = new TrainingAssistantState(
                TrainingAssistantState.Status.ACTIVE, 4, 1, 0);

        TrainingAdaptationEngine.Result result = engine.evaluate(sets(3, 12), config, state,
                ready(3, 12, load), 6);

        assertEquals(TrainingAdaptationEngine.Reason.LOAD_INCREASED, result.reason);
        assertEquals(Long.valueOf(52_500), result.config.load.milliUnits);
        assertEquals(8, result.prescription.repetitions);
    }

    @Test public void bodyweightAddsOneSetOnlyBelowConfiguredVolumeCeiling() {
        ResistanceLoad load = ResistanceLoad.bodyweight();
        TrainingAssistantConfig config = new TrainingAssistantConfig(true, 2, 4, 8, 12, 2,
                0, 10, load, TrainingMuscleGroup.QUADRICEPS, Collections.emptySet());
        TrainingAssistantState state = new TrainingAssistantState(
                TrainingAssistantState.Status.ACTIVE, 4, 1, 0);

        TrainingAdaptationEngine.Result added = engine.evaluate(sets(3, 12), config, state,
                ready(3, 12, load), 8);
        assertEquals(TrainingAdaptationEngine.Reason.SET_ADDED, added.reason);
        assertEquals(4, added.prescription.sets);

        TrainingAdaptationEngine.Result held = engine.evaluate(sets(3, 12), config, state,
                ready(3, 12, load), 10);
        assertEquals(TrainingAdaptationEngine.Reason.BOUNDARY_REACHED, held.reason);
        assertEquals(3, held.prescription.sets);
    }

    @Test public void repeatedHardSignalRegressesOneDimension() {
        ResistanceLoad load = ResistanceLoad.numeric(ResistanceLoad.Mode.ASSISTED_BODYWEIGHT,
                ResistanceLoad.Unit.KG, 20_000);
        TrainingAssistantConfig config = TrainingAssistantConfig.defaults(load,
                TrainingMuscleGroup.BACK);
        TrainingAssistantState state = new TrainingAssistantState(
                TrainingAssistantState.Status.ACTIVE, 5, 0, 1);

        TrainingAdaptationEngine.Result result = engine.evaluate(sets(3, 10), config, state,
                hard(3, 7, load), 6);
        assertEquals(TrainingAdaptationEngine.Reason.SET_REMOVED, result.reason);
        assertEquals(2, result.prescription.sets);
        assertEquals(10, result.prescription.repetitions);
        assertEquals(Long.valueOf(20_000), result.config.load.milliUnits);
    }

    @Test public void safetyFlagPausesWithoutChangingPrescription() {
        ResistanceLoad load = ResistanceLoad.bodyweight();
        TrainingAssistantConfig config = TrainingAssistantConfig.defaults(load,
                TrainingMuscleGroup.CORE);
        List<TrainingSetResult> sets = ready(2, 10, load);
        sets.set(1, new TrainingSetResult(10, load, 2, TrainingSetResult.Source.USER,
                TrainingSetResult.SafetyFlag.PAIN_OR_TECHNIQUE));

        TrainingAdaptationEngine.Result result = engine.evaluate(sets(2, 10), config,
                TrainingAssistantState.calibrating(), sets, 2);
        assertEquals(TrainingAdaptationEngine.Reason.SAFETY_PAUSE, result.reason);
        assertEquals(TrainingAssistantState.Status.PAUSED, result.state.status);
        assertFalse(result.changedFrom(sets(2, 10), config));
    }

    @Test public void legacyAndMismatchedLoadAreNotLearningEvidence() {
        ResistanceLoad planned = ResistanceLoad.numeric(ResistanceLoad.Mode.EXTERNAL,
                ResistanceLoad.Unit.KG, 20_000);
        ResistanceLoad actual = ResistanceLoad.numeric(ResistanceLoad.Mode.EXTERNAL,
                ResistanceLoad.Unit.KG, 22_500);
        TrainingAssistantConfig config = TrainingAssistantConfig.defaults(planned,
                TrainingMuscleGroup.SHOULDERS);
        List<TrainingSetResult> values = ready(2, 10, actual);
        assertEquals(TrainingAdaptationEngine.Signal.INELIGIBLE,
                engine.classify(sets(2, 10), config, values));
        assertTrue(config.enabled);
    }

    private static StepAmount.SetsReps sets(int sets, int repetitions) {
        return (StepAmount.SetsReps) StepAmount.setsReps(sets, repetitions);
    }

    private static List<TrainingSetResult> ready(int sets, int reps, ResistanceLoad load) {
        List<TrainingSetResult> result = new ArrayList<>();
        for (int index = 0; index < sets; index++)
            result.add(TrainingSetResult.user(reps, load, 2));
        return result;
    }

    private static List<TrainingSetResult> hard(int sets, int reps, ResistanceLoad load) {
        List<TrainingSetResult> result = new ArrayList<>();
        for (int index = 0; index < sets; index++)
            result.add(TrainingSetResult.user(reps, load, 0));
        return result;
    }
}

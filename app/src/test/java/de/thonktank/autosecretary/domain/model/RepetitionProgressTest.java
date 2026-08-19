package de.thonktank.autosecretary.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class RepetitionProgressTest {
    @Test public void resultsAdvanceTheNextSlotAndDeriveCompletion() {
        RepetitionProgress empty = RepetitionProgress.restore(
                2, Collections.emptyList(), false);
        RepetitionProgress first = empty.record(0);
        RepetitionProgress complete = first.record(999);

        assertEquals(0, empty.nextOpenSlotIndex());
        assertEquals(1, first.nextOpenSlotIndex());
        assertFalse(first.completed());
        assertEquals(-1, complete.nextOpenSlotIndex());
        assertTrue(complete.completed());
        assertEquals(RepetitionProgress.Completion.RESULTS_COMPLETE, complete.completion);
        assertEquals(Arrays.asList(0, 999), complete.actualRepetitions);
    }

    @Test public void oneBoundaryOwnsValidationForNewAndCorrectedValues() {
        RepetitionProgress empty = RepetitionProgress.restore(
                2, Collections.emptyList(), false);
        RepetitionProgress one = empty.record(10);

        assertThrows(IllegalArgumentException.class, () -> empty.record(-1));
        assertThrows(IllegalArgumentException.class, () -> empty.record(1_000));
        assertThrows(IllegalArgumentException.class, () -> one.correct(0, -1));
        assertThrows(IllegalArgumentException.class, () -> one.correct(0, 1_000));
        assertEquals(Collections.singletonList(12), one.correct(0, 12).actualRepetitions);
        assertEquals(0, RepetitionProgress.clampInput(-5));
        assertEquals(999, RepetitionProgress.clampInput(2_000));
    }

    @Test public void legacyValuesRemainReadableButCannotBeRecordedAgain() {
        RepetitionProgress legacy = RepetitionProgress.restore(
                2, Collections.singletonList(1_200), false);

        assertEquals(Collections.singletonList(1_200), legacy.actualRepetitions);
        assertEquals(1, legacy.nextOpenSlotIndex());
        assertThrows(IllegalArgumentException.class, () -> legacy.record(1_200));
        assertThrows(IllegalArgumentException.class, () -> legacy.correct(0, 1_200));
    }

    @Test public void completionWithoutResultsIsExplicitAndReversible() {
        RepetitionProgress partial = RepetitionProgress.restore(
                3, Collections.singletonList(12), false);
        RepetitionProgress completed = partial.completeWithoutResults();
        RepetitionProgress reopened = completed.reopen();

        assertEquals(RepetitionProgress.Completion.COMPLETED_WITHOUT_RESULTS,
                completed.completion);
        assertTrue(completed.completed());
        assertEquals(Collections.singletonList(12), completed.actualRepetitions);
        assertEquals(RepetitionProgress.Completion.IN_PROGRESS, reopened.completion);
        assertEquals(Collections.singletonList(12), reopened.actualRepetitions);
    }

    @Test public void restoreCanonicalizesFormerlyContradictoryFullOpenProgress() {
        RepetitionProgress restored = RepetitionProgress.restore(
                2, Arrays.asList(10, 11), false);
        RepetitionProgress reopened = restored.reopen();

        assertTrue(restored.completed());
        assertEquals(RepetitionProgress.Completion.RESULTS_COMPLETE, restored.completion);
        assertEquals(Collections.singletonList(10), reopened.actualRepetitions);
        assertFalse(reopened.completed());
    }

    @Test public void amountFactoryRejectsProgressForNonRepetitionSteps() {
        assertEquals(null, RepetitionProgress.forAmount(
                StepAmount.duration(60), Collections.emptyList(), false));
        assertThrows(IllegalArgumentException.class, () -> RepetitionProgress.forAmount(
                StepAmount.none(), Collections.singletonList(10), false));
    }
}

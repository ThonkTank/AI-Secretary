package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.autosecretary.features.task.domain.model.TaskCore;
import com.autosecretary.shared.Period;

import org.junit.Test;

import java.time.LocalDate;

/**
 * Invariant protected: an unset or invalid {@link TaskCore.Repetition} is non-schedulable but never
 * throws. A null {@code periodUnit} used to NPE inside {@code periodInDays()} and crash the entire
 * schedule generation (see the assistant one-off-task regression).
 */
public final class TaskRepetitionCharacterizationTest {

    @Test
    public void nullPeriodUnitIsNonSchedulableWithoutThrowingInvariant() {
        TaskCore.Repetition rep = new TaskCore.Repetition();
        rep.periodUnit = null;      // as persisted by the assistant for a one-off task
        rep.perPeriod = 1;
        rep.reps = 3;
        rep.periodStart = LocalDate.now();

        assertEquals(0, rep.periodInDays());
        assertEquals(0, rep.repsPerDay());
        assertNull(rep.periodEnd());
    }

    @Test
    public void nonPositivePerPeriodIsNonSchedulableWithoutThrowingInvariant() {
        TaskCore.Repetition rep = new TaskCore.Repetition();
        rep.periodUnit = Period.WEEK;
        rep.perPeriod = 0;
        rep.reps = 5;
        rep.periodStart = LocalDate.now();

        assertEquals(0, rep.periodInDays());
        assertEquals(0, rep.repsPerDay());
        assertNull(rep.periodEnd());
    }

    @Test
    public void freshRepetitionHasValidDefaultsInvariant() {
        // Defaults must keep a freshly constructed repetition valid (never null periodUnit).
        TaskCore.Repetition rep = new TaskCore.Repetition();
        assertEquals(Period.DAY, rep.periodUnit);
        assertEquals(1, rep.perPeriod);
        assertEquals(0, rep.reps);       // reps=0 → still non-schedulable
        assertEquals(0, rep.repsPerDay());
    }

    @Test
    public void configuredRepetitionComputesUnchangedInvariant() {
        TaskCore.Repetition rep = new TaskCore.Repetition();
        rep.periodUnit = Period.WEEK;
        rep.perPeriod = 1;
        rep.reps = 3;
        LocalDate start = LocalDate.of(2026, 1, 1);
        rep.periodStart = start;

        assertEquals(7, rep.periodInDays());
        assertEquals(1, rep.repsPerDay());          // ceil(3/7)
        assertEquals(start.plusDays(7), rep.periodEnd());
    }
}

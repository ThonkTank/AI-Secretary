package com.autosecretary.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;

public final class RoutineStepProgressTest {
    @Test
    public void overdueRoutineKeepsStepsFromTheOpenOccurrencesWeekday() {
        LocalDate mondayOccurrence = LocalDate.parse("2026-08-10");
        Obligation routine = routine(mondayOccurrence);
        RoutineStep monday = new RoutineStep("Montagsschritt", EnumSet.of(DayOfWeek.MONDAY));
        RoutineStep tuesday = new RoutineStep("Dienstagsschritt", EnumSet.of(DayOfWeek.TUESDAY));
        monday.setCompletedFor(mondayOccurrence, true, LocalDateTime.parse("2026-08-11T09:00:00"));
        routine.steps.add(monday);
        routine.steps.add(tuesday);

        assertTrue(routine.planStepsFor(LocalDate.parse("2026-08-11")).get(0).completed());
        assertTrue(routine.activeStepsFor(LocalDate.parse("2026-08-11")).contains(monday));
        assertFalse(routine.activeStepsFor(LocalDate.parse("2026-08-11")).contains(tuesday));
    }

    @Test
    public void routineIsCompleteOnlyAfterEveryActiveStepWasChecked() {
        LocalDate occurrence = LocalDate.parse("2026-08-10");
        Obligation routine = routine(occurrence);
        RoutineStep first = new RoutineStep("Erster Schritt", EnumSet.noneOf(DayOfWeek.class));
        RoutineStep second = new RoutineStep("Zweiter Schritt", EnumSet.noneOf(DayOfWeek.class));
        routine.steps.add(first);
        routine.steps.add(second);

        first.setCompletedFor(occurrence, true, LocalDateTime.parse("2026-08-10T08:00:00"));
        assertFalse(routine.allActiveStepsCompleted(occurrence));

        second.setCompletedFor(occurrence, true, LocalDateTime.parse("2026-08-10T08:05:00"));
        assertTrue(routine.allActiveStepsCompleted(occurrence));
    }

    @Test
    public void completedStepDoesNotCarryIntoTheNextOccurrence() {
        LocalDate occurrence = LocalDate.parse("2026-08-10");
        Obligation routine = routine(occurrence);
        RoutineStep step = new RoutineStep("Wiederkehrend", EnumSet.noneOf(DayOfWeek.class));
        step.setCompletedFor(occurrence, true, LocalDateTime.parse("2026-08-10T08:00:00"));
        routine.steps.add(step);

        routine.nextDueDate = LocalDate.parse("2026-08-11");

        assertFalse(routine.planStepsFor(LocalDate.parse("2026-08-11")).get(0).completed());
    }

    private static Obligation routine(LocalDate due) {
        Obligation routine = new Obligation();
        routine.kind = Obligation.Kind.ROUTINE;
        routine.cadenceDays = 1;
        routine.nextDueDate = due;
        return routine;
    }
}

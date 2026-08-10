package com.autosecretary.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class FocusPlannerTest {
    private final FocusPlanner planner = new FocusPlanner();

    @Test
    public void learnedMorningOrderDoesNotFlipWhenNowIsAlreadyNoon() {
        LocalDate today = LocalDate.parse("2026-08-10");
        Obligation morning = routine("morning", "Morgenroutine", today);
        Obligation lunch = routine("lunch", "Mittagsroutine", today);
        List<Completion> evidence = List.of(
                completion("morning", "2026-08-03T08:10:00"),
                completion("lunch", "2026-08-03T12:20:00"),
                completion("morning", "2026-08-04T08:30:00"),
                completion("lunch", "2026-08-04T12:10:00"));

        List<PlanItem> plan = planner.plan(
                List.of(lunch, morning), evidence, List.of(),
                LocalDateTime.parse("2026-08-10T12:00:00"), 3);

        assertEquals("morning", plan.get(0).obligation().id);
        assertEquals("lunch", plan.get(1).obligation().id);
    }

    @Test
    public void manualOrderOverridesCalculatedUrgencyForToday() {
        LocalDate today = LocalDate.parse("2026-08-10");
        Obligation first = routine("first", "Erste Routine", today);
        Obligation second = routine("second", "Zweite Routine", today);
        first.manualOrderOn = today;
        first.manualOrderRank = 2;
        second.manualOrderOn = today;
        second.manualOrderRank = 1;

        List<PlanItem> plan = planner.plan(
                List.of(first, second), List.of(), List.of(),
                LocalDateTime.parse("2026-08-10T09:00:00"), 3);

        assertEquals("second", plan.get(0).obligation().id);
        assertEquals("first", plan.get(1).obligation().id);
    }

    @Test
    public void explicitEveningPreferenceDelaysSuggestionUntilEvening() {
        LocalDate today = LocalDate.parse("2026-08-10");
        Obligation evening = routine("evening", "Abendroutine", today);
        evening.timePreference = TimePreference.EVENING;

        PlanItem item = planner.plan(
                List.of(evening), List.of(), List.of(),
                LocalDateTime.parse("2026-08-10T09:00:00"), 3).get(0);

        assertEquals(LocalDateTime.parse("2026-08-10T18:00:00"), item.suggestedStart());
    }

    @Test
    public void learnedCompletionTimeDelaysSuggestionWhenNoPreferenceWasSet() {
        LocalDate today = LocalDate.parse("2026-08-10");
        Obligation learned = routine("learned", "Gelernte Routine", today);

        PlanItem item = planner.plan(
                List.of(learned), List.of(completion("learned", "2026-08-03T08:30:00")), List.of(),
                LocalDateTime.parse("2026-08-10T07:00:00"), 3).get(0);

        assertEquals(LocalDateTime.parse("2026-08-10T08:30:00"), item.suggestedStart());
    }

    @Test
    public void inflexibleItemIgnoresLearnedCompletionTime() {
        LocalDate today = LocalDate.parse("2026-08-10");
        Obligation fixed = routine("fixed", "Feste Routine", today);
        fixed.flexible = false;

        PlanItem item = planner.plan(
                List.of(fixed), List.of(completion("fixed", "2026-08-03T18:30:00")), List.of(),
                LocalDateTime.parse("2026-08-10T09:00:00"), 3).get(0);

        assertEquals(LocalDateTime.parse("2026-08-10T09:00:00"), item.suggestedStart());
    }

    @Test
    public void flexiblePreferenceKeepsLearningNearPreferredWindow() {
        LocalDate today = LocalDate.parse("2026-08-10");
        Obligation anchored = routine("anchored", "Flexible Morgenroutine", today);
        anchored.timePreference = TimePreference.MORNING;
        anchored.flexible = true;

        PlanItem item = planner.plan(
                List.of(anchored), List.of(completion("anchored", "2026-08-03T18:30:00")), List.of(),
                LocalDateTime.parse("2026-08-10T06:00:00"), 3).get(0);

        assertEquals(LocalDateTime.parse("2026-08-10T08:40:00"), item.suggestedStart());
    }

    @Test
    public void planningAvoidsCalendarConflictsWithoutMutatingCalendar() {
        LocalDate today = LocalDate.parse("2026-08-10");
        Obligation task = routine("focus", "Fokus", today);
        task.durationMinutes = 30;
        CalendarBlock appointment = new CalendarBlock(
                LocalDateTime.parse("2026-08-10T09:10:00"),
                LocalDateTime.parse("2026-08-10T10:00:00"),
                "Arzttermin");

        PlanItem item = planner.plan(
                List.of(task), List.of(), List.of(appointment),
                LocalDateTime.parse("2026-08-10T09:00:00"), 3).get(0);

        assertEquals(LocalDateTime.parse("2026-08-10T10:15:00"), item.suggestedStart());
        assertEquals(LocalDateTime.parse("2026-08-10T10:45:00"), item.suggestedEnd());
        assertEquals("Arzttermin", item.precedingCalendarBlock().title());
        assertEquals(appointment, new CalendarBlock(
                LocalDateTime.parse("2026-08-10T09:10:00"),
                LocalDateTime.parse("2026-08-10T10:00:00"),
                "Arzttermin"));
    }

    @Test
    public void focusSurfaceIsStrictlyLimitedAndCanMarkNoRemainingSlot() {
        LocalDate today = LocalDate.parse("2026-08-10");
        Obligation a = routine("a", "A", today);
        Obligation b = routine("b", "B", today);
        Obligation c = routine("c", "C", today);
        Obligation d = routine("d", "D", today);
        a.durationMinutes = 180;

        List<PlanItem> plan = planner.plan(
                List.of(a, b, c, d), List.of(), List.of(),
                LocalDateTime.parse("2026-08-10T21:00:00"), 3);

        assertEquals(3, plan.size());
        assertNull(plan.get(0).suggestedStart());
    }

    private static Obligation routine(String id, String title, LocalDate due) {
        Obligation item = new Obligation();
        item.id = id;
        item.kind = Obligation.Kind.ROUTINE;
        item.title = title;
        item.cadenceDays = 1;
        item.nextDueDate = due;
        item.createdAt = LocalDateTime.parse("2026-01-01T00:00:00");
        return item;
    }

    private static Completion completion(String obligationId, String at) {
        return new Completion(obligationId + at, obligationId, LocalDateTime.parse(at));
    }
}

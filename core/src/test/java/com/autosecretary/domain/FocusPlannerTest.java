package com.autosecretary.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class FocusPlannerTest {
    private final FocusPlanner planner = new FocusPlanner();

    @Test
    public void urgentEveningItemDoesNotPushMorningItemIntoEvening() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 7, 0);
        Task evening = task("evening", "Abgabe", 60,
                LocalDateTime.of(2026, 8, 11, 22, 0), TimePreference.EVENING);
        Task morning = task("morning", "Lesen", 45, null, null);

        PlanningResult result = planner.plan(List.of(evening, morning), List.of(), List.of(),
                List.of(), PlanningSettings.defaults(), now);

        assertEquals(id("morning"), result.assignments().get(0).workItem().id());
        assertTrue(result.assignments().get(0).start().getHour() < 12);
        assertEquals(id("evening"), result.assignments().get(1).workItem().id());
        assertTrue(result.assignments().get(1).start().getHour() >= 17);
    }

    @Test
    public void taskMustFinishBeforeItsDeadline() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 8, 0);
        Task impossible = task("deadline", "Zu knapp", 60,
                LocalDateTime.of(2026, 8, 11, 8, 30), null);

        PlanningResult result = planner.plan(List.of(impossible), List.of(), List.of(),
                List.of(), PlanningSettings.defaults(), now);

        assertTrue(result.assignments().isEmpty());
        assertEquals(PlanConflict.Reason.AFTER_DEADLINE, result.conflicts().get(0).reason());
    }

    @Test
    public void deadlineBoundsEveningPreferenceInsteadOfDiscardingMorningCapacity() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 7, 0);
        Task item = task("deadline-evening", "Früh fertig", 30,
                LocalDateTime.of(2026, 8, 11, 10, 0), TimePreference.EVENING);

        PlanningResult result = planner.plan(List.of(item), List.of(), List.of(), List.of(),
                PlanningSettings.defaults(), now);

        assertEquals(1, result.assignments().size());
        assertEquals(LocalDateTime.of(2026, 8, 11, 10, 0),
                result.assignments().get(0).end());
        assertTrue(result.conflicts().isEmpty());
    }

    @Test
    public void deadlineConflictReportsCapacityWhenNoGapCanFitAtAll() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 7, 0);
        Task item = task("deadline-no-capacity", "Zu lang", 60,
                LocalDateTime.of(2026, 8, 11, 12, 0), null);
        List<BusyInterval> calendar = List.of(
                new BusyInterval(LocalDateTime.of(2026, 8, 11, 7, 20),
                        LocalDateTime.of(2026, 8, 11, 21, 40), "Block"));
        PlanningSettings defaults = PlanningSettings.defaults();
        PlanningSettings oneDay = new PlanningSettings(defaults.day(), defaults.morning(),
                defaults.midday(), defaults.evening(), defaults.taskTransitionMinutes(),
                defaults.calendarBufferBeforeMinutes(), defaults.calendarBufferAfterMinutes(), 1);

        PlanningResult result = planner.plan(List.of(item), List.of(), calendar, List.of(),
                oneDay, now);

        assertEquals(PlanConflict.Reason.NO_CAPACITY, result.conflicts().get(0).reason());
    }

    @Test
    public void nextCalendarDayWinsAcrossYearBoundary() {
        LocalDateTime now = LocalDateTime.of(2026, 12, 31, 21, 50);
        Routine januaryFirst = new Routine(id("jan1"), "Neujahr", 10, null, null, true,
                List.of(), now.minusDays(1), 1, LocalDate.of(2027, 1, 1),
                CompletionStats.empty(), 0);
        Routine januarySecond = new Routine(id("jan2"), "Danach", 10, null, null, true,
                List.of(), now.minusDays(1), 1, LocalDate.of(2027, 1, 2),
                CompletionStats.empty(), 0);

        PlanningResult result = planner.plan(List.of(januarySecond, januaryFirst), List.of(),
                List.of(), List.of(), PlanningSettings.defaults(), now);

        assertEquals(LocalDate.of(2027, 1, 1), result.assignments().get(0).start().toLocalDate());
        assertEquals(id("jan1"), result.assignments().get(0).workItem().id());
        assertEquals(LocalDate.of(2027, 1, 2), result.assignments().get(1).start().toLocalDate());
    }

    @Test
    public void learnedTimeNeedsThreeSamplesAndRemainsBoundedByPreference() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 7, 0);
        Task item = task("learned", "Gewohnheit", 30, null, TimePreference.MORNING);
        List<CompletionEvidence> evidence = List.of(
                new CompletionEvidence(item.id(), now.minusDays(3).withHour(14)),
                new CompletionEvidence(item.id(), now.minusDays(2).withHour(14)),
                new CompletionEvidence(item.id(), now.minusDays(1).withHour(14)));

        PlanningResult result = planner.plan(List.of(item), evidence, List.of(), List.of(),
                PlanningSettings.defaults(), now);

        int hour = result.assignments().get(0).start().getHour();
        assertTrue(hour >= 7 && hour <= 11);
    }

    @Test
    public void newUrgentTaskStaysAheadOfExistingManualDirective() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 7, 0);
        Task manuallyFirst = task("manual", "Manuell", 30, null, null);
        Task ordinary = task("ordinary", "Normal", 30, null, null);
        Task urgent = task("urgent", "Heute fällig", 30,
                LocalDateTime.of(2026, 8, 11, 12, 0), null);
        PlanOrderDirective directive = new PlanOrderDirective(
                now.toLocalDate(), manuallyFirst.id(),
                PlanOrderDirective.Relation.FIRST, null, now.minusHours(1));

        PlanningResult result = planner.plan(List.of(manuallyFirst, ordinary, urgent),
                List.of(), List.of(), List.of(directive), PlanningSettings.defaults(), now);

        assertEquals(urgent.id(), result.assignments().get(0).workItem().id());
        assertEquals(manuallyFirst.id(), result.assignments().get(1).workItem().id());
    }

    @Test
    public void planningNeverStartsBeforeTheCurrentMinuteHasFinished() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 8, 0, 45);

        PlanningResult result = planner.plan(List.of(task("ceil", "Jetzt", 30, null, null)),
                List.of(), List.of(), List.of(), PlanningSettings.defaults(), now);

        assertEquals(LocalDateTime.of(2026, 8, 11, 8, 1),
                result.assignments().get(0).start());
    }

    @Test
    public void omittedItemLeavesTodayWithoutBeingCompletedOrDeleted() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 7, 0);
        Task omitted = task("omitted", "Heute nicht", 30, null, null);
        Task retained = task("retained", "Bleibt", 30, null, null);
        PlanOrderDirective directive = new PlanOrderDirective(now.toLocalDate(), omitted.id(),
                PlanOrderDirective.Relation.OMIT, null, now);

        PlanningResult result = planner.plan(List.of(omitted, retained), List.of(), List.of(),
                List.of(directive), PlanningSettings.defaults(), now);

        assertEquals(List.of(retained.id()), result.assignments().stream()
                .map(value -> value.workItem().id()).toList());
        assertTrue(!omitted.completed());
    }

    @Test
    public void transitionBoostNeedsThreeObservationsAndSixtyPercentConfidence() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 7, 0);
        Task next = task("next", "Zulu", 30, null, null);
        Task alternative = task("alternative", "Alpha", 30, null, null);
        String previousId = id("previous");
        List<CompletionEvidence> evidence = new java.util.ArrayList<>();
        for (int day = 1; day <= 3; day++) {
            evidence.add(new CompletionEvidence(previousId, now.minusDays(day).withHour(6)));
            evidence.add(new CompletionEvidence(next.id(), now.minusDays(day).withHour(7)));
        }
        for (int day = 4; day <= 5; day++) {
            evidence.add(new CompletionEvidence(previousId, now.minusDays(day).withHour(6)));
            evidence.add(new CompletionEvidence(alternative.id(), now.minusDays(day).withHour(7)));
        }
        evidence.add(new CompletionEvidence(previousId, now.minusMinutes(5)));

        PlanningResult result = planner.plan(List.of(alternative, next), evidence,
                List.of(), List.of(), PlanningSettings.defaults(), now);

        assertEquals(next.id(), result.assignments().get(0).workItem().id());
    }

    private static Task task(
            String id,
            String title,
            int duration,
            LocalDateTime deadline,
            TimePreference preference) {
        return new Task(id(id), title, duration, deadline, preference, true, List.of(),
                LocalDateTime.of(2026, 8, 1, 8, 0), false, CompletionStats.empty(), 0);
    }

    private static String id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }
}

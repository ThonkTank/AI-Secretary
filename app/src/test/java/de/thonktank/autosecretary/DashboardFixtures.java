package de.thonktank.autosecretary;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;

/** Deterministic examples shared by characterization tests and future presenter tests. */
final class DashboardFixtures {
    private DashboardFixtures() { }

    static DashboardState emptyDashboard() {
        return new DashboardState(0, Collections.emptyList());
    }

    static TaskSnapshot simpleTask() {
        return task("simple", "Brief beantworten", TaskSlot.MORNING, Recurrence.ONCE,
                Collections.emptyList(), 0, false, false, false, false, 0, 1_001_000L);
    }

    static TaskSnapshot taskWithSteps() {
        return task("steps", "Morgenroutine", TaskSlot.MORNING, Recurrence.DAILY,
                Arrays.asList(
                        new TaskStepSnapshot("step-1", "Duschen", true),
                        new TaskStepSnapshot("step-2", "Anziehen", false)),
                1, false, false, false, false, 6, 1_002_000L);
    }

    static TaskSnapshot overdueTask() {
        return task("overdue", "Rechnung bezahlen", TaskSlot.MIDDAY, Recurrence.ONCE,
                Collections.emptyList(), 0, false, false, false, true, 0, 2_001_000L);
    }

    static TaskSnapshot recurringTask() {
        return task("routine", "Abendrunde", TaskSlot.EVENING, Recurrence.WEEKDAYS,
                Collections.emptyList(), 0, false, false, false, false, 4, 3_001_000L);
    }

    static TaskSnapshot ongoingTask() {
        return task("ongoing", "Praktikum", TaskSlot.LATER, Recurrence.ONCE,
                Collections.emptyList(), 0, true, true, false, false, 0, 4_001_000L);
    }

    static TaskSnapshot completedTodayTask() {
        return task("done", "Tabletten nehmen", TaskSlot.MORNING, Recurrence.DAILY,
                Collections.emptyList(), 0, false, false, true, false, 3, 1_003_000L);
    }

    static DashboardState fullDashboard() {
        return new DashboardState(120, Arrays.asList(
                taskWithSteps(), completedTodayTask(), overdueTask(), recurringTask(), ongoingTask()));
    }

    static List<CalendarEventSnapshot> calendarEvents() {
        return Arrays.asList(
                new CalendarEventSnapshot("ganztägig", "Urlaub", 0),
                new CalendarEventSnapshot("10:15", "Arzttermin", 10 * 60 + 15));
    }

    private static TaskSnapshot task(String id, String title, TaskSlot slot, Recurrence recurrence,
                                     List<TaskStepSnapshot> steps, int remainingSteps,
                                     boolean terminalCondition, boolean ongoing, boolean done,
                                     boolean overdue, int comboStage, long displayOrder) {
        return new TaskSnapshot(id, done ? "occurrence-done" : "occurrence-" + id,
                title, slot, "weiche Zeit", terminalCondition ? "Bedingung" : "Nächster Schritt",
                recurrence, steps, remainingSteps, terminalCondition, ongoing, done, overdue,
                comboStage, displayOrder);
    }
}

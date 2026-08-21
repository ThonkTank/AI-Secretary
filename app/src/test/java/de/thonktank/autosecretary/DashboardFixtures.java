package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.today.TodayUiModel;

import de.thonktank.autosecretary.presentation.FocusStepUiModel;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.XpProgress;
import de.thonktank.autosecretary.widget.WidgetDashboardUiModel;
import de.thonktank.autosecretary.widget.WidgetStepUiModel;
import de.thonktank.autosecretary.widget.WidgetTaskUiModel;

/** Deterministic examples shared by characterization tests and future presenter tests. */
final class DashboardFixtures {
    private DashboardFixtures() { }

    static TodayUiModel emptyDashboard() {
        return today(0, Collections.emptyList());
    }

    static TaskSnapshot simpleTask() {
        return task("simple", "Brief beantworten", TaskSlot.MORNING, Recurrence.ONCE,
                Collections.emptyList(), 0, false, false, false, false, 0, 1_001_000L);
    }

    static TaskSnapshot taskWithSteps() {
        return task("steps", "Morgenroutine", TaskSlot.MORNING, Recurrence.DAILY,
                Arrays.asList(
                        FocusStepUiModel.of("step-1", "Duschen", true),
                        FocusStepUiModel.of("step-2", "Anziehen", false)),
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

    static TodayUiModel fullDashboard() {
        return today(120, Arrays.asList(
                taskWithSteps(), completedTodayTask(), overdueTask(), recurringTask(), ongoingTask()));
    }

    static WidgetDashboardUiModel widgetDashboard() {
        WidgetTaskUiModel focus = WidgetTaskUiModel.of("steps", "occurrence-steps",
                "Morgenroutine", false, false, "Rest erledigen", Arrays.asList(
                        WidgetStepUiModel.of("step-1", "Duschen",
                                "3 Sätze · 8 Wiederholungen", true),
                        WidgetStepUiModel.of("step-2", "Anziehen", "", false)));
        return WidgetDashboardUiModel.of(focus, "Rechnung bezahlen");
    }

    static WidgetDashboardUiModel emptyWidgetDashboard() {
        return WidgetDashboardUiModel.empty();
    }

    static WidgetDashboardUiModel ongoingWidgetDashboard() {
        return WidgetDashboardUiModel.of(WidgetTaskUiModel.of("ongoing",
                "occurrence-ongoing", "Praktikum", false, true, "Bedingung erfüllt",
                Collections.emptyList()), null);
    }

    static List<CalendarEventSnapshot> calendarEvents() {
        return Arrays.asList(
                new CalendarEventSnapshot("ganztägig", "Urlaub", 0),
                new CalendarEventSnapshot("10:15", "Arzttermin", 10 * 60 + 15));
    }

    private static TaskSnapshot task(String id, String title, TaskSlot slot, Recurrence recurrence,
                                     List<FocusStepUiModel> steps, int remainingSteps,
                                     boolean terminalCondition, boolean ongoing, boolean done,
                                     boolean overdue, int comboStage, long displayOrder) {
        return new TaskSnapshot(id, done ? "occurrence-done" : "occurrence-" + id,
                title, slot, "weiche Zeit", terminalCondition ? "Bedingung" : "Nächster Schritt",
                recurrence, steps, remainingSteps, terminalCondition, ongoing, done, overdue,
                comboStage, displayOrder);
    }

    static TodayUiModel today(int xp, List<TaskSnapshot> tasks) {
        TaskSnapshot focus = null;
        for (TaskSnapshot task : tasks) if (!task.done) { focus = task; break; }
        return new TodayUiModel(xp, new XpProgress(xp), tasks, focus);
    }
}

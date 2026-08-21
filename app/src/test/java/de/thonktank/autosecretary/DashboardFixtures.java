package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.today.CalendarEventSnapshot;

import java.util.Arrays;
import java.util.Collections;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.XpProgress;
import de.thonktank.autosecretary.presentation.today.CompletedTaskUiModel;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TimelineItemUiModel;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.widget.WidgetDashboardUiModel;
import de.thonktank.autosecretary.widget.WidgetStepUiModel;
import de.thonktank.autosecretary.widget.WidgetTaskUiModel;

/** Deterministic named examples shared by characterization and presenter tests. */
final class DashboardFixtures {
    private DashboardFixtures() { }

    static TodayUiModel emptyDashboard() { return TodayUiModel.empty(); }

    static FocusTaskUiModel simpleTask() {
        return FocusTaskFixtures.task("simple", "Brief beantworten")
                .occurrence("occurrence-simple").build();
    }

    static FocusTaskUiModel taskWithSteps() {
        return FocusTaskFixtures.task("steps", "Morgenroutine")
                .occurrence("occurrence-steps").recurrence(Recurrence.DAILY).combo(6)
                .allowDefer(true)
                .steps(Arrays.asList(
                        FocusTaskFixtures.simpleStep("step-1", "Duschen", true),
                        FocusTaskFixtures.simpleStep("step-2", "Anziehen", false)))
                .build();
    }

    static FocusTaskUiModel overdueTask() {
        return FocusTaskFixtures.task("overdue", "Rechnung bezahlen")
                .occurrence("occurrence-overdue").slot(TaskSlot.MIDDAY).overdue(true).build();
    }

    static FocusTaskUiModel recurringTask() {
        return FocusTaskFixtures.task("routine", "Abendrunde")
                .occurrence("occurrence-routine").slot(TaskSlot.EVENING)
                .recurrence(Recurrence.WEEKDAYS).combo(4).build();
    }

    static FocusTaskUiModel ongoingTask() {
        return FocusTaskFixtures.task("ongoing", "Praktikum")
                .occurrence("occurrence-ongoing").slot(TaskSlot.LATER)
                .terminal(true).ongoing(true).build();
    }

    static CompletedTaskUiModel completedTodayTask() {
        return CompletedTaskUiModel.of("occurrence-done", "Tabletten nehmen", 10, true);
    }

    static TodayUiModel fullDashboard() {
        FocusTaskUiModel focus = taskWithSteps();
        return new TodayUiModel(new XpProgress(120), focus, Arrays.asList(
                TimelineItemUiModel.task(FocusTaskFixtures.timeline(overdueTask(),
                        "weiche Zeit", 2_001_000L)),
                TimelineItemUiModel.task(FocusTaskFixtures.timeline(recurringTask(),
                        "weiche Zeit", 3_001_000L)),
                TimelineItemUiModel.task(FocusTaskFixtures.timeline(ongoingTask(),
                        "weiche Zeit", 4_001_000L))),
                Collections.singletonList(completedTodayTask()));
    }

    static WidgetDashboardUiModel widgetDashboard() {
        WidgetTaskUiModel focus = WidgetTaskUiModel.of("steps", "occurrence-steps",
                "Morgenroutine", false, false, "Rest erledigen", Arrays.asList(
                        WidgetStepUiModel.of("step-1", "Duschen",
                                "3 Sätze · 8 Wiederholungen", true),
                        WidgetStepUiModel.of("step-2", "Anziehen", "", false)));
        return WidgetDashboardUiModel.of(focus, "Rechnung bezahlen");
    }

    static WidgetDashboardUiModel emptyWidgetDashboard() { return WidgetDashboardUiModel.empty(); }

    static WidgetDashboardUiModel ongoingWidgetDashboard() {
        return WidgetDashboardUiModel.of(WidgetTaskUiModel.of("ongoing",
                "occurrence-ongoing", "Praktikum", false, true, "Bedingung erfüllt",
                Collections.emptyList()), null);
    }

    static java.util.List<CalendarEventSnapshot> calendarEvents() {
        return Arrays.asList(new CalendarEventSnapshot("ganztägig", "Urlaub", 0),
                new CalendarEventSnapshot("10:15", "Arzttermin", 10 * 60 + 15));
    }
}

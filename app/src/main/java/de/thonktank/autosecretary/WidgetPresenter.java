package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.TaskStepUiModel;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.calendar.CalendarResult;

public final class WidgetPresenter {
    public static final class CycleData {
        final TodayUiModel dashboard;
        final CalendarResult calendar;
        final DayPalette palette;

        CycleData(TodayUiModel dashboard, CalendarResult calendar, DayPalette palette) {
            this.dashboard = dashboard;
            this.calendar = calendar;
            this.palette = palette;
        }
    }

    private final Context context;
    private final AppContainer container;

    WidgetPresenter(Context context, AppContainer container) {
        this.context = context.getApplicationContext();
        this.container = container;
    }

    WidgetPresenter(Context context) {
        this(context, null);
    }

    CycleData load() {
        DayPalette.Mode mode = DayPalette.Mode.valueOf(container.uiPreferences.themeMode().name());
        return new CycleData(container.dashboardPresenter.refresh(), container.calendar.loadToday(),
                DayPalette.at(container.clock.time(), mode));
    }

    WidgetUiModel present(CycleData data, WidgetSizeClassifier.Size size) {
        TaskSnapshot focus = data.dashboard.firstOpen();
        if (focus == null) return empty(data.palette, size);
        List<WidgetUiModel.Step> steps = new ArrayList<>();
        if (size != WidgetSizeClassifier.Size.SMALL) {
            for (int i = 0; i < Math.min(3, focus.steps.size()); i++) {
                TaskStepUiModel step = focus.steps.get(i);
                steps.add(new WidgetUiModel.Step(step.id, step.title, step.done));
            }
        }
        List<Boolean> progress = new ArrayList<>();
        for (int i = 0; i < Math.min(3, focus.steps.size()); i++)
            progress.add(focus.steps.get(i).done);
        String after = size == WidgetSizeClassifier.Size.LARGE
                ? nextOpenTitle(data.dashboard, focus) : null;
        WidgetUiModel.CalendarItem calendar = supportsCalendar(size)
                ? firstCalendar(data.calendar) : null;
        WidgetUiModel.PrimaryAction action = size == WidgetSizeClassifier.Size.LARGE
                ? WidgetUiModel.PrimaryAction.NONE
                : focus.terminalCondition ? WidgetUiModel.PrimaryAction.CONFIRM_CLOSE
                : WidgetUiModel.PrimaryAction.COMPLETE_OCCURRENCE;
        String actionId = focus.terminalCondition ? focus.taskId : focus.occurrenceId;
        return new WidgetUiModel(size, data.palette,
                context.getString(focus.overdue ? R.string.marker_overdue : R.string.marker_now),
                focus.title, focus.overdue, false, steps, progress,
                Math.max(0, focus.steps.size() - 3), after, calendar, action, actionId,
                size == WidgetSizeClassifier.Size.LARGE ? null : focus.actionLabel(context),
                size == WidgetSizeClassifier.Size.LARGE, focus.title);
    }

    private WidgetUiModel empty(DayPalette palette, WidgetSizeClassifier.Size size) {
        return new WidgetUiModel(size, palette, context.getString(R.string.nav_today),
                context.getString(R.string.widget_empty_title), false, true,
                Collections.emptyList(), Collections.emptyList(), 0, null, null,
                size == WidgetSizeClassifier.Size.LARGE ? WidgetUiModel.PrimaryAction.NONE
                        : WidgetUiModel.PrimaryAction.OPEN_EDITOR,
                null, size == WidgetSizeClassifier.Size.LARGE ? null
                        : context.getString(R.string.action_add_task),
                size == WidgetSizeClassifier.Size.LARGE, "");
    }

    private static boolean supportsCalendar(WidgetSizeClassifier.Size size) {
        return size == WidgetSizeClassifier.Size.TALL || size == WidgetSizeClassifier.Size.LARGE;
    }

    private static WidgetUiModel.CalendarItem firstCalendar(CalendarResult result) {
        if (!(result instanceof CalendarResult.Success) || result.events().isEmpty()) return null;
        CalendarEventSnapshot event = result.events().get(0);
        return new WidgetUiModel.CalendarItem(event.time, event.title);
    }

    private static String nextOpenTitle(TodayUiModel state, TaskSnapshot focus) {
        for (TaskSnapshot task : state.tasks)
            if (!task.done && task != focus) return task.title;
        return null;
    }
}

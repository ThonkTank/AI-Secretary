package de.thonktank.autosecretary.widget;

import android.content.Context;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.calendar.CalendarResult;
import de.thonktank.autosecretary.AppContainer;
import de.thonktank.autosecretary.data.observable.ClockSnapshot;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidation;
import de.thonktank.autosecretary.presentation.today.CalendarEventSnapshot;
import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.WidgetSizeClassifier;

public final class WidgetPresenter {
    public static final class CycleData {
        final WidgetDashboardUiModel dashboard;
        final CalendarResult calendar;
        final DayPalette palette;

        public CycleData(WidgetDashboardUiModel dashboard, CalendarResult calendar,
                         DayPalette palette) {
            this.dashboard = dashboard;
            this.calendar = calendar;
            this.palette = palette;
        }
    }

    private final Context context;
    private final AppContainer container;
    private final WidgetDashboardMapper dashboardMapper;

    public WidgetPresenter(Context context, AppContainer container) {
        this.context = context.getApplicationContext();
        this.container = container;
        this.dashboardMapper = container == null ? null : new WidgetDashboardMapper(container.texts);
    }

    public WidgetPresenter(Context context) {
        this(context, null);
    }

    public void prepare() {
        container.dashboardPresenter.prepare();
    }

    public CycleData load() {
        return load(null);
    }

    public CycleData load(PresentationInvalidation invalidation) {
        ClockSnapshot clockSnapshot = invalidation == null ? null : invalidation.getClock();
        DayPalette.Mode mode = DayPalette.Mode.valueOf(container.uiPreferences.themeMode().name());
        LocalDateTime now = clockSnapshot == null ? container.clock.now() : null;
        LocalDate today = clockSnapshot == null ? now.toLocalDate() : clockSnapshot.getDate();
        java.time.LocalTime time = clockSnapshot == null
                ? now.toLocalTime() : clockSnapshot.getTime();
        WidgetDashboardUiModel dashboard = dashboardMapper.map(
                container.dashboardPresenter.loadDomain(today), today);
        return new CycleData(dashboard, container.calendar.loadToday(),
                DayPalette.at(time, mode));
    }

    public WidgetUiModel present(CycleData data, WidgetSizeClassifier.Size size) {
        WidgetTaskUiModel focus = data.dashboard.focus;
        if (focus == null) return empty(data.palette, size);
        List<WidgetStepUiModel> steps = new ArrayList<>();
        if (size != WidgetSizeClassifier.Size.SMALL) {
            for (int i = 0; i < Math.min(3, focus.steps.size()); i++) {
                steps.add(focus.steps.get(i));
            }
        }
        List<Boolean> progress = new ArrayList<>();
        for (int i = 0; i < Math.min(3, focus.steps.size()); i++)
            progress.add(focus.steps.get(i).done);
        String after = size == WidgetSizeClassifier.Size.LARGE
                ? data.dashboard.afterTitle : null;
        WidgetUiModel.CalendarItem calendar = supportsCalendar(size)
                ? firstCalendar(data.calendar) : null;
        WidgetUiModel.PrimaryAction action = size == WidgetSizeClassifier.Size.LARGE
                ? WidgetUiModel.PrimaryAction.NONE
                : focus.requiresApp ? WidgetUiModel.PrimaryAction.OPEN_APP
                : focus.terminalCondition ? WidgetUiModel.PrimaryAction.CONFIRM_CLOSE
                : WidgetUiModel.PrimaryAction.COMPLETE_OCCURRENCE;
        String actionId = focus.terminalCondition ? focus.taskId : focus.occurrenceId;
        return new WidgetUiModel(size, data.palette,
                context.getString(focus.overdue ? R.string.marker_overdue : R.string.marker_now),
                focus.title, focus.overdue, false, steps, progress,
                Math.max(0, focus.steps.size() - 3), after, calendar, action, actionId,
                size == WidgetSizeClassifier.Size.LARGE ? null : focus.primaryActionLabel,
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

}

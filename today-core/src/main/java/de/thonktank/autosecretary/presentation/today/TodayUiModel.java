package de.thonktank.autosecretary.presentation.today;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.thonktank.autosecretary.domain.model.XpProgress;
import de.thonktank.autosecretary.domain.model.FlowRunSummary;

/** Disjoint Today projection: focus, open timeline and completed history never overlap. */
public final class TodayUiModel {
    public final XpProgress xpProgress;
    public final FocusTaskUiModel focus;
    public final List<TimelineItemUiModel> timeline;
    public final List<CompletedTaskUiModel> completedToday;
    public final List<FlowRunSummary> flowRuns;
    public final List<OpenTask> openTasks;
    public final List<TimelineTaskUiModel> hiddenTasks;

    public static final class OpenTask {
        public final TimelineTaskUiModel row;
        public final FocusTaskUiModel detail;

        public OpenTask(TimelineTaskUiModel row, FocusTaskUiModel detail) {
            this.row = row;
            this.detail = detail;
        }
    }

    public TodayUiModel(XpProgress xpProgress, FocusTaskUiModel focus,
                        List<TimelineItemUiModel> timeline,
                        List<CompletedTaskUiModel> completedToday) {
        this(xpProgress, focus, timeline, completedToday, Collections.emptyList());
    }

    public TodayUiModel(XpProgress xpProgress, FocusTaskUiModel focus,
                        List<TimelineItemUiModel> timeline,
                        List<CompletedTaskUiModel> completedToday,
                        List<FlowRunSummary> flowRuns) {
        this(xpProgress, focus, timeline, completedToday, flowRuns,
                Collections.emptyList(), Collections.emptyList());
    }

    public TodayUiModel(XpProgress xpProgress, FocusTaskUiModel focus,
                        List<TimelineItemUiModel> timeline, List<CompletedTaskUiModel> completedToday,
                        List<FlowRunSummary> flowRuns, List<OpenTask> openTasks,
                        List<TimelineTaskUiModel> hiddenTasks) {
        if (xpProgress == null || timeline == null || completedToday == null || flowRuns == null)
            throw new IllegalArgumentException("Today projection is required");
        this.xpProgress = xpProgress;
        this.focus = focus;
        this.timeline = Collections.unmodifiableList(new ArrayList<>(timeline));
        this.completedToday = Collections.unmodifiableList(new ArrayList<>(completedToday));
        this.flowRuns = Collections.unmodifiableList(new ArrayList<>(flowRuns));
        this.openTasks = Collections.unmodifiableList(new ArrayList<>(openTasks));
        this.hiddenTasks = Collections.unmodifiableList(new ArrayList<>(hiddenTasks));
    }

    public TodayUiModel withCalendar(List<CalendarEventSnapshot> events) {
        List<TimelineItemUiModel> scheduled = new ArrayList<>();
        for (CalendarEventSnapshot event : events) scheduled.add(TimelineItemUiModel.event(event));
        scheduled.sort(Comparator.comparingInt(item -> item.minute));
        List<TimelineItemUiModel> values = new ArrayList<>();
        int nextEvent = 0;
        for (TimelineItemUiModel task : timeline) if (task.task != null) {
            while (nextEvent < scheduled.size() && scheduled.get(nextEvent).minute <= task.minute)
                values.add(scheduled.get(nextEvent++));
            values.add(task);
        }
        values.addAll(scheduled.subList(nextEvent, scheduled.size()));
        return new TodayUiModel(xpProgress, focus, values, completedToday, flowRuns,
                openTasks, hiddenTasks);
    }

    public TodayUiModel select(String selectedKey) {
        FocusTaskUiModel selected = null;
        List<TimelineItemUiModel> values = new ArrayList<>();
        for (OpenTask task : openTasks) {
            if (task.row.actionTarget.item.key().equals(selectedKey)) selected = task.detail;
            else values.add(TimelineItemUiModel.task(task.row));
        }
        List<CalendarEventSnapshot> events = new ArrayList<>();
        for (TimelineItemUiModel item : timeline) if (item.event != null) events.add(item.event);
        return new TodayUiModel(xpProgress, selected, values, completedToday, flowRuns,
                openTasks, hiddenTasks).withCalendar(events);
    }

    TodayUiModel withUpdatedFocus(FocusTaskUiModel updatedFocus) {
        return new TodayUiModel(xpProgress, updatedFocus, timeline, completedToday, flowRuns,
                openTasks, hiddenTasks);
    }

    public static TodayUiModel compose(TodayUiModel today, List<CalendarEventSnapshot> events) {
        return today.withCalendar(events);
    }

    public static TodayUiModel empty() {
        return new TodayUiModel(new XpProgress(0), null, Collections.emptyList(),
                Collections.emptyList());
    }
}

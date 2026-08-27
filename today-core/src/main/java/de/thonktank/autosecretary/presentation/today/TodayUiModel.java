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

    public TodayUiModel(XpProgress xpProgress, FocusTaskUiModel focus,
                        List<TimelineItemUiModel> timeline,
                        List<CompletedTaskUiModel> completedToday) {
        this(xpProgress, focus, timeline, completedToday, Collections.emptyList());
    }

    public TodayUiModel(XpProgress xpProgress, FocusTaskUiModel focus,
                        List<TimelineItemUiModel> timeline,
                        List<CompletedTaskUiModel> completedToday,
                        List<FlowRunSummary> flowRuns) {
        if (xpProgress == null || timeline == null || completedToday == null || flowRuns == null)
            throw new IllegalArgumentException("Today projection is required");
        this.xpProgress = xpProgress;
        this.focus = focus;
        this.timeline = Collections.unmodifiableList(new ArrayList<>(timeline));
        this.completedToday = Collections.unmodifiableList(new ArrayList<>(completedToday));
        this.flowRuns = Collections.unmodifiableList(new ArrayList<>(flowRuns));
    }

    public TodayUiModel withCalendar(List<CalendarEventSnapshot> events) {
        List<TimelineItemUiModel> values = new ArrayList<>();
        for (TimelineItemUiModel item : timeline) if (item.task != null) values.add(item);
        for (CalendarEventSnapshot event : events) values.add(TimelineItemUiModel.event(event));
        values.sort(Comparator.comparingInt((TimelineItemUiModel item) -> item.minute)
                .thenComparingLong(item -> item.order));
        return new TodayUiModel(xpProgress, focus, values, completedToday, flowRuns);
    }

    public static TodayUiModel compose(TodayUiModel today, List<CalendarEventSnapshot> events) {
        return today.withCalendar(events);
    }

    public static TodayUiModel empty() {
        return new TodayUiModel(new XpProgress(0), null, Collections.emptyList(),
                Collections.emptyList());
    }
}

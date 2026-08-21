package de.thonktank.autosecretary.presentation.today;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.thonktank.autosecretary.CalendarEventSnapshot;
import de.thonktank.autosecretary.domain.model.XpProgress;

/** Disjoint Today projection: focus, open timeline and completed history never overlap. */
public final class TodayUiModel {
    public final XpProgress xpProgress;
    @Nullable public final FocusTaskUiModel focus;
    public final List<TimelineItemUiModel> timeline;
    public final List<CompletedTaskUiModel> completedToday;

    public TodayUiModel(XpProgress xpProgress, @Nullable FocusTaskUiModel focus,
                        List<TimelineItemUiModel> timeline,
                        List<CompletedTaskUiModel> completedToday) {
        if (xpProgress == null || timeline == null || completedToday == null)
            throw new IllegalArgumentException("Today projection is required");
        this.xpProgress = xpProgress;
        this.focus = focus;
        this.timeline = Collections.unmodifiableList(new ArrayList<>(timeline));
        this.completedToday = Collections.unmodifiableList(new ArrayList<>(completedToday));
    }

    public TodayUiModel withCalendar(List<CalendarEventSnapshot> events) {
        List<TimelineItemUiModel> values = new ArrayList<>();
        for (TimelineItemUiModel item : timeline) if (item.task != null) values.add(item);
        for (CalendarEventSnapshot event : events) values.add(TimelineItemUiModel.event(event));
        values.sort(Comparator.comparingInt((TimelineItemUiModel item) -> item.minute)
                .thenComparingLong(item -> item.order));
        return new TodayUiModel(xpProgress, focus, values, completedToday);
    }

    public static TodayUiModel compose(TodayUiModel today, List<CalendarEventSnapshot> events) {
        return today.withCalendar(events);
    }

    public static TodayUiModel empty() {
        return new TodayUiModel(new XpProgress(0), null, Collections.emptyList(),
                Collections.emptyList());
    }
}

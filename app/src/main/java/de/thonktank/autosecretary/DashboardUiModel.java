package de.thonktank.autosecretary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.thonktank.autosecretary.domain.model.XpProgress;

public final class DashboardUiModel {
    public final int xp;
    public final XpProgress xpProgress;
    public final List<TaskSnapshot> tasks;
    public final List<TimelineItemUiModel> timeline;

    private DashboardUiModel(int xp, List<TaskSnapshot> tasks,
                             List<TimelineItemUiModel> timeline) {
        this.xp = xp;
        this.xpProgress = new XpProgress(xp);
        this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
        this.timeline = Collections.unmodifiableList(new ArrayList<>(timeline));
    }

    public static DashboardUiModel compose(DashboardState dashboard,
                                           List<CalendarEventSnapshot> events) {
        TaskSnapshot focus = dashboard.firstOpen();
        List<TimelineItemUiModel> timeline = new ArrayList<>();
        for (TaskSnapshot task : dashboard.tasks)
            if (task != focus) timeline.add(TimelineItemUiModel.task(task));
        for (CalendarEventSnapshot event : events)
            timeline.add(TimelineItemUiModel.event(event));
        timeline.sort(Comparator.comparingInt((TimelineItemUiModel item) -> item.minute)
                .thenComparingLong(item -> item.order));
        return new DashboardUiModel(dashboard.xp, dashboard.tasks, timeline);
    }

    public static DashboardUiModel empty() {
        return compose(new DashboardState(0, Collections.emptyList()), Collections.emptyList());
    }

    public TaskSnapshot firstOpen() {
        for (TaskSnapshot task : tasks) if (!task.done) return task;
        return null;
    }
}

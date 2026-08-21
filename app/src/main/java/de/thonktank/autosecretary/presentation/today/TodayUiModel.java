package de.thonktank.autosecretary.presentation.today;

import de.thonktank.autosecretary.CalendarEventSnapshot;
import de.thonktank.autosecretary.TaskSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.thonktank.autosecretary.domain.model.XpProgress;
import de.thonktank.autosecretary.presentation.FocusStepUiModel;

/** Today screen model containing focus-card tasks and consumer-specific timeline items. */
public final class TodayUiModel {
    public final int xp;
    public final XpProgress xpProgress;
    public final List<TaskSnapshot> tasks;
    public final TaskSnapshot focus;
    public final List<TimelineItemUiModel> timeline;

    public TodayUiModel(int xp, XpProgress xpProgress, List<TaskSnapshot> tasks,
                        TaskSnapshot focus) {
        this(xp, xpProgress, tasks, focus, Collections.emptyList());
    }

    private TodayUiModel(int xp, XpProgress xpProgress, List<TaskSnapshot> tasks,
                         TaskSnapshot focus, List<TimelineItemUiModel> timeline) {
        this.xp = Math.max(0, xp);
        this.xpProgress = xpProgress;
        this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
        this.focus = focus;
        this.timeline = Collections.unmodifiableList(new ArrayList<>(timeline));
    }

    public TodayUiModel withCalendar(List<CalendarEventSnapshot> events) {
        List<TimelineItemUiModel> values = new ArrayList<>();
        for (TaskSnapshot task : tasks)
            if (task != focus) values.add(TimelineItemUiModel.task(timelineTask(task)));
        for (CalendarEventSnapshot event : events) values.add(TimelineItemUiModel.event(event));
        values.sort(Comparator.comparingInt((TimelineItemUiModel item) -> item.minute)
                .thenComparingLong(item -> item.order));
        return new TodayUiModel(xp, xpProgress, tasks, focus, values);
    }

    public TaskSnapshot firstOpen() { return focus; }

    public static TodayUiModel compose(TodayUiModel today, List<CalendarEventSnapshot> events) {
        return today.withCalendar(events);
    }

    public static TodayUiModel empty() {
        return new TodayUiModel(0, new XpProgress(0), Collections.emptyList(), null);
    }

    private static TimelineTaskUiModel timelineTask(TaskSnapshot source) {
        List<TimelineStepUiModel> steps = new ArrayList<>();
        for (FocusStepUiModel step : source.steps)
            steps.add(TimelineStepUiModel.completion(step.done));
        return TimelineTaskUiModel.of(source.taskId, source.occurrenceId, source.title,
                source.slot, source.softTime, steps, source.terminalCondition, source.done,
                source.overdue, source.displayOrder, source.comboStage, source.claimableXp,
                source.awardedXp, source.undoAvailable);
    }
}

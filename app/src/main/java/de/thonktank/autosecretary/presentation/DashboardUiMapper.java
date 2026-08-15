package de.thonktank.autosecretary.presentation;

import de.thonktank.autosecretary.DashboardState;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.TaskSnapshot;
import de.thonktank.autosecretary.TaskStepSnapshot;
import de.thonktank.autosecretary.domain.model.Dashboard;
import de.thonktank.autosecretary.domain.model.DashboardTask;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class DashboardUiMapper {
    private final UiTextProvider texts;

    public DashboardUiMapper(UiTextProvider texts) {
        this.texts = texts;
    }

    public DashboardState map(Dashboard dashboard, LocalDate today) {
        List<TaskSnapshot> snapshots = new ArrayList<>();
        for (DashboardTask item : dashboard.tasks) snapshots.add(snapshot(item, today));
        return new DashboardState(dashboard.xp, snapshots);
    }

    private TaskSnapshot snapshot(DashboardTask item, LocalDate today) {
        Task task = item.task;
        List<TaskStepSnapshot> steps = new ArrayList<>();
        int remaining = 0;
        String next = task.conditionText;
        for (OccurrenceStep step : item.steps) {
            boolean done = item.done || step.done;
            steps.add(new TaskStepSnapshot(step.id, step.text, done));
            if (!done) {
                remaining++;
                if (remaining == 1) next = step.text;
            }
        }
        if (next == null || next.isEmpty())
            next = texts.text(steps.isEmpty() ? R.string.next_mark_done : R.string.next_all_done);
        boolean overdue = item.occurrence != null && !item.done
                && item.occurrence.scheduledOn.isBefore(today);
        return new TaskSnapshot(task.id.value, item.occurrence == null ? "" : item.occurrence.id,
                task.title, task.slot, softTime(task.slot, task.ongoing), next, task.recurrence,
                steps, remaining, !task.conditionText.isEmpty(), task.ongoing, item.done, overdue,
                task.recurrence == Recurrence.ONCE ? 0 : task.routineProgress.weekStreak,
                task.displayOrder);
    }

    public String softTime(TaskSlot slot, boolean ongoing) {
        if (ongoing) return texts.text(R.string.soft_time_ongoing);
        if (slot == TaskSlot.MORNING) return texts.text(R.string.soft_time_morning);
        if (slot == TaskSlot.MIDDAY) return texts.text(R.string.soft_time_midday);
        if (slot == TaskSlot.EVENING) return texts.text(R.string.soft_time_evening);
        return texts.text(R.string.soft_time_later);
    }
}

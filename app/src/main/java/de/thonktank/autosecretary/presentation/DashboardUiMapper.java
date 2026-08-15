package de.thonktank.autosecretary.presentation;

import de.thonktank.autosecretary.DashboardState;
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
            next = steps.isEmpty() ? "Als erledigt markieren" : "Alles erledigt";
        boolean overdue = item.occurrence != null && !item.done
                && item.occurrence.scheduledOn.isBefore(today);
        return new TaskSnapshot(task.id.value, item.occurrence == null ? "" : item.occurrence.id,
                task.title, task.slot, softTime(task.slot, task.ongoing), next, task.recurrence,
                steps, remaining, !task.conditionText.isEmpty(), task.ongoing, item.done, overdue,
                task.recurrence == Recurrence.ONCE ? 0 : task.routineProgress.weekStreak,
                task.displayOrder);
    }

    public static String softTime(TaskSlot slot, boolean ongoing) {
        if (ongoing) return "fortlaufend, bis die Bedingung erfüllt ist";
        if (slot == TaskSlot.MORNING) return "heute am Morgen";
        if (slot == TaskSlot.MIDDAY) return "um die Mittagszeit";
        if (slot == TaskSlot.EVENING) return "heute am Abend";
        return "später, sobald Platz ist";
    }
}
